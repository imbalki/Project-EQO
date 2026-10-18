// Origin: EQO-authored fake-clock handle tests; no platform actions or content.
package ai.eqo.accessibility.handle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandlePresentationTest {
    @Test
    fun `placement avoids keyboard and focused input or hides when no space remains`() {
        assertEquals(400, fitHandleY(900, 100, 500, null, null))
        assertEquals(200, fitHandleY(350, 100, 800, 300, 600))
        assertEquals(100, fitHandleY(0, 100, 800, 0, 100))
        assertEquals(null, fitHandleY(0, 100, 500, 0, 500))
        assertEquals(null, fitHandleY(0, 600, 500, null, null))
    }

    @Test
    fun `panel collapses after three seconds without interaction and interaction resets deadline`() {
        var now = 0L
        val state = HandlePresentation { now }
        state.open()
        now = 2_999L
        assertFalse(state.tick())
        state.interact()
        now = 3_000L
        assertFalse(state.tick())
        now = 5_999L
        assertTrue(state.tick())
        assertFalse(state.expanded)
    }

    @Test
    fun `success lasts two seconds and foreground change clears transient presentation`() {
        var now = 0L
        val state = HandlePresentation { now }
        state.inserted()
        now = 1_999L
        assertTrue(state.resultVisible())
        now = 2_000L
        assertFalse(state.resultVisible())
        state.open()
        state.hide("synthetic.app")
        assertTrue(state.hidden("synthetic.app"))
        assertFalse(state.hidden("other.app"))
        assertFalse(state.expanded)
        state.foregroundChanged()
        assertFalse(state.hidden("synthetic.app"))
        assertFalse(state.resultVisible())
    }

    @Test
    fun `outside dismissal does not reopen on a stale timer`() {
        var now = 0L
        val state = HandlePresentation { now }
        state.open()
        state.collapse()
        now = 3_000L
        assertFalse(state.tick())
        assertFalse(state.expanded)
    }
}
