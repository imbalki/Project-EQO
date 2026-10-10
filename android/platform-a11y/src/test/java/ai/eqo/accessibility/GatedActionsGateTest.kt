/*
 * EQO (TASK-012, issue #17): security follow-up SF-1 — every action goes
 * through the takeover-gated path (EqoAutomation.runAction). These tests fail
 * against the pre-fix code, where GenericAppAutomator's global/gesture actions
 * and the donor automators called the service directly.
 */
package ai.eqo.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Short name for the fixture's triple (line-length: ktlint + detekt). */
private typealias Fixture = Triple<GatedServiceActions, FakeServiceActionOps, TakeoverDetector>

/**
 * Fake raw service ops: records every invocation and can simulate a user touch
 * landing DURING an action (the takeover-detection bracket).
 */
class FakeServiceActionOps : ServiceActionOps {
    val calls = mutableListOf<String>()

    var inFlightDuringCall: Boolean? = null

    var detector: TakeoverDetector? = null

    /** When set, reports a user touch from inside the action. */
    var touchDuringCall: TakeoverDetector? = null

    private fun record(name: String): Boolean {
        calls += name
        inFlightDuringCall = detector?.isAgentActionInFlight()
        touchDuringCall?.onTouch(TakeoverDetector.TouchSource.USER, nowMs = 1_000L)
        return true
    }

    override fun performImeEnter(): Boolean = record("performImeEnter")

    override fun performGlobalBack(): Boolean = record("performGlobalBack")

    override fun performGlobalHome(): Boolean = record("performGlobalHome")

    override fun performScroll(forward: Boolean): Boolean = record("performScroll")

    override fun clickCoordinates(
        x: Float,
        y: Float,
    ): Boolean = record("clickCoordinates")

    override fun findAndClick(text: String): Boolean = record("findAndClick:$text")

    override fun findAndClickById(viewId: String): Boolean = record("findAndClickById:$viewId")

    override fun findAndType(
        searchText: String,
        content: String,
    ): Boolean = record("findAndType:$searchText")

    override fun findAndTypeById(
        viewId: String,
        content: String,
    ): Boolean = record("findAndTypeById:$viewId")
}

class GatedActionsGateTest {
    @Test
    fun appEditBackRefusesOwnWindowButNavigatesExternalAppInsideGate() {
        val ops = FakeServiceActionOps()
        val detector = TakeoverDetector()
        var root = FakeNode(packageName = "ai.eqo.app")
        val automation =
            EqoAutomation(
                { root },
                { EqoAutomation.ServiceState.AVAILABLE },
                detector,
                ownPackage = "ai.eqo.app",
            )
        val actions = GatedServiceActions(automation, ops)
        assertFalse(actions.pressBack(restrictToApp = true).isSuccess)
        assertTrue(ops.calls.isEmpty())
        root = FakeNode(packageName = "com.google.android.keep")
        assertTrue(actions.pressBack(restrictToApp = true).isSuccess)
        assertEquals(listOf("performGlobalBack"), ops.calls)
    }

    @Test
    fun typedScrollFailureSurvivesTheGatedServiceRoute() {
        val expected = A11yResult.failure(A11yError.ActionRejected("scroll"))
        val ops =
            object : ServiceActionOps by FakeServiceActionOps() {
                override fun performScrollResult(forward: Boolean): A11yResult = expected
            }
        val detector = TakeoverDetector()
        val automation = EqoAutomation({ null }, { EqoAutomation.ServiceState.AVAILABLE }, detector)
        assertEquals(expected, GatedServiceActions(automation, ops).scroll(forward = true))
        assertFalse(detector.isAgentActionInFlight())
    }

    private fun fixture(presetPaused: Boolean = false): Fixture {
        val detector = TakeoverDetector()
        if (presetPaused) {
            detector.onAgentActionStarted()
            detector.onTouch(TakeoverDetector.TouchSource.USER, nowMs = 500L)
            detector.onAgentActionFinished()
            check(detector.isPaused)
        }
        val ops = FakeServiceActionOps().also { it.detector = detector }
        val automation =
            EqoAutomation(
                rootProvider = { null },
                serviceState = { EqoAutomation.ServiceState.AVAILABLE },
                takeover = detector,
            )
        return Triple(GatedServiceActions(automation, ops), ops, detector)
    }

    @Test
    fun `every raw action is refused while the takeover has paused the loop`() {
        val (actions, ops, _) = fixture(presetPaused = true)
        val results =
            listOf(
                actions.pressEnter(),
                actions.pressBack(),
                actions.pressHome(),
                actions.scroll(true),
                actions.clickCoordinates(1f, 2f),
                actions.findAndClick("Send"),
                actions.findAndClickById("send"),
                actions.findAndType("Type a message", "hi"),
                actions.findAndTypeById("entry", "hi"),
            )
        for (result in results) {
            assertTrue("expected TakeoverDetected, got $result", result is A11yResult.Failure)
            assertEquals(A11yError.TakeoverDetected, (result as A11yResult.Failure).error)
        }
        assertEquals("no raw op may run while paused", emptyList<String>(), ops.calls)
    }

    @Test
    fun `every raw action is bracketed by the detector's agent-action window`() {
        val (actions, ops, detector) = fixture()
        assertTrue(actions.pressEnter().isSuccess)
        assertEquals("the action must run inside the bracket", true, ops.inFlightDuringCall)
        assertFalse("the bracket must close after the action", detector.isAgentActionInFlight())
    }

    @Test
    fun `a user touch during a gated action latches the takeover exactly once`() {
        val (actions, ops, detector) = fixture()
        ops.touchDuringCall = detector
        assertTrue(actions.findAndClick("Send").isSuccess)
        assertTrue("user touch during an action must latch", detector.isPaused)
        assertEquals(TakeoverDetector.TakeoverCause.USER, detector.lastTakeoverCause)
        // The next action is refused.
        val next = actions.pressBack()
        assertEquals(A11yError.TakeoverDetected, (next as A11yResult.Failure).error)
    }

    @Test
    fun `service absence surfaces as typed AccessibilityDisabled, never a retry`() {
        val actions =
            GatedServiceActions(
                EqoAutomation({ null }, { EqoAutomation.ServiceState.ACCESSIBILITY_DISABLED }, TakeoverDetector()),
                FakeServiceActionOps(),
            )
        assertEquals(A11yError.AccessibilityDisabled, (actions.pressBack() as A11yResult.Failure).error)
    }
}
