// Origin: EQO round-6 UX fix; fixed plain wording per action for the run summary.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.agent.AttachmentFailure
import ai.eqo.core.agent.PlainArithmetic
import ai.eqo.core.agent.TaskDisplayText

/** The wording tables behind [RunSummary]. Only fixed resources and the owner's own parameters are used. */
internal object RunSentences {
    const val CALCULATE = "CALCULATE"
    private const val DEFAULT_NOTES_APP = "Keep"

    /** Steps that only move through screens: they never make the sentence. */
    private val plumbing =
        setOf(
            "WAIT",
            "PRESS_BACK",
            "PRESS_HOME",
            "PRESS_ENTER",
            "SCROLL",
            "CLICK_TEXT",
            "CLICK_ID",
            "CLICK_COORDINATES",
            "TYPE_TEXT",
            "TYPE_ID",
            "GET_SCREEN_TEXT",
            "OBSERVE",
            "TAP",
            "TAP_TEXT",
            "TYPE",
            "PASTE",
            "BACK",
            "HOME",
        )
    private val opens = setOf("OPEN_APP", "OPEN_BROWSER", "OPEN_URL")
    private val sends = setOf("SEND_SMS", "SEND_EMAIL", "SHARE_LOCATION", "SHARE_CONTACT")
    private val operation = Regex("^([+-]?[0-9.]+)([+*/-])([+-]?[0-9.]+)$")
    private val operators =
        mapOf(
            "+" to R.string.run_say_answer_add,
            "-" to R.string.run_say_answer_subtract,
            "*" to R.string.run_say_answer_multiply,
            "/" to R.string.run_say_answer_divide,
        )
    private val doneFixed =
        mapOf(
            "OPEN_BROWSER" to R.string.run_say_opened_browser,
            "OPEN_URL" to R.string.run_say_opened_page,
            "SEND_SMS" to R.string.run_say_sent_sms,
            "SEND_EMAIL" to R.string.run_say_sent_email,
            "CREATE_CALENDAR_EVENT" to R.string.run_say_calendar_saved,
            "TAKE_SCREENSHOT" to R.string.run_say_screenshot,
            "SHARE_LOCATION" to R.string.run_say_location_sent,
            "SHARE_CONTACT" to R.string.run_say_contact_sent,
        )

    /** The sentences for a run in which every step finished. */
    fun finished(steps: List<SummaryStep>): List<RunSentence> =
        search(steps)?.let { listOf(it) }
            ?: headlineStep(steps)?.let(::done)
            ?: listOf(RunSentence(R.string.run_say_done))

    /** The step the run is about: the last real effect, else the last app opened. Plumbing never counts. */
    fun headlineStep(steps: List<SummaryStep>): SummaryStep? {
        val shown =
            steps.filter {
                (it.outcome == SummaryOutcome.DONE || it.outcome == SummaryOutcome.UNVERIFIED) &&
                    it.action !in plumbing
            }
        return shown.lastOrNull { it.action !in opens } ?: shown.lastOrNull()
    }

    fun blocked(step: SummaryStep): List<RunSentence> =
        when (step.outcome) {
            SummaryOutcome.HANDOFF -> listOf(HandoffSentences.sentence(step))
            SummaryOutcome.FAILED -> listOf(step.reason ?: RunSentence(R.string.run_failed_unknown))
            else -> listOf(RunSentence(R.string.run_say_unknown))
        }

    /** "238 times 8 is 1904": the owner's own numbers and the computed result. */
    fun answer(step: SummaryStep): RunSentence? {
        val expression = step.params["expression"].orEmpty()
        val computed = PlainArithmetic.evaluate(expression)
        val result = computed?.result ?: step.detail.substringAfterLast(" = ", "").trim()
        val parts = computed?.let { operation.matchEntire(it.expression) }?.destructured
        val operator = parts?.component2()?.let(operators::get)
        return when {
            result.isBlank() -> null
            parts != null && operator != null ->
                RunSentence(operator, listOf(parts.component1(), parts.component3(), result))
            else -> {
                val shownExpression = SummaryText.shown(computed?.expression ?: expression)
                RunSentence(R.string.run_say_answer, listOf(shownExpression, result))
            }
        }
    }

    private fun done(step: SummaryStep): List<RunSentence> {
        val chat = HandoffSentences.chatName(step.action)
        val sentence =
            doneFixed[step.action]?.let { RunSentence(it) }
                ?: chat?.let { RunSentence(R.string.run_say_sent_chat, listOf(it)) }
                ?: doneByAction(step)
                ?: RunSentence(R.string.run_say_done)
        val unconfirmed = step.outcome == SummaryOutcome.UNVERIFIED && (step.action in sends || chat != null)
        return listOf(sentence) + if (unconfirmed) listOf(RunSentence(R.string.run_say_unconfirmed)) else emptyList()
    }

    private fun doneByAction(step: SummaryStep): RunSentence? =
        when (step.action) {
            "OPEN_APP" ->
                SummaryText.appName(step.params)?.let { RunSentence(R.string.run_say_opened, listOf(it)) }
                    ?: RunSentence(R.string.run_say_opened_app)
            "CREATE_NOTE" ->
                RunSentence(R.string.run_say_note_saved, listOf(SummaryText.appName(step.params) ?: DEFAULT_NOTES_APP))
            CALCULATE -> answer(step)
            else -> null
        }

    /** "Searched banana in Zepto": an app was opened and then a search field was typed into. */
    private fun search(steps: List<SummaryStep>): RunSentence? {
        val typed =
            steps.lastOrNull {
                it.action in SummaryText.typing && SummaryText.isSearchField(it) && SummaryText.query(it).isNotBlank()
            } ?: return null
        val app =
            steps
                .takeWhile { it !== typed }
                .lastOrNull { it.action == "OPEN_APP" }
                ?.let { SummaryText.appName(it.params) }
        val query = SummaryText.shown(SummaryText.query(typed))
        return if (app != null) {
            RunSentence(R.string.run_say_searched_in, listOf(query, app))
        } else {
            RunSentence(R.string.run_say_searched, listOf(query))
        }
    }
}

/** What to tell the owner when EQO did its part and they finish the last tap or answer a question. */
internal object HandoffSentences {
    private val chats =
        mapOf(
            "SEND_WHATSAPP" to "WhatsApp",
            "SEND_WHATSAPP_GROUP" to "WhatsApp",
            "SEND_TELEGRAM" to "Telegram",
        )
    private val drafts =
        mapOf(
            "SEND_SMS" to R.string.run_say_ready_sms,
            "COMPOSE_SMS" to R.string.run_say_ready_sms,
            "SEND_EMAIL" to R.string.run_say_ready_email,
            "COMPOSE_EMAIL" to R.string.run_say_ready_email,
        )
    private val fixed =
        mapOf(
            "CREATE_CALENDAR_EVENT" to R.string.run_say_calendar_ready,
            "SET_REMINDER" to R.string.run_say_reminder_ready,
            "SET_ALARM" to R.string.run_say_alarm_ready,
            "SET_TIMER" to R.string.run_say_timer_ready,
            "MAKE_CALL" to R.string.run_say_call_ready,
            "MAKE_VIDEO_CALL" to R.string.run_say_video_call_ready,
            "WHATSAPP_CALL" to R.string.run_say_video_call_ready,
            "SEND_WHATSAPP_GROUP" to R.string.run_say_group_open,
            "SUMMARIZE_URL" to R.string.run_say_opened_page,
        )
    private val searchParam =
        mapOf(
            "WEB_SEARCH" to "query",
            "DEFINE_WORD" to "word",
            "FACT_CHECK" to "claim",
            "CHECK_STOCK" to "symbol",
            "TRANSLATE" to "text",
            RunSentences.CALCULATE to "expression",
        )

    fun chatName(action: String): String? = chats[action]

    fun isFileNeedsYou(detail: String): Boolean =
        AttachmentFailure.kind(detail) in setOf("attachment_cancelled", "attachment_selection_required")

    /** A "Needs you" that is a reason (a missing control, a cancelled file choice), not a finished hand-off. */
    fun isReasonNeedsYou(detail: String): Boolean = detail == "control_not_found" || isFileNeedsYou(detail)

    fun sentence(step: SummaryStep): RunSentence =
        if (isQuestion(step.action, step.detail)) {
            RunSentence(R.string.run_say_question, listOf(SummaryText.shown(step.detail)))
        } else {
            known(step) ?: RunSentence(R.string.run_say_handoff)
        }

    /** The plain "what to tap" for a step row, or null when the executor text is the owner's question. */
    fun instruction(
        action: String,
        detail: String,
    ): RunSentence? =
        if (isQuestion(action, detail)) {
            null
        } else {
            known(SummaryStep(action.uppercase(), emptyMap(), SummaryOutcome.HANDOFF, detail))
        }

    private fun isQuestion(
        action: String,
        detail: String,
    ): Boolean = action.uppercase() == "ASK_USER" || detail.trim().endsWith("?")

    private fun known(step: SummaryStep): RunSentence? {
        val draft = step.detail.contains("draft", ignoreCase = true)
        val chat = chats[step.action]
        return when {
            step.action in drafts -> drafts[step.action]?.takeIf { draft }?.let { RunSentence(it) }
            chat != null && step.action != "SEND_WHATSAPP_GROUP" ->
                RunSentence(R.string.run_say_ready_chat, listOf(chat)).takeIf { draft }
            step.action in fixed -> fixed[step.action]?.let { RunSentence(it) }
            step.action.startsWith("LIST_CALENDAR") -> RunSentence(R.string.run_say_opened, listOf("Calendar"))
            else -> searched(step)
        }
    }

    private fun searched(step: SummaryStep): RunSentence? =
        searchParam[step.action]
            ?.let { step.params[it].orEmpty().trim() }
            ?.takeIf { it.isNotBlank() }
            ?.let { RunSentence(R.string.run_say_search_opened, listOf(SummaryText.shown(it))) }
}

/** Owner-supplied text for a sentence: bounded, trimmed, hidden characters made visible. */
internal object SummaryText {
    private const val MAX_SHOWN = 80
    val typing = setOf("TYPE_TEXT", "TYPE_ID", "TYPE", "PASTE")
    private val brands = mapOf("whatsapp" to "WhatsApp", "youtube" to "YouTube", "gmail" to "Gmail")

    fun shown(value: String): String = TaskDisplayText.escape(value.trim().take(MAX_SHOWN))

    fun isSearchField(step: SummaryStep): Boolean =
        (step.params["searchText"] ?: step.params["target"]).orEmpty().contains("search", ignoreCase = true)

    fun query(step: SummaryStep): String = (step.params["content"] ?: step.params["text"]).orEmpty().trim()

    fun appName(params: Map<String, String>): String? {
        val raw = (params["appName"] ?: params["app_name"] ?: params["app"]).orEmpty().trim()
        return raw.takeIf { it.isNotBlank() }?.let(::friendly)
    }

    /** Names typed all in lower case read better capitalised; a name the planner already cased is kept. */
    private fun friendly(name: String): String {
        val cased = if (name == name.lowercase()) name.split(' ').joinToString(" ", transform = ::capitalise) else name
        return shown(cased)
    }

    private fun capitalise(word: String): String = brands[word] ?: word.replaceFirstChar { it.uppercase() }
}
