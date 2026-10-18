// Origin: EQO cart-only shopping regression fixtures; no real shopping accounts or screens.
package ai.eqo.core.agent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingTest {
    private val enabled = setOf("OPEN_APP", "WAIT", "CLICK_TEXT", "TYPE_TEXT", "PRESS_ENTER")

    @Test fun codResourceIdsAreDeniedInGenericModelAndRepairWithRegistryParameters() =
        runTest {
            for (id in listOf("shop:id/cod_button", "shop:id/select_cod", "shop:id/COD", "shop:id/cod-option")) {
                for (verb in listOf("CLICK_ID", "TYPE_ID")) {
                    val params =
                        if (verb == "TYPE_ID") {
                            mapOf("viewId" to id, "content" to "synthetic")
                        } else {
                            mapOf("viewId" to id)
                        }
                    val step = LoopStep("task-1", ExecutedAction(verb, params))
                    assertTrue(RegistryPlanVocabulary.errors(listOf(step), enabled + verb).isEmpty())
                    assertThrows(IllegalArgumentException::class.java) {
                        IntentPlanProtection.validate(TaskIntent("Generic model task"), listOf(step))
                    }
                    val jsonParams =
                        if (verb == "TYPE_ID") {
                            """{"viewId":"$id","content":"synthetic"}"""
                        } else {
                            """{"viewId":"$id"}"""
                        }
                    val output = """{"steps":[{"action":"$verb","params":$jsonParams}]}"""
                    val fake = IntentFixtureProvider(listOf(output, output))
                    val result = runCatching { TaskPlanner(fake, enabled + verb).planWithNotes("finish a task") }
                    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
                    assertEquals(2, fake.requests.size)
                }
            }
            val safe = LoopStep("task-1", ExecutedAction("CLICK_ID", mapOf("viewId" to "shop:id/barcode_button")))
            assertEquals(listOf(safe), IntentPlanProtection.validate(TaskIntent("Scan a barcode"), listOf(safe)))
        }

    @Test fun aiPlannerAndItsRepairBothPassPaymentProtection() =
        runTest {
            val targets = listOf("Pay", "Place order", "Confirm order", "Buy now", "UPI", "card", "cod", "subscribe")
            for (target in targets) {
                val output = """{"steps":[{"action":"CLICK_TEXT","params":{"text":"$target"}}]}"""
                val fake = IntentFixtureProvider(listOf(output, output))
                val result = runCatching { TaskPlanner(fake, enabled).planWithNotes("finish a task") }
                assertTrue(result.exceptionOrNull() is IllegalArgumentException)
                assertEquals(2, fake.requests.size)
            }
        }

    @Test fun addingANoteDoesNotBecomeShoppingAndLargeNumericQuantitiesNeverDefaultToOne() {
        assertEquals(null, ShoppingIntents.from("add a note in Keep", listOf("Keep")))
        for (quantity in listOf("999999999999999999", "1.5", "-2")) {
            val intent = requireNotNull(ShoppingIntents.from("buy $quantity milk on Zepto", listOf("Zepto")))
            assertEquals("quantity", intent.missingParameter())
        }
    }

    @Test fun paymentWordsInTypedContentAreNotPaymentFieldTargets() {
        val action = ExecutedAction("type_text", mapOf("target" to "Note", "text" to "birthday card"))
        val step = LoopStep("task-1", action)
        assertEquals(listOf(step), IntentPlanProtection.validate(TaskIntent("write a note"), listOf(step)))
    }

    @Test fun extractsWordsDigitsUnitsAndCartRequestsWithoutModel() =
        runTest {
            val cases =
                mapOf(
                    "buy two milk on Zepto" to ("milk" to "2"),
                    "buy 2 milk on Zepto" to ("milk" to "2"),
                    "order 1 kg tomatoes on Zepto" to ("kg tomatoes" to "1"),
                    "add bread to my Zepto cart" to ("bread" to "1"),
                    "purchase a bread from Zepto" to ("bread" to "1"),
                    "buy ten apples in Zepto" to ("apples" to "10"),
                )
            val provider = IntentFixtureProvider(emptyList())
            for ((request, slots) in cases) {
                val intent = IntentUnderstanding(provider, listOf("Zepto")).understand(request)
                assertEquals("purchase", intent.action)
                assertEquals("Zepto", intent.app)
                assertEquals(slots.first, intent.params["item"])
                assertEquals(slots.second, intent.params["quantity"])
                assertFalse(intent.needsClarification())
            }
            assertTrue(provider.requests.isEmpty())
        }

    @Test fun missingAppAndOutOfRangeQuantitiesAskAndNeverPlan() {
        for (request in listOf("buy a dozen milk on Zepto", "buy 11 milk on Zepto", "buy 0 milk on Zepto")) {
            val intent = requireNotNull(ShoppingIntents.from(request, listOf("Zepto")))
            assertTrue(intent.needsClarification())
            assertEquals("quantity", intent.missingParameter())
            assertThrows(IllegalArgumentException::class.java) { ShoppingPlans.plan(intent) }
        }
        val missing = requireNotNull(ShoppingIntents.from("buy milk", listOf("Zepto", "Blinkit")))
        assertEquals(listOf("Zepto", "Blinkit"), missing.options)
        assertEquals("app", missing.missingParameter())
        val request = "buy milk\nUser clarification 1: Blinkit"
        val clarified = requireNotNull(ShoppingIntents.from(request, listOf("Blinkit")))
        assertEquals("Blinkit", clarified.app)
        assertFalse(clarified.needsClarification())
        val capped =
            requireNotNull(
                ShoppingIntents.from(
                    "buy a dozen milk\nUser clarification 1: Zepto\nUser clarification 2: two",
                    listOf("Zepto"),
                ),
            )
        assertEquals("Zepto", capped.app)
        assertEquals("2", capped.params["quantity"])
        assertFalse(capped.needsClarification())
        assertFalse(ShoppingIntents.supported("Amazon Music"))
        assertTrue(ShoppingIntents.supported("Big Basket"))
    }

    @Test fun eachFamilyHasExactCartOnlySkillAndPlainPreview() =
        runTest {
            for (app in ShoppingIntents.families) {
                val intent = intent(app)
                val fake = IntentFixtureProvider(emptyList())
                val planned = TaskPlanner(fake, enabled).planWithNotes("buy two milk on $app", intent = intent)
                assertEquals(9, planned.steps.size)
                assertEquals(listOf(ShoppingPlans.HANDOFF), planned.notes)
                assertEquals("milk", planned.steps[3].action.params["content"])
                val search =
                    if (app == "Zepto") "search:id:com.zeptoconsumerapp:id/search-query-input" else "search:Search"
                assertEquals(search, planned.steps[3].action.params["searchText"])
                val navigation =
                    if (app == "Zepto") {
                        "id:com.zeptoconsumerapp:id/homepage-search-box"
                    } else {
                        "exact:Search,exact:Search for products"
                    }
                assertEquals(navigation, planned.steps[2].action.params["text"])
                assertEquals(
                    ShoppingPlans.HANDOFF,
                    planned.steps
                        .last()
                        .action.plannerClaim,
                )
                assertTrue(PlanCompleteness.gaps("buy two milk on $app", intent, planned.steps, enabled).isEmpty())
                val preview = TaskPlanPreview.describe(planned.steps)
                assertTrue(preview.contains("add 2 x milk to the cart"))
                assertTrue(preview.contains("open the cart and stop so you can pay"))
                assertTrue(fake.requests.isEmpty())
            }
        }

    @Test fun modelPaymentTapsAndFieldsAreDeniedIncludingAliasesAndIds() {
        val targets =
            listOf(
                "pay",
                "place order",
                "confirm order",
                "buy now",
                "checkout confirm",
                "proceed to pay",
                "UPI",
                "card",
                "cod",
                "order now",
                "subscribe",
                "Confirm address",
                "id:place_order",
            )
        for (target in targets) {
            for (verb in listOf("CLICK_TEXT", "CLICK_ID", "TYPE_TEXT", "TYPE_ID", "tap_text", "paste")) {
                val step = LoopStep("task-1", ExecutedAction(verb, mapOf("target" to target)))
                assertThrows(IllegalArgumentException::class.java) {
                    IntentPlanProtection.validate(intent(), listOf(step))
                }
                assertThrows(IllegalArgumentException::class.java) {
                    IntentPlanProtection.validate(TaskIntent("Model planned task"), listOf(step))
                }
            }
        }
    }

    @Test fun modelCannotDropAddChangeQuantityOrAppendAnythingAfterCart() {
        val intent = intent()
        val steps = requireNotNull(IntentSkills.plan(intent, enabled))
        for (bad in listOf(
            steps.dropLast(1),
            steps.drop(1),
            steps + steps.first(),
            steps.filterNot {
                it.action.params["text"] == "exact:+,exact:Increase quantity"
            },
        )) {
            assertThrows(IllegalArgumentException::class.java) { IntentPlanProtection.validate(intent, bad) }
        }
        assertTrue(PlanCompleteness.gaps("buy two milk on Zepto", intent, steps.take(1), enabled).isNotEmpty())
    }

    @Test fun unsupportedAppsRemainOpenOnlyWithHonestShoppingNote() =
        runTest {
            val intent = intent("Unknown shop")
            val planned =
                TaskPlanner(IntentFixtureProvider(emptyList()), enabled)
                    .planWithNotes("buy two milk on Unknown shop", intent = intent)
            assertEquals(listOf("OPEN_APP"), planned.steps.map { it.action.name })
            assertTrue(planned.notes.single().startsWith(PlanCompleteness.SHOPPING))
        }

    private fun intent(app: String = "Zepto") =
        TaskIntent("Buy milk", app, "purchase", mapOf("item" to "milk", "quantity" to "2"), confidence = 1.0)
}
