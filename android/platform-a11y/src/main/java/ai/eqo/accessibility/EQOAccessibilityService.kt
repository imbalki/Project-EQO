// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/accessibility/OpenDroidAccessibilityService.kt
package ai.eqo.accessibility

import ai.eqo.NotificationTapTarget
import ai.eqo.core.agent.AgentLoop
import ai.eqo.core.agent.AgentState
import ai.eqo.core.service.ServiceBridge
import ai.eqo.data.repository.SettingsRepository
import ai.eqo.platform.R
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.graphics.toColorInt
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@AndroidEntryPoint
@Suppress("TooManyFunctions") // one service facade; a split is a recorded follow-up (TASK-012 evidence)
class EQOAccessibilityService :
    AccessibilityService(),
    ServiceActionOps {
    @Inject
    lateinit var agentLoop: AgentLoop

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var nodeTraversal: AccessibilityNodeTraversal

    @Inject
    lateinit var habitRoutineEngine: dagger.Lazy<ai.eqo.core.routine.HabitRoutineTracker>

    @Inject
    lateinit var serviceBridge: ServiceBridge

    @Inject
    lateinit var notificationTapTarget: NotificationTapTarget

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var windowManager: WindowManager? = null
    private var floatingView: FloatingWidgetView? = null
    private var touchTargetView: TouchTargetView? = null
    private var isButtonAdded = false
    private var isDeviceLocked = false
    private var showFloatingButtonSetting = false
    private val imeSubmitLabels = listOf("search", "go", "send", "done", "submit", "ok", "enter")

    // ── TASK-009: takeover detection and the typed automation layer ──────────

    /**
     * Shared with [AgentLoop] through [TakeoverDetector.shared]: latches true
     * when the user touches the screen during an agent action, pausing the loop.
     */
    private val takeoverDetector = TakeoverDetector.shared

    /** Non-zero while an EQO-dispatched gesture is in flight (touch attribution). */
    private val gestureInFlight =
        java.util.concurrent.atomic
            .AtomicInteger(0)

    private var touchProbeView: TouchProbeView? = null

    /**
     * Typed observe/tap/scroll/type layer over this service's node tree. Surfaces
     * accessibility-disabled as a typed error with no silent retry.
     */
    val automation: EqoAutomation by lazy {
        EqoAutomation(
            rootProvider = { rootInActiveWindow?.let { AccessibilityNodeAdapter(it) } },
            serviceState = {
                if (instance != null) {
                    EqoAutomation.ServiceState.AVAILABLE
                } else {
                    EqoAutomation.ServiceState.ACCESSIBILITY_DISABLED
                }
            },
            takeover = takeoverDetector,
            isSecureWindow = { isSecureWindowActive() },
        )
    }

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

    /**
     * BroadcastReceiver that tracks device lock/unlock state.
     * Hides the floating button when the device is locked to prevent
     * unintended interaction from the lock screen.
     */
    private val screenStateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        isDeviceLocked = true
                        refreshFloatingButtonVisibility()
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        // ACTION_USER_PRESENT is broadcast when the user unlocks the device
                        isDeviceLocked = false
                        refreshFloatingButtonVisibility()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        // Screen turned on but may still be locked; check KeyguardManager
                        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        isDeviceLocked = keyguardManager?.isKeyguardLocked == true
                        refreshFloatingButtonVisibility()
                    }
                }
            }
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // Initialize lock state
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        isDeviceLocked = keyguardManager?.isKeyguardLocked == true

        // Register receiver for screen on/off/unlock events
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        registerReceiver(screenStateReceiver, filter)

        serviceScope.launch {
            settingsRepository.llmConfig
                .map { it.showFloatingButton }
                .collectLatest { show ->
                    showFloatingButtonSetting = show
                    refreshFloatingButtonVisibility()
                }
        }

        serviceScope.launch {
            agentLoop.agentState.collectLatest { state ->
                floatingView?.updateState(state)
            }
        }

        // TASK-009: start the touch probe that feeds takeover detection.
        addTouchProbe()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // TASK-009: TYPE_TOUCH_INTERACTION_START fires when a finger (or an
        // injected touch) starts on the screen. Combined with the overlay touch
        // probe below it feeds the takeover detector; the second source for the
        // same touch is a no-op because the detector latches.
        if (event.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
            reportTouchToTakeoverDetector()
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank() && pkg != packageName) {
                habitRoutineEngine.get().recordAppOpen(pkg)
            }
        }
    }

    /**
     * TASK-009: one screen touch was observed. Touches seen while an
     * EQO-dispatched gesture is in flight are attributed to that gesture; every
     * other touch is the user's. A user touch during an agent action latches the
     * takeover detector, which pauses the loop.
     */
    private fun reportTouchToTakeoverDetector() {
        val source =
            if (gestureInFlight.get() > 0) {
                TakeoverDetector.TouchSource.AGENT_GESTURE
            } else {
                TakeoverDetector.TouchSource.USER
            }
        val tookOver = takeoverDetector.onTouch(source, android.os.SystemClock.elapsedRealtime())
        if (tookOver) {
            android.util.Log.i("EQOAccessibilityService", "TASK-009: user takeover detected - loop paused")
        }
    }

    override fun onInterrupt() {
        // Handle interruption
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            // Receiver may not have been registered
        }
        serviceScope.cancel()
        removeTouchProbe()
        removeFloatingButton()
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
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
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
     * Refreshes the floating button visibility based on both the user setting
     * and the device lock state. The button is only shown when the setting is
     * enabled AND the device is unlocked.
     */
    private fun refreshFloatingButtonVisibility() {
        if (showFloatingButtonSetting && !isDeviceLocked) {
            addFloatingButton()
        } else {
            removeFloatingButton()
        }
    }

    private fun addFloatingButton() {
        if (isButtonAdded) return

        if (windowManager == null) {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }

        val view = FloatingWidgetView(this)
        view.updateState(agentLoop.agentState.value)
        floatingView = view

        // Draw layer: renders the icon, including its animated glow, which can reach the
        // full edge of this 64dp box. It is deliberately not touchable (FLAG_NOT_TOUCHABLE):
        // without that, the transparent margin around the visible icon (the box is a square,
        // the icon is a circle inscribed in it) would swallow taps meant for whatever app is
        // underneath. See #107.
        val params =
            WindowManager
                .LayoutParams(
                    dpToPx(64),
                    dpToPx(64),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 100
                    y = 300
                }

        // Touch layer: an invisible window sized and centered to match the icon's static
        // footprint (matches the inset used in FloatingWidgetView.onDraw's radius calc), kept
        // in lockstep with the draw layer's position. This is the only part of the widget that
        // is actually touchable, so a tap or drag has to land on the visible icon to be caught
        // by the widget at all — everywhere else in the old 64dp square now falls through to
        // whatever is underneath, same as if the widget weren't there.
        val touchTarget = TouchTargetView(this)
        touchTargetView = touchTarget

        val touchInset = dpToPx(8)
        val touchParams =
            WindowManager
                .LayoutParams(
                    dpToPx(64) - 2 * touchInset,
                    dpToPx(64) - 2 * touchInset,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = params.x + touchInset
                    y = params.y + touchInset
                }

        touchTarget.setOnTouchListener(
            object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var isClick = false
                private val touchSlop = 10f
                private val longPressRunnable =
                    Runnable {
                        isClick = false
                        triggerMicrophoneAction()
                    }

                override fun onTouch(
                    v: View,
                    event: MotionEvent,
                ): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isClick = true
                            touchTarget.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - initialTouchX
                            val dy = event.rawY - initialTouchY
                            if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                                if (isClick) {
                                    isClick = false
                                    touchTarget.removeCallbacks(longPressRunnable)
                                }
                            }
                            params.x = initialX + dx.toInt()
                            params.y = initialY + dy.toInt()

                            val displayMetrics = resources.displayMetrics
                            val screenWidth = displayMetrics.widthPixels
                            val screenHeight = displayMetrics.heightPixels
                            params.x = params.x.coerceIn(0, screenWidth - params.width)
                            params.y = params.y.coerceIn(0, screenHeight - params.height)

                            touchParams.x = params.x + touchInset
                            touchParams.y = params.y + touchInset

                            try {
                                windowManager?.updateViewLayout(view, params)
                                windowManager?.updateViewLayout(touchTarget, touchParams)
                            } catch (e: Exception) {
                                // View might have been removed
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            touchTarget.removeCallbacks(longPressRunnable)
                            if (isClick) {
                                touchTarget.performClick()
                            }
                            return true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            touchTarget.removeCallbacks(longPressRunnable)
                            return true
                        }
                    }
                    return false
                }
            },
        )

        try {
            windowManager?.addView(view, params)
            windowManager?.addView(touchTarget, touchParams)
            isButtonAdded = true
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                windowManager?.removeView(view)
            } catch (removeError: Exception) {
                // Ignore: view may not have been added.
            }
            try {
                windowManager?.removeView(touchTarget)
            } catch (removeError: Exception) {
                // Ignore: view may not have been added.
            }
            floatingView = null
            touchTargetView = null
        }
    }

    private fun removeFloatingButton() {
        if (!isButtonAdded) return
        try {
            floatingView?.let { windowManager?.removeView(it) }
            touchTargetView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            floatingView = null
            touchTargetView = null
            isButtonAdded = false
        }
    }

    private fun triggerMicrophoneAction() {
        try {
            serviceBridge.triggerRecord(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openMainActivityAction() {
        val intent =
            notificationTapTarget.launchIntent(this).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    private fun dpToPx(dp: Int): Int = Math.round(dp * resources.displayMetrics.density)

    /**
     * The invisible window that actually receives touches for the floating widget, sized to
     * just the icon's footprint (see [addFloatingButton]). Given its own named type — rather
     * than plain [android.view.View] — so it draws the same treatment [FloatingWidgetView]
     * already gets from the Lint `StaticFieldLeak` check.
     */
    private inner class TouchTargetView(
        context: Context,
    ) : View(context) {
        override fun performClick(): Boolean {
            super.performClick()
            openMainActivityAction()
            return true
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
        context: Context,
    ) : View(context) {
        override fun performClick(): Boolean = super.performClick()
    }

    inner class FloatingWidgetView(
        context: Context,
    ) : android.widget.ImageView(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var pulseRadius = 0f
        private var pulseAlpha = 255
        private var state: AgentState = AgentState.Idle

        private val animator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1500
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener { animation ->
                    val value = animation.animatedValue as Float
                    pulseRadius = value * dpToPx(8f)
                    pulseAlpha = ((1f - value) * 150).toInt()
                    invalidate()
                }
            }

        init {
            setImageResource(R.drawable.bot)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            val padding = dpToPx(12)
            setPadding(padding, padding, padding, padding)
        }

        fun updateState(newState: AgentState) {
            if (this.state == newState) return
            this.state = newState

            animator.cancel()
            when (newState) {
                is AgentState.Thinking -> {
                    animator.duration = 600
                    animator.repeatMode = ValueAnimator.RESTART
                }
                is AgentState.Listening -> {
                    animator.duration = 1000
                    animator.repeatMode = ValueAnimator.REVERSE
                }
                is AgentState.Speaking -> {
                    animator.duration = 800
                    animator.repeatMode = ValueAnimator.REVERSE
                }
                else -> {
                    animator.duration = 2000
                    animator.repeatMode = ValueAnimator.REVERSE
                }
            }
            if (isAttachedToWindow) {
                animator.start()
            }
            invalidate()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            animator.start()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            animator.cancel()
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = (width / 2f) - dpToPx(8f)

            // Draw cyber grey background circle
            paint.color = "#121216".toColorInt()
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, radius, paint)

            // Draw center logo
            super.onDraw(canvas)

            // Draw glow border
            val color =
                when (state) {
                    is AgentState.Idle -> "#FFFFFF".toColorInt() // Platinum pure white
                    is AgentState.Listening -> "#FF3B30".toColorInt() // Pulsing red
                    is AgentState.Thinking -> "#00F0FF".toColorInt() // Cyan
                    is AgentState.Speaking -> "#007AFF".toColorInt() // Neon blue
                    is AgentState.ExecutingPlan -> "#38BDF8".toColorInt() // Sky sapphire
                    else -> "#FFFFFF".toColorInt()
                }

            paint.color = color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpToPx(3f)

            // Draw main border ring
            canvas.drawCircle(cx, cy, radius, paint)

            // Draw glowing pulse ring
            glowPaint.color = color
            glowPaint.style = Paint.Style.STROKE

            if (state is AgentState.Listening || state is AgentState.Thinking || state is AgentState.Speaking) {
                glowPaint.strokeWidth = dpToPx(1.5f)
                glowPaint.alpha = pulseAlpha
                canvas.drawCircle(cx, cy, radius + pulseRadius, glowPaint)
            }
        }
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

    @Suppress("ReturnCount") // typed early-outs; a single exit would only obscure them
    override fun findAndType(
        searchText: String,
        content: String,
    ): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(searchText)
        for (node in nodes) {
            if (node.isEditable) {
                val arguments =
                    Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, content)
                    }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                node.recycle()
                return true
            }
            node.recycle()
        }
        return false
    }

    @Suppress("ReturnCount") // typed early-outs; a single exit would only obscure them
    override fun findAndTypeById(
        viewId: String,
        content: String,
    ): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
        for (node in nodes) {
            if (node.isEditable) {
                val arguments =
                    Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, content)
                    }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                node.recycle()
                return true
            }
            node.recycle()
        }
        return false
    }

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

    override fun performScroll(forward: Boolean): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val success = performScrollOnNode(rootNode, action)
        rootNode.recycle()
        return success
    }

    private fun performScrollOnNode(
        node: AccessibilityNodeInfo,
        action: Int,
    ): Boolean {
        if (node.isScrollable) {
            return node.performAction(action)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (performScrollOnNode(child, action)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    // --- Gesture Automation Methods (Coordinate Taps) ---

    override fun clickCoordinates(
        x: Float,
        y: Float,
    ): Boolean {
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
        @Volatile
        private var instance: EQOAccessibilityService? = null

        fun getInstance(): EQOAccessibilityService? = instance
    }
}
