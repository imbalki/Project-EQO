// Origin: EQO intent v1; fake-provider ordering, bounds, schema and immutable approval regressions.
package ai.eqo.core.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentPlanningTest {
    private val apps = IntentParaphraseTest.APPS
    private val enabled = IntentParaphraseTest.ENABLED
    private val note =
        TaskIntent(
            "Create a note called Test in Keep",
            "Keep",
            "create_note",
            mapOf("title" to "Test"),
            send = "no",
            confidence = 0.95,
        )

    @Test fun ambiguityAsksBeforePlanningAndAddsAnswerToUnderstandingOnly() =
        runTest {
            val unsure =
                TaskIntent(
                    "Open a tool",
                    confidence = 0.4,
                    question = "Calculator or Notes?",
                    options = listOf("Calculator", "Notes"),
                )
            val fake = IntentFixtureProvider(listOf(json(unsure), json(note)))
            val session = IntentPlanningSession(IntentUnderstanding(fake, apps), TaskPlanner(fake, enabled))
            var asked = 0
            val result =
                session.prepare("open a tool") {
                    assertEquals(1, fake.requests.size)
                    assertEquals(listOf("Calculator", "Notes"), it.options)
                    asked++
                    "Notes called Test in Keep"
                }!!
            assertEquals(1, asked)
            assertEquals(2, fake.requests.size)
            assertTrue(
                fake.requests[1]
                    .messages
                    .single()
                    .text
                    .contains("User clarification 1"),
            )
            assertEquals("create_note", result.intent.action)
            assertFalse(result.bestGuess)
            assertEquals(
                "PRESS_BACK",
                result.steps
                    .last()
                    .action.name,
            )
        }

    @Test fun highConfidenceDoesNotAskAndMissingSlotsAskEvenWhenConfident() =
        runTest {
            val fake = IntentFixtureProvider(listOf(json(note)))
            val result = session(fake).prepare("make Test in Keep") { error("Must not ask") }!!
            assertFalse(result.bestGuess)
            val missing = note.copy(params = emptyMap())
            assertTrue(missing.needsClarification())
            assertEquals("title or body", missing.missingParameter())
            val message = note.copy(action = "send_message", params = mapOf("message" to "hi"))
            assertEquals("contact", message.missingParameter())
            assertTrue(message.needsClarification())
        }

    @Test fun clarificationStopsAtTwoAndPreviewResultDisclosesBestGuess() =
        runTest {
            val uncertain =
                note.copy(
                    confidence = 0.2,
                    question = "Which notes app?",
                    options = listOf("Keep", "Other"),
                )
            val fake = IntentFixtureProvider(List(3) { json(uncertain) })
            var questions = 0
            val result =
                session(fake).prepare("save a note") {
                    questions++
                    "Keep"
                }!!
            assertEquals(2, questions)
            assertEquals(3, fake.requests.size)
            assertTrue(result.bestGuess)
            assertTrue(
                fake.requests
                    .last()
                    .messages
                    .single()
                    .text
                    .contains("User clarification 2"),
            )
        }

    @Test fun cancelNeverPlansOrInventsMissingData() =
        runTest {
            val fake = IntentFixtureProvider(listOf(json(note.copy(params = emptyMap()))))
            assertNull(session(fake).prepare("save a note") { null })
            assertEquals(1, fake.requests.size)
        }

    @Test fun quotedSendAndChannelWordsStayContentAndDoNotOverrideStructuredSlots() =
        runTest {
            val content = "send this WhatsApp email, don't send"
            for (send in listOf("yes", "no")) {
                val intent =
                    TaskIntent(
                        "Message Example",
                        "Messages",
                        "send_message",
                        mapOf("contact" to "Example", "message" to content),
                        "sms",
                        send,
                        0.95,
                    )
                val fake = IntentFixtureProvider(listOf(json(intent)))
                val result =
                    session(fake).prepare("${if (send == "yes") "Send" else "Draft"} Example \"$content\"") {
                        error("Must not ask")
                    }!!
                assertEquals(send, result.intent.send)
                assertEquals("sms", result.intent.channel)
                assertEquals(
                    "SEND_SMS",
                    result.steps
                        .single()
                        .action.name,
                )
                assertEquals(
                    content,
                    result.steps
                        .single()
                        .action.params["message"],
                )
                assertTrue(
                    fake.requests
                        .single()
                        .systemPrompt
                        .contains("INSIDE quoted"),
                )
            }
        }

    @Test fun noSendRejectsAndRepairsOnceWithStructuredIntentAndNoTextLogs() =
        runTest {
            val bad = """{"steps":[{"action":"CLICK_TEXT","params":{"text":"Send"}}]}"""
            val good = """{"steps":[{"action":"PRESS_BACK","params":{}}]}"""
            val fake = IntentFixtureProvider(listOf(bad, good))
            val logs = mutableListOf<String>()
            val intent = TaskIntent("Synthetic private goal", send = "no", confidence = 1.0)
            val result = TaskPlanner(fake, enabled, logs::add).plan("synthetic private request", intent = intent)
            assertEquals("PRESS_BACK", result.single().action.name)
            assertEquals(2, fake.requests.size)
            assertTrue(
                fake.requests
                    .last()
                    .systemPrompt
                    .contains("Intent send=no"),
            )
            assertTrue(
                fake.requests
                    .last()
                    .messages
                    .single()
                    .text
                    .contains("\"send\":\"no\""),
            )
            assertEquals(listOf("planner validation failed; repair=1"), logs)
        }

    @Test fun secondBadProposalStopsWithoutThirdCallAndSchemaSharesTheRepairBudget() =
        runTest {
            val send = """{"steps":[{"action":"CLICK_TEXT","params":{"text":"id:send_button"}}]}"""
            val fake = IntentFixtureProvider(listOf("{}", send))
            try {
                TaskPlanner(fake, enabled).plan("draft only", intent = TaskIntent("Draft", send = "no"))
                error("Must reject repaired submit")
            } catch (_: IllegalArgumentException) {
                assertEquals(2, fake.requests.size)
            }
        }

    @Test fun aliasesSkipUnderstandingButExtraInstructionsNeverDo() =
        runTest {
            val empty = IntentFixtureProvider(emptyList())
            assertEquals("Gmail", IntentUnderstanding(empty, apps).understand("open Gmail").app)
            assertEquals("Gmail", IntentUnderstanding(empty, apps).understand("open Google Mail").app)
            assertTrue(empty.requests.isEmpty())
            val fake = IntentFixtureProvider(listOf(json(note)))
            IntentUnderstanding(fake, apps).understand("open Gmail and create Test in Keep")
            assertEquals(1, fake.requests.size)
        }

    @Test fun invalidKindsAppsBoundsAndNonStringSlotsAreRejected() {
        val original = json(note)
        for (invalid in listOf(
            original.replace("create_note", "run_code"),
            original.replace("Keep", "Not installed"),
            original.replace("0.95", "1.1"),
            original.replace("\"no\"", "\"maybe\""),
            original.replace("\"Test\"", "42"),
            "{}",
            "```json\n$original\n```",
        )) {
            assertThrows(IllegalArgumentException::class.java) { IntentUnderstanding.parse(invalid, apps) }
        }
        val tooMany = note.copy(options = List(5) { "Choice $it" })
        assertThrows(IllegalArgumentException::class.java) { IntentUnderstanding.parse(json(tooMany), apps) }
    }

    @Test fun plainMessageDefaultIsSmsAndTelemetryIsKindsAndBucketsOnly() =
        runTest {
            val message =
                TaskIntent(
                    "Private synthetic goal",
                    action = "send_message",
                    params = mapOf("contact" to "Example", "message" to "hi"),
                    send = "yes",
                    confidence = 0.9,
                )
            val fake = IntentFixtureProvider(listOf(json(message)))
            val logs = mutableListOf<String>()
            val understood = IntentUnderstanding(fake, apps, logs::add).understand("message Example hi")
            assertEquals("sms", understood.channel)
            assertEquals(listOf("intent kind=send_message confidence=high"), logs)
        }

    @Test fun knownSkillsPreserveTitleOnlyMessageDraftCallAndCalculationSlots() {
        val steps = IntentSkills.plan(note, enabled)!!
        assertEquals(
            listOf("id:editable_title"),
            steps
                .filter { it.action.name == "TYPE_TEXT" }
                .map { it.action.params["searchText"] },
        )
        assertEquals("PRESS_BACK", steps.last().action.name)
        val draft =
            note.copy(
                action = "send_message",
                channel = "whatsapp",
                params = mapOf("contact" to "Example", "message" to "hi"),
            )
        assertEquals(
            "true",
            IntentSkills
                .plan(draft, enabled)!!
                .single()
                .action.params["draftOnly"],
        )
        val call = draft.copy(action = "call", params = mapOf("contact" to "Example", "video" to "true"))
        assertEquals(
            "WHATSAPP_CALL",
            IntentSkills
                .plan(call, enabled)!!
                .single()
                .action.name,
        )
        val calculation = note.copy(action = "calculate", params = mapOf("expression" to "238*8"))
        assertEquals(
            "CALCULATE",
            IntentSkills
                .plan(calculation, enabled)!!
                .single()
                .action.name,
        )
        assertNull(IntentSkills.plan(note.copy(app = "Other Notes"), enabled))
    }

    @Test fun structuredChannelMismatchRepairsAndApprovalSnapshotStillRejectsChanges() =
        runTest {
            val bad = """{"steps":[{"action":"SEND_WHATSAPP","params":{"contact":"Example","message":"hi"}}]}"""
            val good = """{"steps":[{"action":"OPEN_APP","params":{"appName":"Messages"}}]}"""
            val fake = IntentFixtureProvider(listOf(bad, good))
            val intent = TaskIntent("SMS draft", action = "send_message", channel = "sms", send = "yes")
            val steps = TaskPlanner(fake, enabled).plan("message Example", intent = intent)
            assertTrue(
                fake.requests
                    .last()
                    .systemPrompt
                    .contains("understood message channel"),
            )
            val approved = ApprovedTaskPlan(steps)
            val changed = steps.map { it.copy(action = it.action.copy(params = mapOf("appName" to "Keep"))) }
            assertFalse(approved.matches(changed))
        }

    private fun json(intent: TaskIntent): String = Json.encodeToString(intent)

    private fun session(fake: IntentFixtureProvider): IntentPlanningSession =
        IntentPlanningSession(IntentUnderstanding(fake, apps), TaskPlanner(fake, enabled))
}
