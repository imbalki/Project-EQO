// Origin: EQO fake-tree regressions for the guarded app-search entry on resumed app screens.
package ai.eqo.accessibility

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fake app screens for the one guarded search entry the shopping skill and the 'search X in
 * <app>' skill share: resumed on results, resumed on a search field, a deep screen, a checkout
 * screen and nothing to find.
 */
class SearchEntryRecoveryTest {
    private val takeover = TakeoverDetector()
    private var serviceState = EqoAutomation.ServiceState.AVAILABLE
    private val entry = "exact:Search,exact:Search for products"

    @Test
    fun resumedResultsPageTypesIntoTheOpenSearchBox() =
        runTest {
            val box = FakeNode(hintText = "Search for products", isEditable = true, text = "milk")
            val row =
                FakeNode()
                    .child(FakeNode(text = "Milk"))
                    .child(FakeNode(text = "Add to cart", isClickable = true))
            val recovery = SearchEntryRecovery(automation { FakeNode(packageName = "shop").child(row).child(box) })
            val outcome = recovery.recover(entry, "bread", "Zepto")
            assertTrue(outcome.result.isSuccess)
            assertTrue(outcome.queryEntered)
            assertNull(outcome.missingReason)
            assertEquals("bread", box.typedValue)
            // The old query is selected before typing: replacement, never an append.
            assertEquals(listOf("focus", "selectAll", "setText"), box.actions)
        }

    @Test
    fun resumedSearchFieldWithOldTextIsSelectedAndReplaced() =
        runTest {
            // No hint and no label: only the resource id names this field a search field.
            val field =
                FakeNode(
                    viewIdResourceName = "shop:id/search_input",
                    isEditable = true,
                    isFocused = true,
                    text = "milk",
                )
            val recovery = SearchEntryRecovery(automation { FakeNode(packageName = "shop").child(field) })
            val outcome = recovery.recover("id:search_input", "bread", "Zepto")
            assertTrue(outcome.result.isSuccess)
            assertTrue(outcome.queryEntered)
            assertEquals("bread", field.typedValue)
            assertEquals(listOf("focus", "selectAll", "setText"), field.actions)
        }

    @Test
    fun deepScreenPressesBackUntilTheSearchEntryReturns() =
        runTest {
            val search = FakeNode(text = "Search", isClickable = true)
            var root =
                FakeNode(packageName = "shop")
                    .child(FakeNode(text = "Product details"))
                    .child(FakeNode(text = "Add to cart", isClickable = true))
            var backs = 0
            val recovery =
                SearchEntryRecovery(automation { root }) {
                    backs++
                    root = FakeNode(packageName = "shop").child(search)
                    A11yResult.success("back")
                }
            val outcome = recovery.recover(entry, "bread", "Zepto")
            assertTrue(outcome.result.isSuccess)
            assertFalse(outcome.queryEntered)
            assertNull(outcome.missingReason)
            assertEquals(1, backs)
            assertEquals(1, search.clickCount)
        }

    @Test
    fun checkoutScreenIsNeverBackedOutOf() =
        runTest {
            var backs = 0
            val recovery =
                SearchEntryRecovery(automation { checkoutScreen() }) {
                    backs++
                    A11yResult.success("back")
                }
            val outcome = recovery.recover(entry, "bread", "Zepto")
            assertFalse(outcome.result.isSuccess)
            assertFalse(outcome.queryEntered)
            assertEquals("I could not find Search in Zepto", outcome.missingReason)
            assertEquals("the recovery must stop on a checkout screen", 0, backs)
        }

    @Test
    fun missingEntryFailsWithThePlainReasonAfterBoundedBacks() =
        runTest {
            var backs = 0
            var entryAttempts = 0
            val screen = FakeNode(packageName = "shop").child(FakeNode(text = "Profile", isClickable = true))
            val recovery =
                SearchEntryRecovery(automation { screen }) {
                    backs++
                    A11yResult.success("back")
                }
            val outcome =
                recovery.recover(entry, "bread", "Zepto") {
                    entryAttempts++
                    A11yResult.failure(A11yError.NodeNotFound("Search"))
                }
            assertFalse(outcome.result.isSuccess)
            assertEquals("I could not find Search in Zepto", outcome.missingReason)
            assertFalse(outcome.queryEntered)
            assertEquals(SearchEntryRecovery.MAX_BACKS, backs)
            assertEquals(SearchEntryRecovery.MAX_BACKS, entryAttempts)
        }

    @Test
    fun backStopsWhenTheWindowNoLongerBelongsToTheApp() =
        runTest {
            var root = FakeNode(packageName = "shop").child(FakeNode(text = "Profile", isClickable = true))
            var backs = 0
            val recovery =
                SearchEntryRecovery(automation { root }) {
                    backs++
                    // A Back that would leave the app: the recovery must not continue on it.
                    root = FakeNode(packageName = "launcher").child(FakeNode(text = "Apps list"))
                    A11yResult.success("back")
                }
            val outcome = recovery.recover(entry, "bread", "Zepto")
            assertFalse(outcome.result.isSuccess)
            assertEquals("I could not find Search in Zepto", outcome.missingReason)
            assertEquals(1, backs)
        }

    @Test
    fun pincodeBoxIsNeverTreatedAsTheSearchField() =
        runTest {
            var backs = 0
            val pincode = FakeNode(hintText = "Search or type your pincode", isEditable = true)
            val screen = FakeNode(packageName = "shop").child(pincode)
            val recovery =
                SearchEntryRecovery(automation { screen }) {
                    backs++
                    A11yResult.success("back")
                }
            val outcome = recovery.recover(entry, "bread", "Zepto")
            assertNull(pincode.typedValue)
            // The pincode hint is on the payment deny-list: no typing and no Back.
            assertEquals("I could not find Search in Zepto", outcome.missingReason)
            assertEquals(0, backs)
        }

    @Test
    fun protectedScreensStopRecoveryWithoutBacking() =
        runTest {
            var backs = 0
            val recovery =
                SearchEntryRecovery(
                    EqoAutomation(
                        rootProvider = { FakeNode(packageName = "shop") },
                        serviceState = { serviceState },
                        takeover = takeover,
                        isSecureWindow = { true },
                    ),
                ) {
                    backs++
                    A11yResult.success("back")
                }
            val outcome = recovery.recover(entry, "bread", "Zepto")
            assertFalse(outcome.result.isSuccess)
            assertEquals(A11yError.SecureWindow, (outcome.result as A11yResult.Failure).error)
            assertNull(outcome.missingReason)
            assertEquals(0, backs)
        }

    /** A checkout screen: payment text is visible, so Back must never run. */
    private fun checkoutScreen(): FakeNode =
        FakeNode(packageName = "shop")
            .child(FakeNode(text = "Checkout", isClickable = true))
            .child(FakeNode(text = "Place order", isClickable = true))
            .child(FakeNode(text = "Delivering to a saved UPI address"))

    private fun automation(root: () -> FakeNode?): EqoAutomation =
        EqoAutomation(
            rootProvider = root,
            serviceState = { serviceState },
            takeover = takeover,
        )
}
