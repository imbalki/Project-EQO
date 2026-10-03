/*
 * EQO (TASK-012, issue #17): value types of the action loop (states, outcomes,
 * decisions). The loop itself is ActionLoop.kt.
 */
package ai.eqo.core.agent

import ai.eqo.data.models.PlanStatus

/** One planned step the loop will execute. */
data class LoopStep(
    val stepId: String,
    val action: ExecutedAction,
    /** Manifest permission this step needs (checked before approval/execute). */
    val requiredPermission: String? = null,
)

/** Result of dispatching one executed action. */
sealed class ExecuteResult {
    data class Success(
        val detail: String,
    ) : ExecuteResult()

    data class Failure(
        val reason: String,
        /** Retryable only for reversible actions (never for irreversible ones). */
        val transient: Boolean = false,
    ) : ExecuteResult()

    /** The apply was cancelled or timed out mid-flight: effect unknown. */
    data class Interrupted(
        val note: String,
    ) : ExecuteResult()
}

/** Loop state (spec: running, paused (takeover), stopped, cancelled). */
enum class LoopState {
    RUNNING,
    PAUSED,
    STOPPED,
    CANCELLED,
}

/** Why the loop is paused. */
enum class PauseReason {
    USER_PAUSE,
    USER_TAKEOVER,

    /**
     * A takeover latched within the self-gesture attribution window of a stroke
     * EQO dispatched itself (security note N-3): most likely our own touch seen
     * late. Never kills the task silently — the loop pauses visibly and the
     * user decides (resume with explicit confirmation, or stop).
     */
    SELF_GESTURE_TAKEOVER_SUSPECTED,
}

/**
 * Terminal outcome of a plan run. STOPPED = the user stopped the task;
 * CANCELLED = the user cancelled it. Both are terminal and map to
 * [PlanStatus.CANCELLED]; COMPLETED/FAILED map to themselves.
 */
enum class PlanTerminal {
    COMPLETED,
    FAILED,
    STOPPED,
    CANCELLED,
    ;

    fun toPlanStatus(): PlanStatus =
        when (this) {
            COMPLETED -> PlanStatus.COMPLETED
            FAILED -> PlanStatus.FAILED
            STOPPED -> PlanStatus.CANCELLED
            CANCELLED -> PlanStatus.CANCELLED
        }
}

/** Permission-check decision. */
sealed class PermissionDecision {
    data object Granted : PermissionDecision()

    data class Denied(
        val reason: String,
    ) : PermissionDecision()
}

/** User-approval decision for a sensitive/irreversible action. */
sealed class ApprovalDecision {
    data object Approved : ApprovalDecision()

    data class Rejected(
        val reason: String,
    ) : ApprovalDecision()
}

/** What happened to one step — always typed, never "probably fine". */
sealed class StepOutcome {
    data class Completed(
        val detail: String,
    ) : StepOutcome()

    data class PartialApply(
        val detail: StepVerifier.PartialApply,
    ) : StepOutcome()

    data class Failed(
        val reason: String,
    ) : StepOutcome()

    /** This step did NOT execute (stopped/paused earlier, or gate rejected). */
    data class NotExecuted(
        val reason: String,
    ) : StepOutcome()
}

/** One step's record in the final report. */
data class StepRecord(
    val stepId: String,
    val outcome: StepOutcome,
    val attempts: Int,
)

/**
 * Final report: exactly what did and did not happen. `planStatusEvents` is the
 * full list of plan-status transitions the run emitted — the exactly-once
 * terminal criterion is checked against it.
 */
data class LoopReport(
    val loopState: LoopState,
    val terminal: PlanTerminal?,
    val pauseReason: PauseReason?,
    val steps: List<StepRecord>,
    val planStatusEvents: List<PlanStatus>,
)
