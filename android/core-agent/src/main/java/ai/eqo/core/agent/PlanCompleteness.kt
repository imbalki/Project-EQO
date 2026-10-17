// Origin: EQO plan completeness guard; pure, no I/O, no persistence, no logging.
package ai.eqo.core.agent

import java.net.URLDecoder
import java.util.Locale

/**
 * Checks that a model plan carries out every part the understanding named. A plan that looks complete but silently
 * dropped the search, the website, the message text or the phone setting is never shown as if it were whole.
 *
 * A [Gap] is repaired by one more planner request; what still cannot be done is told to the person as a note.
 */
object PlanCompleteness {
    /** [reason] is for the planner's one repair request, [note] is plain text for the plan preview. */
    data class Gap(
        val reason: String,
        val note: String,
    )

    const val SHOPPING = "EQO cannot do the shopping part yet"
    private const val MAX_TERM = 80
    private val PURCHASE =
        Regex(
            "\\b(?:buy|purchase|order)\\b(?!\\s+(?:status|history|number|id|details|tracking|update))",
            RegexOption.IGNORE_CASE,
        )
    private val ITEM =
        Regex(
            "\\b(?:buy|purchase|order)\\s+(?:me\\s+)?(?:some\\s+)?(.+?)" +
                "(?:\\s+(?:on|from|at|in|using|via|through)\\s+\\S.*)?\$",
            RegexOption.IGNORE_CASE,
        )

    /** Parts the plan leaves out. Empty when the plan carries out everything that was understood. */
    @Suppress("ReturnCount") // Questions and cart-only shopping have distinct non-executable/complete boundaries.
    fun gaps(
        request: String,
        intent: TaskIntent,
        steps: List<LoopStep>,
        enabled: Set<String>,
    ): List<Gap> {
        val first = request.lines().first()
        // Questions are routed back to intent clarification, not repaired as a partial executable plan.
        if (RegistryPlanVocabulary.clarification(steps) != null) return emptyList()
        if (intent.action == "purchase") {
            return if (ShoppingIntents.supported(intent.app) &&
                steps.lastOrNull()?.action?.plannerClaim == ShoppingPlans.HANDOFF
            ) {
                emptyList()
            } else {
                listOf(Gap("Shopping must end at the cart", "$SHOPPING: this plan does not add the item to the cart."))
            }
        }
        val values = Carriage.carried(steps)
        return listOfNotNull(
            searchGap(first, intent, steps, values),
            siteGap(first, steps, values),
            messageGap(intent, steps, values),
            phoneGap(intent, steps, enabled),
        )
    }

    /** True when the request asks to buy or order something. EQO cannot place orders or pay yet. */
    fun isPurchase(request: String): Boolean = PURCHASE.containsMatchIn(request.lines().first())

    /** The honest note for a purchase that the plan only prepares, shown even when the search part is present. */
    fun purchaseNote(
        request: String,
        gaps: List<Gap>,
    ): String? =
        if (isPurchase(request) && gaps.none { it.note.startsWith(SHOPPING) }) {
            "EQO cannot place orders or pay yet: this plan stops before buying, so you finish the purchase yourself."
        } else {
            null
        }

    private fun searchGap(
        first: String,
        intent: TaskIntent,
        steps: List<LoopStep>,
        values: String,
    ): Gap? {
        val term = searchTerm(first, intent)
        return if (term == null || Carriage.carries(values, term)) {
            null
        } else {
            val shopping = isPurchase(first)
            val shown = TaskDisplayText.escape(term.take(MAX_TERM))
            val limit = Carriage.limit(steps, "this plan does not include it")
            Gap(
                "The plan must carry out the understood search for \"$shown\" (use the app's search box with " +
                    "TYPE_TEXT, or WEB_SEARCH / OPEN_URL); it must not stop at opening the app. " +
                    "Do not place orders or pay.",
                if (shopping) {
                    "$SHOPPING: $limit."
                } else {
                    "EQO could not add the search for \"$shown\": $limit."
                },
            )
        }
    }

    private fun siteGap(
        first: String,
        steps: List<LoopStep>,
        values: String,
    ): Gap? {
        val url =
            first
                .split(' ')
                .map { it.trim('"', '\'', ',', '.', '!', '?', '(', ')') }
                .firstOrNull { WebTargets.looksLikeSite(it) }
                ?.let(WebTargets::siteUrl)
        val shown = url?.let(WebTargets::display)?.substringBefore('/')
        return if (url == null || shown == null || values.contains(shown.lowercase(Locale.ROOT))) {
            null
        } else {
            val limit = Carriage.limit(steps, "this plan does not open it")
            Gap(
                "The plan must open the website $url directly with OPEN_URL url=$url (plus browser=<app> when a " +
                    "browser was named). Do not tap an address bar or search box for it.",
                "EQO could not add the website step: $limit. Open ${TaskDisplayText.escape(shown)} yourself.",
            )
        }
    }

    private fun messageGap(
        intent: TaskIntent,
        steps: List<LoopStep>,
        values: String,
    ): Gap? {
        val text = intent.params["message"]?.trim().orEmpty()
        val referenced =
            steps.any { step ->
                step.action.params.values
                    .any { it.contains("{{step:") }
            }
        val sent = intent.action == "send_message" && text.isNotEmpty() && !referenced
        val missing = sent && !Carriage.carries(values, text, true)
        return if (missing) {
            Gap(
                "The plan must carry the understood message text exactly; it was left out.",
                "EQO could not add the message text: this plan does not include it. Check the steps before approving.",
            )
        } else {
            null
        }
    }

    private fun phoneGap(
        intent: TaskIntent,
        steps: List<LoopStep>,
        enabled: Set<String>,
    ): Gap? {
        val name = intent.params[PhoneActions.PARAM]?.takeIf { it in enabled }
        return if (name == null || steps.any { it.action.name == name }) {
            null
        } else {
            Gap(
                "The request is the phone action $name. Plan it with $name and its documented parameters, " +
                    "not by opening Settings or another app.",
                "EQO did not plan the phone setting you asked for. Check the steps before approving.",
            )
        }
    }

    // What the person wants searched: the understood query, or the item of a buy/order request.
    private fun searchTerm(
        first: String,
        intent: TaskIntent,
    ): String? =
        when {
            intent.action == "search" -> intent.params["query"]?.trim()
            isPurchase(first) ->
                ITEM
                    .find(first)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
            else -> null
        }?.takeIf { it.isNotEmpty() }
}

/** What a plan's steps type, search, open by URL or send, and how much of a wanted text they carry. */
private object Carriage {
    private const val MIN_WORD = 3
    private val OPEN_ONLY = setOf("OPEN_APP", "open_app", "WAIT", "observe", "press_home")
    private val NOT_CARRIERS = setOf("appName", "app", "packageName", "browser")
    private val STOP = "the for and with some any please from this that".split(' ').toSet()
    private val SPLIT = Regex("[^\\p{L}\\p{N}]+")

    // "it will only open Zepto" when the plan does nothing but open apps; otherwise the stated fallback.
    fun limit(
        steps: List<LoopStep>,
        otherwise: String,
    ): String = if (steps.all { it.action.name in OPEN_ONLY }) "it will only ${opens(steps)}" else otherwise

    private fun opens(steps: List<LoopStep>): String {
        val apps =
            steps
                .filter { it.action.name == "OPEN_APP" || it.action.name == "open_app" }
                .mapNotNull { it.action.params["appName"] ?: it.action.params["app"] }
                .map { TaskDisplayText.escape(it) }
                .distinct()
        return if (apps.isEmpty()) "do nothing for it" else "open ${apps.joinToString(" and ")}"
    }

    // Everything the plan types, searches, opens by URL or sends, lower-cased and URL-decoded. App targets excluded.
    fun carried(steps: List<LoopStep>): String =
        steps
            .flatMap { step ->
                step.action.params
                    .filterKeys { it !in NOT_CARRIERS }
                    .values
            }.joinToString("\n") { (it + "\n" + decode(it)).lowercase(Locale.ROOT) }

    private fun decode(value: String): String =
        try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: IllegalArgumentException) {
            value
        }

    // Any significant word of the wanted text is enough; short or stop-word-only text must appear whole.
    fun carries(
        values: String,
        term: String,
        whole: Boolean = false,
    ): Boolean {
        val lowered = term.lowercase(Locale.ROOT)
        val words = lowered.split(SPLIT).filter { it.length >= MIN_WORD && it !in STOP }
        return values.contains(lowered) || (!whole && words.isNotEmpty() && words.any { values.contains(it) })
    }
}
