// Origin: EQO TASK-074 (#20), bounded JVM/Robolectric regressions for productivity, information and conversation.
package ai.eqo.actions.impl

import ai.eqo.accessibility.EqoAutomation
import ai.eqo.accessibility.TakeoverDetector
import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.ActionSchema
import ai.eqo.core.security.SensitiveMemoryStore
import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ProductivityInformationTest {
    private lateinit var context: Context
    private lateinit var takeover: TakeoverDetector
    private lateinit var registry: AndroidActionRegistry
    private val requested = mutableListOf<ActionPermission>()
    private val http = FakeHttp()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        takeover = TakeoverDetector()
        requested.clear()
        registry =
            AndroidActionRegistry.createWithStore(
                context,
                PermissionRequester {
                    requested += it
                    false
                },
                { EqoAutomation({ null }, { EqoAutomation.ServiceState.AVAILABLE }, takeover) },
                UnknownActionSink {},
                RegistryOptions().also {
                    it.informationHttp = http
                    it.memoryStore =
                        object : SensitiveMemoryStore {
                            override fun read(key: String): String? = null

                            override fun write(
                                key: String,
                                value: String,
                            ) = false

                            override fun remove(key: String) = false

                            override fun listKeys() = emptySet<String>()

                            override fun getAllDecrypted() = emptyMap<String, String>()

                            override fun clearAll() = false
                        }
                },
            )
    }

    @Test
    fun `all 27 new names are registered and schema-backed`() {
        val names =
            setOf(
                "CREATE_CALENDAR_EVENT",
                "LIST_CALENDAR_TODAY",
                "LIST_CALENDAR_WEEK",
                "SET_ALARM",
                "SET_TIMER",
                "SET_REMINDER",
                "ADD_NOTE",
                "READ_NOTES",
                "CREATE_TASK",
                "GET_MORNING_BRIEFING",
                "READ_AND_REMEMBER_SCREEN",
                "UPDATE_PREFERENCE",
                "WEB_SEARCH",
                "GET_NEWS",
                "GET_WEATHER",
                "SUMMARIZE_URL",
                "TRANSLATE",
                "DEFINE_WORD",
                "FACT_CHECK",
                "CALCULATE",
                "CONVERT_UNITS",
                "CURRENCY_CONVERT",
                "CHECK_STOCK",
                "RECALL_MEMORY",
                "QUERY_KNOWLEDGE_GRAPH",
                "CHAT",
                "ASK_USER",
            )
        assertEquals(27, names.size)
        assertTrue(registry.enabledActionNames.containsAll(names))
        names.forEach { assertNotNull(ActionSchema.getAction(it)) }
    }

    @Test
    fun `all missing database operations refuse without saving or capturing`() =
        runTest(timeout = 10.seconds) {
            val actions =
                mapOf(
                    "ADD_NOTE" to mapOf("text" to "private note"),
                    "READ_NOTES" to emptyMap(),
                    "CREATE_TASK" to mapOf("title" to "task"),
                    "GET_MORNING_BRIEFING" to emptyMap(),
                    "READ_AND_REMEMBER_SCREEN" to emptyMap(),
                    "UPDATE_PREFERENCE" to mapOf("key" to "diet", "value" to "private value"),
                    "RECALL_MEMORY" to mapOf("topic" to "notes"),
                    "QUERY_KNOWLEDGE_GRAPH" to emptyMap(),
                )
            for ((action, params) in actions) {
                val result = registry.execute(action, params)
                assertTrue("$action: $result", result is ActionResult.Failure)
                assertEquals("needs_database_batch_2", (result as ActionResult.Failure).fallback)
                assertFalse(result.error.orEmpty().contains("private"))
            }
            assertTrue(requested.isEmpty())
        }

    @Test
    fun `conversation returns input state rather than hanging`() =
        runTest(timeout = 10.seconds) {
            assertEquals("Hello", registry.execute("CHAT", mapOf("message" to "Hello")).data)
            val ask =
                registry.execute(
                    "ASK_USER",
                    mapOf("message" to "Which city?", "options" to " Mumbai, Delhi, , ", "paramKey" to "location"),
                ) as ActionResult.NeedsInput
            assertEquals(listOf("Mumbai", "Delhi"), ask.options)
            assertEquals("location", ask.metadata["paramKey"])
            assertFalse(ask.success)
        }

    @Test
    fun `search translation and page handoff never claim retrieved information`() =
        runTest(timeout = 10.seconds) {
            val samples =
                mapOf(
                    "WEB_SEARCH" to mapOf("query" to "a&b"),
                    "GET_NEWS" to mapOf("topic" to "science"),
                    "DEFINE_WORD" to mapOf("word" to "ephemeral"),
                    "FACT_CHECK" to mapOf("claim" to "claim"),
                    "CONVERT_UNITS" to mapOf("value" to "5", "from" to "miles", "to" to "km"),
                    "CURRENCY_CONVERT" to mapOf("amount" to "100", "from" to "USD", "to" to "INR"),
                    "CHECK_STOCK" to mapOf("symbol" to "AAPL"),
                    "TRANSLATE" to mapOf("text" to "a&b", "to" to "hi&x=1"),
                    "SUMMARIZE_URL" to mapOf("url" to "https://example.com/page"),
                )
            for ((action, params) in samples) {
                assertTrue(action, registry.execute(action, params) is ActionResult.UserActionRequired)
                val intent = shadowOf(context as android.app.Application).nextStartedActivity
                assertEquals("https", intent.data?.scheme)
                if (action == "WEB_SEARCH") assertEquals("a&b", intent.data?.getQueryParameter("q"))
                if (action == "TRANSLATE") assertEquals("hi&x=1", intent.data?.getQueryParameter("tl"))
            }
        }

    @Test
    fun `permission denial invalid inputs offline and cancellation refuse honestly`() =
        runTest(timeout = 10.seconds) {
            val created = registry.execute("CREATE_CALENDAR_EVENT", mapOf("title" to "meeting", "date" to "today"))
            assertFalse(created.success)
            assertEquals(Manifest.permission.WRITE_CALENDAR, (requested.single() as ActionPermission.Runtime).name)
            requested.clear()
            assertFalse(registry.execute("CREATE_CALENDAR_EVENT", emptyMap()).success)
            assertTrue(requested.isEmpty())
            http.connected = false
            assertFalse(registry.execute("WEB_SEARCH", mapOf("query" to "q")).success)
            assertFalse(registry.execute("GET_WEATHER", emptyMap()).success)
            assertTrue(requested.isEmpty())
            http.connected = true
            assertFalse(registry.execute("GET_WEATHER", emptyMap()).success)
            val permission = requested.single() as ActionPermission.Runtime
            assertEquals(Manifest.permission.ACCESS_COARSE_LOCATION, permission.name)
            http.failure = IllegalStateException("secret-token")
            val failedWeather = registry.execute("GET_WEATHER", mapOf("location" to "Mumbai"))
            assertTrue(failedWeather is ActionResult.UserActionRequired)
            assertFalse(failedWeather.error.orEmpty().contains("secret-token"))
            http.failure = CancellationException("cancelled")
            try {
                registry.execute("GET_WEATHER", mapOf("location" to "Mumbai"))
                fail("Expected cancellation")
            } catch (
                _: CancellationException,
            ) {
            }
        }

    @Test
    fun `calendar display and drafts remain manual and takeover rejects launching`() =
        runTest(timeout = 10.seconds) {
            for (name in listOf(
                "LIST_CALENDAR_TODAY",
                "LIST_CALENDAR_WEEK",
            )) {
                assertTrue(registry.execute(name, emptyMap()) is ActionResult.UserActionRequired)
            }
            assertTrue(
                registry.execute(
                    "CREATE_CALENDAR_EVENT",
                    mapOf("title" to "meeting", "date" to "tomorrow"),
                ) is ActionResult.UserActionRequired,
            )
            val reminder = registry.execute("SET_REMINDER", mapOf("title" to "medicine", "time" to "8pm"))
            assertTrue(reminder is ActionResult.UserActionRequired)
            takeover.onAgentActionStarted()
            takeover.onTouch(TakeoverDetector.TouchSource.USER)
            takeover.onAgentActionFinished()
            assertFalse(registry.execute("WEB_SEARCH", mapOf("query" to "q")).success)
            assertFalse(registry.execute("GET_WEATHER", mapOf("location" to "Mumbai")).success)
        }

    @Test
    fun `clock missing handlers and invalid time fail without success`() =
        runTest(timeout = 10.seconds) {
            assertFalse(registry.execute("SET_ALARM", mapOf("time" to "garbage")).success)
            assertFalse(registry.execute("SET_ALARM", mapOf("time" to "5 am")).success)
            assertFalse(registry.execute("SET_TIMER", mapOf("duration" to "garbage")).success)
            assertFalse(registry.execute("SET_TIMER", mapOf("duration" to "5 minutes")).success)
            assertTrue(requested.isEmpty())
        }

    @Test
    fun `bounded arithmetic and alarm parsing preserve supported donor cases`() =
        runTest(timeout = 10.seconds) {
            assertEquals(5.0, SimpleCalculation.evaluate("2 + 3"))
            assertEquals(-6.0, SimpleCalculation.evaluate("-2 * 3"))
            for (invalid in listOf("1+2+3", "1/0", "NaN")) assertNull(SimpleCalculation.evaluate(invalid))
            http.connected = false
            assertTrue(registry.execute("CALCULATE", mapOf("expression" to "2*3")).success)
            assertFalse(registry.execute("CALCULATE", mapOf("expression" to "1+2+3")).success)
            assertEquals(0 to 0, AlarmTimeParser.parse("midnight"))
            assertEquals(12 to 0, AlarmTimeParser.parse("noon"))
            assertEquals(5 to 30, AlarmTimeParser.parse("half past 5"))
            assertEquals(23 to 45, AlarmTimeParser.parse("quarter to 0"))
            for (invalid in listOf("0 am", "13 pm", "24:00", "1:60", "quarter to 99")) {
                assertNull(AlarmTimeParser.parse(invalid))
            }
        }

    @Test
    fun `unsafe address schemes credentials and private literals are refused`() =
        runTest(timeout = 10.seconds) {
            for (url in listOf(
                "http://example.com",
                "https://u:owner@example.invalid",
                "https://127.0.0.1",
                "https://" + listOf("10", "0", "0", "1").joinToString("."),
                "https://[::1]",
                "https://foo.local",
                "intent://launch",
                "file:///secret",
            )) {
                assertFalse(url, PublicWebAddress.allowed(url))
                assertFalse(registry.execute("SUMMARIZE_URL", mapOf("url" to url)).success)
            }
            assertTrue(PublicWebAddress.allowed("https://example.com/page?q=a"))
        }

    private class FakeHttp : InformationHttp {
        var connected = true
        var failure: Exception? = null

        override fun online() = connected

        override suspend fun weather(location: String): String {
            failure?.let { throw it }
            return "Sunny, +25C"
        }
    }
}
