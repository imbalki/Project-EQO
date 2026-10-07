/*
 * EQO (TASK-009): minimal node surface used by the EQO action layer, so the
 * observe/tap/scroll/type logic can be unit-tested against fake node trees
 * without a bound accessibility service.
 */
package ai.eqo.accessibility

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.PersistableBundle
import android.view.accessibility.AccessibilityNodeInfo
import java.util.UUID

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

    val hintText: CharSequence? get() = null

    /** Package that owns this node's window (null when unknown, for example in fakes). */
    val packageName: CharSequence? get() = null

    /**
     * TASK-012 (SF-2): true for password/masked fields. Their text and their
     * subtree are never read into screen text.
     */
    val isPassword: Boolean

    /** `android:findViewById`-style resource id name, e.g. `ai.eqo.app:id/target`. */
    val viewIdResourceName: String?

    val className: CharSequence?

    val isClickable: Boolean

    val isEditable: Boolean

    val isFocused: Boolean get() = false

    val isScrollable: Boolean

    val childCount: Int

    /** Parent node, or null at the root. */
    val parent: A11yNode?

    fun childAt(index: Int): A11yNode?

    fun click(): Boolean

    fun focus(): Boolean = false

    fun setText(value: CharSequence): Boolean

    /** Replaces the field via clipboard, only when a safe selection can be made. */
    fun paste(
        value: CharSequence,
        canAct: () -> Boolean = { true },
    ): Boolean = false

    fun scroll(forward: Boolean): Boolean
}

/**
 * Adapter over the real [AccessibilityNodeInfo] tree produced by
 * `rootInActiveWindow` / `UiAutomation.rootInActiveWindow`.
 */
class AccessibilityNodeAdapter(
    private val node: AccessibilityNodeInfo,
    private val context: Context? = null,
) : A11yNode {
    override val text: CharSequence? get() = node.text

    override val contentDescription: CharSequence? get() = node.contentDescription

    override val hintText: CharSequence? get() = node.hintText

    override val packageName: CharSequence? get() = node.packageName

    override val isPassword: Boolean get() = node.isPassword()

    override val viewIdResourceName: String? get() = node.viewIdResourceName

    override val className: CharSequence? get() = node.className

    override val isClickable: Boolean get() = node.isClickable

    override val isEditable: Boolean get() = node.isEditable

    override val isFocused: Boolean get() = node.isFocused

    override val isScrollable: Boolean get() = node.isScrollable

    override val childCount: Int get() = node.childCount

    override val parent: A11yNode? get() = node.parent?.let { AccessibilityNodeAdapter(it, context) }

    override fun childAt(index: Int): A11yNode? = node.getChild(index)?.let { AccessibilityNodeAdapter(it, context) }

    override fun click(): Boolean = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

    override fun focus(): Boolean = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

    override fun paste(
        value: CharSequence,
        canAct: () -> Boolean,
    ): Boolean =
        try {
            pasteWithClipboard(value, canAct)
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        }

    @Suppress("ReturnCount") // fail closed before selection, clipboard write and paste
    private fun pasteWithClipboard(
        value: CharSequence,
        canAct: () -> Boolean,
    ): Boolean {
        if (!canAct()) return false
        val clipboard = context?.getSystemService(ClipboardManager::class.java) ?: return false
        // ACTION_PASTE inserts at the cursor. Select existing contents first to
        // preserve the replacement semantics of ACTION_SET_TEXT; never append.
        val length = node.text?.length ?: 0
        if (length > 0) {
            val selection =
                Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, length)
                }
            if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)) return false
        }
        val previous = clipboard.primaryClip
        val label = "EqoInput-${UUID.randomUUID()}"
        val clip = ClipData.newPlainText(label, value)
        clip.description.extras =
            PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        try {
            if (!canAct()) return false
            clipboard.setPrimaryClip(clip)
            return canAct() && node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } finally {
            // Do not overwrite a clipboard change made by the owner during paste.
            if (clipboard.primaryClipDescription?.label?.toString() == label) {
                if (previous == null) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(previous)
            }
        }
    }

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
