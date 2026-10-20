// Origin: EQO cart-only shopping; local slots, no persistence or payment authority.
package ai.eqo.core.agent

/** Purchase parsing is local so a model cannot downgrade buying to merely opening an app. */
internal object ShoppingIntents {
    const val MAX_QUANTITY = 10
    private const val DOZEN = 12
    private const val MAX_OPTIONS = 4
    val families = listOf("Zepto", "Blinkit", "Swiggy Instamart", "BigBasket", "Amazon", "Flipkart")
    private val start = Regex("^(?:please\\s+)?(?:buy|purchase|order|add)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val appSuffix =
        Regex("\\s+(?:on|from|in|at|using|via|to(?:\\s+my)?)\\s+(.+?)(?:\\s+cart)?$", RegexOption.IGNORE_CASE)
    private val paymentTail =
        Regex(
            "\\s+(?:and\\s+)?(?:pay(?:\\s+(?:with|by|using|via)\\s+" +
                "(?:upi|(?:credit\\s+|debit\\s+)?card|cash|gpay))?|checkout|check\\s+out)[.!]?\\s*$",
            RegexOption.IGNORE_CASE,
        )
    private val words =
        listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")

    fun requestsPayment(request: String): Boolean = paymentTail.containsMatchIn(request.lines().first())

    fun supported(app: String?): Boolean {
        val label = app?.let(InstalledAppResolver::normalize)?.replace(" ", "").orEmpty()
        return label in setOf("swiggy", "instamart", "amazonshopping", "flipkartonlineshopping") ||
            families.any {
                label == InstalledAppResolver.normalize(it).replace(" ", "")
            }
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount") // Local missing/invalid slots fail closed before a model plan.
    fun from(
        request: String,
        installed: List<String>,
    ): TaskIntent? {
        // A trailing payment wish is refused in the preview, never an app name or executable shopping step.
        val shopping =
            request
                .lines()
                .first()
                .trim()
                .replace(paymentTail, "")
        val body = start.matchEntire(shopping)?.groupValues?.get(1) ?: return null
        if (Regex("^(?:status|history|number|id|details|tracking)\\b", RegexOption.IGNORE_CASE).containsMatchIn(body)) {
            return null
        }
        val suffix = appSuffix.find(body)
        val named =
            suffix
                ?.groupValues
                ?.get(1)
                ?.removeSuffix(" cart")
                ?.takeUnless { it.lowercase() in setOf("cart", "shopping app", "the shopping app") }
        val adding =
            request
                .lines()
                .first()
                .trim()
                .startsWith("add ", true)
        if (adding && !body.contains("cart", true) && !supported(named)) return null
        val answers = request.lines().filter { it.startsWith("User clarification ") }.map { it.substringAfter(": ") }
        val app =
            named?.let { InstalledAppResolver.resolve(it, installed) ?: it }
                ?: answers.asReversed().firstNotNullOfOrNull { InstalledAppResolver.resolve(it, installed) }
        val itemText =
            body
                .substringBefore(suffix?.value ?: "\u0000")
                .removePrefix("me ")
                .removePrefix("some ")
                .removeSuffix(" to cart")
                .trim()
        val (quantity, item) = slots(itemText)
        val revised = answers.asReversed().firstNotNullOfOrNull { answerQuantity(it) } ?: quantity
        val options = if (app == null) installed.filter(::supported).take(MAX_OPTIONS) else emptyList()
        return TaskIntent(
            "Add $revised x $item to the cart${app?.let { " in $it" }.orEmpty()}; stop before paying",
            app,
            "purchase",
            mapOf("item" to item, "quantity" to revised.toString()),
            confidence = 1.0,
            question =
                when {
                    app == null -> "Which shopping app?"
                    revised !in 1..MAX_QUANTITY -> "How many units (1 to $MAX_QUANTITY) should I add?"
                    item.isBlank() -> "Which item should I add?"
                    else -> null
                },
            options = options,
        )
    }

    private fun slots(text: String): Pair<Int, String> {
        val first = text.substringBefore(' ').lowercase()
        val word = words.indexOf(first) + 1
        return when {
            text.startsWith("a dozen ", true) -> DOZEN to text.drop("a dozen ".length)
            text.startsWith("dozen ", true) -> DOZEN to text.substringAfter(' ')
            first.toIntOrNull() != null -> first.toInt() to text.substringAfter(' ', "")
            first.any(Char::isDigit) && first.all { it.isDigit() || it in ".-" } -> 0 to text.substringAfter(' ', "")
            word > 0 -> word to text.substringAfter(' ', "")
            first in setOf("a", "an") -> 1 to text.substringAfter(' ', "")
            else -> 1 to text
        }
    }

    private fun answerQuantity(answer: String): Int? {
        val word = (words.indexOf(answer.lowercase()) + 1).takeIf { it > 0 }
        return answer.toIntOrNull() ?: word
    }
}
