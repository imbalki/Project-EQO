/*
 * EQO (TASK-015, issue #20): the task-screen presentation model.
 *
 * PRD REQ-TASK-01..07 / UF-09 / UF-10: per-step progress with elapsed time, a
 * contextual approval card with a visible countdown, Pause != Stop, in-flight
 * uncertainty displayed as "unknown", and receipts for what did and did not happen.
 * This file is pure presentation state; the loop itself is core-agent's ActionLoop.
 */
package ai.eqo.study

/** Live progress of one task step (REQ-TASK-01). */
enum class StepProgressState {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    NEEDS_YOU,

    /**
     * The apply was interrupted mid-flight (timeout or user cancel): the effect is
     * unknown and must be shown as such, never as success or failure (REQ-TASK-04).
     */
    UNKNOWN,
}

data class StepProgress(
    val stepId: String,
    val name: String,
    val state: StepProgressState,
    val elapsedMs: Long = 0L,
    /** Receipt / reason text: what happened, or why it did not. */
    val detail: String = "",
    /** Planned app/button/field label, UI only; never logged. */
    val targetLabel: String = "",
)

/**
 * A contextual approval card (REQ-TASK-02, UF-10 §11.1): the action, its target and the
 * app it happens in, with a visible countdown that times out to a rejection.
 */
data class ApprovalRequest(
    val stepId: String,
    val action: String,
    val target: String,
    val app: String,
    val requestedAtMs: Long,
    /** Study proposal from the PRD (ClosePaw uses 60s): surfaced to the user as countdown. */
    val timeoutMs: Long = APPROVAL_TIMEOUT_MS,
) {
    fun remainingMs(nowMs: Long): Long = (timeoutMs - (nowMs - requestedAtMs)).coerceAtLeast(0L)

    fun isExpired(nowMs: Long): Boolean = remainingMs(nowMs) == 0L

    companion object {
        const val APPROVAL_TIMEOUT_MS: Long = 60_000L
    }
}

/** How one approval card ended. */
sealed class ApprovalOutcome {
    data object Approved : ApprovalOutcome()

    data class Rejected(
        val reason: String,
    ) : ApprovalOutcome()

    /**
     * The countdown ran out. REQ-TASK-02: `AwaitingApproval` times out to `Cancelled` —
     * the step is cancelled and the task never executes on a stale approval.
     */
    data object TimedOut : ApprovalOutcome()
}

/** The end-of-run receipt view (REQ-TASK-06): what happened, what did not, what is unknown. */
data class RunReceipt(
    val steps: List<StepProgress>,
    val terminal: String,
) {
    val executedStepIds: List<String> get() = steps.filter { it.state == StepProgressState.DONE }.map { it.stepId }

    val notExecutedStepIds: List<String>
        get() =
            steps
                .filter { it.state in setOf(StepProgressState.PENDING, StepProgressState.UNKNOWN) }
                .map { it.stepId }

    val unknownResultStepIds: List<String>
        get() = steps.filter { it.state == StepProgressState.UNKNOWN }.map { it.stepId }

    val needsYouStepIds: List<String>
        get() = steps.filter { it.state == StepProgressState.NEEDS_YOU }.map { it.stepId }
}
