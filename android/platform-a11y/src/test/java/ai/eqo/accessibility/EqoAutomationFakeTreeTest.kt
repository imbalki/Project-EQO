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

    @Test
    fun keepSpeedDialAndEditorUseIdsWithoutLabelsOrSearchFallback() {
        val fab =
            FakeNode(
                contentDescription = "Create a note",
                viewIdResourceName = "com.google.android.keep:id/speed_dial_create_close_button",
                isClickable = true,
            )
        val items =
            listOf("new_list_button", "new_drawing_button", "new_photo_note", "new_note_button")
                .map { FakeNode(viewIdResourceName = "com.google.android.keep:id/$it", isClickable = true) }
        val title = FakeNode(viewIdResourceName = "com.google.android.keep:id/editable_title", isEditable = true)
        val body = FakeNode(viewIdResourceName = "com.google.android.keep:id/edit_note_text", isEditable = true)
        val toolbar =
            FakeNode(
                contentDescription = "Search Keep",
                viewIdResourceName = "com.google.android.keep:id/toolbar",
                isEditable = true,
            )
        var root = FakeNode().child(toolbar).child(fab)
        val runner =
            EqoAutomation(
                rootProvider = { root },
                serviceState = { serviceState },
                takeover = takeover,
                ownPackage = "ai.eqo.app",
            )
        assertTrue(runner.tap("Create a note").isSuccess)
        root = FakeNode().child(toolbar)
        items.forEach(root::child)
        assertTrue(runner.tap("id:new_note_button").isSuccess)
        assertEquals(listOf(0, 0, 0, 1), items.map { it.clickCount })
        root = FakeNode().child(toolbar).child(title).child(body)
        assertTrue(runner.type("id:editable_title", "Test").isSuccess)
        assertTrue(runner.type("id:edit_note_text", "Example body").isSuccess)
        assertEquals("Test", title.typedValue)
        assertEquals("Example body", body.typedValue)
        assertNull(toolbar.typedValue)
        root = FakeNode().child(toolbar)
        assertFalse(runner.type("id:editable_title", "Never search").isSuccess)
        assertNull(toolbar.typedValue)
        assertFalse(runner.tap("Create a note").isSuccess)
        root = FakeNode(packageName = "ai.eqo.app").child(title).child(items.last())
        assertFalse(runner.type("id:editable_title", "Never approve").isSuccess)
        assertFalse(runner.tap("id:new_note_button").isSuccess)
        assertEquals("Test", title.typedValue)
        assertEquals(1, items.last().clickCount)
    }

    @Test
    fun explicitIdNeverMatchesLabelDecoysPartialIdsOrPasswords() {
        val label = FakeNode(text = "id:new_note_button", isClickable = true)
        val partial = FakeNode(viewIdResourceName = "keep:id/other_new_note_button", isClickable = true)
        val password = FakeNode(viewIdResourceName = "keep:id/new_note_button", isPassword = true, isClickable = true)
        val runner = automation(FakeNode().child(label).child(partial).child(password))
        assertFalse(runner.tap("id:new_note_button").isSuccess)
        assertFalse(runner.tap("id:").isSuccess)
        assertEquals(listOf(0, 0, 0), listOf(label, partial, password).map { it.clickCount })
    }

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

    @Test
    fun keepEntryAlternativesMatchTextBarAndContentDescriptionFab() {
        listOf(
            FakeNode(text = "Take a note", isClickable = true),
            FakeNode(contentDescription = "New text note", isClickable = true),
        ).forEach { entry ->
            assertTrue(automation(FakeNode().child(entry)).tap("Take a note, New text note").isSuccess)
            assertEquals(1, entry.clickCount)
        }
    }

    @Test
    fun keepTitleAndNoteFieldsAcceptOnlyTheirRequestedContent() {
        val title = FakeNode(hintText = "Title", isEditable = true)
        val body = FakeNode(hintText = "Note", isEditable = true)
        val app = automation(FakeNode().child(title).child(body))
        assertTrue(app.type("Title", "Test").isSuccess)
        assertTrue(app.type("Note", "Example body").isSuccess)
        assertEquals("Test", title.typedValue)
        assertEquals("Example body", body.typedValue)
    }

    @Test
    fun exactAlternativeBeatsEarlierPartialAndUsesDeclaredOrder() {
        val partial = FakeNode(text = "Take a note later", isClickable = true)
        val fab = FakeNode(contentDescription = "New text note", isClickable = true)
        val bar = FakeNode(text = "Take a note", isClickable = true)
        val root = FakeNode().child(partial).child(fab)
        assertTrue(automation(root).tap("Take a note,New text note").isSuccess)
        assertEquals(0, partial.clickCount)
        assertEquals(1, fab.clickCount)
        root.child(bar)
        assertTrue(automation(root).tap("Take a note,New text note").isSuccess)
        assertEquals(1, bar.clickCount)
        assertEquals(1, fab.clickCount)
    }

    @Test
    fun alternativesMatchIdSuffixAndIgnoreEmptyLabelsAndPasswords() {
        val secret = FakeNode(text = "Take a note", isPassword = true, isClickable = true)
        val entry = FakeNode(viewIdResourceName = "com.google.android.keep:id/new_note", isClickable = true)
        val root = FakeNode().child(secret).child(entry)
        assertTrue(automation(root).tap(" ,Take a note,new_note, ").isSuccess)
        assertEquals(0, secret.clickCount)
        assertEquals(1, entry.clickCount)
        assertFalse(automation(root).tap(" , , ").isSuccess)
    }

    @Test
    fun alternativesFallBackToPartialButNeverTryAnotherControlAfterRejection() {
        val partial = FakeNode(contentDescription = "Create new text note", isClickable = true)
        assertTrue(automation(FakeNode().child(partial)).tap("Take a note,New text note").isSuccess)
        val first = FakeNode(text = "Take a note", isClickable = true).apply { rejectActions = true }
        val second = FakeNode(contentDescription = "New text note", isClickable = true)
        val result = automation(FakeNode().child(first).child(second)).tap("Take a note,New text note")
        assertTrue((result as A11yResult.Failure).error is A11yError.ActionRejected)
        assertEquals(listOf("click"), first.actions)
        assertEquals(0, second.clickCount)
    }

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
    fun tapPrefersAnExactLabelOverAnEarlierPartialMatch() {
        // Changed on purpose (2026-10-07): the inherited "first substring match wins" rule tapped "Video call"
        // when the plan said "Call". An exact label now wins; "Send later" is only a fallback for "Send".
        val (root, screen) = probeScreen()
        val result = automation(root).tap("Send")
        assertTrue(result.isSuccess)
        assertEquals(0, screen.decoy.clickCount)
        assertEquals(1, screen.row.clickCount)
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
        assertEquals(1, screen.row.clickCount)
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
        assertEquals(1, screen.row.clickCount)
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
