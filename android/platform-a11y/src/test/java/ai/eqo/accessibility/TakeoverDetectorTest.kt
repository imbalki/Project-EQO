package ai.eqo.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TASK-009 takeover-detection rules on the pure state machine: a user touch
 * during an agent action is detected and pauses the loop; agent gestures and
 * idle-time touches never do.
 */
class TakeoverDetectorTest {
    private val detector = TakeoverDetector()

    @Test
    fun userTouchDuringAnAgentActionTriggersTakeoverExactlyOnce() {
        detector.onAgentActionStarted()
        assertTrue(detector.onTouch(TakeoverDetector.TouchSource.USER))
        assertTrue(detector.isPaused)
        assertEquals(1, detector.takeoverCount)

        // Later touches do not re-trigger while paused.
        assertFalse(detector.onTouch(TakeoverDetector.TouchSource.USER))
        assertEquals(1, detector.takeoverCount)
    }

    @Test
    fun userTouchWhileIdleIsNotTakeover() {
        assertFalse(detector.onTouch(TakeoverDetector.TouchSource.USER))
        assertFalse(detector.isPaused)
        assertEquals(0, detector.takeoverCount)
    }

    @Test
    fun theAgentsOwnGestureTouchesAreNotTakeover() {
        detector.onAgentActionStarted()
        assertFalse(detector.onTouch(TakeoverDetector.TouchSource.AGENT_GESTURE))
        assertFalse(detector.onTouch(TakeoverDetector.TouchSource.AGENT_GESTURE))
        assertFalse(detector.isPaused)
    }

    @Test
    fun nestedActionsKeepTheWindowOpenUntilTheLastOneEnds() {
        detector.onAgentActionStarted()
        detector.onAgentActionStarted()
        detector.onAgentActionFinished()
        assertTrue(detector.isAgentActionInFlight())
        detector.onAgentActionFinished()
        assertFalse(detector.isAgentActionInFlight())

        // After the action window closes, a touch is idle interaction again.
        assertFalse(detector.onTouch(TakeoverDetector.TouchSource.USER))
    }

    @Test
    fun resumeHandsControlBackToTheAgent() {
        detector.onAgentActionStarted()
        detector.onTouch(TakeoverDetector.TouchSource.USER)
        detector.onAgentActionFinished()
        assertTrue(detector.isPaused)

        detector.resume(
            ai.eqo.core.agent.UserResumeConfirmation
                .forExplicitUserConfirmation(1L),
        )
        assertFalse(detector.isPaused)

        // A new in-action user touch takes over again.
        detector.onAgentActionStarted()
        assertTrue(detector.onTouch(TakeoverDetector.TouchSource.USER))
        assertEquals(2, detector.takeoverCount)
    }

    @Test
    fun finishedActionBookkeepingCannotGoNegative() {
        detector.onAgentActionFinished()
        detector.onAgentActionFinished()
        assertFalse(detector.isAgentActionInFlight())
    }
}
