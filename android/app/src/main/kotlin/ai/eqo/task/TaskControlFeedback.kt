package ai.eqo.task

import ai.eqo.core.agent.PauseReason
import ai.eqo.data.models.PlanStatus

/** Persistent feedback, separate from step progress/countdown so neither hides a control. */
enum class TaskControlFeedback {
    PAUSE_REQUESTED,
    PAUSED,
    STOP_REQUESTED,
    STOPPED,
    TAKEOVER,
    RESUMED,
    NOTHING_RUNNING,
    NOT_PAUSED,
    ALREADY_PAUSED,
    NO_SMS_APP,
}

/** Includes the accessibility touch path, not just the Take over button. */
fun settledControlFeedback(
    status: PlanStatus,
    reason: PauseReason?,
): TaskControlFeedback? =
    when (status) {
        PlanStatus.PAUSED ->
            if (reason == PauseReason.USER_PAUSE) TaskControlFeedback.PAUSED else TaskControlFeedback.TAKEOVER
        PlanStatus.CANCELLED -> TaskControlFeedback.STOPPED
        else -> null
    }
