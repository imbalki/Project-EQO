// Origin: EQO-authored (fix/r6-cal): plain-English calendar dates for Calendar hand-offs; no donor code.
package ai.eqo.actions.impl

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * Turns the date wording a planner may emit into a [LocalDate], resolved against a caller-supplied today.
 * Unknown wording returns null: callers must refuse rather than fall back to today.
 *
 * A date with no year means the nearest occurrence that is not in the past. A date that names its year is
 * kept exactly as written, even when it is already past.
 */
internal object CalendarDateText {
    private const val DAYS_PER_WEEK = 7
    private const val TWO_DAYS = 2L
    private const val SEARCH_YEARS = 8
    private const val TWO_DIGIT_YEAR_BASE = 2000
    private const val TWO_DIGIT_YEAR_LIMIT = 100
    private const val MAX_MONTH = 12
    private const val MIN_YEAR = 1970
    private const val MAX_YEAR = 2200
    private const val MAX_RELATIVE = 999L

    private val months =
        listOf(
            listOf("january", "jan"),
            listOf("february", "feb"),
            listOf("march", "mar"),
            listOf("april", "apr"),
            listOf("may"),
            listOf("june", "jun"),
            listOf("july", "jul"),
            listOf("august", "aug"),
            listOf("september", "sept", "sep"),
            listOf("october", "oct"),
            listOf("november", "nov"),
            listOf("december", "dec"),
        ).flatMapIndexed { index, names -> names.map { it to index + 1 } }.toMap()

    private val weekdays =
        listOf(
            DayOfWeek.MONDAY to listOf("monday", "mon"),
            DayOfWeek.TUESDAY to listOf("tuesday", "tues", "tue"),
            DayOfWeek.WEDNESDAY to listOf("wednesday", "weds", "wed"),
            DayOfWeek.THURSDAY to listOf("thursday", "thurs", "thur", "thu"),
            DayOfWeek.FRIDAY to listOf("friday", "fri"),
            DayOfWeek.SATURDAY to listOf("saturday", "sat"),
            DayOfWeek.SUNDAY to listOf("sunday", "sun"),
        ).flatMap { (day, names) -> names.map { it to day } }.toMap()

    private val ordinalSuffix = Regex("""(\d)(?:st|nd|rd|th)\b""")
    private val inThe = Regex("""\bin the\b""")
    private val fillerWords = Regex("""\b(?:on|the|of|at|this|coming|upcoming|next)\b""")
    private val separators = Regex("""[,\s]+""")
    private val joinedNamedDate = Regex("""(?<=\d)[-/.](?=[a-z])|(?<=[a-z])[-/.](?=\d)""")
    private val iso = Regex("""^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$""")
    private val dayThenMonth = Regex("""^(\d{1,2}) ([a-z]+)(?: (\d{4}))?$""")
    private val monthThenDay = Regex("""^([a-z]+) (\d{1,2})(?: (\d{4}))?$""")
    private val numeric = Regex("""^(\d{1,2})[-/.](\d{1,2})(?:[-/.](\d{4}|\d{2}))?$""")
    private val inDays = Regex("""^in (\d{1,3}) (day|days|week|weeks)$""")

    fun parse(
        raw: String,
        today: LocalDate,
    ): LocalDate? {
        val text = normalize(raw)
        return relative(text, today)
            ?: weekdays[text]?.let { nextWeekday(it, today) }
            ?: isoDate(text)
            ?: namedDate(text, today)
            ?: numericDate(text, today)
    }

    private fun normalize(raw: String): String {
        val lowered =
            raw
                .lowercase(Locale.ROOT)
                .replace(ordinalSuffix, "\$1")
                .replace(inThe, " ")
                .replace(fillerWords, " ")
        val spaced = if (lowered.any { it.isLetter() }) lowered.replace(joinedNamedDate, " ") else lowered
        return spaced.replace(separators, " ").trim()
    }

    private fun relative(
        text: String,
        today: LocalDate,
    ): LocalDate? =
        when (text) {
            "today", "tonight" -> today
            "tomorrow", "tmrw", "tmr" -> today.plusDays(1)
            "day after tomorrow", "day after" -> today.plusDays(TWO_DAYS)
            else -> inDays.find(text)?.let { match -> shifted(match, today) }
        }

    private fun shifted(
        match: MatchResult,
        today: LocalDate,
    ): LocalDate? {
        val count = match.groupValues[1].toLong()
        val unitDays = if (match.groupValues[2].startsWith("week")) DAYS_PER_WEEK else 1
        return if (count in 1..MAX_RELATIVE) today.plusDays(count * unitDays) else null
    }

    /** A bare weekday name means the next one, never today. */
    private fun nextWeekday(
        target: DayOfWeek,
        today: LocalDate,
    ): LocalDate {
        val ahead = (target.value - today.dayOfWeek.value + DAYS_PER_WEEK) % DAYS_PER_WEEK
        return today.plusDays(if (ahead == 0) DAYS_PER_WEEK.toLong() else ahead.toLong())
    }

    private fun isoDate(text: String): LocalDate? =
        iso.find(text)?.destructured?.let { (year, month, day) ->
            safeDate(year.toInt(), month.toInt(), day.toInt())
        }

    private fun namedDate(
        text: String,
        today: LocalDate,
    ): LocalDate? {
        val parts =
            dayThenMonth.find(text)?.destructured?.let { (day, name, year) -> Triple(day, name, year) }
                ?: monthThenDay.find(text)?.destructured?.let { (name, day, year) -> Triple(day, name, year) }
        val month = parts?.let { months[it.second] }
        return if (parts == null || month == null) {
            null
        } else {
            monthDay(parts.first.toInt(), month, parts.third.toIntOrNull(), today)
        }
    }

    /** Day first (19/10, 19-10-2026); a first part that cannot be a day and a second part above 12 is month first. */
    private fun numericDate(
        text: String,
        today: LocalDate,
    ): LocalDate? =
        numeric.find(text)?.destructured?.let { (first, second, yearText) ->
            val a = first.toInt()
            val b = second.toInt()
            val monthFirst = b > MAX_MONTH && a <= MAX_MONTH
            val year = yearText.toIntOrNull()?.let { if (it < TWO_DIGIT_YEAR_LIMIT) it + TWO_DIGIT_YEAR_BASE else it }
            if (monthFirst) monthDay(b, a, year, today) else monthDay(a, b, year, today)
        }

    private fun monthDay(
        day: Int,
        month: Int,
        year: Int?,
        today: LocalDate,
    ): LocalDate? =
        if (year != null) {
            safeDate(year, month, day)
        } else {
            (0..SEARCH_YEARS)
                .asSequence()
                .mapNotNull { safeDate(today.year + it, month, day) }
                .firstOrNull { !it.isBefore(today) }
        }

    private fun safeDate(
        year: Int,
        month: Int,
        day: Int,
    ): LocalDate? = runCatching { LocalDate.of(year, month, day) }.getOrNull()?.takeIf { it.year in MIN_YEAR..MAX_YEAR }
}
