// Origin: EQO Keep fixes; synthetic slot and model-plan regressions, no device access.
package ai.eqo.core.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepPlanningTest {
    private val enabled = IntentParaphraseTest.ENABLED
    private val queryField = "id:com.google.android.keep:id/search_actionbar_query_text"

    @Test fun notePhrasingsAndMisclassifiedSlotsAlwaysUseNativeAction() =
        runTest {
            val cases =
                listOf(
                    "make a note called Test in Keep" to mapOf("title" to "Test"),
                    "write a note in Keep with body Example body" to mapOf("body" to "Example body"),
                    "make a note called Test with Example body in Keep" to
                        mapOf("title" to "Test", "body" to "Example body"),
                    "add Example body to a new note in Keep" to mapOf("body" to "Example body"),
                )
            for ((request, slots) in cases) {
                for (app in listOf(null, "Keep")) {
                    for (action in listOf("create_note", "other", "open_app")) {
                        val understood = TaskIntent("Make note", app, action, slots, confidence = 1.0)
                        val fake = IntentFixtureProvider(listOf(Json.encodeToString(understood)))
                        val intent = IntentUnderstanding(fake, listOf("Keep")).understand(request)
                        assertEquals("create_note", intent.action)
                        assertEquals("Keep", intent.app)
                        val steps = TaskPlanner(fake, enabled).plan(request, intent = intent)
                        val note = steps.single().action
                        assertEquals("CREATE_NOTE", note.name)
                        assertEquals(slots["body"] ?: slots["title"], note.params["body"])
                        assertEquals(slots["title"].takeIf { slots["body"] != null }, note.params["title"])
                        assertEquals(1, fake.requests.size)
                        assertTrue(ApprovedTaskPlan(steps).matches(steps))
                    }
                }
            }
        }

    @Test fun directPlannerNormalizesOtherButMissingContentStillAsks() =
        runTest {
            val intent = TaskIntent("Note", "Keep", "other", mapOf("body" to "Example body"), confidence = 1.0)
            val fake = IntentFixtureProvider(emptyList())
            val steps = TaskPlanner(fake, enabled).plan("add Example body to a new note in Keep", intent = intent)
            assertEquals("CREATE_NOTE", steps.single().action.name)
            assertTrue(fake.requests.isEmpty())
            val missing =
                AppPlanHygiene.normalize(
                    "create a note in Keep",
                    intent.copy(params = emptyMap()),
                    listOf("Keep"),
                )
            assertEquals("title or body", missing.missingParameter())
            assertNull(IntentSkills.plan(missing, enabled))
            val changed =
                AppPlanHygiene.normalize(
                    "create a note in Keep\nUser clarification 1: use another app",
                    intent.copy(app = null, action = "create_note"),
                    listOf("Keep"),
                )
            assertNull(changed.app)
            assertEquals("app", changed.missingParameter())
        }

    @Test fun unsafeModelKeepEditorChainUsesOneRepairAndNeverReachesApproval() =
        runTest {
            val unsafe =
                """{"steps":[{"action":"OPEN_APP","params":{"appName":"Keep"}},""" +
                    """{"action":"CLICK_TEXT","params":{"text":"id:speed_dial_create_close_button"}},""" +
                    """{"action":"CLICK_TEXT","params":{"text":"id:new_note_button"}},""" +
                    """{"action":"TYPE_TEXT","params":{"searchText":"id:editable_title","content":"Test"}}]}"""
            val fixed = """{"steps":[{"action":"CREATE_NOTE","params":{"appName":"Keep","body":"Test"}}]}"""
            val fake = IntentFixtureProvider(listOf(unsafe, fixed))
            // No structured note slots: the free planner must still be guarded.
            val steps = TaskPlanner(fake, enabled).plan("make a note called Test in Keep")
            assertEquals("CREATE_NOTE", steps.single().action.name)
            assertEquals(2, fake.requests.size)
            assertTrue(
                fake.requests
                    .last()
                    .systemPrompt
                    .contains("Keep note creation must use CREATE_NOTE"),
            )
            assertFalse(TaskPlanPreview.describe(steps).contains("editable_title"))
        }

    @Test fun exactLoggedSevenStepSearchIsTrimmedBeforePreviewAndApproval() =
        runTest {
            for (action in listOf("search", "other")) {
                val intent =
                    TaskIntent("Search Test in Keep", "Keep", action, mapOf("query" to "Test"), confidence = 1.0)
                val fake = IntentFixtureProvider(listOf(searchJson()))
                val result = TaskPlanner(fake, enabled).planWithNotes("search Test in Keep", intent = intent)
                assertEquals(
                    listOf("OPEN_APP", "WAIT", "CLICK_TEXT", "TYPE_TEXT", "PRESS_ENTER"),
                    result.steps.map { it.action.name },
                )
                assertTrue(result.notes.isEmpty())
                assertEquals(1, fake.requests.size)
                assertTrue(ApprovedTaskPlan(result.steps).matches(result.steps))
                assertEquals(1, result.steps.count { it.action.name == "TYPE_TEXT" })
                assertEquals("search \"Test\" in \"Keep\"", TaskPlanPreview.summary(result.steps))
                assertEquals("EQO will:\n1. search \"Test\" in \"Keep\"", TaskPlanPreview.describe(result.steps))
            }
        }

    @Test fun cleanupPreservesResultNavigationAndNonSearchPlans() {
        val intent = TaskIntent("Search", "Keep", "search", mapOf("query" to "Test"))
        val steps = RegistryPlanVocabulary.parse(searchJson(), enabled)
        val result = LoopStep("task-8", ExecutedAction("CLICK_TEXT", mapOf("text" to "Result note")))
        val clean = IntentPlanProtection.validate(intent, steps + result)
        assertEquals("Result note", clean.last().action.params["text"])
        assertEquals("task-6", clean.last().stepId)
        assertEquals(steps, IntentPlanProtection.validate(intent.copy(action = "other"), steps))
        assertEquals(steps, IntentPlanProtection.validate(intent.copy(app = null), steps))
        assertEquals(steps, IntentPlanProtection.validate(intent.copy(params = mapOf("query" to "Different")), steps))
        val live = steps.filterNot { it.action.name == "PRESS_ENTER" }
        assertEquals(live, IntentPlanProtection.validate(intent, live))
    }

    @Test fun fullyQualifiedKeepEditorIdsAreRefusedEvenForOtherIntent() {
        val fields =
            listOf("id:com.google.android.keep:id/editable_title", "id:com.google.android.keep:id/edit_note_text")
        for (field in fields + listOf("Title", "Body")) {
            val steps =
                listOf(
                    LoopStep("task-1", ExecutedAction("OPEN_APP", mapOf("appName" to "Keep"))),
                    LoopStep(
                        "task-2",
                        ExecutedAction(
                            "TYPE_TEXT",
                            mapOf("searchText" to field, "content" to "Test"),
                        ),
                    ),
                )
            assertThrows(IllegalArgumentException::class.java) {
                IntentPlanProtection.validate(TaskIntent("Note", action = "other"), steps)
            }
            if (field in fields) {
                assertThrows(IllegalArgumentException::class.java) {
                    IntentPlanProtection.validate(TaskIntent("Note", action = "other"), steps.drop(1))
                }
            }
        }
    }

    @Test fun suffixQueryIdsAreCleanedButOutputReferencesForceRepair() {
        val intent = TaskIntent("Search", "Keep", "search", mapOf("query" to "Test"))
        val steps = RegistryPlanVocabulary.parse(searchJson(), enabled)
        val shortTargets =
            steps.dropLast(2) +
                steps.takeLast(2).map { step ->
                    step.copy(
                        action =
                            step.action.copy(
                                params =
                                    step.action.params.mapValues { (_, value) ->
                                        value.replace(queryField, "id:search_actionbar_query_text")
                                    },
                            ),
                    )
                }
        assertEquals(5, IntentPlanProtection.validate(intent, shortTargets).size)
        val reference = LoopStep("task-8", ExecutedAction("CALCULATE", mapOf("expression" to "{{step:4:result}}")))
        assertThrows(IllegalArgumentException::class.java) {
            IntentPlanProtection.validate(intent, steps + reference)
        }
    }

    @Test fun compoundSearchKeepsOtherIntentAndAllSlots() {
        val intent =
            TaskIntent(
                "Search then message",
                "WhatsApp",
                "other",
                mapOf("query" to "Example", "contact" to "Example", "message" to "Hello"),
            )
        assertEquals(
            intent,
            AppPlanHygiene.normalize("search Example in WhatsApp then send Hello", intent, listOf("WhatsApp")),
        )
    }

    @Test fun keepRestrictionsEndWhenAnotherAppOpens() {
        val search = RegistryPlanVocabulary.parse(searchJson(), enabled).dropLast(2)
        val other =
            listOf(
                LoopStep("task-6", ExecutedAction("OPEN_APP", mapOf("appName" to "Notes"))),
                LoopStep("task-7", ExecutedAction("TYPE_TEXT", mapOf("searchText" to "Title", "content" to "Test"))),
            )
        val steps = search + other
        assertEquals(steps, IntentPlanProtection.validate(TaskIntent("Search then write", action = "other"), steps))
    }

    @Test fun keepPlannerQuestionReturnsToClarificationBeforeApproval() =
        runTest {
            val output = """{"steps":[{"action":"ASK_USER","params":{"question":"What should the note say?"}}]}"""
            val fake = IntentFixtureProvider(listOf(output))
            val intent = TaskIntent("Create note", "Keep", "create_note", confidence = 1.0)
            val steps = TaskPlanner(fake, enabled + "ASK_USER").plan("create a note in Keep", intent = intent)
            assertEquals("What should the note say?", RegistryPlanVocabulary.clarification(steps)?.question)
            assertEquals(1, fake.requests.size)
        }

    private fun searchJson(): String =
        """{"steps":[{"action":"OPEN_APP","params":{"appName":"Keep"}},""" +
            """{"action":"WAIT","params":{"durationMs":"3000"}},""" +
            """{"action":"CLICK_TEXT","params":{"text":"id:toolbar"}},""" +
            """{"action":"TYPE_TEXT","params":{"searchText":"$queryField","content":"Test"}},""" +
            """{"action":"PRESS_ENTER","params":{}},""" +
            """{"action":"CLICK_TEXT","params":{"text":"$queryField"}},""" +
            """{"action":"TYPE_TEXT","params":{"searchText":"$queryField","content":"Test"}}]}"""
}
