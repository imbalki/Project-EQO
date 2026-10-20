// Origin: EQO-authored (fix/r6-cal): resolves calendar date, time and duration params to epoch millis.
package ai.eqo.actions.impl

import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.TaskPlanPreview
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/** Display-only copies: resolve dates with the executor's parser, never change the approved payload. */
object CalendarPlanPreview {
    fun describe(
        steps: List<LoopStep>,
        recipients: Map<String, String> = emptyMap(),
        clock: Clock = Clock.systemDefaultZone(),
    ): String = TaskPlanPreview.describe(displaySteps(steps, clock), recipients)

    fun summary(
        steps: List<LoopStep>,
        noPayment: Boolean = false,
        clock: Clock = Clock.systemDefaultZone(),
    ): String = TaskPlanPreview.summary(displaySteps(steps, clock), noPayment)

    private fun displaySteps(
        steps: List<LoopStep>,
        clock: Clock,
    ): List<LoopStep> =
        steps.map { step ->
            val calendar = step.action.name in setOf("CREATE_CALENDAR_EVENT", "SET_REMINDER")
            val result = if (calendar) CalendarWhenParser.resolve(step.action.params, clock) else null
            if (result is CalendarWhenResult.Resolved) {
                val value = result.value
                val zone = if (value.allDay) ZoneOffset.UTC else clock.zone
                val day = Instant.ofEpochMilli(value.startMillis).atZone(zone).dayOfWeek
                val label = "${day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${value.label}"
                val slot = if (step.action.name == "SET_REMINDER") "datetime" else "date"
                step.copy(action = step.action.copy(params = step.action.params - "time" + (slot to label)))
            } else {
                step
            }
        }
}

/**
 * A resolved start and end for a Calendar hand-off. All-day events use UTC midnight boundaries, which is what the
 * Calendar app and provider expect for all-day rows. [label] is the plain-words form shown to the owner.
 */
internal data class CalendarWhen(
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val label: String,
)

internal sealed interface CalendarWhenResult {
    /** No date or time was given at all: the Calendar app keeps its own defaults. */
    data object Unspecified : CalendarWhenResult

    data class Resolved(
        val value: CalendarWhen,
    ) : CalendarWhenResult

    /** A date or time was given that could not be understood; [field] is "date" or "time". */
    data class Invalid(
        val field: String,
        val text: String,
    ) : CalendarWhenResult
}

/**
 * Reads the `date`, `time`, `datetime` and `duration` params of CREATE_CALENDAR_EVENT and SET_REMINDER.
 * A date alone is an all-day event; a time alone means its next occurrence. A date or time that cannot be
 * understood is [CalendarWhenResult.Invalid], never silently today.
 */
internal object CalendarWhenParser {
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MILLIS_PER_DAY = 24L * 60L * MILLIS_PER_MINUTE
    private const val HALF_DAY_HOURS = 12
    private const val MINUTE_WIDTH = 2
    private val monthNames =
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private const val AM_PM = """am|pm|a\.m\.|p\.m\."""
    private val isoStamp =
        Regex(
            """^(\d{4}-\d{2}-\d{2})[T ](\d{1,2}:\d{2})(?::\d{2}(?:\.\d+)?)?\s*(?:z|[+-]\d{2}:?\d{2})?$""",
            RegexOption.IGNORE_CASE,
        )
    private val clockTime =
        Regex(
            """(?:\bat\s+)?(?<![\d:])(\d{1,2}:\d{2}(?:\s*(?:$AM_PM))?|\d{1,2}\s*(?:$AM_PM))(?![\w:])""",
            RegexOption.IGNORE_CASE,
        )
    private val atHour = Regex("""\bat\s+(\d{1,2})(?![\d:])""", RegexOption.IGNORE_CASE)
    private val dayPart =
        Regex("""\b(noon|midday|midnight|morning|afternoon|evening|night)\b""", RegexOption.IGNORE_CASE)
    private val tonight = Regex("""\btonight\b""", RegexOption.IGNORE_CASE)
    private val seconds = Regex("""^(\d{1,2}:\d{2}):\d{2}$""")
    private val spaces = Regex("""\s+""")
    private val allDay = Regex("""\ball[ -]day[.!]?$""", RegexOption.IGNORE_CASE)

    private data class Split(
        val date: String,
        val time: String?,
    )

    fun resolve(
        params: Map<String, String>,
        clock: Clock,
    ): CalendarWhenResult {
        val splits =
            listOf(params["date"], params["datetime"])
                .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
                .map(::split)
        val dateText = splits.map { it.date }.firstOrNull { it.isNotEmpty() }.orEmpty()
        val explicitAllDay =
            listOf("date", "datetime", "time").any { allDay.containsMatchIn(params[it].orEmpty().trim()) }
        val timeText =
            (params["time"]?.trim()?.takeIf(String::isNotEmpty) ?: splits.firstNotNullOfOrNull { it.time })
                .takeUnless { explicitAllDay }
        val minutes = CalendarDurationText.parseMinutes(params["duration"]) ?: CalendarDurationText.DEFAULT_MINUTES
        val now = ZonedDateTime.now(clock)
        val time = timeText?.let(::parseTime)
        return when {
            dateText.isEmpty() && timeText == null -> CalendarWhenResult.Unspecified
            timeText != null && time == null -> CalendarWhenResult.Invalid("time", timeText)
            dateText.equals("now", ignoreCase = true) && time == null && !explicitAllDay ->
                CalendarWhenResult.Resolved(timed(now, minutes))
            else -> resolveDay(dateText, time, now, minutes)
        }
    }

    private fun resolveDay(
        dateText: String,
        time: LocalTime?,
        now: ZonedDateTime,
        minutes: Long,
    ): CalendarWhenResult {
        val today = now.toLocalDate()
        val day =
            when {
                dateText.isEmpty() -> nextOccurrence(time, now)
                dateText.equals("now", ignoreCase = true) -> today
                else -> CalendarDateText.parse(dateText, today)
            }
        return when {
            day == null -> CalendarWhenResult.Invalid("date", dateText)
            time == null -> CalendarWhenResult.Resolved(allDay(day))
            else -> CalendarWhenResult.Resolved(timed(day.atTime(time).atZone(now.zone), minutes))
        }
    }

    /** A time-only request means its next occurrence: today if still ahead, otherwise tomorrow. */
    private fun nextOccurrence(
        time: LocalTime?,
        now: ZonedDateTime,
    ): LocalDate {
        val today = now.toLocalDate()
        val passed = time != null && !today.atTime(time).atZone(now.zone).isAfter(now)
        return if (passed) today.plusDays(1) else today
    }

    /**
     * Separates a spoken or ISO time out of a date string such as "tomorrow at 6 pm" or "2026-10-19T18:00".
     * An ISO midnight is read as "no time", so a model that writes T00:00 for a plain date gets an all-day event.
     */
    private fun split(text: String): Split {
        val clean = allDay.replace(text, "").trim()
        val stamp = isoStamp.matchEntire(clean)
        val spoken = tonight.replace(clean, "today night")
        val found = clockTime.find(spoken) ?: atHour.find(spoken) ?: dayPart.find(spoken)
        return when {
            stamp != null -> {
                val clock = stamp.groupValues[2]
                Split(stamp.groupValues[1], clock.takeUnless { parseTime(it) == LocalTime.MIDNIGHT })
            }
            found != null -> Split(spaces.replace(spoken.removeRange(found.range), " ").trim(), found.groupValues[1])
            else -> Split(spoken, null)
        }
    }

    private fun parseTime(text: String): LocalTime? {
        val spoken = text.trim().lowercase(Locale.ROOT)
        val clean = seconds.replace(spoken.removePrefix("at ").trim(), "\$1")
        return AlarmTimeParser.parse(clean)?.let { (hour, minute) -> LocalTime.of(hour, minute) }
    }

    private fun timed(
        start: ZonedDateTime,
        minutes: Long,
    ): CalendarWhen {
        val begin = start.toInstant().toEpochMilli()
        val label = "${dateLabel(start.toLocalDate())}, ${timeLabel(start.toLocalTime())}"
        return CalendarWhen(begin, begin + minutes * MILLIS_PER_MINUTE, allDay = false, label = label)
    }

    private fun allDay(day: LocalDate): CalendarWhen {
        val begin = day.toEpochDay() * MILLIS_PER_DAY
        // The Calendar editor treats the end of an all-day hand-off as the last day shown (inclusive), so a one-day
        // event ends on the same midnight; begin + 1 day made it span two days on the phone.
        val end = begin
        return CalendarWhen(begin, end, allDay = true, label = "${dateLabel(day)}, all day")
    }

    private fun dateLabel(day: LocalDate): String = "${day.dayOfMonth} ${monthNames[day.monthValue - 1]} ${day.year}"

    private fun timeLabel(time: LocalTime): String {
        val hour = if (time.hour % HALF_DAY_HOURS == 0) HALF_DAY_HOURS else time.hour % HALF_DAY_HOURS
        val marker = if (time.hour < HALF_DAY_HOURS) "am" else "pm"
        return "$hour:${time.minute.toString().padStart(MINUTE_WIDTH, '0')} $marker"
    }
}
