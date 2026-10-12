// Origin: EQO round-6 UX fix; one plain sentence per run, built from approved actions and outcomes.
package ai.eqo.task

import ai.eqo.core.agent.LoopStep
import ai.eqo.study.StepProgress
import ai.eqo.study.StepProgressState
import android.content.Context

/** Fixed wording is a string resource plus plain arguments, so the mapper stays pure and host-testable. */
internal data class RunSentence(
    val resource: Int,
    val args: List<String> = emptyList(),
) {
    /** At most three arguments are ever used, and [Context.getString] has no list overload. */
    fun text(context: Context): String =
        when (args.size) {
            0 -> context.getString(resource)
            1 -> context.getString(resource, args[0])
            2 -> context.getString(resource, args[0], args[1])
            else -> context.getString(resource, args[0], args[1], args[2])
        }
}

/** What happened to one approved step, in the words a person would use. */
internal enum class SummaryOutcome {
    DONE,

    /** EQO pressed Send (or similar) but nothing confirmed the effect. */
    UNVERIFIED,

    /** EQO did its part and the owner finishes the last tap. */
    HANDOFF,
    FAILED,
    UNKNOWN,
    NOT_RUN,
}

/** One approved step and its outcome. [detail] only classifies; it is never shown or spoken as written. */
internal data class SummaryStep(
    val action: String,
    val params: Map<String, String>,
    val outcome: SummaryOutcome,
    val detail: String = "",
    val reason: RunSentence? = null,
)

/**
 * The run-level summary: action names, parameters and outcomes in, a short plain sentence out.
 * Internal steps (waits, taps, typing plumbing) and executor text never reach the sentence.
 */
internal object RunSummary {
    private val unverified = Regex("not verified|unverified|not been verified", RegexOption.IGNORE_CASE)
    private val userEnded = setOf("STOPPED", "CANCELLED", "PAUSED", "NONE")
    private val blocking = setOf(SummaryOutcome.HANDOFF, SummaryOutcome.FAILED, SummaryOutcome.UNKNOWN)

    fun steps(
        plan: List<LoopStep>,
        progress: List<StepProgress>,
    ): List<SummaryStep> =
        progress.map { item ->
            val step = plan.firstOrNull { it.stepId == item.stepId }
            val outcome = outcome(item)
            SummaryStep(
                action = (step?.action?.name ?: item.name).uppercase(),
                params = step?.action?.params.orEmpty(),
                outcome = outcome,
                detail = item.detail,
                reason =
                    RunStatusMapping
                        .detail(item)
                        ?.takeIf { outcome == SummaryOutcome.FAILED }
                        ?.let { RunSentence(it.resource, listOf(it.argument)) },
            )
        }

    private fun outcome(item: StepProgress): SummaryOutcome =
        when (item.state) {
            StepProgressState.DONE ->
                if (unverified.containsMatchIn(item.detail)) SummaryOutcome.UNVERIFIED else SummaryOutcome.DONE
            StepProgressState.NEEDS_YOU ->
                if (HandoffSentences.isReasonNeedsYou(item.detail)) SummaryOutcome.FAILED else SummaryOutcome.HANDOFF
            StepProgressState.FAILED -> SummaryOutcome.FAILED
            StepProgressState.UNKNOWN -> SummaryOutcome.UNKNOWN
            StepProgressState.PENDING, StepProgressState.RUNNING -> SummaryOutcome.NOT_RUN
        }

    /** Empty when the owner ended the run or it never finished: nothing is announced then. */
    fun sentences(
        steps: List<SummaryStep>,
        terminal: String,
    ): List<RunSentence> {
        val stopper = steps.firstOrNull { it.outcome in blocking }
        val unfinished = steps.isEmpty() || (stopper == null && steps.any { it.outcome == SummaryOutcome.NOT_RUN })
        return if (terminal in userEnded || unfinished) {
            emptyList()
        } else {
            val tail = if (stopper != null) RunSentences.blocked(stopper) else RunSentences.finished(steps)
            val lead = steps.firstOrNull { it.action == RunSentences.CALCULATE && it.outcome == SummaryOutcome.DONE }
            val headline = stopper ?: RunSentences.headlineStep(steps)
            val answer =
                lead
                    ?.takeIf { headline?.action != RunSentences.CALCULATE }
                    ?.let(RunSentences::answer)
            listOfNotNull(answer) + tail
        }
    }
}
