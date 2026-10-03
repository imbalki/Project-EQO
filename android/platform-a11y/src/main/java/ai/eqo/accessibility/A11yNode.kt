/*
 * EQO (TASK-009): minimal node surface used by the EQO action layer, so the
 * observe/tap/scroll/type logic can be unit-tested against fake node trees
 * without a bound accessibility service.
 */
package ai.eqo.accessibility

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Minimal read/act surface over one node of an accessibility tree.
 *
 * Production wraps the real [AccessibilityNodeInfo] tree with
 * [AccessibilityNodeAdapter]; tests hand in fake trees instead. Every function
 * takes its root as a parameter (same contract as
 * [AccessibilityNodeTraversal]), so nothing here needs a bound service.
 */
interface A11yNode {
    val text: CharSequence?

    val contentDescription: CharSequence?

    /** `android:findViewById`-style resource id name, e.g. `ai.eqo.app:id/target`. */
    val viewIdResourceName: String?

    val className: CharSequence?

    val isClickable: Boolean

    val isEditable: Boolean

    val isScrollable: Boolean

    val childCount: Int

    /** Parent node, or null at the root. */
    val parent: A11yNode?

    fun childAt(index: Int): A11yNode?

    fun click(): Boolean

    fun setText(value: CharSequence): Boolean

    fun scroll(forward: Boolean): Boolean
}

/**
 * Adapter over the real [AccessibilityNodeInfo] tree produced by
 * `rootInActiveWindow` / `UiAutomation.rootInActiveWindow`.
 */
class AccessibilityNodeAdapter(
    private val node: AccessibilityNodeInfo,
) : A11yNode {
    override val text: CharSequence? get() = node.text

    override val contentDescription: CharSequence? get() = node.contentDescription

    override val viewIdResourceName: String? get() = node.viewIdResourceName

    override val className: CharSequence? get() = node.className

    override val isClickable: Boolean get() = node.isClickable

    override val isEditable: Boolean get() = node.isEditable

    override val isScrollable: Boolean get() = node.isScrollable

    override val childCount: Int get() = node.childCount

    override val parent: A11yNode? get() = node.parent?.let { AccessibilityNodeAdapter(it) }

    override fun childAt(index: Int): A11yNode? = node.getChild(index)?.let { AccessibilityNodeAdapter(it) }

    override fun click(): Boolean = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

    override fun setText(value: CharSequence): Boolean {
        val args =
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override fun scroll(forward: Boolean): Boolean {
        val action =
            if (forward) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
        return node.performAction(action)
    }
}
