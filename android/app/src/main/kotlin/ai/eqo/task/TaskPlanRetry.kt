// Origin: EQO t_9fd2d126, process-only completed-step memory for explicitly reapproved retries.
package ai.eqo.task

import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.study.StepProgress
import ai.eqo.study.StepProgressState

/** Never persisted. Unknown effects cannot be retried; a new plan requires a separate user request. */
internal class TaskPlanRetry {
    private var original: ApprovedTaskPlan? = null
    private val completed = mutableSetOf<String>()
    private var needsYou = false
    private var uncertain = false

    fun started(plan: ApprovedTaskPlan) {
        val remaining = remaining()
        if (remaining == null || !remaining.matches(plan.steps())) {
            original = plan
            completed.clear()
        }
        needsYou = false
        uncertain = false
    }

    fun record(progress: StepProgress) {
        if (original?.steps()?.none { it.stepId == progress.stepId } != false) return
        when (progress.state) {
            StepProgressState.DONE -> completed += progress.stepId
            StepProgressState.NEEDS_YOU -> needsYou = progress.detail.contains("This step did not run.")
            StepProgressState.UNKNOWN -> uncertain = true
            else -> Unit
        }
    }

    fun remaining(): ApprovedTaskPlan? {
        if (!needsYou || uncertain) return null
        val steps = original?.steps()?.filterNot { it.stepId in completed }.orEmpty()
        return steps.takeIf { it.isNotEmpty() }?.let(::ApprovedTaskPlan)
    }
}
