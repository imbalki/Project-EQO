/*
 * EQO: a touch signal caused by EQO's own node action (click, set text) must not pause the run,
 * but a real touch after the grace period still must.
 */
package ai.eqo.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TakeoverAgentActionGraceTest {
    private fun detector(): TakeoverDetector = TakeoverDetector().also { it.onAgentActionStarted() }

    @Test
    fun `a touch right after our own node action is attributed to EQO`() {
        val detector = detector()
        detector.onAgentNodeActionFinished(1_000L)
        assertFalse(detector.onTouch(TakeoverDetector.TouchSource.USER, nowMs = 1_050L))
        assertFalse(detector.isPaused)
    }

    @Test
    fun `a touch after the grace period still pauses the run`() {
        val detector = detector()
        detector.onAgentNodeActionFinished(1_000L)
        val after = 1_000L + TakeoverDetector.AGENT_ACTION_TOUCH_GRACE_MS + 1
        assertTrue(detector.onTouch(TakeoverDetector.TouchSource.USER, nowMs = after))
        assertTrue(detector.isPaused)
    }

    @Test
    fun `without a recent node action every user touch still pauses the run`() {
        val detector = detector()
        assertTrue(detector.onTouch(TakeoverDetector.TouchSource.USER, nowMs = 5_000L))
    }

    @Test
    fun `the grace is spent by one touch so a second touch pauses the run`() {
        val detector = detector()
        detector.onAgentNodeActionFinished(1_000L)
        assertFalse(detector.onTouch(TakeoverDetector.TouchSource.USER, nowMs = 1_050L))
        assertTrue(detector.onTouch(TakeoverDetector.TouchSource.USER, nowMs = 1_100L))
        assertTrue(detector.isPaused)
    }

    @Test
    fun `continuous node actions cannot mask touches beyond the per-run cap`() {
        val detector = detector()
        var now = 1_000L
        repeat(TakeoverDetector.MAX_GRACED_TOUCHES_PER_RUN) {
            detector.onAgentNodeActionFinished(now)
            assertFalse(detector.onTouch(TakeoverDetector.TouchSource.USER, nowMs = now + 10))
            now += 100
        }
        detector.onAgentNodeActionFinished(now)
        assertTrue(detector.onTouch(TakeoverDetector.TouchSource.USER, nowMs = now + 10))
        assertTrue(detector.isPaused)
    }

    @Test
    fun `a new run restarts only the grace budget and never clears a latched takeover`() {
        val detector = detector()
        detector.onAgentNodeActionFinished(1_000L)
        assertFalse(detector.onTouch(TakeoverDetector.TouchSource.USER, nowMs = 1_010L))
        detector.onAgentNodeActionFinished(2_000L)
        assertTrue(detector.onTouch(TakeoverDetector.TouchSource.USER, nowMs = 2_700L))
        assertTrue(detector.isPaused)
        detector.startNewRun()
        assertTrue("a latched takeover stays latched", detector.isPaused)
    }
}
