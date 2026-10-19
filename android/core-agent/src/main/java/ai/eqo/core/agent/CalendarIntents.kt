// Origin: EQO calendar intent routing; ephemeral slots, no execution or storage.
package ai.eqo.core.agent

/** Date-only reminders are Calendar drafts, not incomplete timed alarms or notes. */
internal object CalendarIntents {
    private const val TIME_GROUP = 3
    private val reminder =
        Regex(
            "^(?:please\\s+)?(?:(?:create|set|add|make)\\s+(?:a\\s+)?reminder\\b|remind\\s+me\\b)",
            RegexOption.IGNORE_CASE,
        )
    private val simple =
        Regex(
            "^(?:please\\s+)?(?:create|set|add|make)\\s+(?:a\\s+)?reminder\\s+for\\s+(?:the\\s+)?" +
                "(.+?)\\s+on\\s+(.+?)(?:\\s+at\\s+(.+))?$",
            RegexOption.IGNORE_CASE,
        )
    private val date =
        Regex(
            "(?:[A-Za-z]+\\s+\\d{1,2}(?:st|nd|rd|th)?|\\d{1,2}(?:st|nd|rd|th)?\\s+[A-Za-z]+)" +
                "(?:,?\\s+\\d{4})?|\\d{4}-\\d{2}-\\d{2}|today|tomorrow",
            RegexOption.IGNORE_CASE,
        )
    private val compound = Regex("\\b(?:and|then)\\b", RegexOption.IGNORE_CASE)
    private val simpleTime =
        Regex("(?:\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?|noon|midnight|all[ -]day)", RegexOption.IGNORE_CASE)
    private val allDay = Regex("all[ -]day[.!]?", RegexOption.IGNORE_CASE)
    private val timeQuestion = Regex("\\b(?:what|which)\\s+time\\b", RegexOption.IGNORE_CASE)
    private val suppliedTime =
        Regex(
            "\\bat\\s+\\d|\\b(?:noon|midnight|morning|afternoon|evening|night)\\b|\\d\\s*(?:am|pm)|\\d:",
            RegexOption.IGNORE_CASE,
        )

    // Only a single, fully delimited reminder is local. Rich/compound wording stays with understanding.
    fun from(request: String): TaskIntent? {
        val match =
            simple
                .matchEntire(request.trim())
                ?.takeUnless { compound.containsMatchIn(request) }
                ?.takeUnless { suppliedTime.containsMatchIn(it.groupValues[1]) }
        val time = match?.groupValues?.get(TIME_GROUP).orEmpty()
        val day =
            match?.groupValues?.get(2)?.takeIf {
                date.matches(it) && (time.isBlank() || simpleTime.matches(time))
            }
        return if (match == null || day == null) {
            null
        } else {
            TaskIntent(
                "Reminder for ${match.groupValues[1]}",
                action = "create_calendar_event",
                params =
                    mapOf("title" to match.groupValues[1], "date" to day) +
                        time
                            .takeIf(String::isNotBlank)
                            ?.let { mapOf("time" to it) }
                            .orEmpty(),
                confidence = 1.0,
            )
        }
    }

    fun normalize(
        request: String,
        intent: TaskIntent,
    ): TaskIntent {
        val calendar = intent.action == "create_calendar_event"
        val legacy = intent.action == "other" && isReminder(request)
        if (!calendar && !legacy) return intent
        val params = canonicalSlots(intent.params)
        val complete = !params["title"].isNullOrBlank() && !params["date"].isNullOrBlank()
        val dismissTime =
            complete && optionalTime(request, params) && timeQuestion.containsMatchIn(intent.question.orEmpty())
        val recognized = complete || calendar
        return intent.copy(
            action = if (recognized) "create_calendar_event" else intent.action,
            app = if (recognized) null else intent.app,
            params = if (recognized) params else intent.params,
            confidence = if (dismissTime) 1.0 else intent.confidence,
            question = if (dismissTime) null else intent.question,
            options = if (dismissTime) emptyList() else intent.options,
        )
    }

    private fun optionalTime(
        request: String,
        params: Map<String, String>,
    ): Boolean =
        allDay.matches(params["time"].orEmpty()) ||
            (params["time"].isNullOrBlank() && !suppliedTime.containsMatchIn(request))

    fun plan(intent: TaskIntent): List<ExecutedAction> {
        // An explicit all-day marker avoids the legacy today/now direct-storage shortcut.
        val params =
            if (intent.params["date"].orEmpty().lowercase() in setOf("today", "now") &&
                intent.params["time"].isNullOrBlank()
            ) {
                intent.params + ("time" to "all day")
            } else {
                intent.params
            }
        return listOf(ExecutedAction("CREATE_CALENDAR_EVENT", params, irreversible = true))
    }

    private fun canonicalSlots(params: Map<String, String>): Map<String, String> {
        val canonical = params - "text" - "datetime" + aliases(params)
        // A second date/time source or unsupported slots must reach free planning, not silently disappear.
        // The template schema rejects datetime so the model can separate its time from the supplied date.
        val mixed = !params["date"].isNullOrBlank() && !params["datetime"].isNullOrBlank()
        val explicitAllDay = allDay.matches(params["datetime"].orEmpty()) || allDay.matches(params["time"].orEmpty())
        return if (mixed && !explicitAllDay) {
            canonical + ("datetime" to params.getValue("datetime"))
        } else {
            canonical
        }
    }

    private fun aliases(params: Map<String, String>): Map<String, String> =
        buildMap {
            if (params["title"].isNullOrBlank()) params["text"]?.let { put("title", it) }
            if (params["date"].isNullOrBlank()) {
                params["datetime"]?.takeUnless { allDay.matches(it) }?.let { put("date", it) }
            }
        }

    fun merge(
        request: String,
        previous: TaskIntent,
        updated: TaskIntent,
        question: TaskIntent,
        answer: String,
    ): TaskIntent {
        val calendar = previous.action == "create_calendar_event" || isReminder(request)
        val sameTask = updated.action in setOf("other", "create_calendar_event")
        val isTime = timeQuestion.containsMatchIn(question.clarificationQuestion())
        val allDayAnswer = allDay.matches(answer.trim()) && isTime
        return if (calendar && allDayAnswer && sameTask) {
            val slots = normalize(request, previous).params + updated.params.filterValues(String::isNotBlank)
            val task = updated.copy(action = "create_calendar_event", params = slots + ("time" to "all day"))
            val merged = normalize(request, task)
            if (merged.action == "create_calendar_event" && merged.missingParameter() == null) {
                merged.copy(confidence = 1.0, question = null, options = emptyList())
            } else {
                merged
            }
        } else {
            updated
        }
    }

    private fun isReminder(request: String): Boolean =
        reminder.containsMatchIn(request) && !compound.containsMatchIn(request.lineSequence().first())
}
