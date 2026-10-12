// Origin: EQO-authored (fix/r6-cal): builds the Calendar insert hand-off carrying the requested when/where/who.
package ai.eqo.actions.impl

import ai.eqo.core.agent.TaskDisplayText
import ai.eqo.core.agent.isLiteralEmail
import android.content.Intent
import android.provider.CalendarContract

/** Calendar "insert" intent and the plain-words result text for CREATE_CALENDAR_EVENT and SET_REMINDER. */
internal object CalendarInsertIntent {
    private const val DEFAULT_DESCRIPTION = "Created by EQO"
    private const val MAX_ATTENDEES = 20
    private const val MAX_NAME = 60
    private const val MAX_TITLE_ECHO = 80
    private const val MAX_DATE_ECHO = 40
    private const val MAX_LOCATION = 200

    private val attendeeSeparators = Regex("""\s*(?:,|;|&|\band\b)\s*""", RegexOption.IGNORE_CASE)
    private val angled = Regex("""<([^<>\s]+)>""")

    private data class Attendees(
        val emails: List<String>,
        val names: List<String>,
    )

    fun title(
        params: Map<String, String>,
        default: String,
    ): String = listOf(params["title"], params["text"]).firstOrNull { !it.isNullOrBlank() }?.trim() ?: default

    /**
     * [legacyBeginMillis] is only used when no date or time was given (reminders have always started "now").
     * Attendees that are not email addresses go into the description so nothing the owner said is lost.
     */
    fun build(
        params: Map<String, String>,
        defaultTitle: String,
        outcome: CalendarWhenResult,
        legacyBeginMillis: Long?,
    ): Intent {
        val attendees = attendees(params["attendees"])
        val description =
            listOfNotNull(
                params["description"]?.takeUnless { it.isBlank() } ?: DEFAULT_DESCRIPTION,
                attendees.names.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "With: "),
            ).joinToString("\n")
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title(params, defaultTitle))
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            if (outcome is CalendarWhenResult.Resolved) {
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, outcome.value.startMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, outcome.value.endMillis)
                putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, outcome.value.allDay)
            } else if (legacyBeginMillis != null) {
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, legacyBeginMillis)
            }
            params["location"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
                putExtra(CalendarContract.Events.EVENT_LOCATION, it.take(MAX_LOCATION))
            }
            if (attendees.emails.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, attendees.emails.joinToString(","))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** What was prepared, in plain words; the owner still has to tap Save in Calendar. */
    fun preparedMessage(
        title: String,
        outcome: CalendarWhenResult,
    ): String {
        val shown = TaskDisplayText.escape(title.take(MAX_TITLE_ECHO))
        return if (outcome is CalendarWhenResult.Resolved) {
            "Calendar is open with $shown on ${outcome.value.label}. Tap Save to keep it."
        } else {
            "Calendar is open with $shown. Set the date and time there, then tap Save to keep it."
        }
    }

    fun invalidMessage(invalid: CalendarWhenResult.Invalid): String =
        "I could not understand the ${invalid.field} '${TaskDisplayText.escape(invalid.text.take(MAX_DATE_ECHO))}'. " +
            "Nothing was created."

    private fun attendees(raw: String?): Attendees {
        val tokens =
            raw
                .orEmpty()
                .split(attendeeSeparators)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(MAX_ATTENDEES)
        val parsed = tokens.map { token -> (angled.find(token)?.groupValues?.get(1) ?: token) to token }
        return Attendees(
            emails = parsed.filter { isLiteralEmail(it.first) }.map { it.first }.distinct(),
            names = parsed.filterNot { isLiteralEmail(it.first) }.map { it.second.take(MAX_NAME) },
        )
    }
}
