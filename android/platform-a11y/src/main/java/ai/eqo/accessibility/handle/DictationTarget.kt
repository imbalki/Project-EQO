// Origin: EQO-authored ephemeral focused-field capture; verified insertion, sensitive clipboard, no logging.
package ai.eqo.accessibility.handle

import ai.eqo.accessibility.EQOAccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.os.PersistableBundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/** Pure policy boundary used with fake fields and fake clipboard in tests. */
interface DictationField {
    fun safe(): Boolean

    fun paste(text: String): Boolean

    fun append(text: String): Boolean

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
        if (field.safe()) {
            field.paste(text)
            delay(VERIFY_DELAY_MILLIS)
            accepted = field.safe() && field.verified(text)
            if (!accepted && field.safe()) {
                field.append(text)
                delay(VERIFY_DELAY_MILLIS)
                accepted = field.safe() && field.verified(text)
            }
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

/** A pre-existing occurrence is not proof of insertion; the new suffix must contain the transcript. */
fun dictationVerified(
    original: String,
    current: String,
    transcript: String,
): Boolean =
    transcript.isNotBlank() &&
        current.startsWith(original) &&
        current.drop(original.length).contains(transcript)

/** Refuse stale/partially changed text rather than replace anything the owner typed. */
fun dictationAppend(
    original: String,
    current: String,
    transcript: String,
): String? = if (current == original) current + transcript else null

fun dictationAllowed(
    editable: Boolean,
    password: Boolean,
    owner: CharSequence?,
    ownPackage: String,
    blocked: Boolean,
): Boolean = editable && !password && owner != null && owner.toString() != ownPackage && !blocked

/** Pin identity and contents before recording; resolve the same focused field again before each action. */
class DictationTarget private constructor(
    private val service: EQOAccessibilityService,
    private var node: AccessibilityNodeInfo,
    private val original: String,
) : DictationField {
    private val window = node.windowId
    private val owner = node.packageName.toString()
    private var expected: String? = null
    private val clipboard = service.getSystemService(ClipboardManager::class.java)

    override fun safe(): Boolean =
        resolveFocused() &&
            node.isEditable &&
            !node.isPassword &&
            node.isFocused &&
            node.windowId == window &&
            node.packageName?.toString() == owner &&
            owner != service.packageName &&
            !HandleWindowGuard.shared.panelOpen &&
            !service.isSecureWindowActive() &&
            activeWindow()

    private fun resolveFocused(): Boolean {
        val root = service.rootInActiveWindow
        val focused =
            try {
                root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            } finally {
                root?.recycle()
            } ?: return false
        val same = focused == node && focused.windowId == window && focused.packageName?.toString() == owner
        if (same) {
            node.recycle()
            node = focused
        } else {
            focused.recycle()
        }
        return same
    }

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
        if (!safe() || node.text?.toString().orEmpty() != original) return false
        val selection =
            android.os.Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, original.length)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, original.length)
            }
        val selected = node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
        if (!selected && original.isNotEmpty()) return false
        expected = original + text
        return safe() && node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    override fun append(text: String): Boolean {
        // Never overwrite text edited during recording, or a partially accepted paste.
        val appended = if (safe()) dictationAppend(original, node.text?.toString().orEmpty(), text) else null
        if (appended == null) return false
        expected = appended
        val arguments =
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, expected)
            }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    override fun verified(text: String): Boolean =
        expected != null &&
            dictationVerified(original, node.text?.toString().orEmpty(), text)

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
            return DictationTarget(service, node, text)
        }
    }
}
