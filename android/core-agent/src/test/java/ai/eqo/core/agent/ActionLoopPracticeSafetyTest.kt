package ai.eqo.core.agent

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActionLoopPracticeSafetyTest {
    @Test
    fun `takeover after final apply remains paused until confirmed rather than silently completing`() =
        runTest {
            lateinit var loop: ActionLoop
            var clears = 0
            loop =
                ActionLoop(
                    steps = listOf(LoopStep("last", ExecutedAction("observe"))),
                    approvalGate = { ApprovalDecision.Approved },
                    execute = {
                        loop.takeover()
                        ExecuteResult.Success("observed")
                    },
                    observe = { "" },
                    config = ActionLoop.Config(delayAfterLastStep = false),
                    onResumeConfirmed = { clears++ },
                )
            val run = async { loop.run() }
            runCurrent()
            assertEquals(LoopState.PAUSED, loop.currentState())
            assertFalse(run.isCompleted)
            assertFalse(loop.notifyEnvironmentRecovered())
            assertEquals(0, clears)
            assertTrue(loop.resume(UserResumeConfirmation.forExplicitUserConfirmation(1L)))
            advanceUntilIdle()
            assertEquals(PlanTerminal.COMPLETED, run.await().terminal)
            assertEquals(1, clears)
            assertFalse(loop.resume(UserResumeConfirmation.forExplicitUserConfirmation(2L)))
            assertEquals("rejected resume must not clear detector state", 1, clears)
        }
}
