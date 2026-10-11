// Origin: EQO intent v1; synthetic provider fixtures, not a live-model accuracy benchmark.
package ai.eqo.core.agent

import ai.eqo.core.llm.LLMProvider
import ai.eqo.core.llm.LLMRequest
import ai.eqo.core.llm.LLMResponse
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class IntentParaphraseTest(
    private val request: String,
    private val expected: TaskIntent,
    private val verb: String,
) {
    @Test fun understoodSlotsSelectTheRightSkillWithoutRequestRegexes() =
        runTest {
            val fake = IntentFixtureProvider(listOf(Json.encodeToString(expected)))
            val understanding = IntentUnderstanding(fake, APPS)
            val intent = understanding.understand(request)
            assertEquals(request, expected.action, intent.action)
            assertEquals(expected.params, intent.params)
            assertFalse(intent.needsClarification())
            val steps = TaskPlanner(fake, ENABLED).plan(request, intent = intent)
            assertEquals(verb, steps.first().action.name)
            assertTrue(ApprovedTaskPlan(steps).matches(steps))
            assertTrue(fake.requests.size <= 1) // Stage two is deterministic for these known skills.
            fake.requests.forEach {
                assertEquals(0f, it.temperature)
                assertEquals(600, it.maxTokens)
                assertFalse(it.systemPrompt.contains(request))
                assertTrue(
                    it.messages
                        .single()
                        .text
                        .contains("installed_apps"),
                )
            }
        }

    companion object {
        val APPS = listOf("Keep", "Calculator", "Gmail", "Messages", "WhatsApp", "Chrome")
        val ENABLED =
            setOf(
                "OPEN_APP",
                "WAIT",
                "CLICK_TEXT",
                "TYPE_TEXT",
                "PRESS_BACK",
                "CALCULATE",
                "SEND_SMS",
                "SEND_WHATSAPP",
                "MAKE_CALL",
                "WHATSAPP_CALL",
                "WEB_SEARCH",
                "PRESS_ENTER",
            )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> =
            buildList {
                val note =
                    TaskIntent(
                        "Create a note called eqo test in Keep",
                        "Keep",
                        "create_note",
                        mapOf("title" to "eqo test", "body" to "test eqo"),
                        send = "no",
                        confidence = 0.95,
                    )
                NOTES.forEach { add(arrayOf(it, note, "OPEN_APP")) }
                val calculate =
                    TaskIntent(
                        "Calculate 238 times 8",
                        action = "calculate",
                        params = mapOf("expression" to "238*8"),
                        confidence = 0.95,
                    )
                CALCULATIONS.forEach { add(arrayOf(it, calculate, "CALCULATE")) }
                val message =
                    TaskIntent(
                        "Message Example hi",
                        "Messages",
                        "send_message",
                        mapOf("contact" to "Example", "message" to "hi"),
                        "sms",
                        "yes",
                        0.95,
                    )
                MESSAGES.forEach { add(arrayOf(it, message, "SEND_SMS")) }
                val call =
                    TaskIntent(
                        "Call Example",
                        action = "call",
                        params = mapOf("contact" to "Example"),
                        confidence = 0.95,
                    )
                CALLS.forEach { add(arrayOf(it, call, "MAKE_CALL")) }
                val open = TaskIntent("Open Gmail", "Gmail", "open_app", confidence = 1.0)
                OPENS.forEach { add(arrayOf(it, open, "OPEN_APP")) }
                assertEquals(125, size)
            }

        private val NOTES =
            listOf(
                "open a note, type test eqo, save it as eqo test",
                "save test eqo as a note named eqo test",
                "write test eqo in Keep with title eqo test",
                "make a Keep note eqo test saying test eqo",
                "create a note called eqo test, body test eqo",
                "add test eqo to a new note eqo test",
                "jot test eqo down as eqo test",
                "put test eqo in notes, call it eqo test",
                "note bana do eqo test, andar test eqo",
                "Keep mein eqo test naam se test eqo likho",
                "ek note banao title eqo test text test eqo",
                "test eqo ko eqo test note mein save karo",
                "नई नोट eqo test में test eqo लिखो",
                "eqo test नाम की नोट बनाओ test eqo",
                "please store test eqo under eqo test in Keep",
                "new note eqo test with test eqo",
                "eqo test: test eqo, save in notes",
                "type test eqo then name the note eqo test",
                "creat a not eqo test with test eqo",
                "sav a Keep note eqo test containing test eqo",
                "can you make eqo test in Keep with test eqo",
                "I'd like a note eqo test saying test eqo",
                "record test eqo as eqo test in notes",
                "keep a note of test eqo called eqo test",
                "open Keep and make eqo test containing test eqo",
            )
        private val CALCULATIONS =
            listOf(
                "238 times 8 calculate and let me know",
                "what is 238 multiplied by 8",
                "238*8",
                "calculate 238 x 8",
                "work out 238 times eight",
                "tell me 238 into 8",
                "multiply 238 by 8",
                "product of 238 and 8",
                "238 guna 8 kitna hai",
                "238 ko 8 se multiply karo",
                "दो सौ अड़तीस गुणा आठ",
                "238 गुणा 8 बताओ",
                "answer for 238 times 8 please",
                "please solve 238 × 8",
                "how much is 238 times 8",
                "238 by 8 multiplication",
                "calc 238*8",
                "calclate 238 times 8",
                "find 238 multiplied eight",
                "do the maths 238 times 8",
                "evaluate 238 * 8",
                "I need the result of 238 x 8",
                "eight times 238",
                "238 repeated eight times sum",
                "can you calculate 238 times 8 for me",
            )
        private val MESSAGES =
            listOf(
                "message Example hi",
                "send hi to Example",
                "text Example saying hi",
                "SMS hi to Example",
                "tell Example hi by text",
                "send a text to Example: hi",
                "Example ko hi message bhejo",
                "Example ko hi SMS karo",
                "hi bol do Example ko message mein",
                "Example को hi संदेश भेजो",
                "please message Example with hi",
                "msg Example hi",
                "messge Example hi",
                "send Example hi",
                "hi to Example over SMS",
                "write and send hi to Example",
                "deliver hi in a text to Example",
                "can you text hi to Example",
                "I want to send Example a message hi",
                "text message Example hi",
                "send a message saying hi to Example",
                "ping Example with hi by SMS",
                "SMS Example hi please",
                "Example: hi, send a text",
                "send the words hi to Example in Messages",
            )
        private val CALLS =
            listOf(
                "call Example",
                "phone Example",
                "ring Example",
                "dial Example",
                "make a call to Example",
                "give Example a call",
                "please call Example",
                "Example ko call karo",
                "Example ko phone lagao",
                "Example को कॉल करो",
                "Example को फोन लगाओ",
                "can you ring Example",
                "I want to call Example",
                "connect a phone call to Example",
                "start a call with Example",
                "telephone Example",
                "place a voice call to Example",
                "Example se phone par baat karni hai",
                "cal Example",
                "phne Example",
                "call up Example",
                "dial up Example please",
                "make my phone ring Example",
                "phone call Example now",
                "put a call through to Example",
            )
        private val OPENS =
            listOf(
                "open Gmail",
                "launch Gmail",
                "start Gmail",
                "show Gmail",
                "bring up Gmail",
                "Gmail kholo",
                "Gmail खोलो",
                "Gmail khol do",
                "please open Gmail",
                "can you launch Gmail",
                "take me to Gmail",
                "go to Gmail",
                "I want Gmail open",
                "switch to Gmail",
                "load Gmail",
                "get Gmail on screen",
                "opn Gmail",
                "open Gmal",
                "lauch Gmail",
                "Gmail please",
                "show me the Gmail app",
                "open the Gmail app",
                "bring Gmail to the front",
                "run Gmail",
                "Gmail chalu karo",
            )
    }
}

internal class IntentFixtureProvider(
    private val outputs: List<String>,
) : LLMProvider {
    val requests = mutableListOf<LLMRequest>()
    override val name = "fake"
    override val availableModels = emptyList<String>()

    override suspend fun complete(request: LLMRequest): LLMResponse {
        requests += request
        check(requests.size <= outputs.size) { "Unexpected extra provider call" }
        return LLMResponse(outputs[requests.lastIndex], 0, "fixture", name, 0)
    }

    override fun streamComplete(request: LLMRequest) = emptyFlow<String>()

    override suspend fun isAvailable() = true
}
