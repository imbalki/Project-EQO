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
    @Suppress("ReturnCount") // Fail closed at each live service/window boundary.
    override fun state(): VisionScreenState {
        val service = EQOAccessibilityService.getInstance() ?: return refused("Turn on EQO screen control first.")
        val gate = service.automation.screenCaptureGate()
        if (!gate.isSuccess) {
            return refused(
                if ((gate as? A11yResult.Failure)?.error ==
                    A11yError.ActionRejected("screenshot with a password field")
                ) {
                    "A password field is visible. EQO will not send this screen."
                } else {
                    "EQO cannot look at a protected screen, its own window, or a screen you have taken over."
                },
            )
        }
        val active = service.rootInActiveWindow ?: return refused("EQO cannot check the current screen safely.")
        val app = active.packageName?.toString()
        active.recycle()
        if (app == null || app == service.packageName) {
            return refused("EQO cannot look at its own or an unknown window.")
        }
        val display = service.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val windows = service.windows.sortedBy { it.id }
        val target = windows.firstOrNull { it.isActive } ?: return refused("EQO cannot check the active window.")
        // t_cfbe3719 fail-closed: never inspect or send a window whose secure status
        // cannot be positively verified non-secure. Unknown == refuse, never "probably safe".
        VisionUploadGate.refusal(service.visionSecureStatuses())?.let { return refused(it) }
        val scan = WindowScan(app, target.id)
        var refusal: String? = null
        for (window in windows) {
            inspectWindow(window, service, scan)?.let { refusal = it }
        }
        return VisionScreenState(
            display.width(),
            display.height(),
            scan.digest.digest().joinToString("") { "%02x".format(it) },
            app,
            bounds(target),
            scan.own,
            refusal,
            scan.others,
        )
    }

    override suspend fun capture(): VisionFrame? {
        val before = state()
        if (before.refusal != null) return null
        return EQOAccessibilityService.getInstance()?.captureVisionFrame { state() == before }
    }

    override fun tap(
        box: ControlBox,
        expected: VisionScreenState,
    ): Boolean {
        val service = EQOAccessibilityService.getInstance() ?: return false
        return service.automation
            .runAction {
                val live = state()
                if (live != expected || !safeBox(box, live)) {
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
        delay(SETTLE_MS)
        return state()
    }

    private fun bounds(window: AccessibilityWindowInfo): ControlBox {
        val rect = Rect()
        window.getBoundsInScreen(rect)
        return ControlBox(rect.left, rect.top, rect.right, rect.bottom)
    }

    private fun safeBox(
        box: ControlBox,
        state: VisionScreenState,
    ): Boolean =
        state.refusal == null &&
            box.inside(state.width, state.height) &&
            state.ownWindows.none(box::overlaps) &&
            state.otherWindows.none(box::overlaps) &&
            box.left >= state.targetBounds.left &&
            box.top >= state.targetBounds.top &&
            box.right <= state.targetBounds.right &&
            box.bottom <= state.targetBounds.bottom

    private fun inspectWindow(
        window: AccessibilityWindowInfo,
        service: EQOAccessibilityService,
        scan: WindowScan,
    ): String? {
        // Security is enforced fail-closed before this walk (VisionUploadGate in [state]).
        val root = window.root
        var refusal: String? = null
        if (root == null) {
            refusal = "EQO cannot check every visible window safely."
        } else {
            val app = root.packageName?.toString()
            if (app == null || window.id == scan.activeWindow && app != scan.app) {
                refusal = "The active app or a visible window could not be checked safely."
                root.recycle()
            } else if (app == service.packageName) {
                val box = bounds(window)
                scan.own += box
                // Keep the 1x1 takeover probe, but never send an EQO consent/key/approval surface.
                if (box.right - box.left > 1 || box.bottom - box.top > 1) {
                    refusal = "EQO cannot send an image while its own window is visible."
                }
                root.recycle()
            } else {
                if (app != scan.app) scan.others += bounds(window)
                scan.digest.update("${window.id}:${bounds(window)}".toByteArray())
                inspectTree(root, scan.digest)?.let { refusal = it }
            }
        }
        return refusal
    }

    /** Inspect every node, including masked descendants; never read password text. Recycle obtained nodes. */
    private fun inspectTree(
        root: AccessibilityNodeInfo,
        digest: MessageDigest,
    ): String? {
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
                        "${node.className}|${node.viewIdResourceName}|${node.text}|".toByteArray(),
                    )
                    digest.update("${node.contentDescription}|".toByteArray())
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

    private fun refused(reason: String): VisionScreenState {
        val noBounds = ControlBox(0, 0, 0, 0)
        return VisionScreenState(0, 0, "", "", noBounds, refusal = reason)
    }

    private class WindowScan(
        val app: String,
        val activeWindow: Int,
    ) {
        val own = mutableListOf<ControlBox>()
        val others = mutableListOf<ControlBox>()
        val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
    }

    private companion object {
        const val MAX_NODES = 4_000
        const val SETTLE_MS = 700L
    }
}
