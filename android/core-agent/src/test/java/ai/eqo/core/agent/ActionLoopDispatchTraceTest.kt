package ai.eqo.core.agent

import ai.eqo.data.models.PlanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class ActionLoopDispatchTraceTest {
    @Test
    fun `pause then explicit resume is not terminal and still dispatches`() =
        runTest {
            val trace = DispatchTrace()
            val executor = RecordingExecutor()
            lateinit var loop: ActionLoop
            loop =
                ActionLoop(
                    steps = listOf(testStep("s1", action = "DELETE", irreversible = true)),
                    approvalGate = {
                        assertTrue(loop.pause())
                        ApprovalDecision.Approved
                    },
                    execute = { step ->
                        trace.start(step.stepId, loop.currentState())
                        try {
                            executor.execute(step)
                        } finally {
                            trace.finish(step.stepId, loop.currentState())
                        }
                    },
                    observe = { "" },
                    onPlanStatus = { status ->
                        trace.status(status, loop.currentState())
                        if (status == PlanStatus.PAUSED) {
                            assertTrue(loop.resume(UserResumeConfirmation.forExplicitUserConfirmation(1L)))
                        }
                    },
                    config = ActionLoop.Config(interStepDelayMs = 0),
                )
            val report = loop.run()
            assertEquals(
                listOf(PlanStatus.RUNNING, PlanStatus.PAUSED, PlanStatus.RUNNING, PlanStatus.COMPLETED),
                report.planStatusEvents,
            )
            assertEquals(1, executor.started.get())
            assertEquals(1, executor.finished.get())
            assertEquals("old probe falsely reports a violation", 1, trace.legacyViolations())
            trace.assertSafe("deterministic resume")
            println("deterministic resume\n${trace.dump()}")
        }

    @Test
    fun `corrected probe rejects dispatch after every terminal status`() =
        runBlocking {
            listOf(PlanStatus.COMPLETED, PlanStatus.FAILED, PlanStatus.CANCELLED).forEach { terminal ->
                val trace = DispatchTrace()
                val executor = RecordingExecutor()
                trace.status(PlanStatus.RUNNING, LoopState.RUNNING)
                trace.status(terminal, LoopState.STOPPED)
                // Deliberately dispatch an actual fake apply after terminal, even with a stale RUNNING state.
                trace.start("illegal", LoopState.RUNNING)
                executor.execute(testStep("illegal"))
                trace.finish("illegal", LoopState.RUNNING)
                assertEquals(1, executor.started.get())
                assertEquals(1, executor.finished.get())
                val failure = assertThrows(AssertionError::class.java) { trace.assertSafe("negative $terminal") }
                assertTrue(failure.message.orEmpty().contains("no dispatch after terminal"))
            }
        }

    @Test
    fun `corrected probe rejects dispatch while paused without terminal`() {
        val trace = DispatchTrace()
        trace.status(PlanStatus.PAUSED, LoopState.PAUSED)
        trace.start("illegal", LoopState.PAUSED)
        val failure = assertThrows(AssertionError::class.java) { trace.assertSafe("negative paused") }
        assertTrue(failure.message.orEmpty().contains("no dispatch while paused or stopped"))
    }

    @Test
    fun `ordered threaded stress distinguishes paused history from terminal`() =
        runBlocking {
            val random = Random(17L)
            repeat(2000) { round ->
                val trace = DispatchTrace()
                val executor = RecordingExecutor(applyDelayMs = 5L + random.nextInt(10))
                lateinit var loop: ActionLoop
                loop =
                    ActionLoop(
                        steps = listOf(testStep("s1"), testStep("s2", action = "DELETE", irreversible = true)),
                        approvalGate = { ApprovalDecision.Approved },
                        execute = { step ->
                            trace.start(step.stepId, loop.currentState())
                            try {
                                executor.execute(step)
                            } finally {
                                trace.finish(step.stepId, loop.currentState())
                            }
                        },
                        observe = { "" },
                        onPlanStatus = { trace.status(it, loop.currentState()) },
                        config = ActionLoop.Config(tickMs = 2, actionTimeoutMs = 5_000, interStepDelayMs = 2),
                    )
                val run = async(Dispatchers.Default) { loop.run() }
                val jobs =
                    listOf(
                        launch(Dispatchers.Default) { pauseStorm(loop, trace) },
                        launch(Dispatchers.Default) { resumeStorm(loop, trace) },
                        launch(Dispatchers.Default) {
                            Thread.sleep(random.nextInt(6).toLong())
                            trace.command("stop", loop.stop(), loop.currentState())
                        },
                        launch(Dispatchers.Default) {
                            Thread.sleep(random.nextInt(6).toLong())
                            trace.command("cancel", loop.cancel(), loop.currentState())
                        },
                    )
                jobs.forEach { it.join() }
                val report = run.await()
                val label = "stress-round ${round + 1} legacy=${trace.legacyViolations()}"
                println("$label\n${trace.dump()}")
                trace.assertSafe(label)
                assertEquals(label, executor.started.get(), executor.finished.get())
                assertTrue(label, !loop.isActionInFlight())
                assertEquals(label, 1, report.planStatusEvents.count { it.isTerminalStatus() })
                assertEquals(
                    label,
                    report.steps.size,
                    report.steps
                        .map { it.stepId }
                        .distinct()
                        .size,
                )
                assertTrue(label, report.terminal != null)
                assertTrue(label, report.loopState == LoopState.STOPPED || report.loopState == LoopState.CANCELLED)
            }
        }

    private fun pauseStorm(
        loop: ActionLoop,
        trace: DispatchTrace,
    ) {
        repeat(5) {
            trace.command("pause", loop.pause(), loop.currentState())
            Thread.sleep(1)
            trace.command("takeover", loop.takeover(), loop.currentState())
            Thread.sleep(1)
        }
    }

    private fun resumeStorm(
        loop: ActionLoop,
        trace: DispatchTrace,
    ) {
        repeat(5) {
            val resumed = loop.resume(UserResumeConfirmation.forExplicitUserConfirmation(1L))
            trace.command("resume", resumed, loop.currentState())
            Thread.sleep(1)
        }
    }
}
