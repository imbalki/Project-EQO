// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/accessibility/OpenDroidAccessibilityService.kt
package ai.eqo.accessibility

import ai.eqo.accessibility.handle.EdgeHandleFeatures
import ai.eqo.accessibility.handle.EdgeHandleOverlay
import ai.eqo.accessibility.handle.HandleWindowGuard
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PixelFormat
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Suppress("TooManyFunctions") // one service facade; a split is a recorded follow-up (TASK-012 evidence)
class EQOAccessibilityService :
    AccessibilityService(),
    ServiceActionOps {
    // No donor loop, routines, floating widget, or Python bridge in Phase One (D-005).
    private val nodeTraversal = AccessibilityNodeTraversal()
    private var windowManager: WindowManager? = null
    private val imeSubmitLabels = listOf("search", "go", "send", "done", "submit", "ok", "enter")

    // ── TASK-009: takeover detection and the typed automation layer ──────────

    /**
     * Shared with the study ActionLoop through [TakeoverDetector.shared]: latches true
     * when the user touches the screen during an agent action, pausing the loop.
     */
    private val takeoverDetector = EqoServiceRuntime.sharedTakeover

    /** Non-zero while an EQO-dispatched gesture is in flight (touch attribution). */
    private val gestureInFlight =
        java.util.concurrent.atomic
            .AtomicInteger(0)

    private var touchProbeView: TouchProbeView? = null
    private var edgeHandle: EdgeHandleOverlay? = null
    private val explainButton =
        object : android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: android.accessibilityservice.AccessibilityButtonController) {
                EdgeHandleFeatures.explain(this@EQOAccessibilityService, "accessibility_button")
            }
        }

    /**
     * Typed observe/tap/scroll/type layer over this service's node tree. Surfaces
     * accessibility-disabled as a typed error with no silent retry.
     */
    private val runtime by lazy {
        EqoServiceRuntime(
            rootProvider = { rootInActiveWindow?.let { AccessibilityNodeAdapter(it, this) } },
            ownPackage = packageName,
            serviceState = {
                if (instance === this) {
                    EqoAutomation.ServiceState.AVAILABLE
                } else {
                    EqoAutomation.ServiceState.ACCESSIBILITY_DISABLED
                }
            },
            isSecureWindow = { isSecureWindowActive() },
        )
    }

    val automation: EqoAutomation get() = runtime.automation

    /**
     * TASK-012 (SF-1): the takeover-gated facade over this service's raw
     * [ServiceActionOps]. Every automator action goes through here, which runs
     * it inside [EqoAutomation.runAction].
     */
    val gatedActions: GatedServiceActions by lazy { GatedServiceActions(automation, this) }

    /**
     * TASK-012 (SF-2): true when the active window is secure (FLAG_SECURE).
     *
     * `AccessibilityWindowInfo.isSecure()` is NOT in the public SDK (verified
     * with `javap -classpath android-36/android.jar android.view.accessibility.
     * AccessibilityWindowInfo` -> only `isActive()` etc., no `isSecure`), so it
     * is probed reflectively where the platform exposes it. When the platform
     * hides it we cannot tell and report false — the residual exposure is
     * recorded in android/Phase-One/evidence/task-012-action-loop.md.
     */
    fun isSecureWindowActive(): Boolean = windows.any { it.isActive && isSecureWindow(it) }

    private fun isSecureWindow(window: android.view.accessibility.AccessibilityWindowInfo): Boolean =
        try {
            val isSecure = android.view.accessibility.AccessibilityWindowInfo::class.java.getMethod("isSecure")
            isSecure.invoke(window) == true
        } catch (_: Exception) {
            false
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        accessibilityButtonController.registerAccessibilityButtonCallback(explainButton)
        EdgeHandleFeatures.refreshEntries(this)
        // Only Phase-One takeover observation; no donor work is started on binding.
        addTouchProbe()
        windowManager?.let { manager ->
            edgeHandle = EdgeHandleOverlay(this, manager).also { it.setProbeAvailable(touchProbeView != null) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            EdgeHandleFeatures.refreshEntries(this)
            edgeHandle?.refresh()
            val targetPackage = event.packageName?.toString()
            val windowClass = event.className?.toString().orEmpty()
            val appWindow = targetPackage != packageName || windowClass.endsWith("Activity")
            if (targetPackage != null && appWindow && !windowClass.contains("InputMethod")) {
                edgeHandle?.updateForeground(targetPackage)
            }
        }
        // TASK-009: TYPE_TOUCH_INTERACTION_START fires when a finger (or an
        // injected touch) starts on the screen. Combined with the overlay touch
        // probe below it feeds the takeover detector; the second source for the
        // same touch is a no-op because the detector latches.
        // The coordinate-bearing probe owns touch reports when installed. Otherwise
        // retain the conservative accessibility-event fallback (no touch is dropped).
        if (event.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START && touchProbeView == null) {
            reportTouchToTakeoverDetector()
        }
    }

    /**
     * TASK-009: one screen touch was observed. Touches seen while an
     * EQO-dispatched gesture is in flight are attributed to that gesture; every
     * other touch is the user's. A user touch during an agent action latches the
     * takeover detector, which pauses the loop.
     */

    fun reportControlSurfaceTouch() = reportTouchToTakeoverDetector()

    private fun reportTouchToTakeoverDetector() {
        val source =
            if (gestureInFlight.get() > 0) {
                TakeoverDetector.TouchSource.AGENT_GESTURE
            } else {
                TakeoverDetector.TouchSource.USER
            }
        val tookOver =
            takeoverDetector.onTouch(source, android.os.SystemClock.elapsedRealtime())
        if (tookOver) {
            android.util.Log.i("EQOAccessibilityService", "TASK-009: user takeover detected - loop paused")
        }
    }

    override fun onInterrupt() {
        // Handle interruption
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        edgeHandle?.configurationChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        accessibilityButtonController.unregisterAccessibilityButtonCallback(explainButton)
        edgeHandle?.destroy()
        edgeHandle = null
        removeTouchProbe()
        instance = null
    }

    /**
     * TASK-009: a 1x1 overlay window with FLAG_WATCH_OUTSIDE_TOUCH. It consumes
     * exactly one screen pixel (top-left corner) and receives ACTION_OUTSIDE for
     * every touch anywhere else on screen - including touches on other apps'
     * windows - so the takeover detector sees user input while EQO works.
     */
    private fun addTouchProbe() {
        if (touchProbeView != null) return
        if (windowManager == null) {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }
        val probe = TouchProbeView(this)
        val params =
            WindowManager
                .LayoutParams(
                    1,
                    1,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 0
                    y = 0
                }
        probe.setOnTouchListener(
            View.OnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE &&
                    !takeoverDetector.isControlTouch(event.rawX.toInt(), event.rawY.toInt())
                ) {
                    reportTouchToTakeoverDetector()
                }
                if (event.action == MotionEvent.ACTION_UP) {
                    // Lint ClickableViewAccessibility: report the genuine click
                    // (touch released on the probe) through the a11y click path.
                    v.performClick()
                }
                true
            },
        )
        try {
            windowManager?.addView(probe, params)
            touchProbeView = probe
        } catch (e: Exception) {
            android.util.Log.w("EQOAccessibilityService", "TASK-009: touch probe not added", e)
            touchProbeView = null
        }
    }

    private fun removeTouchProbe() {
        val probe = touchProbeView ?: return
        touchProbeView = null
        try {
            windowManager?.removeView(probe)
        } catch (e: Exception) {
            // View may already be gone with the window session.
        }
    }

    /**
     * TASK-009: the 1x1 takeover-probe window (see [addTouchProbe]). Named type
     * for the same Lint `StaticFieldLeak` treatment as [TouchTargetView].
     * `performClick` is overridden (and called from the probe's touch listener
     * on ACTION_UP) per the Android accessibility guideline - the probe itself
     * has no click action, so it just delegates to the base implementation.
     */
    private inner class TouchProbeView(
        context: android.content.Context,
    ) : View(context) {
        override fun performClick(): Boolean = super.performClick()
    }

    // --- Node Automation Methods ---

    override fun findAndClick(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return nodeTraversal.findAndClick(rootNode, text)
    }

    @Suppress("ReturnCount") // typed early-outs; a single exit would only obscure them
    override fun findAndClickById(viewId: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
        for (node in nodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node.recycle()
                return true
            }
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    parent.recycle()
                    node.recycle()
                    return true
                }
                parent = parent.parent
            }
            node.recycle()
        }
        return false
    }

    // Legacy donor entry points share the native-node typing implementation.
    // The outer GatedServiceActions gate and inner typed gate are depth-counted.
    override fun findAndType(
        searchText: String,
        content: String,
    ): Boolean = automation.type(searchText, content).isSuccess

    override fun findAndTypeById(
        viewId: String,
        content: String,
    ): Boolean = automation.typeById(viewId, content).isSuccess

    /** TASK-012 (SF-1): raw global back, gated by [GatedServiceActions]. */
    override fun performGlobalBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    /** TASK-012 (SF-1): raw global home, gated by [GatedServiceActions]. */
    override fun performGlobalHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    /**
     * Performs the IME 'enter/search/go' action on the currently focused editable
     * field — used to submit a search box after TYPE_TEXT/TYPE_ID types into it.
     * Uses AccessibilityNodeInfo.ACTION_IME_ENTER on API 30+ (the only reliable way
     * to trigger the IME action programmatically). On older API levels — or if that
     * fails — falls back to tapping a nearby clickable control whose label reads like
     * a submit action ("search", "go", "send", "done", "submit", "ok", "enter").
     */
    override fun performImeEnter(): Boolean {
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val success =
            if (focusedNode != null) {
                try {
                    focusedNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                } finally {
                    focusedNode.recycle()
                }
            } else {
                false
            }
        return success || performSubmitFallback()
    }

    private fun performSubmitFallback(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val result = nodeTraversal.findAndClickSubmitControl(rootNode, imeSubmitLabels)
        rootNode.recycle()
        return result
    }

    override fun performScroll(forward: Boolean): Boolean = performScrollResult(forward).isSuccess

    override fun performScrollResult(forward: Boolean): A11yResult {
        val rootNode = rootInActiveWindow
        return try {
            NodeTreeSearch.scroll(rootNode?.let { AccessibilityNodeAdapter(it) }, forward)
        } finally {
            rootNode?.recycle()
        }
    }

    // --- Gesture Automation Methods (Coordinate Taps) ---

    override fun clickCoordinates(
        x: Float,
        y: Float,
    ): Boolean {
        // The active root can belong to another app underneath our overlay: coordinates need their own guard.
        if (HandleWindowGuard.shared.blocksGesture(x, y)) return false
        val path =
            Path().apply {
                moveTo(x, y)
            }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture =
            GestureDescription
                .Builder()
                .apply {
                    addStroke(stroke)
                }.build()

        // TASK-009: mark the gesture in flight so the touch probe attributes the
        // gesture's own touches to EQO instead of to the user.
        gestureInFlight.incrementAndGet()
        val callback =
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    gestureInFlight.decrementAndGet()
                    // TASK-012 (N-3): remember when OUR stroke finished, so a
                    // late-arriving touch from it is tagged as a suspected
                    // self-gesture takeover instead of a silent task-killer.
                    takeoverDetector.onSelfGestureFinished(android.os.SystemClock.elapsedRealtime())
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    gestureInFlight.decrementAndGet()
                    takeoverDetector.onSelfGestureFinished(android.os.SystemClock.elapsedRealtime())
                }
            }
        val dispatched = dispatchGesture(gesture, callback, null)
        if (!dispatched) {
            gestureInFlight.decrementAndGet()
        }
        return dispatched
    }

    // --- Screen Text Extraction ---

    @Suppress("ReturnCount") // each privacy refusal returns its typed empty result
    fun getScreenText(): String {
        // TASK-012 (SF-2): nothing is read from a secure (FLAG_SECURE) window.
        if (isSecureWindowActive()) return ""
        val rootNode = rootInActiveWindow ?: return ""
        val text = nodeTraversal.screenText(rootNode)
        rootNode.recycle()
        return text
    }

    /**
     * Saves a PNG of the current screen to [target]. Returns false when Android refuses the capture or the file
     * cannot be written. Callers run [EqoAutomation.screenshotGate] first; secure windows are refused there.
     */
    suspend fun takeScreenshotToFile(target: java.io.File): Boolean {
        val bitmap = captureBitmap() ?: return false
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                target.parentFile?.mkdirs()
                java.io.FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
            } catch (_: java.io.IOException) {
                false
            } finally {
                bitmap.recycle()
            }
        }
    }

    private suspend fun captureBitmap(): Bitmap? =
        suspendCoroutine { continuation ->
            try {
                takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            val buffer = screenshotResult.hardwareBuffer
                            val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshotResult.colorSpace)
                            val software = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                            hardware?.recycle()
                            buffer.close()
                            continuation.resume(software)
                        }

                        override fun onFailure(errorCode: Int) {
                            continuation.resume(null)
                        }
                    },
                )
            } catch (_: RuntimeException) {
                continuation.resume(null)
            }
        }

    suspend fun takeScreenshotAndEncode(): String? {
        return suspendCoroutine { continuation ->
            try {
                takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            try {
                                val hardwareBuffer = screenshotResult.hardwareBuffer
                                val colorSpace = screenshotResult.colorSpace
                                val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                if (bitmap == null) {
                                    continuation.resume(null)
                                    return
                                }
                                val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                                bitmap.recycle()
                                hardwareBuffer.close()

                                if (softwareBitmap == null) {
                                    continuation.resume(null)
                                    return
                                }

                                val outputStream = ByteArrayOutputStream()
                                softwareBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                                val byteArray = outputStream.toByteArray()
                                val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                                softwareBitmap.recycle()
                                continuation.resume(base64String)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                continuation.resume(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            continuation.resume(null)
                        }
                    },
                )
            } catch (e: Exception) {
                e.printStackTrace()
                continuation.resume(null)
            }
        }
    }

    companion object {
        private const val PNG_QUALITY = 100

        @Volatile
        private var instance: EQOAccessibilityService? = null

        fun getInstance(): EQOAccessibilityService? = instance
    }
}
