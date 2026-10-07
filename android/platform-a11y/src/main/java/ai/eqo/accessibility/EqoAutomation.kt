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
 * @param isSecureWindow reports whether the active window is secure
 *   (FLAG_SECURE). Screen text is never read from a secure window (SF-2).
 *
 * Kept together deliberately: the typed operation surface and its gated helpers.
 */
@Suppress("TooManyFunctions")
class EqoAutomation(
    private val rootProvider: () -> A11yNode?,
    private val serviceState: () -> ServiceState,
    private val takeover: TakeoverDetector,
    private val isSecureWindow: () -> Boolean = { false },
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
            when {
                // TASK-012 (SF-2): nothing is read from a secure window.
                isSecureWindow() -> A11yResult.failure(A11yError.SecureWindow)
                root == null -> A11yResult.failure(A11yError.NodeNotFound("active window"))
                else -> A11yResult.success(NodeTreeSearch.screenText(root))
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

    /**
     * True when a focused, non-password text input currently holds [content]. Used to confirm typing landed,
     * because tapping a search bar often opens a separate search screen whose field takes the text.
     */
    fun focusedInputHolds(content: String): Boolean =
        rootProvider()?.let { root ->
            NodeTreeSearch.findFirst(root) { node ->
                NodeTreeSearch.isTextInput(node) &&
                    node.isFocused &&
                    !node.isPassword &&
                    node.text?.toString()?.contains(content, ignoreCase = true) == true
            }
        } != null

    /** True when some text input currently has focus. */
    fun hasFocusedInput(): Boolean =
        rootProvider()?.let { root ->
            NodeTreeSearch.findFirst(root) { node -> NodeTreeSearch.isTextInput(node) && node.isFocused }
        } != null

    /** Types [content] into the first editable node with the resource id [viewId]. */
    fun typeById(
        viewId: String,
        content: String,
    ): A11yResult =
        runAction {
            typeTarget(viewId, content, byViewId = true)
        }

    /** Scrolls the first container that accepts the requested direction. */
    fun scroll(forward: Boolean): A11yResult = runAction { NodeTreeSearch.scroll(rootProvider(), forward) }

    /**
     * THE single takeover-gated action path (TASK-012 SF-1). Every action EQO
     * performs — including the global/gesture actions routed through
     * [GatedServiceActions] — must run inside this gate: it refuses while the
     * takeover detector has paused the loop and brackets the call with the
     * detector's agent-action window.
     */
    fun runAction(block: () -> A11yResult): A11yResult =
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
                    takeover.onAgentNodeActionFinished(android.os.SystemClock.elapsedRealtime())
                    takeover.onAgentActionFinished()
                }
            }
        }

    private fun tapTarget(
        target: String,
        byViewId: Boolean,
    ): A11yResult {
        // The first match that can actually be tapped: a label or a previous result shown earlier on the
        // screen (for example "12x3" before the "1" key) must not hide the real button.
        val clickable =
            rootProvider()?.let { root ->
                NodeTreeSearch
                    .findFirst(root) { node ->
                        NodeTreeSearch.matches(node, target, byViewId) &&
                            NodeTreeSearch.clickableSelfOrAncestor(node) != null
                    }?.let { NodeTreeSearch.clickableSelfOrAncestor(it) }
            }
        val result =
            when {
                clickable == null -> A11yResult.failure(A11yError.NodeNotFound(target))
                clickable.click() -> A11yResult.success("clicked $target")
                else -> A11yResult.failure(A11yError.ActionRejected(target))
            }
        return typingResult(clickable, result, if (byViewId) "CLICK_ID" else "CLICK_TEXT")
    }

    private fun currentGateFailure(): A11yResult? =
        when {
            takeover.isPaused -> A11yResult.failure(A11yError.TakeoverDetected)
            serviceState() == ServiceState.ACCESSIBILITY_DISABLED -> A11yResult.failure(A11yError.AccessibilityDisabled)
            else -> null
        }

    private companion object {
        private val VIEW_ID_LOG_SAFE = Regex("[A-Za-z0-9_.:/]{1,100}")
    }

    private fun typingResult(
        node: A11yNode?,
        result: A11yResult,
        action: String = "TYPE_TEXT",
    ): A11yResult {
        val code =
            when (result) {
                is A11yResult.Success -> "action_accepted"
                is A11yResult.Failure -> result.error.javaClass.simpleName
            }
        // Never log the target, field text, hints, clipboard or typed contents.
        val nodeClass =
            when (node?.className?.toString()) {
                "android.widget.EditText" -> "EditText"
                "android.widget.AutoCompleteTextView" -> "AutoCompleteTextView"
                "android.widget.MultiAutoCompleteTextView" -> "MultiAutoCompleteTextView"
                null -> "none"
                else -> "other"
            }
        // Structural widget id only (it names the app and widget, never user content).
        val viewId =
            node
                ?.viewIdResourceName
                ?.takeIf { VIEW_ID_LOG_SAFE.matches(it) }
                ?.let { " view=$it" }
                .orEmpty()
        val flags = node?.let { " clickable=${it.isClickable} editable=${it.isEditable}" }.orEmpty()
        android.util.Log.i(
            "EqoRun",
            "action=$action node_found=${node != null} node_class=$nodeClass result=$code$viewId$flags",
        )
        return result
    }

    /** The input matching [target]; with an unmatched text hint, the only input on screen, never a guess. */
    private fun resolveTypeTarget(
        target: String,
        byViewId: Boolean,
    ): A11yNode? {
        val root = rootProvider() ?: return null
        val focusedOnly = !byViewId && target.lowercase() in setOf("focused", "current")
        val matched =
            NodeTreeSearch.findFirst(root) { node ->
                NodeTreeSearch.isTextInput(node) &&
                    if (focusedOnly) node.isFocused else NodeTreeSearch.matches(node, target, byViewId)
            }
        return matched ?: if (byViewId || focusedOnly) null else NodeTreeSearch.soleTextInput(root)
    }

    @Suppress("ReturnCount") // live takeover/service checks must stop between mutations
    private fun typeTarget(
        target: String,
        content: String,
        byViewId: Boolean,
    ): A11yResult {
        val editable = resolveTypeTarget(target, byViewId)
        if (editable == null) return typingResult(null, A11yResult.failure(A11yError.NodeNotFound(target)))
        // A rejected focus/click does not imply SET_TEXT is unsupported. Try both
        // preparation actions, but re-check the live gate before EVERY mutation.
        currentGateFailure()?.let { return typingResult(editable, it) }
        editable.focus()
        currentGateFailure()?.let { return typingResult(editable, it) }
        if (editable.isClickable) editable.click()
        currentGateFailure()?.let { return typingResult(editable, it) }
        if (editable.setText(content)) return typingResult(editable, A11yResult.success("typed into $target"))
        currentGateFailure()?.let { return typingResult(editable, it) }
        val result =
            if (editable.paste(content) { currentGateFailure() == null }) {
                A11yResult.success("typed into $target")
            } else {
                currentGateFailure() ?: A11yResult.failure(A11yError.ActionRejected(target))
            }
        return typingResult(editable, result)
    }
}
