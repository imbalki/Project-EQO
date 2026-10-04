/*
 * EQO (TASK-015, issue #20): the study run controller — the production constructor of
 * core-agent's ActionLoop (UF-09 / UF-10).
 *
 * The loop is built with the REAL gates from `StudyLoopWiring` (no fail-open test
 * defaults), and the control surface is the task screen's buttons: Pause, Stop,
 * takeover, and a resume that only ever accepts a [UserResumeConfirmation] minted by an
 * actual user gesture in UI code (TASK-012 SF-4).
 */
package ai.eqo.task

import ai.eqo.core.agent.ActionLoop
import ai.eqo.core.agent.ExecuteResult
import ai.eqo.core.agent.LoopReport
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.StepOutcome
import ai.eqo.core.agent.UserResumeConfirmation
import ai.eqo.data.models.PlanStatus
import ai.eqo.study.RunReceipt
import ai.eqo.study.StepProgress
import ai.eqo.study.StepProgressState

/**
 * Runs one plan through [ActionLoop] and renders what did and did not happen.
 *
 * @param permissionCheck the real permission / capability plane check.
 * @param approvalGate the real user-confirmation card gate (60s countdown).
 * One deliberate run controller: the production constructor of ActionLoop plus its
 * control surface and receipt mapping (same reasoning as ActionLoop's own suppression).
 *
 * @param executor the compose-only study executor.
 * @param observe reads untrusted screen text after each apply.
 */
@Suppress("LongParameterList", "TooManyFunctions")
class StudyTaskController(
    private val steps: List<LoopStep>,
    permissionCheck: StudyPermissionCheck,
    approvalGate: StudyApprovalGate,
    private val executor: StudyActionExecutor,
    observe: () -> String,
    onPlanStatus: (PlanStatus) -> Unit = {},
    /** Live per-step progress for the task screen (REQ-TASK-01). */
    private val onStepProgress: (StepProgress) -> Unit = {},
    config: ActionLoop.Config = ActionLoop.Config(),
) {
    private val loop =
        ActionLoop(
            steps = steps,
            approvalGate = { step -> approvalGate.request(step) },
            execute = { step -> dispatch(step) },
            observe = { observe() },
            permissionCheck = { step -> permissionCheck.check(step) },
            // TASK-012 security pass: the shipped wiring must not use the ALLOW_ALL test
            // default. D-010: the study build carries no hard-block list — this is the
            // explicit policy object that says so.
            appBlockPolicy = StudyAppBlockPolicy(),
            onPlanStatus = onPlanStatus,
            config = config,
        )

    /** Executes one step and reports its live progress before and after the apply. */
    private suspend fun dispatch(step: LoopStep): ExecuteResult {
        onStepProgress(StepProgress(step.stepId, step.action.name, StepProgressState.RUNNING))
        val result = executor.execute(step)
        onStepProgress(
            StepProgress(
                stepId = step.stepId,
                name = step.action.name,
                state =
                    when (result) {
                        is ExecuteResult.Success -> StepProgressState.DONE
                        is ExecuteResult.Failure -> StepProgressState.FAILED
                        is ExecuteResult.Interrupted -> StepProgressState.UNKNOWN
                    },
                detail =
                    when (result) {
                        is ExecuteResult.Success -> result.detail
                        is ExecuteResult.Failure -> result.reason
                        is ExecuteResult.Interrupted -> "Unknown result — verify manually (${result.note})"
                    },
            ),
        )
        return result
    }

    /** User pause: takes effect after the current step finishes (REQ-TASK-03). */
    fun pause(): Boolean = loop.pause()

    /** User stop: the run ends after the current apply settles (REQ-TASK-03). */
    fun stop(): Boolean = loop.stop()

    /** User takeover from any screen during a run (REQ-TASK-05). */
    fun takeover(): Boolean = loop.takeover()

    /** User cancel: in-flight reversible applies are interrupted (their result is unknown). */
    fun cancel(): Boolean = loop.cancel()

    /**
     * Resume after an explicit user gesture. The caller passes the token minted in the
     * resume button's click handler — there is no parameterless resume anywhere in the
     * study app (SF-4).
     */
    fun resume(confirmation: UserResumeConfirmation): Boolean = loop.resume(confirmation)

    fun isActionInFlight(): Boolean = loop.isActionInFlight()

    fun currentState() = loop.currentState()

    fun currentPauseReason() = loop.currentPauseReason()

    /** Runs the plan to a terminal state and returns the receipt view. */
    suspend fun run(): RunReceipt = toReceipt(loop.run())

    private fun toReceipt(report: LoopReport): RunReceipt {
        val progress =
            report.steps.map { record ->
                val step = steps.firstOrNull { it.stepId == record.stepId }
                StepProgress(
                    stepId = record.stepId,
                    name = step?.action?.name ?: record.stepId,
                    state = record.outcome.toProgressState(),
                    elapsedMs = 0L,
                    detail = record.outcome.toDetail(),
                )
            }
        return RunReceipt(steps = progress, terminal = report.terminal?.name ?: "NONE")
    }

    private fun StepOutcome.toProgressState(): StepProgressState =
        when (this) {
            is StepOutcome.Completed -> StepProgressState.DONE
            is StepOutcome.PartialApply -> StepProgressState.UNKNOWN
            is StepOutcome.Failed -> StepProgressState.FAILED
            is StepOutcome.NotExecuted -> StepProgressState.PENDING
        }

    private fun StepOutcome.toDetail(): String =
        when (this) {
            is StepOutcome.Completed -> detail
            is StepOutcome.PartialApply -> "Unknown result — verify manually (${detail.describe()})"
            is StepOutcome.Failed -> reason
            is StepOutcome.NotExecuted -> reason
        }
}

/** The verifier's typed partial-apply detail, rendered without pretending to know the result. */
private fun ai.eqo.core.agent.StepVerifier.PartialApply.describe(): String = this.toString()
