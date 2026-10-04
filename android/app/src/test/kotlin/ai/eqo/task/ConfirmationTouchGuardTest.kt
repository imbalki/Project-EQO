package ai.eqo.task

import android.view.MotionEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmationTouchGuardTest {
    @Test
    fun `clean and unrelated flags are accepted`() {
        assertTrue(acceptsConfirmationTouch(0))
        assertTrue(acceptsConfirmationTouch(MotionEvent.FLAG_CANCELED))
    }

    @Test
    fun `fully obscured touches are rejected`() {
        assertFalse(acceptsConfirmationTouch(MotionEvent.FLAG_WINDOW_IS_OBSCURED))
    }

    @Test
    fun `partially obscured touches are rejected`() {
        assertFalse(acceptsConfirmationTouch(MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED))
    }

    @Test
    fun `both obscuration bits with unrelated flags are rejected`() {
        assertFalse(
            acceptsConfirmationTouch(
                MotionEvent.FLAG_WINDOW_IS_OBSCURED or
                    MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED or MotionEvent.FLAG_CANCELED,
            ),
        )
    }

    @Test
    fun `obscured down cannot become a clean up confirmation`() {
        val guard = ConfirmationTouchGuard()
        assertFalse(guard.accepts(MotionEvent.FLAG_WINDOW_IS_OBSCURED, isDown = true))
        assertFalse(guard.accepts(0, isDown = false))
        assertTrue(guard.accepts(0, isDown = true))
        assertTrue(guard.accepts(0, isDown = false))
    }

    @Test
    fun `obscured move poisons even a clean down and up`() {
        val guard = ConfirmationTouchGuard()
        assertTrue(guard.accepts(0, isDown = true))
        assertFalse(guard.accepts(MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED, isDown = false))
        assertFalse(guard.accepts(0, isDown = false))
    }

    @Test
    fun `obscured up never confirms and the next clean gesture works`() {
        val guard = ConfirmationTouchGuard()
        assertTrue(guard.accepts(0, isDown = true))
        assertFalse(guard.accepts(MotionEvent.FLAG_WINDOW_IS_OBSCURED, isDown = false))
        assertTrue(guard.accepts(0, isDown = true))
        assertTrue(guard.accepts(0, isDown = false))
    }
}
