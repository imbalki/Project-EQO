// Origin: EQO-authored ephemeral focused-field capture; paste-only, sensitive clipboard, no logging.
package ai.eqo.accessibility.handle

import ai.eqo.accessibility.EQOAccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/** Pure policy boundary used with fake fields and fake clipboard in tests. */
interface DictationField {
    fun safe(): Boolean

    fun paste(text: String): Boolean

    fun verified(text: String): Boolean
}

suspend fun insertDictation(
    field: DictationField,
    text: String,
    copy: (String) -> Unit,
): Boolean {
    // Android 11 permits writes, not background reads. Retain the sensitive clip by design.
    // Let a clipboard write failure reach the UI as an error, never as a false "copied" result.
    copy(text)
    var accepted = false
    try {
        if (field.safe() && field.paste(text)) {
            delay(VERIFY_DELAY_MILLIS)
            accepted = field.safe() && field.verified(text)
        }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: RuntimeException) {
        // Revoked/stale native nodes are a failed insert, not a provider failure.
        accepted = false
    }
    return accepted
}

private const val VERIFY_DELAY_MILLIS = 250L

fun dictationAllowed(
    editable: Boolean,
    password: Boolean,
    owner: CharSequence?,
    ownPackage: String,
    blocked: Boolean,
): Boolean = editable && !password && owner != null && owner.toString() != ownPackage && !blocked

/** The native node identity, window, contents and cursor are pinned before recording starts. */
class DictationTarget private constructor(
    private val service: EQOAccessibilityService,
    private val node: AccessibilityNodeInfo,
    private val original: String,
    private val start: Int,
    private val end: Int,
) : DictationField {
    private val window = node.windowId
    private val owner = node.packageName.toString()
    private var expected: String? = null
    private val clipboard = service.getSystemService(ClipboardManager::class.java)

    override fun safe(): Boolean =
        node.refresh() &&
            node.isEditable &&
            !node.isPassword &&
            node.isFocused &&
            node.windowId == window &&
            node.packageName?.toString() == owner &&
            owner != service.packageName &&
            !HandleWindowGuard.shared.panelOpen &&
            !service.isSecureWindowActive() &&
            activeWindow()

    private fun activeWindow(): Boolean {
        val root = service.rootInActiveWindow ?: return false
        return try {
            root.windowId == window && root.packageName?.toString() == owner
        } finally {
            root.recycle()
        }
    }

    @Suppress("ReturnCount") // Fail closed before changing selection or pasting into a changed/stale field.
    override fun paste(text: String): Boolean {
        if (node.text?.toString().orEmpty() != original) return false
        val selection =
            android.os.Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
            }
        val selected = node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
        if (!selected && original.isNotEmpty()) return false
        expected = original.substring(0, start) + text + original.substring(end)
        return safe() && node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    override fun verified(text: String): Boolean = expected != null && node.text?.toString() == expected

    private fun copy(text: String) {
        val clip = ClipData.newPlainText("EqoDictation", text)
        clip.description.extras =
            PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        clipboard.setPrimaryClip(clip)
    }

    suspend fun insert(text: String): Boolean = insertDictation(this, text, ::copy)

    fun close() {
        node.recycle()
        expected = null
    }

    companion object {
        fun capture(service: EQOAccessibilityService): DictationTarget? {
            val root = service.rootInActiveWindow ?: return null
            val focused =
                try {
                    root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                } finally {
                    root.recycle()
                }
            return focused?.let { captureNode(service, it) }
        }

        private fun captureNode(
            service: EQOAccessibilityService,
            node: AccessibilityNodeInfo,
        ): DictationTarget? {
            val safe =
                dictationAllowed(
                    node.isEditable,
                    node.isPassword,
                    node.packageName,
                    service.packageName,
                    HandleWindowGuard.shared.panelOpen || service.isSecureWindowActive(),
                )
            if (!safe) {
                node.recycle()
                return null
            }
            val text = node.text?.toString().orEmpty()
            val start = node.textSelectionStart.takeIf { it in 0..text.length } ?: text.length
            val end = node.textSelectionEnd.takeIf { it in start..text.length } ?: start
            return DictationTarget(service, node, text, start, end)
        }
    }
}
