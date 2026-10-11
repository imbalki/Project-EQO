// Origin: EQO intent v1 owner addendum; bounded local arithmetic, never code or web fallback.
package ai.eqo.core.agent

import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale

/** A number or one plain binary operation. Decimal arithmetic is shared by preview and execution. */
object PlainArithmetic {
    data class Answer(
        val expression: String,
        val result: String,
    )

    private const val MAX_INPUT = 256
    private const val MAX_RESULT = 512
    private const val PERCENT_SCALE = 100
    private const val NUMBER = "[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"
    private val operation = Regex("^($NUMBER)([+*/-])($NUMBER)$")
    private val percent = Regex("^($NUMBER)\\s*(?:percent of|% of)\\s*($NUMBER)$")
    private val words =
        mapOf(
            "multiplied by" to "*",
            "times" to "*",
            "x" to "*",
            "plus" to "+",
            "minus" to "-",
            "divided by" to "/",
            "over" to "/",
        )

    fun evaluate(expression: String): Answer? {
        if (expression.length > MAX_INPUT) return null
        val raw =
            expression
                .lowercase(Locale.ROOT)
                .trim()
                .removePrefix("what is ")
                .removeSuffix("?")
        val percentMatch = percent.matchEntire(raw)
        var clean = raw.replace('×', '*').replace('÷', '/')
        words.forEach { (word, symbol) -> clean = clean.replace(Regex("\\b$word\\b"), symbol) }
        clean = clean.replace(Regex("\\s+"), "")
        val match = operation.matchEntire(clean)
        val value =
            when {
                percentMatch != null ->
                    percentMatch.groupValues[1]
                        .toBigDecimal()
                        .multiply(percentMatch.groupValues[2].toBigDecimal())
                        .divide(BigDecimal(PERCENT_SCALE))
                match != null -> apply(match)
                else -> if (Regex("^$NUMBER$").matches(clean)) clean.toBigDecimalOrNull() else null
            }
        val result = value?.stripTrailingZeros()?.toPlainString()?.takeIf { it.length <= MAX_RESULT }
        return result?.let { Answer(if (percentMatch == null) clean else raw, it) }
    }

    /** Only complete arithmetic requests qualify; no stripping of send/contact/extra task instructions. */
    fun fromRequest(request: String): Answer? {
        var expression = request.trim().lowercase(Locale.ROOT)
        for (prefix in listOf("calculate ", "what is ", "work out ", "evaluate ", "tell me ")) {
            expression = expression.removePrefix(prefix)
        }
        for (suffix in listOf(" calculate and let me know", " and let me know", " please", "?")) {
            expression = expression.removeSuffix(suffix)
        }
        return evaluate(expression)
    }

    private fun apply(match: MatchResult): BigDecimal? {
        val (leftText, operator, rightText) = match.destructured
        val left = leftText.toBigDecimal()
        val right = rightText.toBigDecimal()
        return when (operator) {
            "+" -> left.add(right)
            "-" -> left.subtract(right)
            "*" -> left.multiply(right)
            else -> if (right.compareTo(BigDecimal.ZERO) == 0) null else left.divide(right, MathContext.DECIMAL64)
        }
    }
}
