// Origin: EQO one guarded app-search entry shared by the shopping and search skills.
package ai.eqo.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The two skills' search entry is recognized from the approved plan; everything else is not. */
class AppSearchEntryTest {
    private val enabled = setOf("OPEN_APP", "WAIT", "CLICK_TEXT", "TYPE_TEXT", "PRESS_ENTER")

    @Test fun shoppingPlansRouteTheirSearchEntryThroughTheGuard() {
        for (app in listOf("Zepto", "Blinkit")) {
            val intent =
                TaskIntent("Buy milk", app, "purchase", mapOf("item" to "milk", "quantity" to "2"), confidence = 1.0)
            val spec = AppSearchEntry.of(requireNotNull(IntentSkills.plan(intent, enabled)))
            val entry =
                if (app == "Zepto") {
                    "id:com.zeptoconsumerapp:id/homepage-search-box"
                } else {
                    "exact:Search,exact:Search for products"
                }
            assertEquals("task-3", spec?.entryStepId)
            assertEquals(entry, spec?.entry)
            assertEquals("task-4", spec?.typeStepId)
            assertEquals("milk", spec?.query)
            assertEquals(app, spec?.app)
        }
    }

    @Test fun searchInAppPlansRouteTheirSearchEntryThroughTheGuard() {
        val steps =
            listOf(
                LoopStep("task-1", ExecutedAction("OPEN_APP", mapOf("appName" to "Blinkit"))),
                LoopStep("task-2", ExecutedAction("WAIT", mapOf("durationMs" to "3000"))),
                LoopStep("task-3", ExecutedAction("CLICK_TEXT", mapOf("text" to "Search"))),
                LoopStep(
                    "task-4",
                    ExecutedAction("TYPE_TEXT", mapOf("searchText" to "search:Search", "content" to "cats")),
                ),
                LoopStep("task-5", ExecutedAction("PRESS_ENTER")),
            )
        val spec = AppSearchEntry.of(steps)
        assertEquals(AppSearchEntry.Spec("task-3", "Search", "task-4", "cats", "Blinkit"), spec)
    }

    @Test fun whatsappSearchStaysFailClosedOnRestoredChats() {
        val intent = TaskIntent("Search WhatsApp", "WhatsApp", "search", mapOf("query" to "cats"), send = "no")
        val steps = requireNotNull(IntentSkills.plan(intent, enabled))
        assertNull(AppSearchEntry.of(steps))
    }

    @Test fun plansWithoutASearchEntryAreNeverGuarded() {
        val note = TaskIntent("Write a note", "Google Keep", "create_note", mapOf("body" to "milk"), confidence = 1.0)
        val keepPlan = requireNotNull(IntentSkills.plan(note, enabled + "CREATE_NOTE"))
        assertNull(AppSearchEntry.of(keepPlan))
        val openOnly = listOf(LoopStep("task-1", ExecutedAction("OPEN_APP", mapOf("appName" to "Zepto"))))
        assertNull(AppSearchEntry.of(openOnly))
        val typing =
            listOf(
                LoopStep("task-1", ExecutedAction("OPEN_APP", mapOf("appName" to "Blinkit"))),
                LoopStep("task-2", ExecutedAction("CLICK_TEXT", mapOf("text" to "Search"))),
                LoopStep(
                    "task-3",
                    ExecutedAction("TYPE_TEXT", mapOf("searchText" to "id:cart_notes", "content" to "cats")),
                ),
            )
        assertNull(AppSearchEntry.of(typing))
    }

    @Test fun thePlainMissingReasonNamesTheApp() {
        assertEquals("I could not find Search in Zepto", AppSearchEntry.missingReason("Zepto"))
    }
}
