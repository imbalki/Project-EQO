package ai.eqo.task

import ai.eqo.accessibility.TakeoverDetector
import ai.eqo.core.agent.ActionLoop
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopState
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.UserResumeConfirmation
import ai.eqo.data.models.PlanStatus
import ai.eqo.study.ApprovalOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SamplePracticeTest {
    private class Fixture {
        val executed = mutableListOf<String>()
        val statuses = mutableListOf<PlanStatus>()
        val feedback = mutableListOf<TaskControlFeedback>()
        val waits = mutableListOf<Triple<Int, Int, Int>>()
        val detector = TakeoverDetector()
        val controller =
            StudyTaskController(
                steps = (1..3).map { LoopStep("s$it", ExecutedAction(name = "observe")) },
                permissionCheck = StudyPermissionCheck({ true }, { true }, { true }),
                approvalGate = StudyApprovalGate(StudyApprovalSurface { ApprovalOutcome.Approved }, { 0L }),
                executor =
                    StudyActionExecutor(
                        object : StudyAutomationPort {
                            override fun observe(): String {
                                executed += "observe"
                                return "screen"
                            }

                            override fun tap(text: String) = true

                            override fun tapById(viewId: String) = true

                            override fun typeText(text: String) = true

                            override fun scroll(direction: String) = true

                            override fun back() = true

                            override fun home() = true

                            override fun composeSmsDraft(
                                recipient: String,
                                body: String,
                            ) = true
                        },
                    ),
                observe = { "screen" },
                onPlanStatus = { statuses += it },
                config = SamplePractice.config,
                onControlFeedback = { feedback += it },
                onInterStepWait = { next, total, ms -> waits += Triple(next, total, SamplePractice.seconds(ms)) },
                takeoverDetector = detector,
            )
    }

    @Test
    fun `sample alone opts into eight second waits and countdown while running`() =
        runTest {
            assertEquals(250L, ActionLoop.Config().interStepDelayMs)
            assertTrue(ActionLoop.Config().delayAfterLastStep)
            val f = Fixture()
            val run = async { f.controller.run() }
            runCurrent()
            assertEquals(1, f.executed.size)
            assertEquals(Triple(2, 3, 8), f.waits.first())
            advanceTimeBy(7_000)
            runCurrent()
            assertEquals(1, f.executed.size)
            assertEquals(Triple(2, 3, 1), f.waits.last())
            assertEquals(LoopState.RUNNING, f.controller.currentState())
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(2, f.executed.size)
            assertEquals(Triple(3, 3, 8), f.waits.last())
            advanceUntilIdle()
            assertEquals("COMPLETED", run.await().terminal)
            assertEquals(16_000L, testScheduler.currentTime)
            assertEquals(3, f.executed.size)
            assertFalse(f.detector.isAgentActionInFlight())
        }

    @Test
    fun `pause freezes countdown and steps until explicit confirmation then reports resumed`() =
        runTest {
            val f = Fixture()
            val run = async { f.controller.run() }
            runCurrent()
            assertTrue(f.controller.pause())
            assertEquals(TaskControlFeedback.PAUSE_REQUESTED, f.feedback.last())
            advanceTimeBy(100)
            runCurrent()
            assertEquals(TaskControlFeedback.PAUSED, f.feedback.last())
            assertEquals(PlanStatus.PAUSED, f.statuses.last())
            val waits = f.waits.size
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(waits, f.waits.size)
            assertEquals(1, f.executed.size)
            assertTrue(f.controller.resume(UserResumeConfirmation.forExplicitUserConfirmation(1L)))
            assertEquals(TaskControlFeedback.RESUMED, f.feedback.last())
            advanceUntilIdle()
            assertEquals("COMPLETED", run.await().terminal)
        }

    @Test
    fun `stop gives requested then settled feedback and no next step`() =
        runTest {
            val f = Fixture()
            val run = async { f.controller.run() }
            runCurrent()
            assertTrue(f.controller.stop())
            assertEquals(TaskControlFeedback.STOP_REQUESTED, f.feedback.last())
            advanceUntilIdle()
            assertEquals("STOPPED", run.await().terminal)
            assertEquals(TaskControlFeedback.STOPPED, f.feedback.last())
            assertEquals(1, f.executed.size)
            assertFalse(f.controller.pause())
            assertEquals(TaskControlFeedback.NOTHING_RUNNING, f.feedback.last())
            assertFalse(f.controller.stop())
            assertEquals(TaskControlFeedback.NOTHING_RUNNING, f.feedback.last())
            assertFalse(f.controller.takeover())
            assertEquals(TaskControlFeedback.NOTHING_RUNNING, f.feedback.last())
            assertFalse(f.controller.resume(UserResumeConfirmation.forExplicitUserConfirmation(2L)))
            assertEquals(TaskControlFeedback.NOT_PAUSED, f.feedback.last())
        }

    @Test
    fun `takeover button pauses visibly and repeated pause explains why`() =
        runTest {
            val f = Fixture()
            val run = async { f.controller.run() }
            runCurrent()
            assertTrue(f.controller.takeover())
            advanceTimeBy(100)
            runCurrent()
            assertEquals(LoopState.PAUSED, f.controller.currentState())
            assertEquals(TaskControlFeedback.TAKEOVER, f.feedback.last())
            assertFalse(f.controller.pause())
            assertEquals(TaskControlFeedback.ALREADY_PAUSED, f.feedback.last())
            f.controller.stop()
            advanceUntilIdle()
            assertEquals("STOPPED", run.await().terminal)
        }

    @Test
    fun `accessibility screen touch during wait pauses and presents takeover without a button`() =
        runTest {
            val f = Fixture()
            val run = async { f.controller.run() }
            runCurrent()
            assertTrue(f.detector.onTouch(TakeoverDetector.TouchSource.USER, nowMs = 10L))
            advanceTimeBy(100)
            runCurrent()
            assertEquals(LoopState.PAUSED, f.controller.currentState())
            assertEquals(TaskControlFeedback.TAKEOVER, f.feedback.last())
            advanceTimeBy(20_000)
            runCurrent()
            assertEquals(1, f.executed.size)
            assertTrue(f.detector.isPaused)
            assertTrue(f.controller.resume(UserResumeConfirmation.forExplicitUserConfirmation(11L)))
            assertFalse(f.detector.isPaused)
            advanceUntilIdle()
            assertEquals("COMPLETED", run.await().terminal)
        }

    @Test
    fun `only exact control hitboxes are excluded and other coordinates still latch`() {
        val detector = TakeoverDetector()
        detector.onAgentActionStarted()
        detector.setControlTouchExclusion { x, y -> x == 10 && y == 20 }
        assertTrue(detector.isControlTouch(10, 20))
        assertFalse(detector.isControlTouch(100, 200))
        assertFalse(detector.onTouch(TakeoverDetector.TouchSource.AGENT_GESTURE))
        assertTrue(detector.onTouch(TakeoverDetector.TouchSource.USER))
        detector.resume(UserResumeConfirmation.forExplicitUserConfirmation(1L))
        detector.setControlTouchExclusion(null)
        assertFalse(detector.isControlTouch(10, 20))
        assertTrue(detector.onTouch(TakeoverDetector.TouchSource.USER))
        detector.onAgentActionFinished()
    }
}
