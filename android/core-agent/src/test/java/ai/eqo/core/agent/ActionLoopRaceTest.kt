/*
 * EQO (TASK-012, issue #17): race tests — pause, stop, takeover and resume
 * fired concurrently against a running loop. Invariants: no action mid-flight
 * at settle, plan terminal status exactly once, every step reported exactly
 * once. Console output of these rounds is quoted in the evidence file.
 */
package ai.eqo.core.agent

import ai.eqo.data.models.PlanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.Random

class ActionLoopRaceTest {
    private fun assertInvariants(
        round: String,
        loop: ActionLoop,
        executor: RecordingExecutor,
        report: LoopReport,
        events: List<PlanStatus>,
    ) {
        assertTrue(
            "$round: no apply starts while PAUSED or stopped",
            executor.dispatchStates.all { it == LoopState.RUNNING },
        )
        assertEquals("$round: no apply starts after terminal emission", 0, executor.dispatchAfterTerminal.get())
        assertEquals("$round: every dispatch was checked", executor.started.get(), executor.dispatchStates.size)
        val terminalEvents =
            events.count {
                it == PlanStatus.COMPLETED || it == PlanStatus.FAILED || it == PlanStatus.CANCELLED
            }
        assertEquals("$round: plan terminal status must be emitted exactly once", 1, terminalEvents)
        assertEquals("$round: no action may be mid-flight at settle", executor.started.get(), executor.finished.get())
        assertFalse("$round: isActionInFlight at settle", loop.isActionInFlight())
        assertEquals(
            "$round: every step reported exactly once",
            report.steps.size,
            report.steps
                .map { it.stepId }
                .distinct()
                .size,
        )
        assertEquals("$round: terminal must be set", true, report.terminal != null)
        assertTrue(
            "$round: settle state must be terminal, was ${report.loopState}",
            report.loopState == LoopState.STOPPED || report.loopState == LoopState.CANCELLED,
        )
        val notExecuted = report.steps.filter { it.outcome is StepOutcome.NotExecuted }
        println(
            "$round state=${report.loopState} terminal=${report.terminal} " +
                "applies=${executor.started.get()} " +
                "steps=${report.steps.map { it.stepId to it.outcome::class.simpleName }} " +
                "notExecuted=${notExecuted.size}",
        )
    }

    @Test
    fun `virtual-time command storm keeps every settle invariant`() =
        runTest {
            val random = Random(20261003L)
            for (round in 1..40) {
                val events = Collections.synchronizedList(mutableListOf<PlanStatus>())
                val executor = RecordingExecutor(applyDelayMs = 40L + random.nextInt(60))
                lateinit var loop: ActionLoop
                loop =
                    ActionLoop(
                        steps =
                            listOf(
                                testStep("s1", action = "TAP"),
                                testStep("s2", action = "SEND_MESSAGE", irreversible = true),
                                testStep("s3", action = "SCROLL"),
                            ),
                        approvalGate = { ApprovalDecision.Approved },
                        execute = { step ->
                            executor.dispatchStates += loop.currentState()
                            if (events.any { it != PlanStatus.RUNNING }) {
                                executor.dispatchAfterTerminal.incrementAndGet()
                            }
                            executor.execute(step)
                        },
                        observe = { "" },
                        onPlanStatus = { events += it },
                        config = ActionLoop.Config(tickMs = 10, actionTimeoutMs = 5_000, interStepDelayMs = 20),
                    )
                val run = async { loop.run() }
                runCurrent()
                launch {
                    repeat(4) {
                        loop.pause()
                        yield()
                        loop.takeover()
                        yield()
                    }
                }
                launch {
                    repeat(4) {
                        loop.resume(UserResumeConfirmation.forExplicitUserConfirmation(1L))
                        yield()
                    }
                }
                launch {
                    delay(random.nextInt(30).toLong())
                    loop.stop()
                }
                launch {
                    delay(random.nextInt(30).toLong())
                    loop.cancel()
                }
                advanceUntilIdle()
                val report = run.await()
                assertInvariants("virtual-round $round:", loop, executor, report, events)
            }
        }

    @Test
    fun `threaded command storm keeps every settle invariant`() =
        runBlocking {
            val random = Random(17L)
            repeat(10) { round ->
                val events = Collections.synchronizedList(mutableListOf<PlanStatus>())
                val executor = RecordingExecutor(applyDelayMs = 5L + random.nextInt(10))
                lateinit var loop: ActionLoop
                loop =
                    ActionLoop(
                        steps =
                            listOf(
                                testStep("s1", action = "TAP"),
                                testStep("s2", action = "DELETE", irreversible = true),
                            ),
                        approvalGate = { ApprovalDecision.Approved },
                        execute = { step ->
                            executor.dispatchStates += loop.currentState()
                            if (events.any { it != PlanStatus.RUNNING }) {
                                executor.dispatchAfterTerminal.incrementAndGet()
                            }
                            executor.execute(step)
                        },
                        observe = { "" },
                        onPlanStatus = { events += it },
                        config = ActionLoop.Config(tickMs = 2, actionTimeoutMs = 5_000, interStepDelayMs = 2),
                    )
                val run = async(Dispatchers.Default) { loop.run() }
                val commandJobs =
                    listOf(
                        launch(Dispatchers.Default) {
                            repeat(5) {
                                loop.pause()
                                Thread.sleep(1)
                                loop.takeover()
                                Thread.sleep(1)
                            }
                        },
                        launch(Dispatchers.Default) {
                            repeat(5) {
                                loop.resume(UserResumeConfirmation.forExplicitUserConfirmation(1L))
                                Thread.sleep(1)
                            }
                        },
                        launch(Dispatchers.Default) {
                            Thread.sleep((random.nextInt(6)).toLong())
                            loop.stop()
                        },
                        launch(Dispatchers.Default) {
                            Thread.sleep((random.nextInt(6)).toLong())
                            loop.cancel()
                        },
                    )
                commandJobs.forEach { it.join() }
                val report = withContext(Dispatchers.Default) { run.await() }
                assertInvariants("threaded-round ${round + 1}:", loop, executor, report, events)
            }
        }
}
