// Origin: EQO note clarification; ephemeral slot retention, no storage or execution.
package ai.eqo.core.agent

/** The question, not the second model's classification, determines which note slot an answer fills. */
internal class NoteClarifications(
    private val apps: List<String>,
) {
    private val noteRequest =
        Regex("^(?:please\\s+)?(?:make|create|write|add|take|save)\\b.*\\bnote\\b", RegexOption.IGNORE_CASE)
    private val appQuestion =
        Regex(
            "\\b(?:which|what)\\s+(?:notes?\\s+)?(?:app|application)\\b|" +
                "\\b(?:app|application)\\b.*\\b(?:use|choose|prefer|select)\\b",
            RegexOption.IGNORE_CASE,
        )
    private val contentQuestion = Regex("\\b(?:say|saying|contain|content|body|text|write)\\b", RegexOption.IGNORE_CASE)
    private val correction = Regex("^(?:actually\\b|instead\\b|cancel\\b|forget\\b)", RegexOption.IGNORE_CASE)
    private val appChoice =
        Regex("^(?:(?:actually,?\\s+)?use\\s+|in\\s+)(.+?)(?:\\s+instead)?[.!]?$", RegexOption.IGNORE_CASE)

    fun defaultApp(intent: TaskIntent): TaskIntent {
        val keep = apps.singleOrNull(AppPlanHygiene::isKeep)
        val defaulted =
            if (intent.action == "create_note" && intent.app == null && keep != null) {
                intent.copy(app = keep)
            } else {
                intent
            }
        return if (defaulted != intent && appQuestion.containsMatchIn(intent.question.orEmpty())) {
            finish(defaulted)
        } else {
            defaulted
        }
    }

    @Suppress("ReturnCount") // Non-note tasks and explicit task corrections must not inherit note slots.
    fun merge(
        request: String,
        previous: TaskIntent,
        updated: TaskIntent,
        question: TaskIntent,
        answer: String,
    ): TaskIntent {
        val text = question.clarificationQuestion()
        val isApp = appQuestion.containsMatchIn(text)
        val isContent = !isApp && contentQuestion.containsMatchIn(text)
        val context = noteContext(request, previous, text)
        if (!context || (!isApp && !isContent)) return updated
        val chosen = chosenApp(answer, isApp)
        if (chosen == null && correction.containsMatchIn(answer.trim())) return updated
        if (updated.action !in setOf("create_note", "other", "open_app")) return updated
        // App selection cannot edit content: the model may misassign the app answer to title/body.
        // Explicit user corrections above still use the corrected intent without inheriting old slots.
        val slots = retainedSlots(previous, updated, isApp, chosen)
        val params = if (isContent && chosen == null) slots + ("body" to answer) else slots
        val app = chosen ?: if (isApp) null else previous.app ?: updated.app
        // An unrecognized app answer must not silently select the default or revive the old app.
        val merged = updated.copy(action = "create_note", app = app, params = params, channel = null)
        val result = if (isApp) merged else defaultApp(merged)
        return finish(result)
    }

    private fun noteContext(
        request: String,
        previous: TaskIntent,
        question: String,
    ): Boolean =
        previous.action == "create_note" ||
            (
                previous.action == "other" &&
                    previous.channel == null &&
                    previous.params.keys.all { it in setOf("title", "body") } &&
                    question.contains("note", ignoreCase = true) &&
                    noteRequest.containsMatchIn(request.lineSequence().first())
            )

    private fun noteSlots(intent: TaskIntent): Map<String, String> =
        intent.params.filter { (key, value) -> key in setOf("title", "body") && value.isNotBlank() }

    private fun retainedSlots(
        prior: TaskIntent,
        next: TaskIntent,
        isApp: Boolean,
        chosen: String?,
    ): Map<String, String> = if (isApp || chosen != null) noteSlots(prior) else noteSlots(prior) + noteSlots(next)

    private fun finish(intent: TaskIntent): TaskIntent =
        intent.copy(
            confidence = 1.0,
            question =
                when (intent.missingParameter()) {
                    "app" -> "Which notes app should I use?"
                    "title or body" -> "What would you like the note to say?"
                    else -> null
                },
            options = emptyList(),
        )

    private fun chosenApp(
        answer: String,
        isApp: Boolean,
    ): String? {
        val named = appChoice.matchEntire(answer.trim())?.groupValues?.get(1)
        val label = named ?: answer.trim().takeIf { isApp }
        return label?.let { InstalledAppResolver.resolve(it, apps) }
    }
}
