// Origin: EQO Keep planning fixes; pure intent normalization and pre-approval plan hygiene.
package ai.eqo.core.agent

/** Never guesses note content from UI steps. Missing slots stay with understanding/clarification. */
internal object AppPlanHygiene {
    private val keepNames = setOf("keep", "google keep", "keep notes", "google keep notes", "com.google.android.keep")
    private val creation = Regex("^(?:please\\s+)?(?:make|create|write|add|take|save)\\b", RegexOption.IGNORE_CASE)
    private val note = Regex("\\b(?:a\\s+)?(?:new\\s+)?note\\b", RegexOption.IGNORE_CASE)
    private val namedKeep =
        Regex("\\b(?:in|on|using|via)\\s+(?:Google\\s+)?Keep(?:\\s+Notes)?\\b", RegexOption.IGNORE_CASE)
    private val search = Regex("^(?:please\\s+)?(?:search|find|look\\s+for)\\b", RegexOption.IGNORE_CASE)

    fun isKeep(app: String?): Boolean = app?.trim()?.lowercase() in keepNames

    fun normalize(
        request: String,
        intent: TaskIntent,
        apps: List<String>,
    ): TaskIntent {
        // After clarification, rely on the updated slots; never restore an app from the original first line.
        val first =
            request
                .trim()
                .takeUnless { '\n' in it }
                .orEmpty()
        val app = intent.app ?: apps.singleOrNull { isKeep(it) && namedKeep.containsMatchIn(first) }
        val noteRequest = creation.containsMatchIn(first) && note.containsMatchIn(first)
        val noteSlotsOnly = intent.params.keys.all { it in setOf("title", "body") }
        return when {
            isKeep(app) &&
                (
                    intent.action == "create_note" ||
                        (intent.action in setOf("other", "open_app") && noteRequest && noteSlotsOnly)
                ) ->
                intent.copy(app = app, action = "create_note")
            intent.action == "other" &&
                app != null &&
                search.containsMatchIn(first) &&
                searchOnly(intent) -> intent.copy(app = app, action = "search")
            else -> intent
        }
    }

    private fun searchOnly(intent: TaskIntent): Boolean =
        intent.params.keys == setOf("query") &&
            !intent.params["query"].isNullOrBlank()

    fun validate(
        intent: TaskIntent,
        steps: List<LoopStep>,
    ): List<LoopStep> {
        // Clarification is non-executable and must stay on the existing question-before-approval path.
        if (RegistryPlanVocabulary.clarification(steps) != null) return steps
        var inKeep = isKeep(intent.app)
        val unsafe =
            steps.any { step ->
                if (step.action.name == "OPEN_APP") inKeep = isKeep(step.action.params["appName"])
                (inKeep || targetsKeep(step)) && (keepEditorTyping(step) || createNoteTap(step))
            }
        require(!unsafe) {
            "Keep note creation must use CREATE_NOTE with the supplied title/body, never TYPE_TEXT in its editor"
        }
        val keep = isKeep(intent.app) || steps.any(::targetsKeep)
        require(!keep || intent.action != "create_note" || steps.map { it.action.name } == listOf("CREATE_NOTE")) {
            "Keep note creation must use one CREATE_NOTE, never a UI chain"
        }
        return searchTail(intent, steps)
    }

    // Only remove repeated query-field edits after a submission of the actual understood query.
    // Other result navigation and non-search plans remain untouched.
    private fun searchTail(
        intent: TaskIntent,
        steps: List<LoopStep>,
    ): List<LoopStep> {
        val query = intent.params["query"]
        if (intent.action != "search" || intent.app.isNullOrBlank() || query.isNullOrBlank()) return steps
        var target: String? = null
        var submitted = false
        val kept =
            steps.filter { step ->
                val action = step.action
                val field = action.params["searchText"]?.let(::fieldKey)
                if (!submitted && action.name == "TYPE_TEXT") {
                    target = field.takeIf { action.params["content"] == query }
                }
                if (action.name == "PRESS_ENTER" && target != null) submitted = true
                val repeated = submitted && target?.let { queryEdit(action, it) } == true
                !repeated
            }
        // Output references are positional, not stepId-based. Ask for a fresh plan instead of retargeting them.
        val hasReferences =
            steps.any { step ->
                step.action.params.values
                    .any { it.contains("{{step:") }
            }
        require(kept.size == steps.size || !hasReferences) {
            "Remove repeated query-field edits after PRESS_ENTER and rebuild positional output references"
        }
        return if (kept.size == steps.size) {
            steps
        } else {
            kept.mapIndexed { index, step ->
                step.copy(stepId = "task-${index + 1}")
            }
        }
    }

    private fun queryEdit(
        action: ExecutedAction,
        target: String,
    ): Boolean =
        when (action.name) {
            "TYPE_TEXT" -> action.params["searchText"]?.let(::fieldKey) == target
            "CLICK_TEXT" -> action.params["text"]?.let(::fieldKey) == target
            else -> false
        }

    private fun createNoteTap(step: LoopStep): Boolean =
        step.action.name == "CLICK_TEXT" &&
            step.action.params["text"].orEmpty().split(',').any {
                fieldKey(it) in setOf("new_note_button", "speed_dial_create_close_button", "create a note", "new note")
            }

    private fun targetsKeep(step: LoopStep): Boolean =
        (step.action.name == "OPEN_APP" && isKeep(step.action.params["appName"])) ||
            step.action.params["searchText"]
                .orEmpty()
                .contains("com.google.android.keep:id/")

    private fun fieldKey(value: String): String =
        value
            .trim()
            .substringAfterLast(':')
            .substringAfterLast('/')
            .lowercase()

    private fun keepEditorTyping(step: LoopStep): Boolean =
        step.action.name == "TYPE_TEXT" &&
            fieldKey(step.action.params["searchText"].orEmpty()) != "search_actionbar_query_text"
}
