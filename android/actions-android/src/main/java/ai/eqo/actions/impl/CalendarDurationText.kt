// Origin: EQO-authored (fix/r6-cal): event duration wording for Calendar hand-offs; no donor code.
package ai.eqo.actions.impl

import java.util.Locale
import kotlin.math.roundToLong

/** "1 hour", "30 min", "90 minutes", "1.5 hours", "1h30m", "half an hour" or a bare number of minutes. */
internal object CalendarDurationText {
    const val DEFAULT_MINUTES = 60L
    private const val MINUTES_PER_HOUR = 60.0
    private const val MAX_MINUTES = 7L * 24L * 60L
    private const val MAX_BARE_DIGITS = 5

    private val unit = Regex("""(\d+(?:\.\d+)?)\s*(hours?|hrs?|h|minutes?|mins?|m)""")
    private val filler = Regex("""\b(?:and|about|around|approximately|approx|for)\b|,""")
    private val halfHour = Regex("""\bhalf an? hour\b|\bhalf hour\b""")
    private val hourAndHalf = Regex("""\ban? hour and a half\b|\bone and a half hours?\b|\bhour and a half\b""")
    private val oneHour = Regex("""\b(?:an|a|one) hour\b""")

    /** Whole minutes, or null when the wording is blank, not a duration, or longer than a week. */
    fun parseMinutes(raw: String?): Long? {
        val clean =
            raw
                .orEmpty()
                .lowercase(Locale.ROOT)
                .replace(hourAndHalf, "1.5 hours")
                .replace(halfHour, "30 min")
                .replace(oneHour, "1 hour")
                .trim()
        val parts = unit.findAll(clean).toList()
        val spoken = parts.isNotEmpty() && unit.replace(filler.replace(clean, " "), " ").isBlank()
        val bare = clean.takeIf { it.length <= MAX_BARE_DIGITS && it.all(Char::isDigit) }?.toDoubleOrNull()
        val minutes = if (spoken) parts.sumOf { minutesOf(it) } else bare
        return minutes?.roundToLong()?.takeIf { it in 1..MAX_MINUTES }
    }

    private fun minutesOf(match: MatchResult): Double {
        val value = match.groupValues[1].toDouble()
        return if (match.groupValues[2].startsWith("h")) value * MINUTES_PER_HOUR else value
    }
}
