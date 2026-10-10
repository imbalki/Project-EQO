/*
 * EQO (TASK-012, issue #17): acceptance criterion 1 — each of pause, stop,
 * takeover transitions within a bounded time (virtual-time test).
 */
package ai.eqo.core.agent

import ai.eqo.data.models.PlanStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionLoopTransitionsTest {
    @Test fun permissionWaitDoesNotConsumeTheApplyBudget() =
        runTest {
            var waiting = true
            var applies = 0
            val loop =
                ActionLoop(
                    steps = listOf(testStep("permission")),
                    approvalGate = { ApprovalDecision.Approved },
                    execute = {
                        kotlinx.coroutines.delay(119_000)
                        waiting = false
                        applies++
                        ExecuteResult.Success("done")
                    },
                    observe = { "" },
                    isPermissionWaiting = { waiting },
                )
            val run = async { loop.run() }
            advanceTimeBy(118_000)
            runCurrent()
            assertTrue(loop.isActionInFlight())
            assertFalse(run.isCompleted)
            advanceUntilIdle()
            assertEquals(1, applies)
            assertEquals(PlanTerminal.COMPLETED, run.await().terminal)
        }

    /**
     * Documented transition bounds (virtual time, ms):
     *  - pause/takeover: current apply + one command tick + phase handling
     *    ("Pause after the current step finishes" — the UX copy);
     *  - stop of a reversible apply: one command tick;
     *  - stop of an irreversible apply: its completion, or [ ][ActionLoop.Config.actionTimeoutMs] for a hang.
     */
    private val pauseBoundMs = 200L + 50L + 250L + 100L

    @Test fun attachmentSendAllowsHumanChoiceButStillHasAHardDeadline() =
        runTest {
            val step =
                LoopStep(
                    "file",
                    ExecutedAction("SEND_EMAIL", mapOf("attachment" to "find:bill"), irreversible = true),
                )
            val loop =
                ActionLoop(
                    steps = listOf(step),
                    approvalGate = { ApprovalDecision.Approved },
                    execute = {
                        kotlinx.coroutines.delay(160_000)
                        ExecuteResult.Success("unexpected")
                    },
                    observe = { "" },
                )
            val run = async { loop.run() }
            runCurrent()
            advanceTimeBy(6_000)
            runCurrent()
            assertFalse(run.isCompleted)
            advanceUntilIdle()
            assertTrue(
                run
                    .await()
                    .steps
                    .single()
                    .outcome is StepOutcome.PartialApply,
            )
        }

    private fun loop(
        applyDelayMs: Long,
        stepCount: Int = 2,
        config: ActionLoop.Config = ActionLoop.Config(),
    ): Pair<ActionLoop, RecordingExecutor> {
        val executor = RecordingExecutor(applyDelayMs = applyDelayMs)
        val loop =
            ActionLoop(
                steps = (1..stepCount).map { testStep("s$it") },
                approvalGate = { ApprovalDecision.Approved },
                execute = executor::execute,
                observe = { "" },
                config = config,
            )
        return loop to executor
    }

    @Test
    fun `pause transitions within the bounded time and resumes only after explicit confirmation`() =
        runTest {
            val (loop, executor) = loop(applyDelayMs = 200)
            val run = async { loop.run() }
            runCurrent()

            loop.pause()
            advanceTimeBy(100)
            runCurrent()
            // Pause is honored "after the current step finishes": the apply is still in flight.
            assertEquals(LoopState.RUNNING, loop.currentState())

            advanceTimeBy(pauseBoundMs)
            runCurrent()
            assertEquals(LoopState.PAUSED, loop.currentState())
            assertEquals(PauseReason.USER_PAUSE, loop.currentPauseReason())

            assertTrue(loop.resume(UserResumeConfirmation.forExplicitUserConfirmation(1L)))
            advanceUntilIdle()
            val report = run.await()
            assertEquals(2, executor.finished.get())
            assertFalse(loop.isActionInFlight())
            assertEquals(PlanTerminal.COMPLETED, report.terminal)
        }

    @Test
    fun `stop transitions within a bounded time and interrupts a reversible apply typed as partial-apply`() =
        runTest {
            val (loop, _) = loop(applyDelayMs = 10_000, stepCount = 1)
            val run = async { loop.run() }
            runCurrent()

            loop.stop()
            advanceTimeBy(200)
            runCurrent()
            assertEquals(LoopState.STOPPED, loop.currentState())

            advanceUntilIdle()
            val report = run.await()
            assertEquals(PlanTerminal.STOPPED, report.terminal)
            assertEquals(1, report.planStatusEvents.count { it == PlanStatus.CANCELLED })
            val outcome = report.steps.single().outcome
            assertTrue("expected typed partial-apply, got $outcome", outcome is StepOutcome.PartialApply)
            val partial = (outcome as StepOutcome.PartialApply).detail
            assertTrue(partial.note.contains("cancelled mid-apply"))
            assertFalse(loop.isActionInFlight())
        }

    @Test
    fun `takeover transitions within the bounded time to paused with the takeover reason`() =
        runTest {
            val (loop, _) = loop(applyDelayMs = 200)
            val run = async { loop.run() }
            runCurrent()

            loop.takeover()
            advanceTimeBy(pauseBoundMs)
            runCurrent()
            assertEquals(LoopState.PAUSED, loop.currentState())
            assertEquals(PauseReason.USER_TAKEOVER, loop.currentPauseReason())

            // The takeover pause is released only by explicit user confirmation.
            assertTrue(loop.resume(UserResumeConfirmation.forExplicitUserConfirmation(5L)))
            advanceUntilIdle()
            run.await()
        }

    @Test
    fun `stop never kills an irreversible apply mid-flight`() =
        runTest {
            val executor = RecordingExecutor(applyDelayMs = 300)
            val loop =
                ActionLoop(
                    steps = listOf(testStep("s1", action = "SEND_MESSAGE", irreversible = true)),
                    approvalGate = { ApprovalDecision.Approved },
                    execute = executor::execute,
                    observe = { "message sent" },
                    config = ActionLoop.Config(),
                )
            val run = async { loop.run() }
            runCurrent()

            loop.stop()
            advanceTimeBy(100)
            runCurrent()
            // Mid-apply is fine before settle; the irreversible apply must be finishing, not cancelled.
            assertEquals(LoopState.RUNNING, loop.currentState())
            assertEquals(0, executor.finished.get())

            advanceUntilIdle()
            val report = run.await()
            assertEquals(1, executor.finished.get())
            assertFalse(loop.isActionInFlight())
            assertEquals(PlanTerminal.STOPPED, report.terminal)
            // The effect that DID happen is reported before settling.
            assertTrue(report.steps.single().outcome is StepOutcome.Completed)
        }

    @Test
    fun `a hanging irreversible apply settles as typed partial-apply within the timeout bound`() =
        runTest {
            val executor = RecordingExecutor(applyDelayMs = 10_000_000)
            val loop =
                ActionLoop(
                    steps =
                        listOf(
                            testStep("s1", action = "DELETE", irreversible = true, postconditions = listOf("gone")),
                        ),
                    approvalGate = { ApprovalDecision.Approved },
                    execute = executor::execute,
                    observe = { "" },
                    config = ActionLoop.Config(actionTimeoutMs = 2_000),
                )
            val run = async { loop.run() }
            advanceTimeBy(2_200)
            runCurrent()
            advanceUntilIdle()
            val report = run.await()
            val outcome = report.steps.single().outcome
            assertTrue("expected typed partial-apply, got $outcome", outcome is StepOutcome.PartialApply)
            val partial = (outcome as StepOutcome.PartialApply).detail
            assertTrue(partial.note.contains("timed out mid-apply"))
            assertEquals(listOf("gone"), partial.notApplied)
            assertEquals(PlanTerminal.FAILED, report.terminal)
            assertEquals(1, executor.finished.get())
            assertFalse(loop.isActionInFlight())
        }
}
