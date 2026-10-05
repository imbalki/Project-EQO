/*
 * EQO (TASK-012, issue #17): acceptance criterion 4 — resume only after
 * explicit user confirmation; no automatic resume after recovery.
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

class ActionLoopResumeTest {
    private fun pausedLoop(): Pair<ActionLoop, RecordingExecutor> {
        val executor = RecordingExecutor(applyDelayMs = 100)
        val loop =
            ActionLoop(
                steps = (1..2).map { testStep("s$it") },
                approvalGate = { ApprovalDecision.Approved },
                execute = executor::execute,
                observe = { "" },
            )
        return loop to executor
    }

    @Test
    fun `confirmed resume cannot overwrite stop`() = assertTerminalRace(PlanTerminal.STOPPED)

    @Test
    fun `confirmed resume cannot overwrite cancel`() = assertTerminalRace(PlanTerminal.CANCELLED)

    private fun assertTerminalRace(terminal: PlanTerminal) =
        runTest {
            run {
                val entered = CountDownLatch(1)
                val release = CountDownLatch(1)
                val events = mutableListOf<Pair<PlanStatus, LoopState>>()
                lateinit var loop: ActionLoop
                loop =
                    ActionLoop(
                        steps = listOf(testStep("s1")),
                        approvalGate = { ApprovalDecision.Approved },
                        execute = { error("paused/terminal loop must not dispatch") },
                        observe = { "" },
                        onPlanStatus = { events += it to loop.currentState() },
                        onResumeConfirmed = {
                            entered.countDown()
                            check(release.await(10, TimeUnit.SECONDS))
                        },
                    )
                loop.pause()
                val run = async { loop.run() }
                runCurrent()
                assertEquals(LoopState.PAUSED, loop.currentState())
                val resume = FutureTask { loop.resume(UserResumeConfirmation.forExplicitUserConfirmation(42L)) }
                val resumeThread = Thread(resume, "confirmed-resume")
                // Stage the runner's already-dequeued terminal transition directly, so
                // pollCommand's queue lock cannot accidentally mask the state-write race.
                // No production test hook or altered command semantics are required.
                val settle =
                    FutureTask {
                        val method = ActionLoop::class.java.getDeclaredMethod("terminalize", PlanTerminal::class.java)
                        method.isAccessible = true
                        method.invoke(loop, terminal)
                    }
                val settleThread = Thread(settle, "dequeued-terminal")
                resumeThread.start()
                releaseAfterTerminalBlocked(entered, release, settleThread)
                assertTrue(resume.get(10, TimeUnit.SECONDS))
                settle.get(10, TimeUnit.SECONDS)
                advanceUntilIdle()
                val report = run.await()
                val expected = if (terminal == PlanTerminal.CANCELLED) LoopState.CANCELLED else LoopState.STOPPED
                assertEquals("resume must not resurrect $terminal", expected, report.loopState)
                assertEquals(terminal, report.terminal)
                assertEquals(expected, loop.currentState())
                assertFalse(loop.isActionInFlight())
                assertTrue(report.steps.all { it.outcome is StepOutcome.NotExecuted })
                assertEquals(
                    listOf(PlanStatus.RUNNING, PlanStatus.PAUSED, PlanStatus.RUNNING, PlanStatus.CANCELLED),
                    events.map { it.first },
                )
                assertEquals(PlanStatus.CANCELLED to expected, events.last())
                assertFalse(loop.resume(UserResumeConfirmation.forExplicitUserConfirmation(43L)))
                println("terminal=$terminal report=${report.loopState} events=$events")
            }
        }

    private fun releaseAfterTerminalBlocked(
        entered: CountDownLatch,
        release: CountDownLatch,
        settleThread: Thread,
    ) {
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            settleThread.start()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (settleThread.state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                Thread.yield()
            }
            assertEquals(
                "terminal transition reached the held resume monitor",
                Thread.State.BLOCKED,
                settleThread.state,
            )
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `resume has no parameterless entry point`() {
        val zeroArgResume =
            ActionLoop::class.java.methods.any { it.name == "resume" && it.parameterCount == 0 }
        assertFalse("no agent-reachable parameterless resume may exist", zeroArgResume)
        // The only resume takes the explicit-user-confirmation token.
        val resume = ActionLoop::class.java.getMethod("resume", UserResumeConfirmation::class.java)
        assertEquals(UserResumeConfirmation::class.java, resume.parameterTypes.single())
    }

    @Test
    fun `recovery never resumes a paused loop`() =
        runTest {
            val (loop, _) = pausedLoop()
            val run = async { loop.run() }
            runCurrent()
            loop.pause()
            // Bounded advance only: a paused loop polls forever, so no advanceUntilIdle here.
            advanceTimeBy(400)
            runCurrent()
            assertEquals(LoopState.PAUSED, loop.currentState())

            // Environment recovery (service re-bound, detector cleared, ...): no auto-resume.
            assertFalse(loop.notifyEnvironmentRecovered())
            assertEquals(LoopState.PAUSED, loop.currentState())

            // Only an explicit user confirmation resumes.
            assertTrue(loop.resume(UserResumeConfirmation.forExplicitUserConfirmation(42L)))
            advanceUntilIdle()
            assertEquals(PlanTerminal.COMPLETED, run.await().terminal)
        }

    @Test
    fun `resume is rejected unless the loop is paused`() =
        runTest {
            val (loop, _) = pausedLoop()
            val run = async { loop.run() }
            runCurrent()
            assertFalse(loop.resume(UserResumeConfirmation.forExplicitUserConfirmation(1L)))
            advanceUntilIdle()
            val report = run.await()
            // After terminal, resume can never resurrect the loop.
            assertFalse(loop.resume(UserResumeConfirmation.forExplicitUserConfirmation(2L)))
            assertEquals(PlanTerminal.COMPLETED, report.terminal)
        }

    @Test
    fun `a self-gesture takeover pause is visible and never kills the task silently`() =
        runTest {
            val events = mutableListOf<ai.eqo.data.models.PlanStatus>()
            val executor = RecordingExecutor(applyDelayMs = 100)
            val loop =
                ActionLoop(
                    steps = (1..2).map { testStep("s$it") },
                    approvalGate = { ApprovalDecision.Approved },
                    execute = executor::execute,
                    observe = { "" },
                    onPlanStatus = { events += it },
                )
            val run = async { loop.run() }
            runCurrent()
            // A spurious takeover latched by EQO's own stroke (security note N-3).
            assertTrue(loop.takeover(PauseReason.SELF_GESTURE_TAKEOVER_SUSPECTED))
            advanceTimeBy(400)
            runCurrent()
            assertEquals(LoopState.PAUSED, loop.currentState())
            assertEquals(PauseReason.SELF_GESTURE_TAKEOVER_SUSPECTED, loop.currentPauseReason())
            // Not terminal: the task is paused, not dead.
            assertEquals(
                listOf(ai.eqo.data.models.PlanStatus.RUNNING, ai.eqo.data.models.PlanStatus.PAUSED),
                events,
            )

            assertTrue(loop.resume(UserResumeConfirmation.forExplicitUserConfirmation(7L)))
            advanceUntilIdle()
            val report = run.await()
            assertEquals(PlanTerminal.COMPLETED, report.terminal)
            assertEquals(2, executor.finished.get())
        }
}
