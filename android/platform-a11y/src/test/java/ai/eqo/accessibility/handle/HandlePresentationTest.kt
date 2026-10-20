// Origin: EQO-authored fake-clock handle tests; no platform actions or content.
package ai.eqo.accessibility.handle

import ai.eqo.platform.R
import android.view.View
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
    fun `panel height fits the measured rows and caps at seventy percent of the screen`() {
        assertEquals(200, panelHeight(200, 1000))
        assertEquals(700, panelHeight(900, 1000))
        assertEquals(1120, panelHeight(2000, 1600))
        assertEquals(0, panelHeight(-1, -1))
    }

    @Test
    fun `panel rows chunk every entry by columns with a short trailing row`() {
        assertEquals(listOf(0 until 2, 2 until 4, 4 until 5), panelRows(5, 2))
        assertEquals(emptyList<IntRange>(), panelRows(0, 2))
        assertEquals(listOf(0 until 1, 1 until 2), panelRows(2, 1))
        assertEquals(listOf(0 until 1, 1 until 2), panelRows(2, 0))
    }

    @Test
    fun `panel actions map every shortcut to an icon a spoken name and a stable id`() {
        val shortcuts = listOf(PanelShortcutSpec("ask_eqo", 7, 8), PanelShortcutSpec("made_up", 0, 9))
        val specs = panelActionSpecs(shortcuts, hideAppAvailable = true)
        assertEquals(listOf("close", "hide_now", "ask_eqo", "made_up", "hide_app"), specs.map { it.key })
        assertTrue(specs.all { it.iconRes != 0 })
        assertTrue(specs.all { it.labelRes != 0 })
        assertEquals(7, specs[2].iconRes)
        assertEquals(R.drawable.ic_handle_generic, specs[3].iconRes)
        assertEquals(R.id.eqo_handle_action_close, specs[0].viewId)
        assertEquals(R.id.eqo_handle_action_ask_eqo, specs[2].viewId)
        assertEquals(View.NO_ID, specs[3].viewId)
        assertEquals(
            listOf("close", "hide_now", "ask_eqo", "made_up"),
            panelActionSpecs(shortcuts, hideAppAvailable = false).map { it.key },
        )
    }

    @Test
    fun `panel width fits two spaced icon cells with their padding`() {
        assertEquals(128, panelWidthDp(2))
        assertEquals(72, panelWidthDp(1))
        assertEquals(72, panelWidthDp(0))
    }

    @Test
    fun `tooltip chip sits beside the icon and clamps inside the panel`() {
        val panel = PanelRect(0, 0, 200, 200)
        assertEquals(TooltipPlacement(88, 50), tooltipPlacement(PanelRect(40, 50, 80, 70), panel, 40, 20, 8))
        // No room on the right: the chip moves to the icon's other side.
        assertEquals(TooltipPlacement(112, 90), tooltipPlacement(PanelRect(160, 90, 200, 110), panel, 40, 20, 8))
        // Clamped to the bottom edge, and to the panel when the chip is wider than it.
        assertEquals(TooltipPlacement(18, 180), tooltipPlacement(PanelRect(2, 190, 10, 198), panel, 40, 20, 8))
        assertEquals(TooltipPlacement(0, 100), tooltipPlacement(PanelRect(100, 100, 140, 120), panel, 240, 20, 8))
    }

    @Test
    fun `name chip shows on hover or hold and hides when neither holds`() {
        assertTrue(tooltipVisible(hovering = true, holding = false))
        assertTrue(tooltipVisible(hovering = false, holding = true))
        assertFalse(tooltipVisible(hovering = false, holding = false))
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
