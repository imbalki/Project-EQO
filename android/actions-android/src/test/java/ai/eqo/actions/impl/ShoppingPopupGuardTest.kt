// Origin: EQO fake shopping recovery and per-run budget regressions.
package ai.eqo.actions.impl

import ai.eqo.accessibility.A11yError
import ai.eqo.accessibility.A11yResult
import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.ShoppingPlans
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingPopupGuardTest {
    private val quantity =
        ExecutedAction("CLICK_TEXT", mapOf("text" to ShoppingPlans.INCREASE), plannerClaim = "shopping:milk in Zepto")
    private val missing = ActionResult.Failure("control_not_found")

    @Test fun popupDismissedThenQuantityRetriedOnce() =
        runTest {
            var popup = true
            var attempts = 0
            val guard =
                ShoppingPopupGuard {
                    popup = false
                    A11yResult.success("closed")
                }
            val result =
                guard.execute(quantity) {
                    attempts++
                    if (popup) missing else ActionResult.Success()
                }
            assertTrue(result.success)
            assertEquals(2, attempts)
        }

    @Test fun twoDismissalsAcrossQuantityAndCartThenFreshRunGetsNewBudget() =
        runTest {
            var dismissals = 0
            val close = {
                dismissals++
                A11yResult.success("closed")
            }
            val guard = ShoppingPopupGuard(close)
            val cart = quantity.copy(params = mapOf("text" to ShoppingPlans.CART), plannerClaim = ShoppingPlans.HANDOFF)
            for (action in listOf(quantity, cart)) {
                var attempts = 0
                assertTrue(guard.execute(action) { if (++attempts == 1) missing else ActionResult.Success() }.success)
            }
            val capped = guard.execute(quantity) { missing }
            assertEquals(2, dismissals)
            assertEquals(ActionResult.UserActionRequired(ShoppingPopupGuard.HANDOFF), capped)
            var attempts = 0
            assertTrue(
                ShoppingPopupGuard(close)
                    .execute(quantity) {
                        if (++attempts == 1) missing else ActionResult.Success()
                    }.success,
            )
            assertEquals(3, dismissals)
        }

    @Test fun noSafeCloseAndStillBlockedBothHandOffWithExactPlainText() =
        runTest {
            for (close in listOf(
                A11yResult.failure(A11yError.NodeNotFound("close")),
                A11yResult.failure(A11yError.ActionRejected("close")),
                A11yResult.success("closed"),
            )) {
                var attempts = 0
                val result =
                    ShoppingPopupGuard { close }.execute(quantity) {
                        attempts++
                        missing
                    }
                assertFalse(result.success)
                assertEquals("A pop-up is covering Zepto; close it and try again", result.error)
                assertEquals(if (close.isSuccess) 2 else 1, attempts)
            }
        }

    @Test fun normalStepsAcceptedClicksAndOtherFailuresNeverDismiss() =
        runTest {
            var dismissals = 0
            val guard =
                ShoppingPopupGuard {
                    dismissals++
                    A11yResult.success("closed")
                }
            val search = quantity.copy(params = mapOf("text" to "exact:Search"))
            val unrelated = quantity.copy(plannerClaim = "")
            assertEquals(missing, guard.execute(search) { missing })
            assertEquals(missing, guard.execute(unrelated) { missing })
            assertTrue(guard.execute(quantity) { ActionResult.Success() }.success)
            val rejected = ActionResult.Failure("Android did not accept the requested action.")
            assertEquals(rejected, guard.execute(quantity) { rejected })
            assertEquals(0, dismissals)
        }

    @Test fun gatesAreNotRetriedOrReportedAsPopupSuccess() =
        runTest {
            for (error in listOf(
                A11yError.AccessibilityDisabled,
                A11yError.TakeoverDetected,
                A11yError.SecureWindow,
            )) {
                var attempts = 0
                val closed = A11yResult.failure(error)
                val result =
                    ShoppingPopupGuard { closed }.execute(quantity) {
                        attempts++
                        missing
                    }
                assertEquals(closed.toControlResult(), result)
                assertEquals(1, attempts)
            }
        }
}
