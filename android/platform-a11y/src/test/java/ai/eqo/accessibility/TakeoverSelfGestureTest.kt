/*
 * EQO (TASK-012, issue #17): security note N-3 — a spurious takeover latched by
 * EQO's own stroke must be recognisable and must not kill the task silently.
 */
package ai.eqo.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TakeoverSelfGestureTest {
    private fun latchingDetector(): TakeoverDetector {
        val detector = TakeoverDetector()
        detector.onAgentActionStarted()
        return detector
    }

    @Test
    fun `a touch right after our own gesture is a suspected self-gesture takeover`() {
        val detector = latchingDetector()
        // Our own dispatchGesture stroke finished at t=1000 (its touch events
        // arrive late - the documented mis-attribution window).
        detector.onSelfGestureFinished(1_000L)
        assertTrue(detector.onTouch(TakeoverDetector.TouchSource.USER, nowMs = 1_100L))
        assertTrue("the takeover still latches (safe)", detector.isPaused)
        assertEquals(TakeoverDetector.TakeoverCause.SELF_GESTURE_SUSPECTED, detector.lastTakeoverCause)
    }

    @Test
    fun `a touch far after our own gesture is a real user takeover`() {
        val detector = latchingDetector()
        detector.onSelfGestureFinished(1_000L)
        assertTrue(detector.onTouch(TakeoverDetector.TouchSource.USER, nowMs = 5_000L))
        assertTrue(detector.isPaused)
        assertEquals(TakeoverDetector.TakeoverCause.USER, detector.lastTakeoverCause)
    }

    @Test
    fun `a touch while our gesture is in flight is attributed to the gesture`() {
        val detector = TakeoverDetector()
        detector.onAgentActionStarted()
        detector.onAgentActionStarted() // gesture dispatch in flight
        assertEquals(
            false,
            detector.onTouch(TakeoverDetector.TouchSource.AGENT_GESTURE, nowMs = 1_000L),
        )
        assertEquals(false, detector.isPaused)
        detector.onAgentActionFinished()
        detector.onAgentActionFinished()
    }

    @Test
    fun `the attribution window is bounded and documented`() {
        assertTrue(TakeoverDetector.SELF_GESTURE_ATTRIBUTION_WINDOW_MS in 1..2_000)
    }
}
