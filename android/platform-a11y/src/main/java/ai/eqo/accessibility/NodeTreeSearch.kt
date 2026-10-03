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
            val label = node.text ?: node.contentDescription
            label?.contains(target, ignoreCase = true) == true
        }

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
