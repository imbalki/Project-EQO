package ai.eqo.browser.cdp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance criterion (AC3): "Navigate and fill a local test form end to end". The controller
 * is driven against an in-memory [FakeCdpConnection] that simulates Chrome's DevTools responses,
 * so the navigate + fill + read-back composition is proven on the host. The real on-device run
 * is covered by the device test plan (PENDING owner presence) in the TASK-010 evidence.
 */
class ChromePageControllerTest {
    private val evalQueue = ArrayDeque<String>()

    private fun buildClient(): Triple<ChromeCdpClient, ChromePageController, FakeCdpConnection> {
        val conn = FakeCdpConnection()
        conn.responder = { req -> respond(req) }
        val client = ChromeCdpClient(conn.factory())
        return Triple(client, ChromePageController(client), conn)
    }

    private fun respond(req: FakeCdpConnection.FakeRequest): JsonObject =
        when (req.method) {
            "Target.attachToTarget" ->
                buildJsonObject {
                    put("id", req.id)
                    put("result", buildJsonObject { put("sessionId", "s-1") })
                }
            "Runtime.evaluate" -> {
                val value = evalQueue.removeFirstOrNull() ?: ""
                buildJsonObject {
                    put("id", req.id)
                    put("result", buildJsonObject { put("result", buildJsonObject { put("value", value) }) })
                }
            }
            else -> FakeCdpConnection.defaultResponse(req)
        }

    @Test
    fun navigateSendsPageEnableAndNavigate() {
        val (client, controller, conn) = buildClient()
        runBlocking {
            client.connect("ws://test/y")
            client.attachToTarget("t-1")
            controller.navigate("https://example.test/form")
        }
        val methods = conn.sent.map { it["method"]?.jsonPrimitive?.content }
        assertTrue("Page.enable" in methods)
        assertTrue("Page.navigate" in methods)
        val navigate = conn.sent.first { it["method"]?.jsonPrimitive?.content == "Page.navigate" }
        assertEquals(
            "https://example.test/form",
            navigate["params"]
                ?.jsonObject
                ?.get("url")
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun fillFieldSendsEvaluateEmbeddingSelectorAndText() {
        val (client, controller, conn) = buildClient()
        runBlocking {
            client.connect("ws://test/y")
            client.attachToTarget("t-1")
            evalQueue.add("alice")
            val returned = controller.fillField("#name", "alice")
            assertEquals("alice", returned)
        }
        val evaluate = conn.sent.last()
        assertEquals("Runtime.evaluate", evaluate["method"]?.jsonPrimitive?.content)
        val expression =
            evaluate["params"]
                ?.jsonObject
                ?.get("expression")
                ?.jsonPrimitive
                ?.content
        assertTrue(expression!!.contains("\"#name\""))
        assertTrue(expression.contains("\"alice\""))
    }

    @Test
    fun readFieldReturnsTheValueReadBack() {
        val (client, controller, _) = buildClient()
        runBlocking {
            client.connect("ws://test/y")
            client.attachToTarget("t-1")
            evalQueue.add("alice")
            assertEquals("alice", controller.readField("#name"))
        }
    }

    @Test
    fun fillFormNavigatesFillsAndReadsBackEndToEnd() {
        val (client, controller, conn) = buildClient()
        val url = "https://example.test/form"
        val fields =
            listOf(
                FormField("#name", "alice"),
                FormField("#email", "owner@example.invalid"),
            )
        // Per field: one fill evaluate (return ignored) then one read evaluate (returns the text).
        evalQueue.addAll(listOf("", "alice", "", "owner@example.invalid"))

        val result =
            runBlocking {
                client.connect("ws://test/y")
                client.attachToTarget("t-1")
                controller.fillForm(url, fields)
            }

        assertEquals(url, result.navigatedTo)
        assertEquals("alice", result.readBack["#name"])
        assertEquals("owner@example.invalid", result.readBack["#email"])
        assertTrue(result.allFieldsMatch(fields))

        val methods = conn.sent.map { it["method"]?.jsonPrimitive?.content }
        assertTrue("Page.navigate" in methods)
        assertEquals(4, methods.count { it == "Runtime.evaluate" }) // 2 fields * (fill + read)
    }

    @Test
    fun fillFormIssuesOneFillAndOneReadPerField() {
        val (client, controller, conn) = buildClient()
        val fields = listOf(FormField("#a", "1"), FormField("#b", "2"), FormField("#c", "3"))
        evalQueue.addAll(listOf("", "1", "", "2", "", "3"))
        runBlocking {
            client.connect("ws://test/y")
            client.attachToTarget("t-1")
            controller.fillForm("https://example.test/x", fields)
        }
        val evaluateCount = conn.sent.count { it["method"]?.jsonPrimitive?.content == "Runtime.evaluate" }
        assertEquals(6, evaluateCount) // 3 fields * (1 fill + 1 read)
    }
}
