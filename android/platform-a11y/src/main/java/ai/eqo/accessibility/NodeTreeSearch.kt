/*
 * EQO (TASK-009): pure node-tree search helpers shared by the EQO action layer
 * and its fake-tree unit tests.
 */
package ai.eqo.accessibility

/** Depth-first search and matching over an [A11yNode] tree. */
internal object NodeTreeSearch {
    /** Depth-first search for the first node satisfying [predicate]. */
    fun findFirst(
        node: A11yNode,
        predicate: (A11yNode) -> Boolean,
    ): A11yNode? {
        if (predicate(node)) {
            return node
        }
        var index = 0
        var found: A11yNode? = null
        while (found == null && index < node.childCount) {
            val child = node.childAt(index)
            found = child?.let { findFirst(it, predicate) }
            index++
        }
        return found
    }

    /**
     * The only text input on screen, or null when there are none or several. Used when the planner's
     * hint text does not match the real field label: with exactly one input there is no ambiguity.
     */
    fun soleTextInput(root: A11yNode): A11yNode? {
        var count = 0
        var first: A11yNode? = null
        findFirst(root) { node ->
            if (isTextInput(node)) {
                count++
                if (first == null) first = node
            }
            false
        }
        return first.takeIf { count == 1 }
    }

    /** Tries later containers when an earlier scrollable node rejects this direction. */
    fun scroll(
        root: A11yNode?,
        forward: Boolean,
    ): A11yResult {
        var foundScrollable = false
        val applied =
            root?.let {
                findFirst(it) { node ->
                    if (node.isScrollable) {
                        foundScrollable = true
                        node.scroll(forward)
                    } else {
                        false
                    }
                }
            }
        val result =
            when {
                root == null -> A11yResult.failure(A11yError.NodeNotFound("active window"))
                applied != null -> A11yResult.success("scroll action accepted")
                foundScrollable -> A11yResult.failure(A11yError.ActionRejected("scroll"))
                else -> A11yResult.failure(A11yError.NodeNotFound("scrollable node"))
            }
        val code =
            when {
                root == null -> "no_active_root"
                applied != null -> "action_accepted"
                foundScrollable -> "action_rejected"
                else -> "no_scrollable_node"
            }
        android.util.Log.i("EqoRun", "scroll=$code")
        return result
    }

    /** Text/contentDescription of every node in the tree, depth-first. */
    fun screenText(root: A11yNode): String {
        val sb = StringBuilder()
        collectText(root, sb)
        return sb.toString()
    }

    private fun collectText(
        node: A11yNode,
        sb: StringBuilder,
    ) {
        // TASK-012 (SF-2): never read password/masked fields (or their subtree).
        if (node.isPassword) return
        val nodeText = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()
        if (!nodeText.isNullOrEmpty()) {
            sb.append(nodeText).append('\n')
        } else if (!contentDesc.isNullOrEmpty()) {
            sb.append(contentDesc).append('\n')
        }
        for (i in 0 until node.childCount) {
            val child = node.childAt(i) ?: continue
            collectText(child, sb)
        }
    }

    /** Substring/label match by text (or resource id), like findAndClick's contract. */
    fun matches(
        node: A11yNode,
        target: String,
        byViewId: Boolean,
    ): Boolean =
        if (byViewId) {
            node.viewIdResourceName?.endsWith("/$target") == true || node.viewIdResourceName == target
        } else {
            listOf(node.text, node.contentDescription, node.hintText).any { label ->
                label?.contains(target, ignoreCase = true) == true
            }
        }

    /** A label that equals [target] exactly (ignoring case), so "Call" does not also pick "Video call". */
    fun matchesExactly(
        node: A11yNode,
        target: String,
    ): Boolean =
        listOf(node.text, node.contentDescription, node.hintText).any { label ->
            label?.toString()?.trim().equals(target.trim(), ignoreCase = true)
        }

    /** Some native app fields expose EditText class but omit the editable flag. Password fields are never inputs. */
    fun isTextInput(node: A11yNode): Boolean =
        !node.isPassword &&
            (
                node.isEditable ||
                    node.className?.toString() in
                    setOf(
                        "android.widget.EditText",
                        "android.widget.AutoCompleteTextView",
                        "android.widget.MultiAutoCompleteTextView",
                    )
            )

    /**
     * The matched node itself if clickable, else its nearest clickable ancestor
     * (same contract as AccessibilityNodeTraversal.findAndClick).
     */
    fun clickableSelfOrAncestor(target: A11yNode): A11yNode? {
        var node: A11yNode? = target
        while (node != null) {
            if (node.isClickable) {
                return node
            }
            node = node.parent
        }
        return null
    }
}
