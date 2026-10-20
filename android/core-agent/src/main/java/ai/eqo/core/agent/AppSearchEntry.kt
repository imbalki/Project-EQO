// Origin: EQO one guarded app-search entry shared by the shopping and search skills.
package ai.eqo.core.agent

/**
 * The search-entry plan of the two app-opening skills: the cart-only shopping skill and the
 * 'search X in <app>' skill. Both open the app, click the app's search entry and type a query
 * into a search field, and both can resume on an old screen of the target app — so both route
 * that entry through one guarded helper instead of two recoveries.
 *
 * Recognition reads the approved plan only (claims and step shapes); a plan that is neither of
 * the two skills — including the fail-closed WhatsApp search template — is never guarded.
 */
object AppSearchEntry {
    /** Prefix of the shopping skill's per-step claim ("shopping:<item> in <app>"). */
    const val SHOPPING_CLAIM = "shopping:"

    data class Spec(
        val entryStepId: String,
        val entry: String,
        val typeStepId: String,
        val query: String,
        val app: String,
    )

    /** The two skills' search entry, or null when the plan is not one of them. */
    fun of(steps: List<LoopStep>): Spec? {
        val guarded = steps.any { it.action.plannerClaim.startsWith(SHOPPING_CLAIM) } || searchSkill(steps)
        val app =
            steps
                .firstOrNull { it.action.name == "OPEN_APP" }
                ?.action
                ?.params
                ?.get("appName")
        val entry =
            steps.zipWithNext().firstOrNull { (click, type) ->
                click.action.name == "CLICK_TEXT" && type.action.name == "TYPE_TEXT" && searchTyped(type.action)
            }
        if (guarded && app != null && entry != null) {
            val (click, type) = entry
            return Spec(
                entryStepId = click.stepId,
                entry = click.action.params["text"].orEmpty(),
                typeStepId = type.stepId,
                query = type.action.params["content"].orEmpty(),
                app = app,
            )
        }
        return null
    }

    /** The plain reason a guarded run reports when no search entry could be reached. */
    fun missingReason(app: String): String = "I could not find Search in $app"

    /** Same rule as the 'search X in <app>' preview: open, click search, type the query, submit. */
    private fun searchSkill(steps: List<LoopStep>): Boolean {
        val visible = steps.filterNot { it.action.name == "WAIT" }
        val type = visible.singleOrNull { it.action.name == "TYPE_TEXT" }?.action
        return visible.map { it.action.name } == listOf("OPEN_APP", "CLICK_TEXT", "TYPE_TEXT", "PRESS_ENTER") &&
            type != null &&
            searchTyped(type)
    }

    private fun searchTyped(action: ExecutedAction): Boolean = searchTyped(action.params["searchText"])

    private fun searchTyped(searchText: String?): Boolean = searchText.orEmpty().contains("search", ignoreCase = true)
}
