// Origin: EQO edge-handle task; user-only accessibility overlay, independent of the touch probe.
package ai.eqo.accessibility.handle

import ai.eqo.platform.R
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlin.math.abs

/** No screen-text reads, broadcasts, permission grants or resume entry points. */
@Suppress("TooManyFunctions")
class EdgeHandleOverlay(
    private val context: Context,
    private val manager: WindowManager,
) {
    private val preferences = HandlePreferences(context)
    private val guard = HandleWindowGuard.shared
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var foregroundPackage = context.packageName
    private var probeAvailable = false
    private var pendingLongPress: Runnable? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
    private val refreshTask = Runnable { render() }
    private val openPanelTask = Runnable { showPanel(requestPause = false) }

    init {
        preferences.preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun updateForeground(packageName: String) {
        if (packageName == foregroundPackage) return
        foregroundPackage = packageName
        closePanel()
        refresh()
    }

    fun setProbeAvailable(available: Boolean) {
        probeAvailable = available
        refresh()
    }

    fun configurationChanged() {
        removeWindow()
        refresh()
    }

    private fun refresh() {
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
    }

    private fun render() {
        if (!preferences.enabled || !probeAvailable || preferences.isHidden(foregroundPackage)) {
            removeWindow()
        } else if (view == null) {
            showHandle()
        }
    }

    fun destroy() {
        preferences.preferences.unregisterOnSharedPreferenceChangeListener(listener)
        handler.removeCallbacksAndMessages(null)
        removeWindow()
    }

    private fun dimensions(): Pair<Int, Int> {
        val bounds = manager.currentWindowMetrics.bounds
        return bounds.width() to bounds.height()
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    @android.annotation.SuppressLint("RtlHardcoded") // Dock coordinates are physical left/right, not reading direction.
    private fun params(
        width: Int,
        height: Int,
        focusable: Boolean,
    ): WindowManager.LayoutParams =
        WindowManager
            .LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                title = context.getString(R.string.edge_handle_title)
            }

    private fun showHandle() {
        val (width, height) = dimensions()
        val size = dp(TOUCH_WIDTH_DP)
        val params = params(size, dp(TOUCH_HEIGHT_DP), false)
        params.x = if (preferences.rightEdge) width - size else 0
        params.y = ((height - params.height) * preferences.verticalFraction).toInt()
        val handle = FrameLayout(context)
        handle.contentDescription = context.getString(R.string.edge_handle_open)
        handle.isClickable = true
        handle.isFocusable = true
        val bar = View(context)
        bar.background =
            GradientDrawable().apply {
                setColor(HANDLE_COLOR)
                cornerRadius = dp(BAR_RADIUS_DP).toFloat()
            }
        handle.addView(bar, FrameLayout.LayoutParams(dp(BAR_WIDTH_DP), dp(BAR_HEIGHT_DP), Gravity.CENTER))
        handle.setOnClickListener { showPanel() }
        handle.setOnLongClickListener {
            showPanel()
            true
        }
        bindDrag(handle, params)
        attach(handle, params)
    }

    @Suppress("CyclomaticComplexMethod") // One DOWN/MOVE/UP/CANCEL state machine with tap and long-press attribution.
    private fun bindDrag(
        handle: View,
        params: WindowManager.LayoutParams,
    ) {
        var downX = 0f
        var downY = 0f
        var startY = 0
        var moving = false
        var longPressed = false
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        val longPress =
            Runnable {
                longPressed = true
                handle.performLongClick()
            }
        pendingLongPress = longPress
        handle.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startY = params.y
                    moving = false
                    longPressed = false
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawY - downY) > slop || abs(event.rawX - downX) > slop) {
                        moving = true
                        handler.removeCallbacks(longPress)
                    }
                    if (moving && view === handle) {
                        val (width, height) = dimensions()
                        params.y = (startY + event.rawY - downY).toInt().coerceIn(0, height - params.height)
                        params.x = if (event.rawX > width / 2f) width - params.width else 0
                        manager.updateViewLayout(handle, params)
                        updateBounds(handle)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    val inward = if (preferences.rightEdge) downX - event.rawX else event.rawX - downX
                    val swipeIn = inward > dp(SWIPE_IN_DP) && abs(event.rawY - downY) < dp(SWIPE_IN_DP)
                    val switchedEdge = (params.x > 0) != preferences.rightEdge
                    val openRequested = !moving || (swipeIn && !switchedEdge)
                    if (!longPressed && openRequested) {
                        target.performClick()
                    } else if (moving && view === handle) {
                        preferences.rightEdge = params.x > 0
                        preferences.verticalFraction = params.y.toFloat() / (dimensions().second - params.height)
                    }
                }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
            }
            true
        }
    }

    private fun panelReady(requestPause: Boolean): Boolean {
        // Pause through the app's public controls before installing the guard. An in-flight
        // action must settle first rather than fail against a newly installed own window.
        handler.removeCallbacks(openPanelTask)
        val ready = EdgeHandleFeatures.preparePanel(requestPause)
        if (!ready) {
            handler.postDelayed(openPanelTask, PANEL_SETTLE_POLL_MS)
        }
        return ready && removeWindow()
    }

    @android.annotation.SuppressLint("RtlHardcoded") // Keep the panel at the user's physical dock edge in RTL too.
    private fun showPanel(requestPause: Boolean = true) {
        if (!preferences.enabled || !probeAvailable || preferences.isHidden(foregroundPackage)) return
        if (!panelReady(requestPause)) return
        guard.panelOpen = true
        val root = PanelView()
        root.setBackgroundColor(BACKDROP_COLOR)
        root.isFocusableInTouchMode = true
        root.setOnClickListener { closePanel() }
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP))
        column.setBackgroundColor(Color.WHITE)
        column.isClickable = true
        EdgeHandleFeatures.registry?.visible(context)?.forEach { shortcut ->
            addButton(column, shortcut.labelRes, shortcut.iconRes) {
                closePanel()
                // Availability is checked again; a run may have ended while the panel was open.
                if (shortcut.unavailableReason(context) == null) shortcut.run(context)
            }
        }
        addButton(column, R.string.edge_handle_hide_app) {
            preferences.hide(foregroundPackage)
            removeWindow()
        }
        addButton(column, R.string.edge_handle_close) { closePanel() }
        val scroll = ScrollView(context)
        scroll.addView(column)
        root.addView(
            scroll,
            FrameLayout.LayoutParams(dp(PANEL_WIDTH_DP), WindowManager.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_VERTICAL or if (preferences.rightEdge) Gravity.RIGHT else Gravity.LEFT
            },
        )
        attach(root, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, true))
        root.requestFocus()
    }

    private fun addButton(
        parent: LinearLayout,
        label: Int,
        icon: Int = 0,
        action: () -> Unit,
    ) {
        parent.addView(
            Button(context).apply {
                setText(label)
                minHeight = dp(TOUCH_WIDTH_DP)
                if (icon != 0) setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
                setOnClickListener { action() }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun closePanel() {
        handler.removeCallbacks(openPanelTask)
        if (!guard.panelOpen) return
        removeWindow()
        render()
    }

    private fun attach(
        target: View,
        params: WindowManager.LayoutParams,
    ) {
        target.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateBounds(target) }
        try {
            manager.addView(target, params)
            view = target
            updateBounds(target)
        } catch (_: RuntimeException) {
            guard.clear()
        }
    }

    private fun updateBounds(target: View) {
        if (guard.panelOpen) {
            val (width, height) = dimensions()
            guard.bounds = HandleWindowGuard.Bounds(0, 0, width, height)
        } else {
            val location = IntArray(2)
            target.getLocationOnScreen(location)
            guard.bounds =
                HandleWindowGuard.Bounds(
                    location[0],
                    location[1],
                    location[0] + target.width,
                    location[1] + target.height,
                )
        }
    }

    private fun removeWindow(): Boolean {
        handler.removeCallbacks(openPanelTask)
        pendingLongPress?.let(handler::removeCallbacks)
        pendingLongPress = null
        view?.let { target ->
            try {
                manager.removeViewImmediate(target)
            } catch (_: RuntimeException) {
                // Retain the guard if a failed removal leaves an actual window attached.
                if (target.isAttachedToWindow) return false
            }
        }
        view = null
        guard.clear()
        return true
    }

    private inner class PanelView : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) closePanel()
                true
            } else {
                super.dispatchKeyEvent(event)
            }
    }

    private companion object {
        const val PANEL_SETTLE_POLL_MS = 50L
        const val TOUCH_WIDTH_DP = 48
        const val TOUCH_HEIGHT_DP = 72
        const val BAR_WIDTH_DP = 8
        const val BAR_HEIGHT_DP = 56
        const val BAR_RADIUS_DP = 4
        const val SWIPE_IN_DP = 32
        const val PANEL_WIDTH_DP = 260
        const val PANEL_PADDING_DP = 12
        const val HANDLE_COLOR = -1773116276 // ARGB 150,80,100,140
        const val BACKDROP_COLOR = 0x37000000
    }
}
