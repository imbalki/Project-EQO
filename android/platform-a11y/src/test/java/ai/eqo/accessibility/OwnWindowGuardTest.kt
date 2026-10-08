/*
 * EQO: security review of PR #88 (2026-10-07). EQO's own AccessibilityService must never act inside EQO's own
 * windows, so a planned step can neither press "Allow" in the helper consent dialog nor approve its own plan.
 */
package ai.eqo.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnWindowGuardTest {
    private val takeover = TakeoverDetector()
    private val state = EqoAutomation.ServiceState.AVAILABLE

    private fun automation(root: FakeNode) = EqoAutomation({ root }, { state }, takeover, { false }, "ai.eqo.app")

    @Test
    fun `a tap inside EQO's own window is refused and nothing is clicked`() {
        val allow = FakeNode(text = "Allow", isClickable = true, packageName = "ai.eqo.app")
        val root = FakeNode(packageName = "ai.eqo.app").child(allow)
        val result = automation(root).tap("Allow")
        assertTrue(result is A11yResult.Failure)
        assertTrue(allow.actions.isEmpty())
    }

    @Test
    fun `typing and scrolling inside EQO's own window are refused`() {
        val field = FakeNode(className = "android.widget.EditText", packageName = "ai.eqo.app")
        val root = FakeNode(packageName = "ai.eqo.app").child(field)
        assertTrue(automation(root).type("anything", "x") is A11yResult.Failure)
        assertTrue(automation(root).scroll(true) is A11yResult.Failure)
        assertEquals(null, field.typedValue)
    }

    @Test
    fun `the same tap works in another app's window`() {
        val send = FakeNode(text = "Allow", isClickable = true, packageName = "com.other.app")
        val root = FakeNode(packageName = "com.other.app").child(send)
        assertTrue(automation(root).tap("Allow").isSuccess)
        assertTrue(send.actions.contains("click"))
    }

    @Test
    fun `gated gestures and node clicks are refused in EQO's own window`() {
        val root = FakeNode(packageName = "ai.eqo.app")
        val ops = FakeServiceActionOps()
        val actions = GatedServiceActions(automation(root), ops)
        assertTrue(actions.clickCoordinates(10f, 10f) is A11yResult.Failure)
        assertTrue(actions.findAndClick("Allow") is A11yResult.Failure)
        assertTrue(actions.findAndClickById("allow_button") is A11yResult.Failure)
        assertTrue(actions.pressEnter() is A11yResult.Failure)
        assertTrue("no raw operation may run: ${ops.calls}", ops.calls.isEmpty())
    }

    @Test
    fun `without a configured own package nothing changes`() {
        val key = FakeNode(text = "Send", isClickable = true, packageName = "ai.eqo.app")
        val root = FakeNode(packageName = "ai.eqo.app").child(key)
        assertTrue(EqoAutomation({ root }, { state }, takeover).tap("Send").isSuccess)
    }

    @Test
    fun `screen capture refuses EQO password and secure windows`() {
        assertTrue(automation(FakeNode(packageName = "ai.eqo.app")).screenCaptureGate() is A11yResult.Failure)
        val password = FakeNode(packageName = "com.other.app").child(FakeNode(isPassword = true))
        assertEquals(
            A11yError.ActionRejected("screenshot with a password field"),
            (automation(password).screenCaptureGate() as A11yResult.Failure).error,
        )
        val secure = EqoAutomation({ FakeNode() }, { state }, takeover, { true }, "ai.eqo.app")
        assertEquals(A11yError.SecureWindow, (secure.screenCaptureGate() as A11yResult.Failure).error)
    }

    @Test
    fun `privacy inspection never creates extra touch grace`() {
        val automator = automation(FakeNode(packageName = "com.other.app"))
        takeover.onAgentActionStarted()
        try {
            assertTrue(automator.screenCaptureGate().isSuccess)
            assertTrue(takeover.onTouch(TakeoverDetector.TouchSource.USER, 10L))
            assertEquals(A11yError.TakeoverDetected, (automator.screenCaptureGate() as A11yResult.Failure).error)
        } finally {
            takeover.onAgentActionFinished()
        }
    }
}
