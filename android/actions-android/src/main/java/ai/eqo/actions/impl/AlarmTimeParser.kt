// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51
// Origin: path: app/src/main/java/com/opendroid/ai/actions/CalendarActions.kt lines 180-265; EQO range hardening.
package ai.eqo.actions.impl

internal object AlarmTimeParser {
    private const val MAX_HOUR = 23
    private const val HOURS_PER_DAY = 24
    private const val MAX_MINUTE = 59
    private const val NOON = 12
    private const val MORNING = 8
    private const val AFTERNOON = 14
    private const val EVENING = 18
    private const val NIGHT = 21
    private const val QUARTER_PAST = 15
    private const val HALF_PAST = 30
    private const val QUARTER_TO = 45

    // Natural language times.
    private val naturalTimes =
        mapOf(
            "midnight" to Pair(0, 0),
            "noon" to Pair(NOON, 0),
            "midday" to Pair(NOON, 0),
            "morning" to Pair(MORNING, 0),
            "afternoon" to Pair(AFTERNOON, 0),
            "evening" to Pair(EVENING, 0),
            "night" to Pair(NIGHT, 0),
        )

    // "5 am", "5am", "11 pm", "11pm"
    private val amPmSimple = Regex("""^(\d{1,2})\s*(am|pm|a\.m\.|p\.m\.)$""")

    // "5:30 am", "5:30am", "11:45 pm"
    private val amPmWithMinutes = Regex("""^(\d{1,2})[:\.](\d{2})\s*(am|pm|a\.m\.|p\.m\.)$""")

    // "17:30", "05:00", "9:45"
    private val military = Regex("""^(\d{1,2})[:\.](\d{2})$""")
    private val halfPast = Regex("""^half past (\d{1,2})$""")
    private val quarterPast = Regex("""^quarter past (\d{1,2})$""")
    private val quarterTo = Regex("""^quarter to (\d{1,2})$""")

    fun parse(input: String): Pair<Int, Int>? {
        val clean =
            input
                .lowercase(java.util.Locale.ROOT)
                .trim()
                .replace("o'clock", "")
                .replace("hours", "")
                .trim()
        return naturalTimes[clean]
            ?: parseAmPm(clean)
            ?: parseMilitary(clean)
            ?: parseBareHour(clean)
            ?: parseRelative(clean)
    }

    private fun parseAmPm(clean: String): Pair<Int, Int>? {
        val simple = amPmSimple.find(clean)?.destructured?.let { (hour, marker) -> Triple(hour, "0", marker) }
        val timed = amPmWithMinutes.find(clean)?.destructured?.let { (h, m, marker) -> Triple(h, m, marker) }
        val (hourText, minuteText, marker) = simple ?: timed ?: return null
        val hour = hourText.toInt()
        val minute = minuteText.toInt()
        val hour24 = (hour % NOON) + if (marker.startsWith("p")) NOON else 0
        return if (hour in 1..NOON && minute <= MAX_MINUTE) Pair(hour24, minute) else null
    }

    private fun parseMilitary(clean: String): Pair<Int, Int>? =
        military.find(clean)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            if (hour <= MAX_HOUR && minute <= MAX_MINUTE) Pair(hour, minute) else null
        }

    // Just a number: "5", "7", "22"
    private fun parseBareHour(clean: String): Pair<Int, Int>? {
        val hour = clean.toIntOrNull()
        return if (hour != null && hour in 0..MAX_HOUR) Pair(hour, 0) else null
    }

    private fun parseRelative(clean: String): Pair<Int, Int>? {
        // "half past 5" -> 5:30, "quarter past 5" -> 5:15
        val past =
            halfPast.find(clean)?.let { atHour(it, HALF_PAST) }
                ?: quarterPast.find(clean)?.let { atHour(it, QUARTER_PAST) }
        // "quarter to 6" -> 5:45
        val before =
            quarterTo.find(clean)?.let { match ->
                val target = match.groupValues[1].toInt()
                if (target in 0..MAX_HOUR) Pair((target + MAX_HOUR) % HOURS_PER_DAY, QUARTER_TO) else null
            }
        return past ?: before
    }

    private fun atHour(
        match: MatchResult,
        minute: Int,
    ): Pair<Int, Int>? =
        match.groupValues[1]
            .toInt()
            .takeIf { it in 0..MAX_HOUR }
            ?.let { Pair(it, minute) }

    fun format(
        hour: Int,
        minute: Int,
    ): String {
        val amPm = if (hour < NOON) "AM" else "PM"
        val displayHour =
            when {
                hour == 0 -> NOON
                hour > NOON -> hour - NOON
                else -> hour
            }
        return "$displayHour:${minute.toString().padStart(2, '0')} $amPm"
    }
}

/** Same supported donor units, with complete-input and overflow validation before a Clock intent. */
internal object TimerDurationParser {
    private const val SECONDS_PER_HOUR = 3600L
    private const val SECONDS_PER_MINUTE = 60L
    private val unitPattern =
        Regex("""(\d+)\s*(hours|hour|hrs|hr|h|minutes|minute|mins|min|m|seconds|second|secs|sec|s)""")

    fun parse(input: String): Int? {
        val clean = input.trim().lowercase(java.util.Locale.ROOT)
        val bare = clean.toLongOrNull()
        return if (bare != null) {
            bare.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
        } else {
            parseUnits(clean)
        }
    }

    private fun parseUnits(clean: String): Int? {
        val matches = unitPattern.findAll(clean).toList()
        var total: Long? = 0L
        if (matches.isEmpty() || unitPattern.replace(clean, "").isNotBlank()) total = null
        for (match in matches) total = total?.let { addUnit(it, match) }
        return total?.takeIf { it > 0 }?.toInt()
    }

    private fun addUnit(
        total: Long,
        match: MatchResult,
    ): Long? {
        val value = match.groupValues[1].toLongOrNull() ?: return null
        val factor =
            when (match.groupValues[2].first()) {
                'h' -> SECONDS_PER_HOUR
                'm' -> SECONDS_PER_MINUTE
                else -> 1L
            }
        return if (value > Int.MAX_VALUE / factor) null else (total + value * factor).takeIf { it <= Int.MAX_VALUE }
    }
}
