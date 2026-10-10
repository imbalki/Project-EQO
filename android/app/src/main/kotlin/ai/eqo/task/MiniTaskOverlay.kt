package ai.eqo.task

import ai.eqo.MainActivity
import ai.eqo.R
import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.handle.HandleEntry
import ai.eqo.accessibility.handle.HandleWindowGuard
import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.TaskPlanPreview
import ai.eqo.data.models.PlanStatus
import ai.eqo.handle.HandleReturn
import ai.eqo.study.StepProgressState
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One in-memory panel. The foreground service, not this view, owns the approved run. */
@Suppress("TooManyFunctions", "LargeClass") // Window lifecycle and widgets share one attachment owner.
internal class MiniTaskOverlay(
    private val service: EQOAccessibilityService,
    private val entry: HandleEntry?,
    private val prepare: suspend (String) -> ApprovedTaskPlan = { MiniTaskPreparation.prepare(service, it) },
    private val startRun: (ApprovedTaskPlan) -> Unit = { plan ->
        TaskRunSession.pending = plan
        TaskRunSession.status = PlanStatus.RUNNING
        service.startForegroundService(android.content.Intent(service, TaskRunService::class.java))
    },
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val manager = service.getSystemService(WindowManager::class.java)
    private var attached = false
    private var closed = false
    private var collapsed = false
    private var generation = 0
    private var preparing = false
    private var starting = false
    private var inputsBlocked = false
    private var waitingMicrophone = false
    private val microphoneReturned: () -> Unit = {
        if (!closed) {
            waitingMicrophone = false
            voice?.refreshAvailability()
            applyLayout()
        }
    }
    private var showingRun = TaskRunSession.pending != null ||
        TaskRunSession.controller != null || TaskRunSession.receipt != null
    private var approved: ApprovedTaskPlan? = null
    private var voice: TaskVoiceInput? = null
    private val column = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
    private val root =
        object : LinearLayout(service) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
                if (event.action == KeyEvent.ACTION_UP) close()
                return true
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(BACKGROUND_ALPHA, 24, 28, 36))
            isSaveEnabled = false
        }
    private val status =
        TextView(service).apply {
            setTextColor(Color.WHITE)
            textSize = PREVIEW_SP
            ellipsize = android.text.TextUtils.TruncateAt.END
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
    private val draft =
        EditText(service).apply {
            id = R.id.task_request
            setHint(R.string.task_request_hint)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            filters = arrayOf(android.text.InputFilter.LengthFilter(MAX_REQUEST))
        }
    private val preview =
        TextView(service).apply {
            setTextColor(Color.WHITE)
            textSize = PREVIEW_SP
        }
    private val approve =
        Button(service).apply {
            setText(R.string.task_approval_approve)
            isEnabled = false
        }
    private val params =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                0,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

    fun open() {
        build()
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> bounds() }
        applyLayout()
        try {
            manager.addView(root, params)
            attached = true
            bounds()
            voice =
                TaskVoiceInput(service, root = root, overlayPermission = {
                    waitingMicrophone = true
                    MiniVoicePermissionActivity.completed = microphoneReturned
                    applyLayout()
                    service.startActivity(
                        android.content
                            .Intent(service, MiniVoicePermissionActivity::class.java)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                })
            watchDraft()
            scope.launch { watchRun() }
        } catch (_: WindowManager.BadTokenException) {
            close()
        } catch (_: WindowManager.InvalidDisplayException) {
            close()
        }
    }

    private fun build() {
        val header = LinearLayout(service)
        val drag =
            button(header, R.string.mini_drag) {
                collapsed = !collapsed
                applyLayout()
            }
        bindDrag(drag)
        button(header, R.string.handle_open) { fullEqo() }
        button(header, R.string.explain_close) { close() }
        for (index in 0 until header.childCount) {
            header.getChildAt(index).layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        root.addView(header)
        root.addView(status)
        root.addView(ScrollView(service).apply { addView(column) }, LinearLayout.LayoutParams(-1, 0, 1f))
        column.addView(draft)
        button(column, R.string.voice_button) {}.id = R.id.task_voice_button
        column.addView(
            TextView(service).apply {
                id = R.id.task_voice_state
                setTextColor(Color.WHITE)
            },
        )
        button(column, R.string.mini_plan) { plan() }
        column.addView(preview)
        column.addView(approve)
        protectConfirmationTouches(approve)
        approve.setOnClickListener { acceptPlan() }
        button(column, android.R.string.cancel) { cancelPreparation() }
        val controls = LinearLayout(service)
        button(controls, R.string.task_pause) {
            cancelQueuedStart()
            TaskRunSession.controller?.pause()
        }
        button(controls, R.string.task_stop) {
            cancelQueuedStart()
            TaskRunSession.controller?.stop()
        }
        root.addView(controls)
        column.addView(
            TextView(service).apply {
                setTextColor(Color.WHITE)
                setText(R.string.mini_takeover_info)
            },
        )
        status.setText(R.string.task_idle)
    }

    private fun button(
        parent: LinearLayout,
        label: Int,
        action: () -> Unit,
    ): Button =
        Button(service).apply {
            setText(label)
            minHeight = dp(TOUCH_DP)
            setOnClickListener { action() }
            parent.addView(this)
        }

    private fun watchDraft() {
        draft.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    cancelPreparation()
                }

                override fun afterTextChanged(s: android.text.Editable?) = Unit
            },
        )
    }

    private fun active(): Boolean = starting || TaskRunSession.pending != null || TaskRunSession.controller != null

    private fun cancelPreparation() {
        generation++
        approved = null
        approve.isEnabled = false
        preview.text = ""
    }

    private fun cancelQueuedStart() {
        cancelPreparation()
        if (TaskRunSession.controller == null && (starting || TaskRunSession.pending != null)) {
            TaskRunSession.pending = null
            starting = false
            showingRun = false
            status.setText(R.string.task_cancelled)
        }
    }

    @Suppress("TooGenericExceptionCaught") // Never display or log raw planner, request or credential failures.
    private fun plan() {
        if (preparing || active()) return
        val request = draft.text.toString().trim()
        if (request.isBlank()) {
            status.setText(R.string.task_request_empty)
            return
        }
        cancelPreparation()
        showingRun = false
        val revision = generation
        preparing = true
        voice?.pause()
        status.setText(R.string.task_planning)
        scope.launch {
            try {
                val result = prepare(request)
                if (generation == revision && !closed) {
                    approved = result
                    preview.text = TaskPlanPreview.describe(result.steps())
                    approve.isEnabled = true
                    status.setText(R.string.mini_ready)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: MiniAccessNeeded) {
                if (generation == revision) status.setText(R.string.mini_access_needed)
            } catch (_: Exception) {
                if (generation == revision) status.setText(R.string.task_plan_invalid)
            } finally {
                preparing = false
                if (!closed) voice?.refreshAvailability()
            }
        }
    }

    private fun acceptPlan() {
        val plan = approved ?: return
        if (active() || !plan.matches(plan.steps())) return
        approved = null
        approve.isEnabled = false
        starting = true
        showingRun = true
        val revision = generation
        voice?.pause()
        draft.clearFocus()
        service
            .getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            .hideSoftInputFromWindow(root.windowToken, 0)
        applyLayout()
        scope.launch {
            // Let Android restore the underlying app as active before any agent observation.
            delay(WINDOW_SETTLE_MS)
            if (!closed && revision == generation) startRun(plan)
            starting = false
        }
    }

    private suspend fun watchRun() {
        while (!closed) {
            if (EQOAccessibilityService.getInstance() !== service) {
                close()
                break
            }
            if (showingRun) renderRun()
            updateInputs()
            applyLayout()
            delay(REFRESH_MS)
        }
    }

    private fun updateInputs() {
        val blocked = active() || preparing
        draft.isEnabled = !active()
        if (blocked) {
            root.findViewById<Button>(R.id.task_voice_button).isEnabled = false
        } else if (inputsBlocked) {
            voice?.refreshAvailability()
        }
        inputsBlocked = blocked
    }

    private fun renderRun() {
        val step = TaskRunSession.progress.values.lastOrNull { it.state == StepProgressState.RUNNING }
        val receipt = TaskRunSession.receipt.takeIf { !active() }
        val state =
            when (receipt?.let(RunStatusMapping::terminal) ?: TaskRunSession.status.name) {
                "RUNNING" -> R.string.task_running
                "PAUSED" -> R.string.task_paused
                "COMPLETED" -> R.string.task_completed
                "STOPPED" -> R.string.task_stopped
                "NEEDS_YOU" -> R.string.task_needs_you
                "CANCELLED" -> R.string.task_cancelled
                "FAILED" -> R.string.task_failed
                else -> R.string.task_pending
            }
        val final =
            receipt
                ?.steps
                ?.lastOrNull()
                ?.let {
                    RunStatusMapping.detail(it)?.let { detail -> service.getString(detail.resource, detail.argument) }
                        ?: it.detail
                }.orEmpty()
        status.text =
            service.getString(
                R.string.mini_status,
                service.getString(state),
                step
                    ?.name
                    ?.lowercase()
                    ?.replace('_', ' ')
                    .orEmpty(),
                final,
            )
    }

    private fun applyLayout() {
        val height =
            when {
                waitingMicrophone -> 1
                collapsed -> dp(CHIP_DP)
                else -> (manager.currentWindowMetrics.bounds.height() * HEIGHT).toInt()
            }
        val flags =
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_SECURE or
                if (waitingMicrophone) {
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                } else if (active() || collapsed) {
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                } else {
                    0
                }
        root.alpha = if (waitingMicrophone) 0f else 1f
        column.visibility = if (collapsed) View.GONE else View.VISIBLE
        status.maxLines = if (collapsed) 1 else STATUS_LINES
        root.getChildAt(root.childCount - 1).visibility = if (collapsed) View.GONE else View.VISIBLE
        if (height != params.height || flags != params.flags) {
            params.height = height
            params.flags = flags
            if (attached) manager.updateViewLayout(root, params)
        }
    }

    private fun bindDrag(view: View) {
        var down = 0f
        var initial = 0
        view.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    down = event.rawY
                    initial = params.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val maximum = (manager.currentWindowMetrics.bounds.height() - params.height).coerceAtLeast(0)
                    params.y = (initial + down - event.rawY).toInt().coerceIn(0, maximum)
                    if (attached) manager.updateViewLayout(root, params)
                }
                MotionEvent.ACTION_UP -> if (kotlin.math.abs(down - event.rawY) < dp(DRAG_DP)) target.performClick()
            }
            true
        }
    }

    private fun bounds() {
        val location = IntArray(2)
        root.getLocationOnScreen(location)
        HandleWindowGuard.shared.compactBounds =
            HandleWindowGuard.Bounds(
                location[0],
                location[1],
                location[0] + root.width,
                location[1] + root.height,
            )
    }

    private fun fullEqo() {
        service.startActivity(HandleReturn.intent(service, MainActivity::class.java, entry))
        close()
    }

    fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        voice?.close()
        if (MiniVoicePermissionActivity.completed === microphoneReturned) MiniVoicePermissionActivity.completed = null
        if (attached) {
            try {
                manager.removeViewImmediate(root)
            } catch (_: IllegalArgumentException) {
                // Service disconnect may already have removed this window.
                if (root.isAttachedToWindow) throw IllegalStateException("Compact window still attached")
            }
        }
        attached = false
        HandleWindowGuard.shared.compactBounds = null
        draft.text.clear()
        approved = null
        if (current === this) current = null
        service.redrawHandle()
    }

    private fun dp(value: Int): Int = (service.resources.displayMetrics.density * value).toInt()

    companion object {
        private var current: MiniTaskOverlay? = null
        private const val HEIGHT = 0.30f
        private const val BACKGROUND_ALPHA = 217
        private const val TOUCH_DP = 48
        private const val CHIP_DP = 96
        private const val DRAG_DP = 12
        private const val PREVIEW_SP = 14f
        private const val STATUS_LINES = 3
        private const val MAX_REQUEST = 8000
        private const val REFRESH_MS = 250L
        private const val WINDOW_SETTLE_MS = 350L

        fun show(
            service: EQOAccessibilityService,
            entry: HandleEntry?,
        ) {
            current?.close()
            val panel = MiniTaskOverlay(service, entry)
            current = panel
            panel.open()
        }
    }
}
