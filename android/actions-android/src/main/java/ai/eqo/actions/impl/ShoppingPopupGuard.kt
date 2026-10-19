// Origin: EQO bounded, close-only recovery for cart-only shopping runs.
package ai.eqo.actions.impl

import ai.eqo.accessibility.A11yError
import ai.eqo.accessibility.A11yResult
import ai.eqo.accessibility.EqoAutomation
import ai.eqo.accessibility.GenericAppAutomator
import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.ShoppingPlans
import android.util.Log

/** One instance per executor/run, never a global budget or an extra registry action. */
class ShoppingPopupGuard(
    private val dismiss: (String) -> A11yResult = GenericAppAutomator::dismissShoppingPopup,
) {
    private var dismissals = 0

    suspend fun execute(
        action: ExecutedAction,
        attempt: suspend () -> ActionResult,
    ): ActionResult {
        val first = attempt()
        return if (shoppingTarget(action) && first.error == "control_not_found") {
            recover(action.params.getValue("text"), first, attempt)
        } else {
            first
        }
    }

    private suspend fun recover(
        target: String,
        missing: ActionResult,
        attempt: suspend () -> ActionResult,
    ): ActionResult {
        if (dismissals >= MAX_DISMISSALS) return handoff("dismissal_cap", CONTROL_HANDOFF)
        // Count attempts too: a rejected click must not be retried by another step.
        dismissals++
        val closed = dismiss(target)
        val error = (closed as? A11yResult.Failure)?.error
        return when {
            error == A11yError.NodeNotFound(EqoAutomation.NO_SHOPPING_POPUP) -> {
                dismissals--
                missing
            }
            error is A11yError.NodeNotFound -> handoff("no_safe_close")
            error is A11yError.ActionRejected -> handoff("close_rejected")
            !closed.isSuccess -> closed.toControlResult()
            else -> {
                Log.i("EqoRun", "shopping_popup reason=missing_control_dismissed")
                val retried = attempt()
                if (retried.error == "control_not_found") handoff("still_blocked") else retried
            }
        }
    }

    private fun shoppingTarget(action: ExecutedAction): Boolean =
        action.name == "CLICK_TEXT" &&
            (action.plannerClaim.startsWith("shopping:") || action.plannerClaim == ShoppingPlans.HANDOFF) &&
            action.params["text"] in setOf(ShoppingPlans.INCREASE, ShoppingPlans.CART, ADD)

    private fun handoff(
        reason: String,
        message: String = HANDOFF,
    ): ActionResult {
        Log.i("EqoRun", "shopping_popup reason=$reason")
        return ActionResult.UserActionRequired(message)
    }

    companion object {
        const val HANDOFF = "A pop-up is covering Zepto; close it and try again"
        const val CONTROL_HANDOFF = "Zepto's requested control is unavailable; check the screen and try again"
        private const val MAX_DISMISSALS = 2
        private const val ADD = "exact:Add,exact:ADD,exact:Add to cart"
    }
}
