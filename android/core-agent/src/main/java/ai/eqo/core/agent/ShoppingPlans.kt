// Origin: EQO cart-only skill and fail-closed model-plan protection.
package ai.eqo.core.agent

object ShoppingPlans {
    const val FIRST_RESULT =
        "adds the first search result; this may be an advertised item; check the cart before paying"
    const val HANDOFF = "Needs you: EQO $FIRST_RESULT; pay yourself"
    const val INCREASE = "exact:+,exact:increase quantity"
    const val CART = "exact:Cart,exact:View cart,exact:Go to cart"
    private val forbidden =
        Regex(
            "pay|place[ _-]*order|confirm|buy[ _-]*now|checkout|upi|card|" +
                "(?<![a-z0-9])cod(?![a-z0-9])|order[ _-]*now|subscribe|address",
            RegexOption.IGNORE_CASE,
        )
    private val taps = setOf("CLICK_TEXT", "CLICK_ID", "TAP_TEXT", "TAP_ID", "TYPE_TEXT", "TYPE_ID", "PASTE")

    fun requestsPayment(request: String): Boolean = ShoppingIntents.requestsPayment(request)

    fun plan(intent: TaskIntent): List<ExecutedAction> {
        require(intent.missingParameter() == null && !intent.needsClarification()) { "Shopping needs clarification" }
        val app = requireNotNull(intent.app)
        if (!ShoppingIntents.supported(app)) return listOf(action("OPEN_APP", "appName" to app))
        val quantity = requireNotNull(intent.params["quantity"]?.toIntOrNull())
        require(quantity in 1..ShoppingIntents.MAX_QUANTITY) { "Shopping quantity needs clarification" }
        val item = intent.params.getValue("item")
        val zepto = InstalledAppResolver.normalize(app) == "zepto"
        val search =
            if (zepto) "id:com.zeptoconsumerapp:id/homepage-search-box" else "exact:Search,exact:Search for products"
        val input = if (zepto) "search:id:com.zeptoconsumerapp:id/search-query-input" else "search:Search"
        return buildList {
            add(action("OPEN_APP", "appName" to app))
            add(action("WAIT", "durationMs" to "3000"))
            add(action("CLICK_TEXT", "text" to search))
            add(action("TYPE_TEXT", "searchText" to input, "content" to item))
            add(action("PRESS_ENTER"))
            add(action("WAIT", "durationMs" to "3000"))
            add(action("CLICK_TEXT", "text" to "exact:Add,exact:ADD,exact:Add to cart"))
            repeat(quantity - 1) {
                add(action("CLICK_TEXT", "text" to INCREASE))
            }
            add(action("CLICK_TEXT", "text" to CART).copy(plannerClaim = HANDOFF))
        }.map { action ->
            if (action.plannerClaim == HANDOFF) {
                action
            } else {
                action.copy(plannerClaim = "${AppSearchEntry.SHOPPING_CLAIM}$item in $app")
            }
        }
    }

    /** Purchase plans are pinned to the local skill, including the final cart and search-only typing. */
    fun validate(
        intent: TaskIntent,
        steps: List<LoopStep>,
    ): List<LoopStep> {
        require(steps.none(::paymentTarget)) { "EQO never enters payment details or confirms an order" }
        if (intent.action == "purchase") {
            val expected = plan(intent)
            require(steps.map { it.action.name to it.action.params } == expected.map { it.name to it.params }) {
                "Shopping must use the cart-only skill and stop at the cart"
            }
            return steps.mapIndexed { index, step -> step.copy(action = expected[index]) }
        }
        return steps
    }

    private fun paymentTarget(step: LoopStep): Boolean {
        val action = step.action
        val name = action.name.uppercase()
        val typing = name in setOf("TYPE_TEXT", "TYPE_ID", "PASTE")
        val targets =
            action.params
                .filterKeys {
                    !typing || it in setOf("searchText", "target", "viewId", "view_id")
                }.values
        return name in taps && targets.any { forbidden.containsMatchIn(it) }
    }

    private fun action(
        name: String,
        vararg params: Pair<String, String>,
    ) = ExecutedAction(name, params.toMap(), irreversible = true)
}
