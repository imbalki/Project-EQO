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
    /** EQO's own package: its own windows (consent dialogs, approvals) are never automation targets. */
    private val ownPackage: String? = null,
) {
    // Structural path and product labels only: never retain native nodes between actions.
    private var shoppingSelection: ShoppingSelection? = null

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

    /** Exact content-description only, scoped to the active app. No substring or text-field fallback. */
    fun tapContentDescription(
        description: String,
        packageName: String,
    ): A11yResult =
        runAction {
            val root = rootProvider()
            val target =
                root?.takeIf { it.packageName?.toString() == packageName && !isSecureWindow() }?.let {
                    NodeTreeSearch
                        .findFirst(it) { node ->
                            !node.isPassword &&
                                node.contentDescription
                                    ?.toString()
                                    ?.trim()
                                    .equals(description, ignoreCase = true) &&
                                NodeTreeSearch.clickableSelfOrAncestor(node) != null
                        }?.let(NodeTreeSearch::clickableSelfOrAncestor)
                }
            when {
                isSecureWindow() -> A11yResult.failure(A11yError.SecureWindow)
                target == null -> A11yResult.failure(A11yError.NodeNotFound(description))
                target.click() -> A11yResult.success("clicked $description")
                else -> A11yResult.failure(A11yError.ActionRejected(description))
            }
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

    /**
     * Gate for taking a screenshot (the capture itself is done by the service). Refused while the takeover
     * detector has paused the loop, when the service is not bound, when the active window is secure
     * (FLAG_SECURE: banking, authenticator, password screens) and when EQO's own window is in front.
     */
    fun screenshotGate(): A11yResult =
        runAction {
            when {
                isSecureWindow() -> A11yResult.failure(A11yError.SecureWindow)
                ownWindowBlocked("screenshot") != null ->
                    A11yResult.failure(A11yError.ActionRejected("screenshot of EQO's own window"))
                else -> A11yResult.success("A screenshot may be taken.")
            }
        }

    /** Scrolls the first container that accepts the requested direction. */
    fun scroll(forward: Boolean): A11yResult =
        runAction {
            ownWindowBlocked("scroll") ?: NodeTreeSearch.scroll(rootProvider(), forward)
        }

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
            ownPackage != null && ai.eqo.accessibility.handle.HandleWindowGuard.shared.panelOpen ->
                A11yResult.failure(A11yError.ActionRejected("EQO edge handle panel"))
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
        ownWindowBlocked(target)?.let { return it }
        // Best tappable match: an exact label beats a partial one ("Voice call" beats "Call" appearing in
        // "Video call"), and a button or result row beats a text field that merely contains the text.
        val root = rootProvider()
        val increase = !byViewId && target == "exact:+,exact:Increase quantity"
        val selection = shoppingSelection
        if (!increase) shoppingSelection = null
        val clickable =
            root?.let {
                if (increase) selection?.increase(it, ::safeSearchInput) else bestTappable(it, target, byViewId)
            }
        val addedProduct =
            root
                ?.takeIf { !byViewId && target.startsWith("exact:Add") }
                ?.let { ShoppingSelection.capture(it, target, ::safeSearchInput) }
        val result =
            when {
                clickable == null -> A11yResult.failure(A11yError.NodeNotFound(target))
                clickable.click() -> {
                    if (addedProduct != null) shoppingSelection = addedProduct
                    A11yResult.success("clicked $target")
                }
                else -> A11yResult.failure(A11yError.ActionRejected(target))
            }
        return typingResult(clickable, result, if (byViewId) "CLICK_ID" else "CLICK_TEXT")
    }

    /**
     * EQO's own windows are never automation targets: the plan approval and the helper consent dialog must be
     * answered by the owner's hand, never by a step the model planned. Reported as not-found so that cold-start
     * retries keep waiting for the real target app to come to the front.
     */
    fun ownWindowBlocked(target: String): A11yResult? =
        if (ownPackage != null &&
            (
                ai.eqo.accessibility.handle.HandleWindowGuard.shared.panelOpen ||
                    rootProvider()?.packageName?.toString() == ownPackage
            )
        ) {
            A11yResult.failure(A11yError.NodeNotFound(target))
        } else {
            null
        }

    @Suppress("ReturnCount") // Strict cart controls use a separate first-product path; ordinary matching is unchanged.
    private fun bestTappable(
        root: A11yNode,
        target: String,
        byViewId: Boolean,
    ): A11yNode? {
        val alternatives =
            if (byViewId) listOf(target) else target.split(',').map(String::trim).filter(String::isNotEmpty)
        if (!byViewId && alternatives.isNotEmpty() && alternatives.all { it.startsWith("exact:") }) {
            return firstExactTappable(root, alternatives.map { it.removePrefix("exact:") })
        }
        val exactPasses = if (byViewId) listOf(true) else listOf(true, false)
        for (exact in exactPasses) {
            for (label in alternatives) {
                if (!exact && label.startsWith("exact:")) continue
                val hit = findTappable(root, label, byViewId, exact)
                if (hit != null) return hit
            }
        }
        return null
    }

    /** Strict shopping alternatives preserve product tree order, not label preference. */
    private fun firstExactTappable(
        root: A11yNode,
        labels: List<String>,
    ): A11yNode? {
        val hit =
            NodeTreeSearch.findFirst(root) { node ->
                !node.isPassword &&
                    safeSearchInput(node) &&
                    labels.any { NodeTreeSearch.matchesExactly(node, it) } &&
                    NodeTreeSearch.clickableSelfOrAncestor(node)?.let(::safeSearchInput) == true
            }
        return hit?.let(NodeTreeSearch::clickableSelfOrAncestor)
    }

    @Suppress("CyclomaticComplexMethod") // Ordered exact/ID/partial passes plus payment-safe clickable ancestors.
    private fun findTappable(
        root: A11yNode,
        label: String,
        byViewId: Boolean,
        exact: Boolean,
    ): A11yNode? {
        for (allowInput in listOf(false, true)) {
            val hit =
                NodeTreeSearch.findFirst(root) { node ->
                    val matches =
                        when {
                            byViewId || label.startsWith("id:") -> NodeTreeSearch.matches(node, label, true)
                            label.startsWith("exact:") ->
                                NodeTreeSearch.matchesExactly(node, label.removePrefix("exact:"))
                            exact ->
                                NodeTreeSearch.matchesExactly(node, label) || NodeTreeSearch.matches(node, label, true)
                            else -> NodeTreeSearch.matches(node, label, false)
                        }
                    !node.isPassword &&
                        matches &&
                        (allowInput || !NodeTreeSearch.isTextInput(node)) &&
                        NodeTreeSearch.clickableSelfOrAncestor(node)?.let {
                            !label.startsWith("exact:") || safeSearchInput(it)
                        } == true
                }
            if (hit != null) return NodeTreeSearch.clickableSelfOrAncestor(hit)
        }
        return null
    }

    private fun currentGateFailure(): A11yResult? =
        when {
            takeover.isPaused -> A11yResult.failure(A11yError.TakeoverDetected)
            serviceState() == ServiceState.ACCESSIBILITY_DISABLED -> A11yResult.failure(A11yError.AccessibilityDisabled)
            else -> null
        }

    private companion object {
        private val VIEW_ID_LOG_SAFE = Regex("[A-Za-z0-9_.:/]{1,100}")

        // Observed Zepto structural IDs, not payment cards. Only these two UUID-suffixed IDs are exempt.
        private val ZEPTO_PRODUCT_ID =
            Regex(
                "com\\.zeptoconsumerapp:id/product-card-(?:container|add-button)" +
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            )
        private val PAYMENT_INPUT =
            Regex(
                "pay|upi|card|address|pincode|postcode|postal|confirm|checkout|subscribe|buy[ _-]*now|" +
                    "place[ _-]*order|order[ _-]*now|(?<![a-z0-9])cod(?![a-z0-9])",
                RegexOption.IGNORE_CASE,
            )
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

    private fun safeSearchInput(node: A11yNode): Boolean = generateSequence(node) { it.parent }.all(::safeNode)

    private fun safeNode(node: A11yNode): Boolean {
        val id = node.viewIdResourceName.orEmpty()
        val productId = node.packageName?.toString() == "com.zeptoconsumerapp" && ZEPTO_PRODUCT_ID.matches(id)
        return listOf(node.text, node.contentDescription, node.hintText)
            .none { PAYMENT_INPUT.containsMatchIn(it?.toString().orEmpty()) } &&
            (productId || !PAYMENT_INPUT.containsMatchIn(id))
    }

    /** The input matching [target]; strict search targets never fall back to another input. */
    private fun resolveTypeTarget(
        target: String,
        byViewId: Boolean,
    ): A11yNode? {
        val root = rootProvider() ?: return null
        val focusedOnly = !byViewId && target.lowercase() in setOf("focused", "current")
        val searchOnly = target.startsWith("search:")
        val label = target.removePrefix("search:")
        val strict = listOf(byViewId, focusedOnly, searchOnly, target.startsWith("id:")).any { it }
        val matched =
            NodeTreeSearch.findFirst(root) { node ->
                NodeTreeSearch.isTextInput(node) &&
                    (!searchOnly || safeSearchInput(node)) &&
                    if (focusedOnly) node.isFocused else NodeTreeSearch.matches(node, label, byViewId)
            }
        return matched ?: if (strict) {
            null
        } else {
            NodeTreeSearch.soleTextInput(root)
        }
    }

    @Suppress("ReturnCount") // live takeover/service checks must stop between mutations
    private fun typeTarget(
        target: String,
        content: String,
        byViewId: Boolean,
    ): A11yResult {
        ownWindowBlocked(target)?.let { return typingResult(null, it) }
        shoppingSelection = null
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

/** Conservative product binding: ambiguous grouping, renamed/reordered rows and missing controls stop the run. */
private data class ShoppingSelection(
    val path: List<Int>,
    val label: String,
    val packageName: String?,
) {
    fun increase(
        root: A11yNode,
        safe: (A11yNode) -> Boolean,
    ): A11yNode? {
        val row = path.fold(root as A11yNode?) { node, index -> node?.childAt(index) }
        val entries = row?.let(::nodes).orEmpty()
        val matches = entries.map { it.second }
        val controls = entries.filter { isControl(it.second, INCREASE) }
        val sameProduct =
            root.packageName?.toString() == packageName &&
                productLabels(matches) == listOf(label) &&
                uniqueLabel(root, label)
        return controls
            .singleOrNull()
            ?.takeIf { sameProduct && safe(it.second) }
            ?.let { (controlPath, _) -> row?.let { scopedClickable(it, controlPath) } }
            ?.takeIf(safe)
    }

    companion object {
        private val ADD = listOf("Add", "Add to cart")
        private val INCREASE = listOf("+", "Increase quantity")
        private val CONTROLS = ADD + INCREASE + listOf("-", "Decrease quantity", "Cart", "View cart", "Go to cart")

        fun capture(
            root: A11yNode,
            target: String,
            safe: (A11yNode) -> Boolean,
        ): ShoppingSelection? {
            val labels = target.split(',').map { it.removePrefix("exact:") }
            val hit =
                nodes(root).firstOrNull { (_, node) ->
                    isControl(node, labels) &&
                        safe(node) &&
                        NodeTreeSearch.clickableSelfOrAncestor(node)?.let(safe) == true
                }
            // Never bind to the whole window: a sibling product's + is not an acceptable fallback.
            val ancestors =
                hit
                    ?.first
                    .orEmpty()
                    .indices
                    .reversed()
                    .filter { it > 0 }
            return ancestors.firstNotNullOfOrNull { depth ->
                val path = requireNotNull(hit).first.take(depth)
                val row = path.fold(root as A11yNode?) { node, index -> node?.childAt(index) }
                val entries = row?.let(::nodes).orEmpty().map { it.second }
                val name = productLabels(entries).singleOrNull()
                val addPath = entriesForControl(row, ADD).singleOrNull()?.first
                val boundAdd = addPath?.let { controlPath -> row?.let { scopedClickable(it, controlPath) } }
                val unambiguous =
                    listOf(
                        name?.let { uniqueLabel(root, it) } == true,
                        entries.count { isControl(it, ADD) } == 1,
                        entries.count { isControl(it, INCREASE) } <= 1,
                        boundAdd != null,
                    ).all { it }
                if (unambiguous) {
                    ShoppingSelection(path, requireNotNull(name), root.packageName?.toString())
                } else {
                    null
                }
            }
        }

        private fun isControl(
            node: A11yNode,
            labels: List<String>,
        ): Boolean = !node.isPassword && labels.any { NodeTreeSearch.matchesExactly(node, it) }

        private fun entriesForControl(
            row: A11yNode?,
            labels: List<String>,
        ): List<Pair<List<Int>, A11yNode>> = row?.let(::nodes).orEmpty().filter { isControl(it.second, labels) }

        private fun scopedClickable(
            row: A11yNode,
            path: List<Int>,
        ): A11yNode? =
            (path.size downTo 0).firstNotNullOfOrNull { depth ->
                path
                    .take(depth)
                    .fold(row as A11yNode?) { node, index -> node?.childAt(index) }
                    ?.takeIf { it.isClickable }
            }

        private fun productLabels(entries: List<A11yNode>): List<String> =
            entries
                .flatMap { node ->
                    if (node.isEditable || node.isPassword || isControl(node, CONTROLS)) {
                        emptyList()
                    } else {
                        listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                            .map(String::trim)
                            .filter { it.any(Char::isLetter) }
                    }
                }.distinct()

        private fun uniqueLabel(
            root: A11yNode,
            label: String,
        ): Boolean = nodes(root).count { (_, node) -> NodeTreeSearch.matchesExactly(node, label) } == 1

        private fun nodes(
            root: A11yNode,
            path: List<Int> = emptyList(),
        ): List<Pair<List<Int>, A11yNode>> =
            if (root.isPassword) {
                emptyList()
            } else {
                listOf(path to root) +
                    (0 until root.childCount).flatMap { index ->
                        root.childAt(index)?.let { nodes(it, path + index) }.orEmpty()
                    }
            }
    }
}
