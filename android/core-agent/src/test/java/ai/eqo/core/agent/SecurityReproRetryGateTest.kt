/*
 * EQO security regression (adapted from reviewer repro) - TASK-012 t_d306e5bb.
 * Preserves the reviewer invariants (R4 signature repaired in attached source).
 * Proves/disproves three suspected loop-control gaps:
 *  R1 retry dispatches while the loop is PAUSED;
 *  R2 retry dispatches after the plan already reached terminal (STOPPED);
 *  R3 a stop submitted during the approval prompt is overridden by dispatch.
 */
package ai.eqo.core.agent

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityReproRetryGateTest {
    @Test
    fun `pause during approval blocks dispatch until explicit resume`() =
        runTest {
            val executor = RecordingExecutor()
            lateinit var loop: ActionLoop
            loop =
                ActionLoop(
                    steps = listOf(testStep("s1", action = "SEND_MESSAGE", irreversible = true)),
                    approvalGate = {
                        loop.pause()
                        delay(50)
                        ApprovalDecision.Approved
                    },
                    execute = executor::execute,
                    observe = { "" },
                    config = ActionLoop.Config(tickMs = 10, interStepDelayMs = 20),
                )
            val run = async { loop.run() }
            advanceTimeBy(200)
            runCurrent()
            assertEquals(LoopState.PAUSED, loop.currentState())
            assertEquals(0, executor.started.get())
            assertTrue(loop.resume(UserResumeConfirmation.forExplicitUserConfirmation(1)))
            advanceUntilIdle()
            assertEquals(PlanTerminal.COMPLETED, run.await().terminal)
            assertEquals(1, executor.started.get())
        }

    @Test
    fun `stop during permission check blocks dispatch`() =
        runTest {
            val executor = RecordingExecutor()
            lateinit var loop: ActionLoop
            loop =
                ActionLoop(
                    steps = listOf(testStep("s1", permission = "camera")),
                    permissionCheck = {
                        loop.stop()
                        delay(50)
                        PermissionDecision.Granted
                    },
                    approvalGate = { ApprovalDecision.Approved },
                    execute = executor::execute,
                    observe = { "" },
                )
            val report = loop.run()
            assertEquals(PlanTerminal.STOPPED, report.terminal)
            assertTrue(report.steps.single().outcome is StepOutcome.NotExecuted)
            assertEquals(0, executor.started.get())
        }

    @Test
    fun `R1 - pause that landed during the step must block the retry dispatch`() =
        runTest {
            val executor =
                RecordingExecutor(
                    applyDelayMs = 50,
                    behavior = { _, attempt ->
                        if (attempt == 1) {
                            ExecuteResult.Failure("boom", transient = true)
                        } else {
                            ExecuteResult.Success("ok")
                        }
                    },
                )
            var loopRef: ActionLoop? = null
            val stateAtDispatch = mutableListOf<String>()
            val loop =
                ActionLoop(
                    steps = listOf(testStep("s1")),
                    approvalGate = { ApprovalDecision.Approved },
                    execute = { step ->
                        stateAtDispatch += "${loopRef?.currentState()}/${loopRef?.currentPauseReason()}"
                        executor.execute(step)
                    },
                    observe = { "" },
                    config = ActionLoop.Config(tickMs = 10, actionTimeoutMs = 5_000, interStepDelayMs = 20),
                )
            loopRef = loop
            val run = async { loop.run() }
            runCurrent()
            delay(20) // inside attempt 1 (50ms apply)
            assertTrue("pause submits", loop.pause())
            advanceTimeBy(300) // apply end + settle + (bug) retry dispatch
            runCurrent()
            println(
                "R1 RESULT started=${executor.started.get()} calls=${executor.calls} " +
                    "stateAtDispatch=$stateAtDispatch state=${loop.currentState()} " +
                    "pauseReason=${loop.currentPauseReason()}",
            )
            run.cancel()
            assertEquals(
                "R1: after a pause landed mid-step, no further action may be dispatched " +
                    "(retry checkpoint must honor pause)",
                1,
                executor.started.get(),
            )
        }

    @Test
    fun `R2 - stop that landed during observe must block the retry dispatch`() =
        runTest {
            val executor =
                RecordingExecutor(
                    behavior = { _, attempt ->
                        if (attempt == 1) {
                            ExecuteResult.Failure("boom", transient = true)
                        } else {
                            ExecuteResult.Success("ok")
                        }
                    },
                )
            var loopRef: ActionLoop? = null
            val loop =
                ActionLoop(
                    steps = listOf(testStep("s1")),
                    approvalGate = { ApprovalDecision.Approved },
                    execute = executor::execute,
                    observe = {
                        // the user stops while the loop is observing the screen
                        loopRef?.stop()
                        ""
                    },
                    config = ActionLoop.Config(tickMs = 10, actionTimeoutMs = 5_000, interStepDelayMs = 20),
                )
            loopRef = loop
            val events = mutableListOf<ai.eqo.data.models.PlanStatus>()
            val run = async { loop.run() }
            advanceUntilIdle()
            val report = run.await()
            events += report.planStatusEvents
            println(
                "R2 RESULT started=${executor.started.get()} calls=${executor.calls} " +
                    "state=${loop.currentState()} terminal=${report.terminal} events=$events",
            )
            assertEquals(
                "R2: after STOPPED was reached, no further action may be dispatched",
                1,
                executor.started.get(),
            )
        }

    @Test
    fun `R4 - an irreversible action with a neutral verb must still require approval`() {
        // spec scope: "Approval before sensitive or irreversible actions"
        val action =
            ExecutedAction(
                name = "RESTART_DEVICE",
                params = emptyMap(),
                irreversible = true,
            )
        assertTrue(
            "R4: ExecutedAction.irreversible=true must require approval regardless of the verb",
            SensitivityApprovalPolicy.requiresApproval(action),
        )
    }

    @Test
    fun `R3 - stop submitted during the approval prompt must prevent dispatch`() =
        runTest {
            val executor = RecordingExecutor()
            var loopRef: ActionLoop? = null
            val loop =
                ActionLoop(
                    steps = listOf(testStep("s1", action = "SEND_MESSAGE", irreversible = true)),
                    approvalGate = {
                        // the user hits STOP instead of approving
                        loopRef?.stop()
                        delay(50)
                        ApprovalDecision.Approved
                    },
                    execute = executor::execute,
                    observe = { "" },
                    config = ActionLoop.Config(tickMs = 10, actionTimeoutMs = 5_000, interStepDelayMs = 20),
                )
            loopRef = loop
            val run = async { loop.run() }
            advanceUntilIdle()
            val report = run.await()
            println(
                "R3 RESULT started=${executor.started.get()} calls=${executor.calls} " +
                    "state=${loop.currentState()} terminal=${report.terminal} events=${report.planStatusEvents}",
            )
            assertEquals(
                "R3: a stop submitted before dispatch ('cancelled before dispatch') must prevent the irrevesible apply",
                0,
                executor.started.get(),
            )
        }
}
