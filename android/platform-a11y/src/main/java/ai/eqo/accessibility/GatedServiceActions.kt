/*
 * EQO (TASK-012, issue #17): the takeover-gated wrapper every service-level
 * action goes through (security follow-up SF-1).
 */
package ai.eqo.accessibility

/**
 * The single gated path for every action that reaches the service's raw
 * [ServiceActionOps] surface: global actions (back/home), IME enter, scroll,
 * gesture taps and the donor automators' findAndClick/findAndType calls.
 *
 * Every method runs inside [EqoAutomation.runAction], which
 *  - refuses while the takeover detector has paused the loop
 *    ([A11yError.TakeoverDetected]) — the "paused" loop can no longer act on
 *    screen through these paths, and
 *  - brackets the call with the detector's agent-action window so a user touch
 *    during the action is detected.
 *
 * Returns the typed [A11yResult] contract; nothing here retries.
 */
class GatedServiceActions(
    private val automation: EqoAutomation,
    private val ops: ServiceActionOps,
) {
    fun pressEnter(): A11yResult =
        automation.runAction {
            if (ops.performImeEnter()) {
                A11yResult.success("ime enter")
            } else {
                A11yResult.failure(A11yError.ActionRejected("enter"))
            }
        }

    fun pressBack(): A11yResult = globalAction("back") { ops.performGlobalBack() }

    fun pressHome(): A11yResult = globalAction("home") { ops.performGlobalHome() }

    fun scroll(forward: Boolean): A11yResult =
        automation.runAction {
            if (ops.performScroll(forward)) {
                A11yResult.success("scrolled ${if (forward) "forward" else "backward"}")
            } else {
                A11yResult.failure(A11yError.NodeNotFound("scrollable node"))
            }
        }

    fun clickCoordinates(
        x: Float,
        y: Float,
    ): A11yResult =
        automation.runAction {
            if (ops.clickCoordinates(x, y)) {
                A11yResult.success("gesture tap ($x,$y)")
            } else {
                A11yResult.failure(A11yError.ActionRejected("gesture tap"))
            }
        }

    fun findAndClick(text: String): A11yResult =
        automation.runAction {
            if (ops.findAndClick(text)) {
                A11yResult.success("clicked $text")
            } else {
                A11yResult.failure(A11yError.NodeNotFound(text))
            }
        }

    fun findAndClickById(viewId: String): A11yResult =
        automation.runAction {
            if (ops.findAndClickById(viewId)) {
                A11yResult.success("clicked id $viewId")
            } else {
                A11yResult.failure(A11yError.NodeNotFound(viewId))
            }
        }

    fun findAndType(
        searchText: String,
        content: String,
    ): A11yResult =
        automation.runAction {
            if (ops.findAndType(searchText, content)) {
                A11yResult.success("typed into $searchText")
            } else {
                A11yResult.failure(A11yError.NodeNotFound(searchText))
            }
        }

    fun findAndTypeById(
        viewId: String,
        content: String,
    ): A11yResult =
        automation.runAction {
            if (ops.findAndTypeById(viewId, content)) {
                A11yResult.success("typed into id $viewId")
            } else {
                A11yResult.failure(A11yError.NodeNotFound(viewId))
            }
        }

    private fun globalAction(
        name: String,
        action: () -> Boolean,
    ): A11yResult =
        automation.runAction {
            if (action()) {
                A11yResult.success(name)
            } else {
                A11yResult.failure(A11yError.ActionRejected(name))
            }
        }
}
