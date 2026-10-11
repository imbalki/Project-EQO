// Origin: EQO intent-understanding v1; deterministic templates use only understood slots.
package ai.eqo.core.agent

/** Templates are still schema validated, previewed and approved; none executes during understanding. */
object IntentSkills {
    val names = listOf("keep_note", "calculate", "message", "call", "open_app", "search")

    fun plan(
        intent: TaskIntent,
        enabled: Set<String>,
    ): List<LoopStep>? {
        val actions = if (intent.missingParameter() == null) template(intent) else emptyList()
        val steps = actions.mapIndexed { index, action -> LoopStep("task-${index + 1}", action) }
        return steps.takeIf { it.isNotEmpty() && RegistryPlanVocabulary.errors(it, enabled).isEmpty() }
    }

    private fun action(
        name: String,
        vararg params: Pair<String, String>,
    ) = ExecutedAction(name, params.toMap(), irreversible = true)

    private fun open(app: String) = listOf(action("OPEN_APP", "appName" to app), action("WAIT", "durationMs" to "3000"))

    private fun template(intent: TaskIntent): List<ExecutedAction> =
        when (intent.action) {
            "open_app" -> listOf(action("OPEN_APP", "appName" to intent.app.orEmpty()))
            "create_note" -> keep(intent)
            "calculate" -> listOf(action("CALCULATE", "expression" to intent.params.getValue("expression")))
            "send_message" -> message(intent)
            "call" -> call(intent)
            "search" -> search(intent)
            else -> emptyList()
        }

    private fun keep(intent: TaskIntent): List<ExecutedAction> =
        if (intent.app?.lowercase() in setOf("keep", "google keep", "keep notes", "google keep notes")) {
            buildList {
                addAll(open(requireNotNull(intent.app)))
                add(action("CLICK_TEXT", "text" to "Create a note"))
                add(action("CLICK_TEXT", "text" to "id:new_note_button"))
                for ((slot, target) in mapOf("title" to "id:editable_title", "body" to "id:edit_note_text")) {
                    intent.params[slot]?.takeIf { it.isNotBlank() }?.let {
                        add(action("TYPE_TEXT", "searchText" to target, "content" to it))
                    }
                }
                add(action("PRESS_BACK"))
            }
        } else {
            emptyList()
        }

    private fun message(intent: TaskIntent): List<ExecutedAction> {
        val p = intent.params
        val contact = p["contact"].orEmpty()
        val text = p["message"].orEmpty()
        val name =
            when (intent.channel) {
                null, "sms" -> "SEND_SMS"
                "whatsapp" -> "SEND_WHATSAPP"
                "email" -> "SEND_EMAIL"
                else -> "SEND_TELEGRAM"
            }
        val params =
            if (name == "SEND_EMAIL") {
                mapOf("to" to contact, "subject" to p["subject"].orEmpty(), "body" to text)
            } else {
                mapOf("contact" to contact, "message" to text)
            } + p.filterKeys { it == "attachment" }
        val draft = if (name == "SEND_WHATSAPP") mapOf("draftOnly" to (intent.send != "yes").toString()) else emptyMap()
        // Telegram has no draft executor. Unknown/no send falls back to free planning of a local draft.
        return if (name == "SEND_TELEGRAM" && intent.send != "yes") {
            emptyList()
        } else {
            listOf(ExecutedAction(name, params + draft, irreversible = true))
        }
    }

    private fun call(intent: TaskIntent): List<ExecutedAction> {
        val name = if (intent.channel == "whatsapp") "WHATSAPP_CALL" else "MAKE_CALL"
        val params =
            mapOf("contact" to intent.params.getValue("contact")) +
                intent.params.filterKeys { it == "video" && name == "WHATSAPP_CALL" }
        return listOf(ExecutedAction(name, params, irreversible = true))
    }

    private fun search(intent: TaskIntent): List<ExecutedAction> =
        if (intent.app == null) {
            listOf(action("WEB_SEARCH", "query" to intent.params.getValue("query")))
        } else if (intent.app.equals("whatsapp", ignoreCase = true)) {
            // Fail closed on restored chats/version differences. Search is live-filtered: never press Enter.
            // Explicit IDs cannot fall back to the sole message composer when search is absent.
            open(intent.app) +
                listOf(
                    action("CLICK_TEXT", "text" to "id:com.whatsapp:id/menuitem_search"),
                    action(
                        "TYPE_TEXT",
                        "searchText" to "id:com.whatsapp:id/search_src_text",
                        "content" to intent.params.getValue("query"),
                    ),
                )
        } else {
            // Apps without a pinned search flow use hint-driven planning, not generic editable-field guesses.
            emptyList()
        }
}

/** Only intent slots decide channel/send. Target text is checked for submit controls, not request keywords. */
object IntentPlanProtection {
    fun validate(
        intent: TaskIntent,
        steps: List<LoopStep>,
    ): List<LoopStep> {
        if (intent.send == "no") require(steps.none(::submits)) { "Intent send=no cannot include Send or submit steps" }
        if (intent.action == "send_message") {
            val channel = intent.channel ?: "sms"
            val names = steps.map { it.action.name }
            val forbidden = CHANNEL_ACTIONS.filterKeys { it != channel }.values.flatten()
            require(names.none { it in forbidden }) { "Plan must use the understood message channel" }
        }
        return if (intent.action == "create_note") noteSteps(steps) else steps
    }

    /**
     * A note plan may use any ordinary step the AI chooses. Pure Save/Done taps are dropped (notes apps save by
     * themselves, so tapping them only fails); anything that communicates or shares outward is refused.
     */
    private fun noteSteps(steps: List<LoopStep>): List<LoopStep> {
        val kept = steps.filterNot(::saveNoise)
        require(kept.isNotEmpty() && kept.none(::outward)) { "Note editing cannot include communication steps" }
        return kept.mapIndexed { index, step -> step.copy(stepId = "task-${index + 1}") }
    }

    private fun tapText(step: LoopStep) = step.action.params["text"].orEmpty().trim().lowercase()

    private fun isTap(step: LoopStep) = step.action.name.uppercase() in setOf("CLICK_TEXT", "TAP_TEXT")

    private fun saveNoise(step: LoopStep) = isTap(step) && tapText(step) in SAVE_NOISE_TAPS

    private fun outward(step: LoopStep): Boolean =
        step.action.name.uppercase() in OUTWARD_ACTIONS ||
            (isTap(step) && OUTWARD_WORDS.containsMatchIn(tapText(step)))

    private val SAVE_NOISE_TAPS = setOf("save", "done", "ok", "okay", "finish", "confirm", "close")
    private val OUTWARD_ACTIONS =
        setOf(
            "SEND_WHATSAPP",
            "SEND_WHATSAPP_GROUP",
            "SEND_SMS",
            "SEND_EMAIL",
            "SEND_TELEGRAM",
            "COMPOSE_SMS",
            "COMPOSE_EMAIL",
            "MAKE_CALL",
            "MAKE_VIDEO_CALL",
            "WHATSAPP_CALL",
            "SHARE_CONTACT",
            "SHARE_LOCATION",
        )
    private val OUTWARD_WORDS =
        Regex(
            "send|share|post|publish|submit|reply|forward|message|whatsapp|sms|email|mail|telegram|call|pay|buy",
            RegexOption.IGNORE_CASE,
        )

    private fun submits(step: LoopStep): Boolean =
        when (step.action.name) {
            "SEND_WHATSAPP" -> step.action.params["draftOnly"] != "true"
            "SHARE_CONTACT", "SHARE_LOCATION" -> step.action.params["via"] == "whatsapp"
            "SEND_SMS", "SEND_EMAIL", "compose_sms", "compose_email" -> false // Native draft-only executors.
            "PRESS_ENTER", "press_enter", "send_whatsapp", "send_telegram", "SEND_TELEGRAM" -> true
            "CLICK_TEXT", "CLICK_ID", "tap_text", "tap_id" ->
                step.action.params.values
                    .any { SUBMIT_CONTROL.containsMatchIn(it) }
            else -> step.action.name.startsWith("SEND_")
        }

    private val SUBMIT_CONTROL = Regex("send|submit|share|publish|post|forward|reply", RegexOption.IGNORE_CASE)
    private val CHANNEL_ACTIONS =
        mapOf(
            "sms" to listOf("SEND_SMS", "compose_sms"),
            "whatsapp" to listOf("SEND_WHATSAPP", "send_whatsapp"),
            "email" to listOf("SEND_EMAIL", "compose_email"),
            "telegram" to listOf("SEND_TELEGRAM", "send_telegram"),
        )
}
