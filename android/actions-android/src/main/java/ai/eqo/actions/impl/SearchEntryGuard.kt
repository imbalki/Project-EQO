// Origin: EQO guarded app-search entry for runs that resume on an old app screen.
package ai.eqo.actions.impl

import ai.eqo.accessibility.GenericAppAutomator
import ai.eqo.accessibility.SearchEntryRecovery
import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.AppSearchEntry
import ai.eqo.core.agent.ExecutedAction
import android.util.Log

/**
 * One instance per run, like [ShoppingPopupGuard]: it routes the search-entry click of a shopping
 * plan or a 'search X in <app>' plan through the guarded [SearchEntryRecovery] when the app
 * resumed on one of its old screens, and marks the plan's type step as already done when the
 * recovery typed the query itself. A null spec (any other plan) never touches a step.
 */
class SearchEntryGuard(
    private val spec: AppSearchEntry.Spec?,
    private val openSearch: suspend (String, String, String) -> SearchEntryRecovery.Outcome =
        GenericAppAutomator::openSearchEntry,
) {
    private var queryEntered = false

    @Suppress("ReturnCount") // One early exit per guarded step kind, like the other run guards.
    suspend fun execute(
        action: ExecutedAction,
        stepId: String,
        attempt: suspend () -> ActionResult,
    ): ActionResult {
        val target = spec ?: return attempt()
        if (stepId == target.typeStepId) {
            // The recovery already put the query into the app's open search field.
            if (queryEntered && action.params["content"] == target.query) {
                return ActionResult.Success(mapOf("message" to TYPED_DETAIL))
            }
            return attempt()
        }
        if (stepId != target.entryStepId) return attempt()
        val first = attempt()
        if (first.error != CONTROL_NOT_FOUND) return first
        val outcome = openSearch(target.entry, target.query, target.app)
        outcome.missingReason?.let { reason ->
            Log.i("EqoRun", "search_entry reason=missing_search")
            return ActionResult.Failure(reason)
        }
        if (outcome.queryEntered) queryEntered = true
        Log.i("EqoRun", "search_entry reason=recovered query_entered=${outcome.queryEntered}")
        return outcome.result.toActionResult()
    }

    private companion object {
        const val CONTROL_NOT_FOUND = "control_not_found"
        const val TYPED_DETAIL = "Typed into the open search field."
    }
}
