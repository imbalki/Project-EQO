/*
 * EQO (TASK-012, issue #17): acceptance criterion 2 — no action mid-flight at
 * settle; the plan reaches a terminal status exactly once. Plus the
 * no-automatic-retry rule for irreversible actions.
 */
package ai.eqo.core.agent

import ai.eqo.data.models.PlanStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class ActionLoopSettleTest {
    @Test
    fun `no action is mid-flight at settle and every step is reported exactly once`() =
        runTest {
            val executor = RecordingExecutor(applyDelayMs = 100)
            val loop =
                ActionLoop(
                    steps = (1..3).map { testStep("s$it") },
                    approvalGate = { ApprovalDecision.Approved },
                    execute = executor::execute,
                    observe = { "" },
                )
            val run = async { loop.run() }
            runCurrent()
            launch { loop.stop() }
            launch { loop.pause() }
            launch { loop.cancel() }
            advanceUntilIdle()
            val report = run.await()

            assertEquals("no action may be mid-flight at settle", executor.started.get(), executor.finished.get())
            assertFalse(loop.isActionInFlight())
            assertEquals(listOf("s1", "s2", "s3"), report.steps.map { it.stepId }.sorted())
            assertEquals(3, report.steps.size)
        }

    @Test
    fun `concurrent stop and cancel settle the plan terminally exactly once`() =
        runTest {
            val events = Collections.synchronizedList(mutableListOf<PlanStatus>())
            val executor = RecordingExecutor(applyDelayMs = 50)
            val loop =
                ActionLoop(
                    steps = (1..3).map { testStep("s$it") },
                    approvalGate = { ApprovalDecision.Approved },
                    execute = executor::execute,
                    observe = { "" },
                    onPlanStatus = { events += it },
                )
            val run = async { loop.run() }
            runCurrent()
            launch { loop.stop() }
            launch { loop.cancel() }
            launch { loop.stop() }
            launch { loop.cancel() }
            advanceUntilIdle()
            val report = run.await()

            val terminalEvents =
                events.count {
                    it == PlanStatus.COMPLETED || it == PlanStatus.FAILED || it == PlanStatus.CANCELLED
                }
            assertEquals("plan must reach a terminal status exactly once", 1, terminalEvents)
            assertEquals(events, report.planStatusEvents)
            assertTrue(report.loopState == LoopState.STOPPED || report.loopState == LoopState.CANCELLED)
            assertEquals(executor.started.get(), executor.finished.get())
        }

    @Test
    fun `an irreversible failure is attempted exactly once even when flagged transient`() =
        runTest {
            var attempts = 0
            val executor =
                RecordingExecutor { _, _ ->
                    attempts++
                    ExecuteResult.Failure("transient glitch", transient = true)
                }
            val loop =
                ActionLoop(
                    steps = listOf(testStep("s1", action = "SEND_MESSAGE", irreversible = true)),
                    approvalGate = { ApprovalDecision.Approved },
                    execute = executor::execute,
                    observe = { "" },
                )
            val run = async { loop.run() }
            advanceUntilIdle()
            val report = run.await()

            assertEquals("irreversible actions are never automatically retried", 1, attempts)
            assertEquals(1, report.steps.single().attempts)
            assertTrue(report.steps.single().outcome is StepOutcome.Failed)
            assertEquals(PlanTerminal.FAILED, report.terminal)
        }

    @Test
    fun `a reversible transient failure is retried once and then completes`() =
        runTest {
            val executor =
                RecordingExecutor { _, attempt ->
                    if (attempt == 1) {
                        ExecuteResult.Failure("transient glitch", transient = true)
                    } else {
                        ExecuteResult.Success("ok")
                    }
                }
            val loop =
                ActionLoop(
                    steps = listOf(testStep("s1")),
                    approvalGate = { ApprovalDecision.Approved },
                    execute = executor::execute,
                    observe = { "" },
                )
            val run = async { loop.run() }
            advanceUntilIdle()
            val report = run.await()

            assertEquals(2, report.steps.single().attempts)
            assertTrue(report.steps.single().outcome is StepOutcome.Completed)
            assertEquals(PlanTerminal.COMPLETED, report.terminal)
        }

    @Test
    fun `a typed partial-apply on an irreversible step stops the plan and names what did not happen`() =
        runTest {
            val executor =
                RecordingExecutor(applyDelayMs = 100) { _, _ -> ExecuteResult.Success("half done") }
            val loop =
                ActionLoop(
                    steps =
                        listOf(
                            testStep("s1", action = "DELETE", irreversible = true, postconditions = listOf("gone")),
                            testStep("s2"),
                        ),
                    approvalGate = { ApprovalDecision.Approved },
                    execute = executor::execute,
                    // The screen does not show the declared postcondition: partial-apply.
                    observe = { "item still visible" },
                )
            val run = async { loop.run() }
            runCurrent()
            launch { loop.stop() }
            advanceUntilIdle()
            val report = run.await()

            val first = report.steps.first { it.stepId == "s1" }
            assertTrue(first.outcome is StepOutcome.PartialApply)
            val partial = (first.outcome as StepOutcome.PartialApply).detail
            assertEquals(listOf("gone"), partial.notApplied)
            assertTrue(partial.applied.isEmpty())
            // s2 did not happen and the report says so.
            assertEquals(
                StepOutcome.NotExecuted("earlier step did not complete"),
                report.steps.first { it.stepId == "s2" }.outcome,
            )
            assertFalse("the never-executed step must not run", executor.calls.contains("s2"))
        }
}
