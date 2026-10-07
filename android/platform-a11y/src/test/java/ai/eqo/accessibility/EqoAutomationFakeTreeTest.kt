package ai.eqo.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TASK-009 acceptance criteria on fake node trees: observe, tap, scroll, text
 * input through [EqoAutomation]; user touch during an agent action pauses the
 * loop; accessibility disabled mid-task gives a typed error with no silent
 * retry.
 */
class EqoAutomationFakeTreeTest {
    private val takeover = TakeoverDetector()
    private var serviceState = EqoAutomation.ServiceState.AVAILABLE
    private var rootProviderCalls = 0

    private fun automation(root: FakeNode?): EqoAutomation =
        EqoAutomation(
            rootProvider = {
                rootProviderCalls++
                root
            },
            serviceState = { serviceState },
            takeover = takeover,
        )

    /** A small "test app" screen: headline, decoy, target row, text field, list. */
    private fun probeScreen(): Pair<FakeNode, Screen> {
        val root = FakeNode()
        val headline = FakeNode(text = "Probe screen")
        val decoy = FakeNode(text = "Send later", isClickable = true)
        val target = FakeNode(text = "Send")
        val row = FakeNode(isClickable = true)
        row.child(target)
        val field = FakeNode(text = "Message", isEditable = true)
        val list = FakeNode(isScrollable = true)
        root
            .child(headline)
            .child(decoy)
            .child(row)
            .child(field)
            .child(list)
        return root to Screen(root, decoy, target, row, field, list)
    }

    private data class Screen(
        val root: FakeNode,
        val decoy: FakeNode,
        val target: FakeNode,
        val row: FakeNode,
        val field: FakeNode,
        val list: FakeNode,
    )

    // ── observe ───────────────────────────────────────────────────────────

    @Test
    fun observeReturnsEveryNodeTextDepthFirst() {
        val (root, _) = probeScreen()
        val result = automation(root).observe()
        val detail = (result as A11yResult.Success).detail
        assertTrue(detail.contains("Probe screen"))
        assertTrue(detail.contains("Send later"))
        assertTrue(detail.contains("Send"))
        assertTrue(detail.contains("Message"))
    }

    @Test
    fun observeWithoutAWindowIsATypedNodeNotFound() {
        val result = automation(null).observe()
        assertTrue(result is A11yResult.Failure)
        assertEquals(A11yError.NodeNotFound("active window"), (result as A11yResult.Failure).error)
    }

    // ── tap ───────────────────────────────────────────────────────────────

    @Test
    fun tapMatchesSubstringsInTreeOrderWithNoExactMatchPreference() {
        // Contract inherited from AccessibilityNodeTraversal.findAndClick:
        // findAccessibilityNodeInfosByText is a case-insensitive SUBSTRING match
        // and the first result in tree order wins. "Send later" sits before the
        // exact "Send" here, so it is the one tapped.
        val (root, screen) = probeScreen()
        val result = automation(root).tap("Send")
        assertTrue(result.isSuccess)
        assertEquals(1, screen.decoy.clickCount)
        assertEquals(0, screen.target.clickCount)
        assertEquals(0, screen.row.clickCount)
    }

    @Test
    fun tapOnNonClickableLeafBubblesToTheClickableAncestorRow() {
        val target = FakeNode(text = "Send")
        val row = FakeNode(isClickable = true).child(target)
        val root = FakeNode().child(row)
        val result = automation(root).tap("Send")
        assertTrue(result.isSuccess)
        assertEquals(1, row.clickCount)
        assertEquals("the leaf itself is not clickable", 0, target.clickCount)
    }

    @Test
    fun tapWithNoMatchIsTypedNodeNotFoundAndClicksNothing() {
        val (root, screen) = probeScreen()
        val result = automation(root).tap("No such label")
        assertEquals(A11yError.NodeNotFound("No such label"), (result as A11yResult.Failure).error)
        assertEquals(0, screen.row.clickCount)
        assertEquals(0, screen.decoy.clickCount)
        assertEquals(0, screen.target.clickCount)
    }

    @Test
    fun tapByIdTargetsTheMatchingResourceId() {
        val target = FakeNode(viewIdResourceName = "ai.eqo.app:id/target", isClickable = true)
        val decoy = FakeNode(viewIdResourceName = "ai.eqo.app:id/decoy", isClickable = true)
        val root = FakeNode().child(target).child(decoy)
        val result = automation(root).tapById("ai.eqo.app:id/target")
        assertTrue(result.isSuccess)
        assertEquals(1, target.clickCount)
        assertEquals(0, decoy.clickCount)
    }

    @Test
    fun tapRejectedByThePlatformIsATypedActionRejected() {
        val target = FakeNode(text = "Send", isClickable = true)
        target.rejectActions = true
        val root = FakeNode().child(target)
        val result = automation(root).tap("Send")
        assertEquals(A11yError.ActionRejected("Send"), (result as A11yResult.Failure).error)
    }

    // ── text input ────────────────────────────────────────────────────────

    @Test
    fun typeFillsTheFirstEditableMatch() {
        val (root, screen) = probeScreen()
        val result = automation(root).type("Message", "hello EQO")
        assertTrue(result.isSuccess)
        assertEquals("hello EQO", screen.field.typedValue)
    }

    @Test
    fun typeWithoutAnEditableMatchIsTypedNodeNotFound() {
        val (root, screen) = probeScreen()
        // A second input makes the unmatched hint ambiguous, so EQO must not guess.
        root.child(FakeNode(text = "Subject", isEditable = true))
        val result = automation(root).type("Absent field", "hello")
        assertEquals(A11yError.NodeNotFound("Absent field"), (result as A11yResult.Failure).error)
        assertNull(screen.field.typedValue)
    }

    @Test
    fun typeByIdFillsTheMatchingField() {
        val field = FakeNode(viewIdResourceName = "ai.eqo.app:id/compose", isEditable = true)
        val root = FakeNode().child(field)
        val result = automation(root).typeById("ai.eqo.app:id/compose", "typed by id")
        assertTrue(result.isSuccess)
        assertEquals("typed by id", field.typedValue)
    }

    // ── scroll ────────────────────────────────────────────────────────────

    @Test
    fun scrollScrollsTheFirstScrollableNode() {
        val (root, screen) = probeScreen()
        val result = automation(root).scroll(forward = true)
        assertTrue(result.isSuccess)
        assertEquals(1, screen.list.scrollCount)
    }

    @Test
    fun scrollWithoutAScrollableNodeIsTypedNodeNotFound() {
        val root = FakeNode(text = "Static screen")
        val result = automation(root).scroll(forward = true)
        assertEquals(A11yError.NodeNotFound("scrollable node"), (result as A11yResult.Failure).error)
    }

    @Test
    fun scrollContinuesIntoARejectedScrollableAncestor() {
        val child = FakeNode(isScrollable = true)
        val root = FakeNode(isScrollable = true).apply { rejectActions = true }.child(child)
        assertTrue(automation(root).scroll(forward = true).isSuccess)
        assertEquals(0, root.scrollCount)
        assertEquals(1, child.scrollCount)
    }

    @Test
    fun scrollContinuesPastARejectedContainerToALaterSibling() {
        val first = FakeNode(isScrollable = true).apply { rejectActions = true }
        val second = FakeNode(isScrollable = true)
        val third = FakeNode(isScrollable = true)
        val root = FakeNode().child(first).child(second).child(third)
        assertTrue(automation(root).scroll(forward = false).isSuccess)
        assertEquals(0, first.scrollCount)
        assertEquals(1, second.scrollCount)
        assertEquals("must stop after the first accepted action", 0, third.scrollCount)
    }

    @Test
    fun scrollDistinguishesMissingRootFromRejectedContainers() {
        val missing = automation(null).scroll(forward = true) as A11yResult.Failure
        assertEquals(A11yError.NodeNotFound("active window"), missing.error)
        val rejected = FakeNode(isScrollable = true).apply { rejectActions = true }
        val result = automation(rejected).scroll(forward = true) as A11yResult.Failure
        assertEquals(A11yError.ActionRejected("scroll"), result.error)
    }

    // ── takeover: user touch during an agent action pauses the loop ───────

    @Test
    fun userTouchDuringAnAgentActionPausesTheLoopAndLaterActionsRefuseToRun() {
        val (root, screen) = probeScreen()
        val automation = automation(root)

        // The user touches the screen while a tap action is in flight. The
        // probe node stands in for the service's touch probe (ACTION_OUTSIDE /
        // TYPE_TOUCH_INTERACTION_START feed the same detector in production).
        val actingTarget = FakeNode(text = "Act", isClickable = true)
        val probe = TouchObservingNode(takeover, actingTarget)
        val actingRoot = FakeNode().child(probe)
        val duringAction =
            EqoAutomation(
                rootProvider = { actingRoot },
                serviceState = { serviceState },
                takeover = takeover,
            )
        val result = duringAction.tap("Act")
        assertTrue(result.isSuccess)
        assertTrue("takeover must fire on the in-action user touch", probe.userTouchTriggeredTakeover)
        assertTrue(takeover.isPaused)

        // The loop is paused: no further action runs, nothing else is clicked.
        val afterPause = automation.tap("Send")
        assertEquals(A11yError.TakeoverDetected, (afterPause as A11yResult.Failure).error)
        assertEquals(0, screen.decoy.clickCount)
        assertEquals(0, screen.row.clickCount)
    }

    @Test
    fun userTouchWhileTheAgentIsIdleIsFreeInteraction() {
        val (root, screen) = probeScreen()
        // Touch while nothing is in flight: not a takeover.
        val triggered = takeover.onTouch(TakeoverDetector.TouchSource.USER)
        assertFalse(triggered)
        assertFalse(takeover.isPaused)
        assertTrue(automation(root).tap("Send").isSuccess)
        assertEquals(1, screen.decoy.clickCount)
    }

    @Test
    fun afterResumeTheLoopRunsAgain() {
        val (root, screen) = probeScreen()
        takeover.onAgentActionStarted()
        takeover.onTouch(TakeoverDetector.TouchSource.USER)
        takeover.onAgentActionFinished()
        assertTrue(takeover.isPaused)
        takeover.resume(
            ai.eqo.core.agent.UserResumeConfirmation
                .forExplicitUserConfirmation(1L),
        )
        assertFalse(takeover.isPaused)
        assertTrue(automation(root).tap("Send").isSuccess)
        assertEquals(1, screen.decoy.clickCount)
    }

    // ── accessibility disabled mid-task: typed error, no silent retry ─────

    @Test
    fun disabledServiceGivesATypedErrorOnTheFirstAttemptAndNeverRetries() {
        val (root, screen) = probeScreen()
        serviceState = EqoAutomation.ServiceState.ACCESSIBILITY_DISABLED
        val automation = automation(root)

        val result = automation.tap("Send")

        assertEquals(A11yError.AccessibilityDisabled, (result as A11yResult.Failure).error)
        assertEquals("the node tree must not be read when the service is gone", 0, rootProviderCalls)
        assertEquals("no silent retry, no side effects", 0, screen.target.clickCount)
    }

    @Test
    fun everyTypedActionReportsTheDisabledServiceAsTheSameTypedError() {
        val (root, _) = probeScreen()
        serviceState = EqoAutomation.ServiceState.ACCESSIBILITY_DISABLED
        val automation = automation(root)
        val results =
            listOf(
                automation.observe(),
                automation.tap("Send"),
                automation.tapById("ai.eqo.app:id/target"),
                automation.type("Message", "x"),
                automation.typeById("ai.eqo.app:id/compose", "x"),
                automation.scroll(forward = true),
            )
        results.forEach { result ->
            assertEquals(A11yError.AccessibilityDisabled, (result as A11yResult.Failure).error)
        }
    }

    /**
     * A node whose "platform" reports a user touch mid-action (standing in for
     * the service's touch probe) and whose tap runs through a whole agent action.
     */
    private class TouchObservingNode(
        private val takeover: TakeoverDetector,
        private val inner: FakeNode,
    ) : A11yNode by inner {
        var userTouchTriggeredTakeover = false

        override fun click(): Boolean {
            // Mid-action, the user touches the screen.
            userTouchTriggeredTakeover = takeover.onTouch(TakeoverDetector.TouchSource.USER)
            return inner.click()
        }
    }
}
