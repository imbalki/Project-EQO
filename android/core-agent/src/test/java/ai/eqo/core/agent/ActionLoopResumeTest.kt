/*
 * EQO (TASK-012, issue #17): acceptance criterion 4 — resume only after
 * explicit user confirmation; no automatic resume after recovery.
 */
package ai.eqo.core.agent

import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
            assertTrue("no terminal plan status while paused", events.none { it.name != "RUNNING" })

            assertTrue(loop.resume(UserResumeConfirmation.forExplicitUserConfirmation(7L)))
            advanceUntilIdle()
            val report = run.await()
            assertEquals(PlanTerminal.COMPLETED, report.terminal)
            assertEquals(2, executor.finished.get())
        }
}
