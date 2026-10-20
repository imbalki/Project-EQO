// Origin: EQO edge-handle task; user-only accessibility overlay, independent of the touch probe.
package ai.eqo.accessibility.handle

import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.platform.R
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

/**
 * No screen-text reads, broadcasts, permission grants or resume entry points.
 * One overlay owns the handle, panel, tooltip and result windows, so it stays one class.
 */
@Suppress("TooManyFunctions", "LargeClass")
class EdgeHandleOverlay(
    private val context: Context,
    private val manager: WindowManager,
) {
    private val preferences = HandlePreferences(context)
    private val guard = HandleWindowGuard.shared
    private val handler = Handler(Looper.getMainLooper())
    private val presentation = HandlePresentation(SystemClock::uptimeMillis)
    private var view: View? = null
    private var foregroundPackage = context.packageName
    private var probeAvailable = false
    private var pendingLongPress: Runnable? = null
    private var recordingExpanded = false
    private var renderedRecording = false
    private var recordingStatusView: TextView? = null
    private val panelTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var panelDownX = 0f
    private var panelDownY = 0f
    private var panelGestureActive = false
    private var panelDragged = false
    private var tooltipChip: TextView? = null
    private var pendingTooltip: Runnable? = null
    private var tooltipHovering = false
    private var tooltipHolding = false
    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            handler.post {
                val micEnabled = EdgeHandleFeatures.registry?.enabled(EdgeHandleFeatures.DICTATION_ID) == true
                if (!preferences.enabled || !micEnabled) {
                    EdgeHandleFeatures.dictationCancel()
                }
                // Shortcut-choice bookkeeping (order/known/enabled) must not tear down an open
                // panel mid-interaction; only a real opt-out or hidden app closes it early.
                val keepPanel = key in CHOICE_KEYS && guard.panelOpen && preferences.enabled && !hidden()
                if (!keepPanel) {
                    removeWindow()
                    refresh()
                }
            }
        }
    private val refreshTask = Runnable { render() }
    private val openPanelTask = Runnable { showPanel(requestPause = false) }
    private val collapseTask = Runnable { if (presentation.tick()) closePanel() }
    private val resultTask =
        Runnable {
            if (EdgeHandleFeatures.dictationInserted && !presentation.resultVisible()) {
                dismissResult(preservePanel = true)
            }
        }

    init {
        EdgeHandleFeatures.recordingChanged = {
            handler.post {
                if (renderedRecording && EdgeHandleFeatures.recordingActive && view != null) {
                    recordingStatusView?.text = EdgeHandleFeatures.recordingMessage
                } else {
                    renderedRecording = EdgeHandleFeatures.recordingActive
                    if (!renderedRecording) recordingExpanded = false
                    removeWindow()
                    refresh()
                }
            }
        }
        preferences.preferences.registerOnSharedPreferenceChangeListener(listener)
        EdgeHandleFeatures.screenshotChanged = {
            handler.post {
                removeWindow()
                refresh()
            }
        }
        EdgeHandleFeatures.dictationChanged = {
            // A previous success must never expire a newer recording or recovery card.
            handler.removeCallbacks(resultTask)
            handler.post {
                if (EdgeHandleFeatures.dictationInserted) {
                    presentation.inserted()
                    handler.removeCallbacks(resultTask)
                    handler.postDelayed(resultTask, HandlePresentation.RESULT_MS)
                }
                removeWindow()
                refresh()
            }
        }
    }

    fun updateForeground(packageName: String) {
        if (packageName == foregroundPackage) return
        foregroundPackage = packageName
        EdgeHandleFeatures.screenshotDismiss()
        presentation.foregroundChanged()
        dismissResult()
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

    fun refresh() {
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
    }

    private fun render() {
        if (EdgeHandleFeatures.screenshotHidden) {
            EdgeHandleFeatures.screenshotOverlayRemoved = removeWindow()
        } else if (!preferences.enabled || !probeAvailable || hidden()) {
            removeWindow()
        } else if (view == null) {
            showHandle()
        } else {
            view?.let { fitWindow(it, it.layoutParams as WindowManager.LayoutParams) }
        }
    }

    fun destroy() {
        if (EdgeHandleFeatures.recordingActive) EdgeHandleFeatures.recordingStop(context)
        EdgeHandleFeatures.recordingChanged = {}
        EdgeHandleFeatures.screenshotDismiss()
        EdgeHandleFeatures.screenshotChanged = {}
        EdgeHandleFeatures.dictationChanged = {}
        EdgeHandleFeatures.dictationCancel()
        preferences.preferences.unregisterOnSharedPreferenceChangeListener(listener)
        handler.removeCallbacksAndMessages(null)
        removeWindow()
    }

    private fun dimensions(): Pair<Int, Int> {
        val metrics = manager.currentWindowMetrics
        val insets = metrics.windowInsets.getInsets(WindowInsets.Type.ime() or WindowInsets.Type.systemBars())
        return metrics.bounds.width() to (metrics.bounds.height() - insets.top - insets.bottom)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    @android.annotation.SuppressLint("RtlHardcoded") // Dock coordinates are physical left/right, not reading direction.
    private fun params(
        width: Int,
        height: Int,
    ): WindowManager.LayoutParams =
        WindowManager
            .LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                title = context.getString(R.string.edge_handle_title)
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

    // Mutually exclusive tiny marker and compact result presentations.
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun showHandle() {
        val (width, height) = dimensions()
        val size = dp(TOUCH_WIDTH_DP)
        val micEnabled = EdgeHandleFeatures.registry?.enabled(EdgeHandleFeatures.DICTATION_ID) == true
        val hasStatus = micEnabled && EdgeHandleFeatures.dictationStatus != 0
        val hasScreenshot = EdgeHandleFeatures.screenshotStatus != 0
        val hasRecording = EdgeHandleFeatures.recordingMessage.isNotEmpty()
        val handleWidth =
            when {
                EdgeHandleFeatures.recordingActive && !recordingExpanded -> size
                hasRecording -> dp(PANEL_WIDTH_DP)
                hasScreenshot -> dp(PANEL_WIDTH_DP)
                !hasStatus -> size
                EdgeHandleFeatures.dictationInserted -> dp(PILL_WIDTH_DP)
                else -> dp(PANEL_WIDTH_DP)
            }
        val params =
            params(
                handleWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
        params.x = if (preferences.rightEdge) width - params.width else 0
        params.y = ((height - dp(TOUCH_HEIGHT_DP)) * preferences.verticalFraction).toInt()
        val handle = FrameLayout(context)
        handle.contentDescription = context.getString(R.string.edge_handle_open)
        handle.isClickable = true

        val bar = View(context)
        bar.background =
            GradientDrawable().apply {
                setColor(HANDLE_COLOR)
                cornerRadius = dp(BAR_RADIUS_DP).toFloat()
            }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val grip = FrameLayout(context)
        grip.addView(bar, FrameLayout.LayoutParams(dp(BAR_WIDTH_DP), dp(BAR_HEIGHT_DP), Gravity.CENTER))
        if (!EdgeHandleFeatures.recordingActive) {
            column.addView(grip, LinearLayout.LayoutParams(size, dp(TOUCH_HEIGHT_DP)))
        }
        if (hasRecording) {
            addRecordingControls(column)
        } else if (hasScreenshot) {
            addScreenshotControls(column)
        } else if (hasStatus) {
            addDictationControls(column)
        }
        handle.addView(column)
        handle.setOnClickListener { showPanel() }
        handle.setOnLongClickListener {
            showPanel()
            true
        }
        bindDrag(handle, params)
        attach(handle, params)
        if (view === handle && !preferences.hintShown) {
            android.widget.Toast
                .makeText(context, R.string.edge_handle_hint, android.widget.Toast.LENGTH_LONG)
                .show()
            preferences.hintShown = true
        }
    }

    private fun addScreenshotControls(column: LinearLayout) {
        column.addView(
            TextView(context).apply {
                setText(EdgeHandleFeatures.screenshotStatus)
                textSize = RESULT_TEXT_SP
                setBackgroundColor(Color.WHITE)
                setTextColor(Color.BLACK)
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            },
        )
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        if (EdgeHandleFeatures.screenshotShareAvailable) {
            addButton(actions, R.string.edge_screenshot_share) { EdgeHandleFeatures.screenshotShare(context) }
        }
        addButton(actions, R.string.edge_screenshot_done) { EdgeHandleFeatures.screenshotDismiss() }
        column.addView(actions)
    }

    private fun addDictationControls(column: LinearLayout) {
        if (EdgeHandleFeatures.dictationBusy) {
            addButton(column, R.string.edge_dictation_mic, android.R.drawable.ic_btn_speak_now) {
                EdgeHandleFeatures.dictationTap(context)
            }
        }
        val status = EdgeHandleFeatures.dictationStatus
        if (status != 0) {
            addResultText(column, status)
            if (EdgeHandleFeatures.dictationBusy) {
                if (!EdgeHandleFeatures.dictationCommitting) {
                    addButton(column, android.R.string.cancel) { EdgeHandleFeatures.dictationCancel() }
                }
            } else {
                addResultActions(column)
            }
        }
    }

    private fun addRecordingControls(column: LinearLayout) {
        recordingStatusView = addRecordingHandleCard(column, recordingExpanded, ::expandRecording)
    }

    private fun expandRecording() {
        recordingExpanded = true
        removeWindow()
        refresh()
        handler.postDelayed({
            if (EdgeHandleFeatures.recordingActive) {
                recordingExpanded = false
                removeWindow()
                refresh()
            }
        }, HandlePresentation.COLLAPSE_MS)
    }

    private fun addResultText(
        column: LinearLayout,
        status: Int,
    ) {
        val transcript = EdgeHandleFeatures.dictationTranscript
        val text =
            TextView(context).apply {
                if (transcript != null) {
                    this.text = transcript
                    setTextIsSelectable(true)
                } else {
                    setText(if (EdgeHandleFeatures.dictationInserted) R.string.edge_dictation_inserted else status)
                    maxLines = STATUS_LINES
                }
                textSize = RESULT_TEXT_SP
                setPadding(dp(RESULT_PADDING_DP), dp(RESULT_PADDING_DP), dp(RESULT_PADDING_DP), dp(RESULT_PADDING_DP))
                setBackgroundColor(Color.WHITE)
                setTextColor(Color.BLACK)
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
        column.addView(
            ScrollView(context).apply { addView(text) },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(if (EdgeHandleFeatures.dictationInserted) PILL_HEIGHT_DP else RESULT_HEIGHT_DP),
            ),
        )
    }

    private fun addResultActions(column: LinearLayout) {
        if (EdgeHandleFeatures.dictationInserted) return
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        if (EdgeHandleFeatures.dictationTranscript != null) {
            addButton(actions, R.string.edge_dictation_copy) { copyResult() }
        } else {
            addButton(actions, R.string.edge_dictation_settings) { EdgeHandleFeatures.dictationSettings(context) }
        }
        addButton(actions, R.string.edge_dictation_dismiss) { dismissResult() }
        column.addView(actions)
    }

    private fun copyResult() {
        EdgeHandleFeatures.dictationTranscript?.let { transcript ->
            val clip = ClipData.newPlainText("EqoDictation", transcript)
            clip.description.extras =
                PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        }
    }

    private fun dismissResult(preservePanel: Boolean = false) {
        handler.removeCallbacks(resultTask)
        EdgeHandleFeatures.dictationTranscript = null
        EdgeHandleFeatures.dictationInserted = false
        if (!EdgeHandleFeatures.dictationBusy) EdgeHandleFeatures.dictationStatus = 0
        if (!preservePanel || !guard.panelOpen) {
            removeWindow()
            refresh()
        }
    }

    private fun hidden(): Boolean = preferences.isHidden(foregroundPackage) || presentation.hidden(foregroundPackage)

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
                        params.y = (startY + event.rawY - downY).toInt().coerceIn(0, maxOf(0, height - handle.height))
                        params.x = if (event.rawX > width / 2f) width - params.width else 0
                        fitWindow(handle, params, forceLayout = true)
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
                        preferences.verticalFraction =
                            params.y.toFloat() / maxOf(1, dimensions().second - handle.height)
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
    @Suppress("CyclomaticComplexMethod") // Shortcut rows share one guarded panel lifecycle.
    private fun showPanel(requestPause: Boolean = true) {
        val unavailable = !preferences.enabled || !probeAvailable || hidden()
        if (unavailable || EdgeHandleFeatures.screenshotHidden) return
        if (!panelReady(requestPause)) return
        guard.panelOpen = true
        presentation.open()
        scheduleCollapse()
        val root = PanelView()
        val specs =
            panelActionSpecs(
                shortcutSpecs(),
                foregroundPackage != context.packageName,
            )
        val column = panelGrid(specs, panelHandlers())
        val (width, height) = dimensions()
        val panelWidth = dp(panelWidthDp(PANEL_COLUMNS)).coerceAtMost(width)
        // Measure the icon grid, including theme insets and spacing, before sizing the window.
        column.measure(
            View.MeasureSpec.makeMeasureSpec(panelWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val contentHeight = column.measuredHeight
        root.addView(
            // Pinning the measured content height keeps the grid scrollable whenever the window is
            // capped and exact-fit when it is not, whatever height rule the ScrollView applies.
            ScrollView(context).apply {
                addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, contentHeight))
            },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        tooltipHovering = false
        tooltipHolding = false
        tooltipChip = tooltipChipView().also { root.addView(it) }
        val panelParams = params(panelWidth, panelHeight(contentHeight, height))
        panelParams.x = if (preferences.rightEdge) width - panelParams.width else 0
        panelParams.y = ((height - panelParams.height) * preferences.verticalFraction).toInt()
        attach(root, panelParams)
    }

    private fun shortcutSpecs(): List<PanelShortcutSpec> =
        EdgeHandleFeatures.registry
            ?.visible(context)
            .orEmpty()
            .map { PanelShortcutSpec(it.id, it.iconRes, it.labelRes) }

    private fun panelHandlers(): Map<String, () -> Unit> {
        val handlers = mutableMapOf<String, () -> Unit>()
        handlers[PANEL_KEY_CLOSE] = { closePanel() }
        handlers[PANEL_KEY_HIDE_NOW] = {
            EdgeHandleFeatures.dictationCancel()
            presentation.hide(foregroundPackage)
            dismissResult()
        }
        EdgeHandleFeatures.registry?.visible(context)?.forEach { shortcut ->
            handlers[shortcut.id] = {
                closePanel()
                // Availability is checked again; a run may have ended while the panel was open.
                if (shortcut.unavailableReason(context) == null) shortcut.run(context)
            }
        }
        handlers[PANEL_KEY_HIDE_APP] = {
            preferences.hide(foregroundPackage)
            removeWindow()
        }
        return handlers
    }

    private fun panelGrid(
        specs: List<PanelActionSpec>,
        handlers: Map<String, () -> Unit>,
    ): LinearLayout {
        val column =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP))
                isClickable = true
            }
        panelRows(specs.size, PANEL_COLUMNS).forEach { rowSpan ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            specs.slice(rowSpan).forEach { spec ->
                row.addView(panelCell(spec, handlers.getValue(spec.key)), panelCellLayout())
            }
            if (rowSpan.count() < PANEL_COLUMNS) {
                row.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            }
            column.addView(row)
        }
        return column
    }

    private fun panelCellLayout(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(PANEL_CELL_DP), dp(PANEL_CELL_DP)).apply {
            val gap = dp(PANEL_CELL_GAP_DP) / 2
            setMargins(gap, gap, gap, gap)
        }

    /** One icon button: 40dp tonal circle on a 48dp cell, named for TalkBack and UI tests. */
    private fun panelCell(
        spec: PanelActionSpec,
        action: () -> Unit,
    ): View {
        val cell = FrameLayout(context)
        cell.id = spec.viewId
        cell.contentDescription = context.getString(spec.labelRes)
        val icon = ImageView(context)
        icon.setImageResource(spec.iconRes)
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        val iconPadding = dp(PANEL_ICON_PADDING_DP)
        icon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
        icon.background =
            GradientDrawable().apply {
                setColor(color(R.color.eqo_handle_icon_surface))
                cornerRadius = dp(PANEL_ICON_DP) / 2f
            }
        cell.addView(icon, FrameLayout.LayoutParams(dp(PANEL_ICON_DP), dp(PANEL_ICON_DP), Gravity.CENTER))
        // A drag inside the panel scrolls; it must never fire a row action or close it.
        cell.setOnClickListener { if (!panelDragged) action() }
        cell.setOnTouchListener(tooltipHold(cell))
        cell.setOnHoverListener(tooltipHover(cell))
        return cell
    }

    private fun color(res: Int): Int = context.resources.getColor(res, context.theme)

    /** Small rounded name chip; never clickable so the next tap still reaches its icon. */
    private fun tooltipChipView(): TextView =
        TextView(context).apply {
            textSize = TOOLTIP_TEXT_SP
            setTextColor(color(R.color.eqo_handle_tooltip_text))
            background =
                GradientDrawable().apply {
                    setColor(color(R.color.eqo_handle_tooltip_surface))
                    cornerRadius = dp(TOOLTIP_RADIUS_DP).toFloat()
                }
            val padding = dp(TOOLTIP_PADDING_DP)
            setPadding(padding, padding, padding, padding)
            visibility = View.GONE
            isClickable = false
            isLongClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

    private fun tooltipHold(anchor: View): View.OnTouchListener =
        View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pendingTooltip?.let(handler::removeCallbacks)
                    pendingTooltip =
                        Runnable {
                            tooltipHolding = true
                            syncTooltip(anchor)
                        }.also { handler.postDelayed(it, TOOLTIP_LONG_PRESS_MS) }
                }
                MotionEvent.ACTION_MOVE -> if (panelDragged) dismissTooltip()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dismissTooltip()
            }
            false
        }

    private fun tooltipHover(anchor: View): View.OnHoverListener =
        View.OnHoverListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    tooltipHovering = true
                    syncTooltip(anchor)
                }
                MotionEvent.ACTION_HOVER_MOVE -> syncTooltip(anchor)
                MotionEvent.ACTION_HOVER_EXIT -> {
                    // The name disappears the moment the pointer leaves, held or not.
                    tooltipHovering = false
                    dismissTooltip()
                }
            }
            false
        }

    private fun syncTooltip(anchor: View) {
        if (tooltipVisible(tooltipHovering, tooltipHolding)) {
            showTooltip(anchor)
        } else {
            tooltipChip?.visibility = View.GONE
        }
    }

    private fun dismissTooltip() {
        pendingTooltip?.let(handler::removeCallbacks)
        pendingTooltip = null
        tooltipHolding = false
        tooltipChip?.visibility = View.GONE
    }

    private fun showTooltip(anchor: View) {
        val panel = view ?: return
        val chip = tooltipChip ?: return
        chip.text = anchor.contentDescription
        chip.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val placement =
            tooltipPlacement(
                anchorRect(anchor, panel),
                PanelRect(0, 0, panel.width, panel.height),
                chip.measuredWidth,
                chip.measuredHeight,
                dp(TOOLTIP_GAP_DP),
            )
        chip.x = placement.x.toFloat()
        chip.y = placement.y.toFloat()
        chip.visibility = View.VISIBLE
    }

    private fun anchorRect(
        anchor: View,
        panel: View,
    ): PanelRect {
        val panelLocation = IntArray(2)
        panel.getLocationOnScreen(panelLocation)
        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val left = anchorLocation[0] - panelLocation[0]
        val top = anchorLocation[1] - panelLocation[1]
        return PanelRect(left, top, left + anchor.width, top + anchor.height)
    }

    private fun scheduleCollapse() {
        handler.removeCallbacks(collapseTask)
        handler.postDelayed(collapseTask, HandlePresentation.COLLAPSE_MS)
    }

    private fun addButton(
        parent: LinearLayout,
        label: Int,
        icon: Int = 0,
        action: () -> Unit,
    ) {
        val layout =
            if (parent.orientation == LinearLayout.HORIZONTAL) {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            } else {
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
        parent.addView(
            Button(context).apply {
                setText(label)
                minHeight = dp(TOUCH_WIDTH_DP)
                if (icon != 0) setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
                setOnClickListener { action() }
            },
            layout,
        )
    }

    private fun closePanel() {
        handler.removeCallbacks(openPanelTask)
        handler.removeCallbacks(collapseTask)
        presentation.collapse()
        if (!guard.panelOpen) return
        removeWindow()
        render()
    }

    private fun attach(
        target: View,
        params: WindowManager.LayoutParams,
    ) {
        target.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (target.isAttachedToWindow) {
                fitWindow(target, params)
            }
            updateBounds(target)
        }
        target.setOnApplyWindowInsetsListener { _, insets ->
            if (target.isAttachedToWindow) fitWindow(target, params)
            insets
        }
        try {
            manager.addView(target, params)
            view = target
            updateBounds(target)
        } catch (_: RuntimeException) {
            guard.clear()
        }
    }

    private fun updateBounds(target: View) {
        if (target.visibility != View.VISIBLE) {
            guard.bounds = null
            return
        }
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

    private fun fitWindow(
        target: View,
        params: WindowManager.LayoutParams,
        forceLayout: Boolean = false,
    ) {
        val ime = target.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0
        val metrics = manager.currentWindowMetrics
        val top = metrics.windowInsets.getInsets(WindowInsets.Type.systemBars()).top
        val height = minOf(dimensions().second, metrics.bounds.height() - top - ime)
        val input = focusedBounds()
        val fitted = fitHandleY(params.y, target.height, height, input?.top?.minus(top), input?.bottom?.minus(top))
        target.visibility = if (fitted == null) View.INVISIBLE else View.VISIBLE
        if (fitted != null && (params.y != fitted || forceLayout)) {
            params.y = fitted
            manager.updateViewLayout(target, params)
        }
        updateBounds(target)
    }

    private fun focusedBounds(): Rect? {
        val root = (context as? EQOAccessibilityService)?.rootInActiveWindow ?: return null
        val focused =
            try {
                root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
            } finally {
                root.recycle()
            }
        return try {
            focused?.let { Rect().apply { it.getBoundsInScreen(this) } }
        } finally {
            focused?.recycle()
        }
    }

    private fun removeWindow(): Boolean {
        handler.removeCallbacks(openPanelTask)
        handler.removeCallbacks(collapseTask)
        presentation.collapse()
        pendingLongPress?.let(handler::removeCallbacks)
        pendingLongPress = null
        pendingTooltip?.let(handler::removeCallbacks)
        pendingTooltip = null
        tooltipChip = null
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

    private fun trackPanelGesture(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                panelDownX = event.rawX
                panelDownY = event.rawY
                panelGestureActive = true
                panelDragged = false
            }
            MotionEvent.ACTION_MOVE ->
                if (abs(event.rawX - panelDownX) > panelTouchSlop || abs(event.rawY - panelDownY) > panelTouchSlop) {
                    panelDragged = true
                }
        }
    }

    private inner class PanelView : FrameLayout(context) {
        init {
            id = R.id.eqo_handle_panel
            background =
                GradientDrawable().apply {
                    setColor(color(R.color.eqo_handle_panel_surface))
                    cornerRadius = dp(PANEL_RADIUS_DP).toFloat()
                }
            clipToOutline = true
            elevation = dp(PANEL_ELEVATION_DP).toFloat()
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                // Only an outside tap dismisses; a gesture already inside the panel must never be
                // reinterpreted as one (Android also reports ACTION_OUTSIDE during moved windows).
                if (!panelGestureActive) closePanel()
                return true
            }
            trackPanelGesture(event)
            presentation.interact()
            scheduleCollapse()
            val handled = super.dispatchTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                panelGestureActive = false
                // panelDragged stays set until the next gesture's DOWN: View may deliver the tap
                // click after this dispatch returns and it must still see the drag suppression.
            }
            return handled
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) closePanel()
                true
            } else {
                super.dispatchKeyEvent(event)
            }
    }

    private companion object {
        // HandlePreferences keeps the shortcut choice in the same store: pure bookkeeping writes.
        val CHOICE_KEYS = setOf("order", "known", "enabled")
        const val PANEL_SETTLE_POLL_MS = 50L
        const val TOUCH_WIDTH_DP = 48
        const val TOUCH_HEIGHT_DP = 72
        const val BAR_WIDTH_DP = 8
        const val BAR_HEIGHT_DP = 56
        const val BAR_RADIUS_DP = 4
        const val SWIPE_IN_DP = 32
        const val PANEL_WIDTH_DP = 240
        const val PANEL_ELEVATION_DP = 6
        const val PANEL_ICON_PADDING_DP = 8
        const val TOOLTIP_RADIUS_DP = 8
        const val TOOLTIP_PADDING_DP = 8
        const val TOOLTIP_TEXT_SP = 12f
        const val PILL_WIDTH_DP = 120
        const val PILL_HEIGHT_DP = 32
        const val RESULT_HEIGHT_DP = 64
        const val RESULT_PADDING_DP = 6
        const val RESULT_TEXT_SP = 14f
        const val STATUS_LINES = 3

        const val HANDLE_COLOR = -1773116276 // ARGB 150,80,100,140
    }
}
