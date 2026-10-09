// Origin: EQO edge-handle task; overlay safety over an underlying foreign active window.
package ai.eqo.accessibility

import ai.eqo.accessibility.handle.HandleWindowGuard
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandleWindowSafetyTest {
    private val guard = HandleWindowGuard.shared

    @After
    fun cleanUp() {
        guard.clear()
    }

    @Test
    fun `coordinate taps at the handle never reach raw ops even when another app is active`() {
        guard.bounds = HandleWindowGuard.Bounds(0, 100, 48, 172)
        val ops = FakeServiceActionOps()
        val actions = GatedServiceActions(automation(), ops)
        assertFalse(actions.clickCoordinates(20f, 130f).isSuccess)
        assertTrue(ops.calls.isEmpty())
        assertTrue(actions.clickCoordinates(90f, 130f).isSuccess)
    }

    @Test
    fun `own overlay panel blocks node clicks gestures typing and observation of underlying apps`() {
        guard.panelOpen = true
        val automation = automation()
        assertFalse(automation.tap("Send").isSuccess)
        assertFalse(automation.tapById("send").isSuccess)
        assertFalse(automation.type("input", "value").isSuccess)
        assertFalse(automation.observe().isSuccess)
        val ops = FakeServiceActionOps()
        assertFalse(GatedServiceActions(automation, ops).clickCoordinates(90f, 130f).isSuccess)
        assertFalse(GatedServiceActions(automation, ops).pressBack().isSuccess)
        assertFalse(GatedServiceActions(automation, ops).pressHome().isSuccess)
        assertTrue(ops.calls.isEmpty())
    }

    @Test
    fun `overlay nodes are also refused by the existing package guard`() {
        val button = FakeNode(text = "Pause", isClickable = true, packageName = "ai.eqo.app")
        val root = FakeNode(packageName = "ai.eqo.app").child(button)
        val automation =
            EqoAutomation(
                { root },
                { EqoAutomation.ServiceState.AVAILABLE },
                TakeoverDetector(),
                { false },
                "ai.eqo.app",
            )
        assertFalse(automation.tap("Pause").isSuccess)
        assertTrue(button.actions.isEmpty())
    }

    @Test
    fun `handle hitboxes compose with task controls and do not swallow background touches`() {
        val detector = TakeoverDetector()
        guard.bounds = HandleWindowGuard.Bounds(0, 100, 48, 172)
        detector.setControlTouchExclusion { x, y -> x == 80 && y == 90 }
        detector.onAgentActionStarted()
        reportProbeTouch(detector, 20, 130)
        reportProbeTouch(detector, 80, 90)
        assertFalse(detector.isPaused)
        detector.setControlTouchExclusion(null)
        reportProbeTouch(detector, 20, 130)
        assertFalse(detector.isPaused)
        reportProbeTouch(detector, 80, 90)
        assertTrue(detector.isPaused)
    }

    @Test
    fun `handle touch never releases an already latched takeover and removal restores detection`() {
        val detector = TakeoverDetector()
        detector.onAgentActionStarted()
        detector.onTouch(TakeoverDetector.TouchSource.USER, 10000)
        guard.bounds = HandleWindowGuard.Bounds(0, 100, 48, 172)
        reportProbeTouch(detector, 20, 130)
        assertTrue(detector.isPaused)
        guard.clear()
        assertFalse(detector.isControlTouch(20, 130))
    }

    private fun reportProbeTouch(
        detector: TakeoverDetector,
        x: Int,
        y: Int,
    ) {
        if (!detector.isControlTouch(x, y)) detector.onTouch(TakeoverDetector.TouchSource.USER, 10000)
    }

    private fun automation(): EqoAutomation =
        EqoAutomation(
            { FakeNode(packageName = "other.app").child(FakeNode(text = "Send", isClickable = true)) },
            { EqoAutomation.ServiceState.AVAILABLE },
            TakeoverDetector(),
            { false },
            "ai.eqo.app",
        )
}
