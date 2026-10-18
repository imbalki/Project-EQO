package ai.eqo.core.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanPreviewPolishTest {
    private val enabled = ActionSchema.ALL_ACTIONS.map { it.name }.toSet()
    private val uncertainNote = TaskIntent("Make a note", action = "other", confidence = 1.0)
    private val question =
        """{"steps":[{"action":"ASK_USER","params":{"question":"What should the note say?"}}]}"""
    private val completeNote =
        TaskIntent("Save a note", "Keep", "create_note", mapOf("body" to "Buy bananas"), confidence = 1.0)

    @Test fun plannerQuestionIsAskedBeforeReturningAnyExecutableSteps() =
        runTest {
            val fake = IntentFixtureProvider(listOf(json(uncertainNote), question, json(completeNote)))
            val session = IntentPlanningSession(IntentUnderstanding(fake, listOf("Keep")), TaskPlanner(fake, enabled))
            var asked = 0
            val result =
                session.prepare("make a note") {
                    asked++
                    assertEquals("What should the note say?", it.clarificationQuestion())
                    assertEquals(2, fake.requests.size)
                    "Buy bananas in Keep"
                }!!
            assertEquals(1, asked)
            assertEquals(1, result.clarificationRounds)
            assertEquals("make a note\nUser clarification 1: Buy bananas in Keep", result.clarifiedRequest)
            val action =
                result.steps
                    .single()
                    .action.name
            assertEquals("CREATE_NOTE", action)
            assertTrue(result.notes.isEmpty())
            val input =
                fake.requests
                    .last()
                    .messages
                    .single()
                    .text
            assertTrue(input.contains("User clarification 1"))
        }

    @Test fun cancellationStopsAndExhaustionDisclosesNonExecutableBestGuess() =
        runTest {
            val cancelled = IntentFixtureProvider(listOf(json(uncertainNote), question))
            assertNull(
                IntentPlanningSession(IntentUnderstanding(cancelled, emptyList()), TaskPlanner(cancelled, enabled))
                    .prepare("make a note") { null },
            )
            val repeated = IntentFixtureProvider(List(3) { listOf(json(uncertainNote), question) }.flatten())
            var asked = 0
            val exhausted =
                IntentPlanningSession(IntentUnderstanding(repeated, emptyList()), TaskPlanner(repeated, enabled))
                    .prepare("make a note") {
                        asked++
                        "More details"
                    }!!
            assertTrue(exhausted.steps.isEmpty())
            assertTrue(exhausted.bestGuess)
            assertTrue(exhausted.notes.single().contains("nothing will run"))
            assertEquals(IntentPlanningSession.MAX_ROUNDS, asked)
        }

    @Test fun questionPlansHaveNoCompletenessOrPhoneSettingWarning() =
        runTest {
            val fake = IntentFixtureProvider(listOf(question))
            val intent = uncertainNote.copy(params = mapOf(PhoneActions.PARAM to "TOGGLE_WIFI"))
            val planned = TaskPlanner(fake, enabled).planWithNotes("make a note", intent = intent)
            assertEquals(1, fake.requests.size)
            assertTrue(planned.notes.isEmpty())
            assertTrue(PlanCompleteness.gaps("make a note", intent, planned.steps, enabled).isEmpty())
            assertThrows(IllegalArgumentException::class.java) { TaskPlanPreview.describe(planned.steps) }
        }

    @Test fun completenessRepairQuestionsAlsoReturnWithoutMisleadingGapNotes() =
        runTest {
            val open = """{"steps":[{"action":"OPEN_APP","params":{"appName":"Zepto"}}]}"""
            val fake = IntentFixtureProvider(listOf(open, question))
            val intent =
                TaskIntent("Search Zepto", "Zepto", "search", mapOf("query" to "milk"), confidence = 1.0)
            val planned = TaskPlanner(fake, enabled).planWithNotes("buy milk on Zepto", intent = intent)
            assertEquals(2, fake.requests.size)
            assertTrue(planned.notes.isEmpty())
            assertEquals("What should the note say?", RegistryPlanVocabulary.clarification(planned.steps)?.question)
        }

    @Test fun questionOptionsUseTheSameCommaSeparatedChoicesAsTheRegistry() {
        val question = step("ASK_USER", mapOf("question" to "Which app?", "options" to "Keep, Other"))
        assertEquals(listOf("Keep", "Other"), RegistryPlanVocabulary.clarification(listOf(question))?.options)
    }

    @Test fun summaryDescribesOpenOnlyPlanNotPaymentWishAndWaitsStayApproved() {
        val steps =
            listOf(
                step("OPEN_APP", mapOf("appName" to "Zepto")),
                step("WAIT", mapOf("durationMs" to "3000"), "wait"),
            )
        val approved = ApprovedTaskPlan(steps)
        assertEquals("EQO will not pay; it will open \"Zepto\" only", TaskPlanPreview.summary(steps, noPayment = true))
        assertEquals("EQO will:\n1. open \"Zepto\"", TaskPlanPreview.describe(steps))
        assertTrue(approved.matches(steps))
        assertEquals(2, approved.steps().size)
        assertFalse(approved.matches(steps.take(1)))
    }

    @Test fun registryInteractionPreviewsArePlainAndWaitsAreNotNumbered() {
        val steps =
            listOf(
                step("TOGGLE_FLASHLIGHT", mapOf("state" to "on")),
                step("WAIT", mapOf("durationMs" to "3000")),
                step("CLICK_TEXT", mapOf("text" to "Search")),
                step("TYPE_TEXT", mapOf("searchText" to "Search", "content" to "banana")),
                step("PRESS_ENTER"),
            )
        val preview = TaskPlanPreview.describe(steps)
        assertTrue(preview.contains("1. turn the flashlight on"))
        assertTrue(preview.contains("2. tap \"Search\""))
        assertTrue(preview.contains("3. type \"banana\" into \"Search\""))
        assertTrue(preview.contains("4. press Enter"))
        assertFalse(preview.contains("WAIT"))
        assertFalse(preview.contains("durationMs"))
    }

    private fun step(
        name: String,
        params: Map<String, String> = emptyMap(),
        id: String = name,
    ) = LoopStep(id, ExecutedAction(name, params))

    private fun json(intent: TaskIntent): String = Json.encodeToString(intent)
}
