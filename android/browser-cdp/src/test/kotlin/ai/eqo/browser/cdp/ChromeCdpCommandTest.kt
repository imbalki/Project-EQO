package ai.eqo.browser.cdp

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CDP message framing: request building and incoming-message parsing. These are the wire-shape
 * primitives the whole CDP stack depends on, so they are pinned here before the higher-level
 * navigate/fill behaviour.
 */
class ChromeCdpCommandTest {
    @Test
    fun buildRequestCarriesIdMethodParams() {
        val raw =
            buildCdpRequest(
                id = 7,
                method = "Page.navigate",
                params = buildJsonObject { put("url", "https://example.test") },
                sessionId = "s-1",
            )
        val json =
            kotlinx.serialization.json.Json
                .parseToJsonElement(raw)
                .jsonObject
        assertEquals(7, json["id"]?.jsonPrimitive?.content?.toInt())
        assertEquals("Page.navigate", json["method"]?.jsonPrimitive?.content)
        assertEquals("s-1", json["sessionId"]?.jsonPrimitive?.content)
        assertEquals(
            "https://example.test",
            json["params"]
                ?.jsonObject
                ?.get("url")
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun buildRequestOmitsSessionIdWhenNull() {
        val raw = buildCdpRequest(1, "Target.getTargets", buildJsonObject {}, sessionId = null)
        val json =
            kotlinx.serialization.json.Json
                .parseToJsonElement(raw)
                .jsonObject
        assertNull(json["sessionId"])
    }

    @Test
    fun parseResponseWithResult() {
        val msg = parseCdpMessage("""{"id":3,"result":{"ok":true}}""")
        assertTrue(msg is CdpIncoming.Response)
        msg as CdpIncoming.Response
        assertEquals(3, msg.id)
        assertNull(msg.error)
    }

    @Test
    fun parseResponseWithError() {
        val msg = parseCdpMessage("""{"id":4,"error":{"code":-32000,"message":"boom"}}""")
        msg as CdpIncoming.Response
        assertEquals(-32000, msg.error?.code)
        assertEquals("boom", msg.error?.message)
    }

    @Test
    fun parseEventCarriesMethodParamsSession() {
        val msg =
            parseCdpMessage(
                """{"method":"Page.loadEventFired","params":{"x":1},"sessionId":"s-9"}""",
            )
        msg as CdpIncoming.Event
        assertEquals("Page.loadEventFired", msg.method)
        assertEquals("s-9", msg.sessionId)
        assertEquals(
            1,
            msg.params["x"]
                ?.jsonPrimitive
                ?.content
                ?.toInt(),
        )
    }
}
