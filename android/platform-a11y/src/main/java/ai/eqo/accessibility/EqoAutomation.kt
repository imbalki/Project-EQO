/*
 * EQO (TASK-009): the typed observe/tap/scroll/type action layer that runs over
 * exactly one accessibility service. No silent retry: a disabled accessibility
 * service surfaces as a typed A11yError.AccessibilityDisabled on the first
 * attempt and stops there.
 */
package ai.eqo.accessibility

/**
 * Observe, tap, scroll and type over one accessibility node tree.
 *
 * Every action:
 *  1. refuses to run while the takeover detector has paused the loop
 *     ([A11yError.TakeoverDetected]),
 *  2. reports a missing service as the typed [A11yError.AccessibilityDisabled]
 *     on the first attempt — never a silent retry,
 *  3. brackets the call with the takeover detector's agent-action window so a
 *     user touch during the action is detected,
 *  4. returns a typed [A11yResult].
 *
 * @param rootProvider the active window's root node, adapted to [A11yNode].
 * @param serviceState reports whether the EQO accessibility service is bound.
 * @param takeover the shared takeover detector.
 */
class EqoAutomation(
    private val rootProvider: () -> A11yNode?,
    private val serviceState: () -> ServiceState,
    private val takeover: TakeoverDetector,
) {
    enum class ServiceState {
        AVAILABLE,

        /** EQO's accessibility service is not bound: disabled or revoked. */
        ACCESSIBILITY_DISABLED,
    }

    /** Text/contentDescription of every node in the active window, depth-first. */
    fun observe(): A11yResult =
        runAction {
            val root = rootProvider()
            if (root == null) {
                A11yResult.failure(A11yError.NodeNotFound("active window"))
            } else {
                A11yResult.success(NodeTreeSearch.screenText(root))
            }
        }

    /** Taps the first clickable node whose text matches [text] (substring, tree order). */
    fun tap(text: String): A11yResult =
        runAction {
            tapTarget(text, byViewId = false)
        }

    /** Taps the first clickable node with the resource id [viewId]. */
    fun tapById(viewId: String): A11yResult =
        runAction {
            tapTarget(viewId, byViewId = true)
        }

    /** Types [content] into the first editable node whose text matches [searchText]. */
    fun type(
        searchText: String,
        content: String,
    ): A11yResult =
        runAction {
            typeTarget(searchText, content, byViewId = false)
        }

    /** Types [content] into the first editable node with the resource id [viewId]. */
    fun typeById(
        viewId: String,
        content: String,
    ): A11yResult =
        runAction {
            typeTarget(viewId, content, byViewId = true)
        }

    /** Scrolls the first scrollable node in the tree. */
    fun scroll(forward: Boolean): A11yResult =
        runAction {
            val root = rootProvider()
            val scrollable = root?.let { NodeTreeSearch.findFirst(it) { node -> node.isScrollable } }
            when {
                root == null -> A11yResult.failure(A11yError.NodeNotFound("active window"))
                scrollable == null -> A11yResult.failure(A11yError.NodeNotFound("scrollable node"))
                scrollable.scroll(forward) -> A11yResult.success("scrolled ${if (forward) "forward" else "backward"}")
                else -> A11yResult.failure(A11yError.ActionRejected("scroll"))
            }
        }

    private fun runAction(block: () -> A11yResult): A11yResult =
        when {
            takeover.isPaused -> A11yResult.failure(A11yError.TakeoverDetected)
            serviceState() == ServiceState.ACCESSIBILITY_DISABLED ->
                // Typed error on the first attempt. Callers must not retry.
                A11yResult.failure(A11yError.AccessibilityDisabled)
            else -> {
                takeover.onAgentActionStarted()
                try {
                    block()
                } finally {
                    takeover.onAgentActionFinished()
                }
            }
        }

    private fun tapTarget(
        target: String,
        byViewId: Boolean,
    ): A11yResult {
        val match =
            rootProvider()?.let { root ->
                NodeTreeSearch.findFirst(root) { node -> NodeTreeSearch.matches(node, target, byViewId) }
            }
        val clickable = match?.let { NodeTreeSearch.clickableSelfOrAncestor(it) }
        return when {
            clickable == null -> A11yResult.failure(A11yError.NodeNotFound(target))
            clickable.click() -> A11yResult.success("clicked $target")
            else -> A11yResult.failure(A11yError.ActionRejected(target))
        }
    }

    private fun typeTarget(
        target: String,
        content: String,
        byViewId: Boolean,
    ): A11yResult {
        val editable =
            rootProvider()?.let { root ->
                NodeTreeSearch.findFirst(root) { node ->
                    node.isEditable && NodeTreeSearch.matches(node, target, byViewId)
                }
            }
        return when {
            editable == null -> A11yResult.failure(A11yError.NodeNotFound(target))
            editable.setText(content) -> A11yResult.success("typed into $target")
            else -> A11yResult.failure(A11yError.ActionRejected(target))
        }
    }
}
