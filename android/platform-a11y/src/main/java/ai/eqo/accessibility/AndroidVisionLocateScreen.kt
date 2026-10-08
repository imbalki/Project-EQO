// Origin: EQO vision-locate, memory-only capture and live gated coordinate dispatch.
package ai.eqo.accessibility

import ai.eqo.core.agent.VisionFrame
import ai.eqo.core.agent.VisionLocateScreen
import ai.eqo.core.agent.VisionScreenState
import ai.eqo.core.llm.ControlBox
import android.graphics.Rect
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.delay
import java.security.MessageDigest

class AndroidVisionLocateScreen : VisionLocateScreen {
    override fun state(): VisionScreenState {
        val service = EQOAccessibilityService.getInstance() ?: return refused("Turn on EQO screen control first.")
        if (!service.automation.screenshotGate().isSuccess) return refused("EQO cannot look at this protected screen, its own window, or a screen you have taken over.")
        val active = service.rootInActiveWindow ?: return refused("EQO cannot check the current screen safely.")
        val app = active.packageName?.toString()
        active.recycle()
        if (app == null || app == service.packageName) return refused("EQO cannot look at its own or an unknown window.")
        val display = service.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val windows = service.windows.sortedBy { it.id }
        val target = windows.firstOrNull { it.isActive } ?: return refused("EQO cannot check the active window.")
        val own = mutableListOf<ControlBox>()
        val digest = MessageDigest.getInstance("SHA-256")
        var refusal: String? = null
        for (window in windows) {
            if (service.isSecureWindow(window)) refusal = "This is a protected window. EQO will not send this screen."
            val root = window.root
            if (root == null) {
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION || window.isActive) {
                    refusal = "EQO cannot check every visible app window safely."
                }
            } else {
                if (root.packageName?.toString() == service.packageName) {
                    val box = bounds(window)
                    own += box
                    // Keep the 1x1 takeover probe, but never send an EQO consent/key/approval surface.
                    if (box.right - box.left > 1 || box.bottom - box.top > 1) {
                        refusal = "EQO cannot send an image while its own window is visible."
                    }
                }
                digest.update("${window.id}:${bounds(window)}".toByteArray())
                inspectTree(root, digest)?.let { refusal = it }
            }
        }
        return VisionScreenState(
            display.width(),
            display.height(),
            digest.digest().joinToString("") { "%02x".format(it) },
            app,
            bounds(target),
            own,
            refusal,
        )
    }

    override suspend fun capture(): VisionFrame? {
        val before = state()
        if (before.refusal != null) return null
        return EQOAccessibilityService.getInstance()?.captureVisionFrame { state() == before }
    }

    override fun tap(box: ControlBox, expected: VisionScreenState): Boolean {
        val service = EQOAccessibilityService.getInstance() ?: return false
        return service.automation.runAction {
            val live = state()
            if (live != expected || live.refusal != null || !box.inside(live.width, live.height) ||
                live.ownWindows.any(box::overlaps)
            ) {
                A11yResult.failure(A11yError.ActionRejected("vision locate"))
            } else {
                service.gatedActions.clickCoordinates(
                    (box.left + box.right) / 2f,
                    (box.top + box.bottom) / 2f,
                )
            }
        }.isSuccess
    }

    override suspend fun settledState(): VisionScreenState {
        delay(700)
        return state()
    }

    private fun bounds(window: AccessibilityWindowInfo): ControlBox {
        val rect = Rect()
        window.getBoundsInScreen(rect)
        return ControlBox(rect.left, rect.top, rect.right, rect.bottom)
    }

    /** Inspect every node, including masked descendants; never read password text. Recycle obtained nodes. */
    private fun inspectTree(root: AccessibilityNodeInfo, digest: MessageDigest): String? {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var inspected = 0
        var refusal: String? = null
        while (pending.isNotEmpty() && inspected++ < MAX_NODES) {
            val node = pending.removeFirst()
            try {
                if (node.isPassword && node.isVisibleToUser) {
                    refusal = "A password field is visible. EQO will not send this screen."
                } else if (!node.isPassword) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    digest.update(
                        "${node.className}|${node.viewIdResourceName}|${node.text}|${node.contentDescription}|".toByteArray(),
                    )
                    digest.update("$rect|${node.isFocused}|${node.isVisibleToUser}|${node.childCount}".toByteArray())
                }
                repeat(node.childCount) { node.getChild(it)?.let(pending::addLast) }
            } finally {
                node.recycle()
            }
        }
        if (pending.isNotEmpty()) {
            pending.forEach { it.recycle() }
            refusal = "This screen is too complex to check safely. EQO will not send it."
        }
        return refusal
    }

    private fun refused(reason: String): VisionScreenState =
        VisionScreenState(0, 0, "", "", ControlBox(0, 0, 0, 0), refusal = reason)

    private companion object {
        const val MAX_NODES = 4_000
    }
}
