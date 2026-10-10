// Origin: EQO t_9fd2d126, fake-backed completed-step and safe retry regressions.
package ai.eqo.task

import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.study.StepProgress
import ai.eqo.study.StepProgressState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class TaskPlanRetryTest {
    private val steps =
        listOf(
            LoopStep("text", ExecutedAction("SEND_WHATSAPP", mapOf("contact" to PHONE, "message" to "test"))),
            LoopStep("location", ExecutedAction("SHARE_LOCATION", mapOf("to" to PHONE, "via" to "whatsapp"))),
        )

    @Test fun permissionStopRetriesLocationOnlyAcrossRepeatedDenials() =
        runTest {
            val retry = TaskPlanRetry()
            val calls = mutableListOf<String>()
            val plan = ApprovedTaskPlan(steps)
            retry.started(plan)
            runPlan(plan, retry, calls)
            assertEquals(listOf("SEND_WHATSAPP", "SHARE_LOCATION"), calls)
            val remaining = requireNotNull(retry.remaining())
            assertEquals(listOf(steps.last()), remaining.steps())
            retry.started(remaining)
            runPlan(remaining, retry, calls)
            assertEquals(listOf("location"), retry.remaining()!!.steps().map { it.stepId })
            assertEquals(1, calls.count { it == "SEND_WHATSAPP" })
            retry.started(retry.remaining()!!)
            retry.record(progress("location", StepProgressState.DONE))
            assertNull(retry.remaining())
        }

    private suspend fun runPlan(
        plan: ApprovedTaskPlan,
        retry: TaskPlanRetry,
        calls: MutableList<String>,
    ) {
        val executor =
            StudyActionExecutor(EqoAutomationPort({ null }, { _, _ -> false }), plan) { name, _ ->
                calls += name
                if (name == "SEND_WHATSAPP") {
                    ActionResult.Success()
                } else {
                    ActionResult.UserActionRequired("Allow location. This step did not run.")
                }
            }
        val controller =
            StudyTaskController(
                plan.steps(),
                StudyPermissionCheck({ true }, { true }, { false }),
                StudyApprovalGate(StudyApprovalSurface { error("Already approved") }, { 0L }, plan),
                executor,
                { "" },
                onStepProgress = retry::record,
                takeoverDetector = ai.eqo.accessibility.TakeoverDetector(),
            )
        controller.run()
    }

    @Test fun unknownEffectsAndManualDraftHandoffsNeverOfferRetry() {
        val retry = TaskPlanRetry()
        retry.started(ApprovedTaskPlan(steps))
        retry.record(progress("text", StepProgressState.UNKNOWN))
        retry.record(progress("location", StepProgressState.NEEDS_YOU, "This step did not run."))
        assertNull(retry.remaining())
        retry.started(ApprovedTaskPlan(steps))
        retry.record(progress("text", StepProgressState.NEEDS_YOU, "Draft opened. You press Send."))
        assertNull(retry.remaining())
    }

    @Test fun freshPlanDoesNotInheritCompletedIdsAndMemoryIsNotPersisted() {
        val retry = TaskPlanRetry()
        retry.started(ApprovedTaskPlan(steps))
        retry.record(progress("text", StepProgressState.DONE))
        retry.record(progress("location", StepProgressState.NEEDS_YOU, "This step did not run."))
        val changed = steps.map { it.copy(stepId = "new-${it.stepId}") }
        retry.started(ApprovedTaskPlan(changed))
        retry.record(progress("new-text", StepProgressState.NEEDS_YOU, "This step did not run."))
        assertEquals(changed, retry.remaining()!!.steps())
        assertNull(TaskPlanRetry().remaining())
    }

    private fun progress(
        id: String,
        state: StepProgressState,
        detail: String = "",
    ) = StepProgress(
        id,
        steps
            .firstOrNull { it.stepId == id }
            ?.action
            ?.name
            .orEmpty(),
        state,
        detail = detail,
    )

    private companion object {
        const val PHONE = "+15555550199"
    }
}
