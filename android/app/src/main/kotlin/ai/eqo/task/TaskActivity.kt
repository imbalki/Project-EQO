/*
 * EQO (TASK-015, issue #20): the task screen (UF-09 / UF-10, REQ-TASK-01..07).
 *
 * Progress per step, contextual approval cards with the 60s countdown, Pause != Stop,
 * takeover, honest "unknown result" rows and an end-of-run receipt. The loop is
 * core-agent's ActionLoop, wired with the REAL permission check and the REAL approval
 * path (see `StudyLoopWiring`).
 *
 * SF-4: resume confirmations are minted only by explicit user gesture handlers:
 * the Run button (a new plan) and the resume dialog's positive button (a paused plan).
 * The SF-4 guard test allowlists exactly these handlers, never agent/recovery code.
 */
package ai.eqo.task

import ai.eqo.R
import ai.eqo.accessibility.TakeoverDetector
import ai.eqo.accessibility.UntrustedScreenText
import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.LoopState
import ai.eqo.core.agent.TaskPlanPreview
import ai.eqo.core.agent.UserResumeConfirmation
import ai.eqo.core.llm.error.LLMException
import ai.eqo.data.models.PlanStatus
import ai.eqo.helper.client.HelperActivationState
import ai.eqo.onboarding.SetupHubActivity
import ai.eqo.onboarding.StudySetup
import ai.eqo.study.ApprovalOutcome
import ai.eqo.study.ApprovalRequest
import ai.eqo.study.FailureClass
import ai.eqo.study.RunReceipt
import ai.eqo.study.StepProgress
import ai.eqo.study.StepProgressState
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Telephony
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

// One screen owns run control + approval cards + receipts; splitting it would scatter the
// SF-4 mint site across files (same reasoning as ActionLoop's own @Suppress precedent).
@Suppress("TooManyFunctions", "LargeClass")
class TaskActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var controller: StudyTaskController? = null
    private var voiceInput: TaskVoiceInput? = null
    private var waitingPermission: String? = null
    private var displayedStatus = PlanStatus.PENDING
    private var preparationGeneration = 0
    private val actionPermissions by lazy {
        TaskPermissionRequester(
            this,
            ::prepareTaskDialog,
            onWaiting = { permission ->
                waitingPermission = permission
                renderPlanStatus(displayedStatus)
            },
            canRequest = { TaskRunSession.controller == null && TaskRunSession.pending == null && controller == null },
        )
    }

    // TASK-069: foundation entry point for ported steps. Typed-request flow remains
    // unchanged; its later integration must call this registry after action approval.
    internal val portedActions by lazy {
        ai.eqo.actions.impl.AndroidActionRegistry
            .create(this, actionPermissions, ::liveAutomation)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        voiceInput?.onPermissionResult(requestCode)
        actionPermissions.onRequestPermissionsResult(requestCode)
    }

    override fun onResume() {
        super.onResume()
        voiceInput?.refreshAvailability()
        actionPermissions.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.task_screen)
        voiceInput = TaskVoiceInput(this)
        registerDebugPlanReceiver()
        val startButton = findViewById<Button>(R.id.task_start_button)

        startButton.setOnClickListener {
            // A NEW run is an explicit hand-back, not an agent/recovery reset.
            val confirmation = UserResumeConfirmation.forExplicitUserConfirmation(SystemClock.elapsedRealtime())
            TakeoverDetector.shared.resume(confirmation)
            planRequest()
        }
        findViewById<Button>(R.id.task_pause_button).setOnClickListener { control { it.pause() } }
        findViewById<Button>(R.id.task_stop_button).setOnClickListener {
            preparationGeneration++
            actionPermissions.cancelWaiting()
            control { it.stop() }
        }
        findViewById<Button>(R.id.task_takeover_button).setOnClickListener { control { it.takeover() } }
        val resumeButton = findViewById<Button>(R.id.task_resume_button)
        protectConfirmationTouches(resumeButton)
        resumeButton.setOnClickListener { confirmResume() }
        TaskRunSession.permissionRequester = actionPermissions
        TaskRunSession.observer = {
            mainHandler.post {
                renderPlanStatus(TaskRunSession.status)
                renderSteps(TaskRunSession.progress.values.toList())
                TaskRunSession.receipt?.let(::renderReceipt)
            }
        }
        renderSteps(TaskRunSession.progress.values.toList())
        protectConfirmationTouches(findViewById<Button>(R.id.task_start_button))
        renderPlanStatus(PlanStatus.PENDING)
        if (TaskRunSession.controller == null && TaskRunSession.pending == null && TaskRunSession.receipt == null) {
            findViewById<TextView>(R.id.task_state).setText(R.string.task_idle)
        } else {
            renderPlanStatus(TaskRunSession.status)
            TaskRunSession.receipt?.let(::renderReceipt)
        }
        findViewById<Button>(R.id.task_setup_button).setOnClickListener {
            (TaskRunSession.controller ?: controller)?.takeover()
            val destination =
                if (liveAutomation() == null) {
                    ai.eqo.onboarding.AccessibilitySetupActivity::class.java
                } else {
                    SetupHubActivity::class.java
                }
            startActivity(Intent(this, destination))
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        TakeoverDetector.shared.setControlTouchExclusion(
            if (hasFocus) ({ x, y -> touchesControl(x, y) }) else null,
        )
    }

    override fun onPause() {
        voiceInput?.pause()
        actionPermissions.onPause()
        TakeoverDetector.shared.setControlTouchExclusion(null)
        super.onPause()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !touchesControl(event.rawX.toInt(), event.rawY.toInt())) {
            reportBackgroundTouch()
        }
        return super.dispatchTouchEvent(event)
    }

    private fun reportBackgroundTouch() {
        val service =
            ai.eqo.accessibility.EQOAccessibilityService
                .getInstance()
        if (service != null) {
            service.reportControlSurfaceTouch()
        } else {
            TakeoverDetector.shared.onTouch(TakeoverDetector.TouchSource.USER, SystemClock.elapsedRealtime())
        }
    }

    private fun touchesControl(
        x: Int,
        y: Int,
    ): Boolean =
        listOf(
            R.id.task_start_button,
            R.id.task_pause_button,
            R.id.task_stop_button,
            R.id.task_takeover_button,
            R.id.task_resume_button,
            R.id.task_setup_button,
        ).any { id ->
            containsTouch(findViewById(id), x, y)
        }

    private fun containsTouch(
        view: View,
        x: Int,
        y: Int,
    ): Boolean {
        val bounds = Rect()
        return view.getGlobalVisibleRect(bounds) && bounds.contains(x, y)
    }

    /** Exclude only the dialog's explicit confirmation buttons, retaining its touch guard. */
    private fun prepareTaskDialog(dialog: AlertDialog) {
        protectConfirmationDialog(dialog)
        val window = dialog.window ?: return
        val callback = window.callback

        fun isDialogControl(
            x: Int,
            y: Int,
        ): Boolean =
            listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE).any {
                containsTouch(dialog.getButton(it), x, y)
            }

        fun registerControls(focused: Boolean) {
            TakeoverDetector.shared.setControlTouchExclusion(
                if (focused) ({ x, y -> isDialogControl(x, y) }) else null,
            )
        }
        registerControls(true)
        window.decorView.viewTreeObserver.addOnWindowFocusChangeListener { registerControls(it) }
        window.callback =
            object : Window.Callback by callback {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN &&
                        !isDialogControl(event.rawX.toInt(), event.rawY.toInt())
                    ) {
                        reportBackgroundTouch()
                    }
                    return callback.dispatchTouchEvent(event)
                }
            }
        dialog.setOnDismissListener {
            TakeoverDetector.shared.setControlTouchExclusion(
                if (hasWindowFocus()) ({ x, y -> touchesControl(x, y) }) else null,
            )
        }
    }

    /**
     * The one resume confirmation path (SF-4): a dialog the owner taps, and the token is
     * minted here — in the gesture handler — never in loop, recovery or agent code.
     */
    private fun confirmResume() {
        if ((TaskRunSession.controller ?: controller)?.currentState() != LoopState.PAUSED) {
            renderControlFeedback(TaskControlFeedback.NOT_PAUSED)
            return
        }
        AlertDialog
            .Builder(this)
            .setTitle(R.string.task_resume_confirm_title)
            .setMessage(R.string.task_resume_confirm_message)
            .setPositiveButton(R.string.task_resume_confirm_yes) { _, _ ->
                val confirmation = UserResumeConfirmation.forExplicitUserConfirmation(SystemClock.elapsedRealtime())
                (TaskRunSession.controller ?: controller)?.resume(confirmation)
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { prepareTaskDialog(it) }
    }

    private fun startRun(approved: ApprovedTaskPlan) {
        TakeoverDetector.shared.startNewRun()
        if (approved.steps().all { it.action.name == it.action.name.uppercase() }) {
            TaskRunSession.pending = approved
            TaskRunSession.status = PlanStatus.RUNNING
            startForegroundService(Intent(this, TaskRunService::class.java))
            renderPlanStatus(PlanStatus.RUNNING)
            return
        }
        findViewById<TextView>(R.id.task_control_feedback).text = ""
        findViewById<TextView>(R.id.task_receipt).text = ""
        renderPlanStatus(PlanStatus.RUNNING)
        val steps = approved.steps()
        renderSteps(steps.map { StepProgress(it.stepId, it.action.name, StepProgressState.PENDING) })
        val permissionCheck =
            StudyPermissionCheck(
                manifestPermissionGranted = { permission ->
                    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                },
                accessibilityServiceEnabled = { StudySetup.accessibilityServiceEnabled(applicationContext) },
                helperBinderAlive = { StudySetup.helper.state == HelperActivationState.State.ACTIVE },
            )
        val executor = actionExecutor(approved)
        val newController =
            StudyTaskController(
                steps = steps,
                permissionCheck = permissionCheck,
                approvalGate =
                    StudyApprovalGate(
                        approvalSurface(),
                        nowMs = { System.currentTimeMillis() },
                        approvedPlan = approved,
                    ),
                executor = executor,
                observe = ::observeTaskScreen,
                onPlanStatus = { status -> mainHandler.post { renderPlanStatus(status) } },
                onStepProgress = { progress -> mainHandler.post { renderStep(progress) } },
                config = SamplePractice.config,
                isPermissionWaiting = actionPermissions::isWaiting,
                cancelPermissionWait = actionPermissions::cancelWaiting,
                onControlFeedback = { feedback -> mainHandler.post { renderControlFeedback(feedback) } },
                onInterStepWait = { next, total, remaining ->
                    mainHandler.post {
                        findViewById<TextView>(R.id.task_practice_countdown).text =
                            getString(R.string.task_practice_countdown, next, total, SamplePractice.seconds(remaining))
                    }
                },
            )
        controller = newController
        scope.launch {
            val receipt = newController.run()
            mainHandler.post {
                renderReceipt(receipt)
                if (controller === newController) controller = null
            }
        }
    }

    private fun observeTaskScreen(): String {
        val result = liveAutomation()?.observe()
        return (result as? ai.eqo.accessibility.A11yResult.Success)?.detail ?: ""
    }

    private fun actionExecutor(approved: ApprovedTaskPlan): StudyActionExecutor {
        val smsOpener =
            SmsDraftOpener(
                defaultSmsPackage = { Telephony.Sms.getDefaultSmsPackage(this) ?: throw ActivityNotFoundException() },
                onNoSmsApp = { mainHandler.post { renderControlFeedback(TaskControlFeedback.NO_SMS_APP) } },
            ) { intent -> startActivity(intent) }
        val emailOpener = EmailDraftOpener { intent -> startActivity(intent) }
        val launcher = StudyAppLauncher(this)
        return StudyActionExecutor(
            EqoAutomationPort(
                automation = { liveAutomation() },
                composeDraft = { recipient, body -> smsOpener.open(recipient, body) },
                composeEmail = { recipient, subject, body -> emailOpener.open(recipient, subject, body) },
                launchApp = { name -> launcher.open(name) },
            ),
            approvedPlan = approved,
        )
    }

    /** The live automation bridge, or null when the accessibility service is not bound. */
    private fun liveAutomation() =
        ai.eqo.accessibility.EQOAccessibilityService
            .getInstance()
            ?.automation

    private fun approvalSurface(): StudyApprovalSurface = StudyApprovalSurface { request -> askForApproval(request) }

    /**
     * The contextual approval card (REQ-TASK-02): action, target and app, with the 60s
     * countdown surfaced to the user. Expiry cancels the step — nothing runs on a stale
     * approval.
     */
    private suspend fun askForApproval(request: ApprovalRequest): ApprovalOutcome =
        suspendCancellableCoroutine { continuation ->
            val settled = AtomicBoolean(false)
            mainHandler.post {
                val message =
                    buildString {
                        append(
                            getString(
                                R.string.task_approval_context,
                                actionLabel(request.action),
                                approvalTarget(request.target),
                                if (request.app == "the current app") {
                                    getString(R.string.task_current_app)
                                } else {
                                    ai.eqo.core.agent.TaskDisplayText
                                        .escape(request.app)
                                },
                            ),
                        ).append("\n\n")
                        append(
                            approvalCountdown((request.timeoutMs / MS_PER_SECOND).toInt()),
                        )
                    }
                val dialog =
                    AlertDialog
                        .Builder(this)
                        .setTitle(R.string.task_approval_title)
                        .setMessage(message)
                        .setCancelable(true)
                        .setPositiveButton(R.string.task_approval_approve) { _, _ ->
                            if (settled.compareAndSet(false, true)) {
                                countdown.removeCallbacksAndMessages(null)
                                continuation.resume(ApprovalOutcome.Approved)
                            }
                        }.setNegativeButton(R.string.task_approval_reject) { _, _ ->
                            if (settled.compareAndSet(false, true)) {
                                countdown.removeCallbacksAndMessages(null)
                                continuation.resume(ApprovalOutcome.Rejected(getString(R.string.task_rejected)))
                            }
                        }.setOnCancelListener {
                            if (settled.compareAndSet(false, true)) {
                                countdown.removeCallbacksAndMessages(null)
                                continuation.resume(ApprovalOutcome.TimedOut)
                            }
                        }.show()
                        .also { prepareTaskDialog(it) }
                val deadlineMs = request.requestedAtMs + request.timeoutMs
                val tick =
                    object : Runnable {
                        override fun run() {
                            val remaining = ((deadlineMs - System.currentTimeMillis()) / MS_PER_SECOND).toInt()
                            if (remaining <= 0) {
                                if (settled.compareAndSet(false, true)) {
                                    dialog.dismiss()
                                    continuation.resume(ApprovalOutcome.TimedOut)
                                }
                            } else {
                                dialog.setMessage(
                                    message.substringBefore("\n\n") +
                                        "\n\n" +
                                        approvalCountdown(remaining),
                                )
                                countdown.postDelayed(this, MS_PER_SECOND)
                            }
                        }
                    }
                countdown.post(tick)
            }
        }

    private fun approvalCountdown(seconds: Int): String =
        resources.getQuantityString(
            R.plurals.task_approval_countdown,
            seconds,
            seconds,
        )

    private val countdown = Handler(Looper.getMainLooper())

    private fun renderPlanStatus(status: PlanStatus) {
        displayedStatus = status
        findViewById<TextView>(R.id.task_state).text = planLabel(status.name)
        waitingPermission?.let {
            findViewById<TextView>(R.id.task_state).text =
                RunStatusMapping.permissionInstruction(it)
        }
        val running = status == PlanStatus.RUNNING
        val paused = status == PlanStatus.PAUSED
        findViewById<Button>(R.id.task_start_button).isEnabled = !running && !paused
        // Keep controls tappable: unavailable commands explain why rather than silently ignoring taps.
        if (!running) findViewById<TextView>(R.id.task_practice_countdown).text = ""
    }

    private fun control(request: (StudyTaskController) -> Boolean) {
        val active = TaskRunSession.controller ?: controller
        if (active == null) renderControlFeedback(TaskControlFeedback.NOTHING_RUNNING) else request(active)
    }

    private fun renderControlFeedback(feedback: TaskControlFeedback) {
        val label =
            when (feedback) {
                TaskControlFeedback.PAUSE_REQUESTED -> R.string.task_pause_requested
                TaskControlFeedback.PAUSED -> R.string.task_paused
                TaskControlFeedback.STOP_REQUESTED -> R.string.task_stop_requested
                TaskControlFeedback.STOPPED -> R.string.task_stopped
                TaskControlFeedback.TAKEOVER -> R.string.task_took_over
                TaskControlFeedback.RESUMED -> R.string.task_resumed
                TaskControlFeedback.NOTHING_RUNNING -> R.string.task_nothing_running
                TaskControlFeedback.NOT_PAUSED -> R.string.task_not_paused
                TaskControlFeedback.ALREADY_PAUSED -> R.string.task_already_paused
                TaskControlFeedback.NO_SMS_APP -> R.string.task_no_sms_app
            }
        findViewById<TextView>(R.id.task_control_feedback).setText(label)
    }

    private fun renderSteps(steps: List<StepProgress>) {
        val container = findViewById<LinearLayout>(R.id.task_steps_container)
        container.removeAllViews()
        steps.forEach { step -> container.addView(stepView(step)) }
    }

    private fun renderStep(step: StepProgress) {
        if (step.state == StepProgressState.RUNNING) {
            findViewById<TextView>(R.id.task_practice_countdown).text = ""
        }
        val container = findViewById<LinearLayout>(R.id.task_steps_container)
        for (index in 0 until container.childCount) {
            val view = container.getChildAt(index) as? TextView ?: continue
            if (view.tag == step.stepId) {
                view.text = stepLabel(step)
                return
            }
        }
    }

    private fun stepView(step: StepProgress): TextView =
        TextView(this).apply {
            tag = step.stepId
            text = stepLabel(step)
            setPadding(0, PADDING_PX, 0, PADDING_PX)
        }

    private fun stepLabel(step: StepProgress): String {
        val state =
            when (step.state) {
                StepProgressState.PENDING -> getString(R.string.task_pending)
                StepProgressState.RUNNING -> getString(R.string.task_running)
                StepProgressState.DONE -> getString(R.string.task_done)
                StepProgressState.FAILED -> getString(R.string.task_failed)
                StepProgressState.NEEDS_YOU -> getString(R.string.task_needs_you)
                StepProgressState.UNKNOWN -> getString(R.string.task_unknown_result)
            }
        return getString(R.string.setup_row_format, actionLabel(step.name), state, stepDetail(step))
    }

    /** The end-of-run receipt: what happened, what did not, what is unknown (REQ-TASK-06). */
    private fun renderReceipt(receipt: RunReceipt) {
        val terminal = RunStatusMapping.terminal(receipt)
        findViewById<TextView>(R.id.task_state).text = planLabel(terminal)
        renderSteps(receipt.steps)

        fun names(ids: List<String>): String =
            ids
                .joinToString(", ") { id -> actionLabel(receipt.steps.first { it.stepId == id }.name) }
                .ifBlank { getString(R.string.task_none) }
        val text =
            getString(
                R.string.task_receipt_format,
                planLabel(terminal),
                names(receipt.executedStepIds),
                names(receipt.notExecutedStepIds),
            ) +
                if (receipt.unknownResultStepIds.isEmpty()) {
                    ""
                } else {
                    getString(R.string.task_receipt_unknown, names(receipt.unknownResultStepIds))
                }
        val needsYou =
            if (receipt.needsYouStepIds.isEmpty()) {
                ""
            } else {
                getString(R.string.run_receipt_needs_you, names(receipt.needsYouStepIds))
            }
        findViewById<TextView>(R.id.task_receipt).text = getString(R.string.run_receipt_join, text, needsYou)
    }

    private fun approvalTarget(target: String): String =
        if (target.startsWith("to=") && target.contains(", body=")) {
            getString(
                R.string.task_target_format,
                ai.eqo.core.agent.TaskDisplayText.escape(
                    target
                        .removePrefix("to=")
                        .substringBefore(", body=")
                        .ifBlank { getString(R.string.task_no_recipient) },
                ),
                ai.eqo.core.agent.TaskDisplayText
                    .escape(target.substringAfter(", body=")),
            )
        } else {
            ai.eqo.core.agent.TaskDisplayText
                .escape(target)
        }

    private fun actionLabel(action: String): String {
        ai.eqo.core.agent.ActionSchema
            .getAction(action)
            ?.let { return it.name.replace('_', ' ') }
        return getString(
            when (action) {
                "observe" -> R.string.task_step_observe
                "scroll" -> R.string.task_step_scroll
                "compose_sms" -> R.string.task_step_compose
                "compose_email" -> R.string.task_step_email
                "open_app" -> R.string.task_step_open_app
                "tap_text" -> R.string.task_step_tap
                "type_text", "paste" -> R.string.task_step_type
                "press_back" -> R.string.task_step_back
                "press_home" -> R.string.task_step_home
                "press_enter" -> R.string.task_step_enter
                "send_whatsapp", "send_telegram" -> R.string.task_step_send_chat
                else -> R.string.task_step_other
            },
        )
    }

    private fun planLabel(status: String): String =
        getString(
            when (status) {
                "PROPOSED", "PENDING", "NONE" -> R.string.task_pending
                "RUNNING" -> R.string.task_running
                "COMPLETED" -> R.string.task_completed
                "FAILED" -> R.string.task_failed
                "NEEDS_YOU" -> R.string.task_needs_you
                "PAUSED" -> R.string.task_paused
                "STOPPED" -> R.string.task_stopped
                "CANCELLED" -> R.string.task_cancelled
                else -> R.string.task_unknown_result
            },
        )

    private fun stepDetail(step: StepProgress): String {
        RunStatusMapping.detail(step)?.let { return getString(it.resource, it.argument) }
        return when {
            step.detail == FailureClass.A11Y_LOST.repair -> getString(R.string.accessibility_disabled)
            step.detail == FailureClass.BINDER_DEAD.repair -> getString(R.string.task_helper_lost)
            step.detail.startsWith("Android permission ") -> getString(R.string.task_permission_missing)
            step.state == StepProgressState.PENDING && step.detail.isNotBlank() -> getString(R.string.task_did_not_run)
            step.state == StepProgressState.UNKNOWN -> getString(R.string.task_unknown_result)
            step.state == StepProgressState.FAILED ->
                ai.eqo.core.agent.TaskDisplayText
                    .escape(step.detail)
            step.state == StepProgressState.DONE &&
                step.name in
                setOf(
                    "compose_sms",
                    "compose_email",
                )
            -> getString(R.string.task_draft_opened)
            else ->
                ai.eqo.core.agent.TaskDisplayText
                    .escape(step.detail)
        }
    }

    private var debugPlanReceiver: DebugPlanReceiver? = null

    // Debuggable builds only: lets a developer supply a finished plan (still validated and approved on screen).
    private fun registerDebugPlanReceiver() {
        val receiver =
            DebugPlanReceiver { json ->
                if (planning || actionPermissions.isWaiting() || TaskRunSession.controller != null) {
                    return@DebugPlanReceiver
                }
                scope.launch {
                    try {
                        val steps =
                            ai.eqo.core.agent.RegistryPlanVocabulary
                                .parse(json, portedActions.enabledActionNames)
                        if (!isFinishing && !isDestroyed) prepareAndShowPlan(steps)
                    } catch (failure: ai.eqo.actions.impl.RecipientPreparationException) {
                        findViewById<TextView>(R.id.task_state).text = failure.message
                    } catch (_: IllegalArgumentException) {
                        android.util.Log.w("EqoRun", "debug_plan code=PLAN_REJECTED")
                    }
                }
            }
        if (DebugPlanReceiver.register(this, mainHandler, receiver)) debugPlanReceiver = receiver
    }

    // A stale pooled connection makes the first model call after a quiet spell fail with a Network error;
    // one immediate retry on a fresh connection is enough. Every other error is reported as-is.
    private suspend fun <T> planWithOneNetworkRetry(call: suspend () -> T): T =
        try {
            call()
        } catch (failure: LLMException) {
            if (failure.error != ai.eqo.core.llm.error.LLMError.Network) throw failure
            android.util.Log.i("EqoRun", "planner network error, retrying once")
            call()
        }

    private var planning = false

    private fun planRequest() {
        val active =
            (TaskRunSession.controller ?: controller)?.currentState() in
                setOf(LoopState.RUNNING, LoopState.PAUSED)
        if (planning || active) return
        val request = findViewById<EditText>(R.id.task_request).text.toString().trim()
        if (request.isBlank()) {
            findViewById<TextView>(R.id.task_state).setText(R.string.task_request_empty)
        } else {
            planning = true
            findViewById<Button>(R.id.task_start_button).isEnabled = false
            findViewById<TextView>(R.id.task_state).setText(R.string.task_planning)
            scope.launch { makePlan(request) }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException", "CyclomaticComplexMethod")
    private suspend fun makePlan(request: String) {
        try {
            val result =
                withContext(Dispatchers.IO) {
                    val planner =
                        TaskPlanningRuntime.planner(applicationContext, portedActions.enabledActionNames)
                            ?: throw MissingTaskKey()
                    // Never capture EQO's key/setup/request UI; execution observations stay local.
                    val resolver =
                        ai.eqo.actions.impl
                            .LaunchableAppResolver(packageManager)
                    val fallback = MissingAppFallback { resolver.resolve(it) != null }
                    val first = planWithOneNetworkRetry { planner.plan(request, UntrustedScreenText.wrap("")) }
                    fallback.prepare(request, first) { constrained ->
                        planWithOneNetworkRetry { planner.plan(constrained, UntrustedScreenText.wrap("")) }
                    }
                }
            if (!isFinishing && !isDestroyed) {
                when (result) {
                    is MissingAppFallback.Result.Ready -> prepareAndShowPlan(result.steps, result.missing)
                    is MissingAppFallback.Result.Stopped ->
                        findViewById<TextView>(R.id.task_state).text = result.message
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: MissingTaskKey) {
            findViewById<TextView>(R.id.task_state).apply {
                setText(R.string.task_key_needed)
                setOnClickListener {
                    startActivity(Intent(this@TaskActivity, ai.eqo.onboarding.ModelKeySetupActivity::class.java))
                }
            }
        } catch (failure: LLMException) {
            // Error category only (never the message, request or key).
            android.util.Log.w(
                "EqoRun",
                "planner code=${if (failure.timedOut) "MODEL_SLOW" else failure.error.code} " +
                    "cause=${failure.causeClass}",
            )
            findViewById<TextView>(R.id.task_state).setText(planningError(failure))
        } catch (failure: ai.eqo.actions.impl.RecipientPreparationException) {
            findViewById<TextView>(R.id.task_state).text = failure.message
        } catch (failure: IllegalArgumentException) {
            android.util.Log.w("EqoRun", "planner code=PLAN_REJECTED")
            findViewById<TextView>(R.id.task_state).text =
                getString(R.string.task_plan_rejected_detail, failure.message.orEmpty())
        } catch (_: Exception) {
            // No raw exception/model output/request is logged or displayed.
            findViewById<TextView>(R.id.task_state).setText(R.string.task_plan_invalid)
        } finally {
            planning = false
            findViewById<Button>(R.id.task_start_button).isEnabled =
                TaskRunSession.pending == null &&
                TaskRunSession.controller == null
        }
    }

    internal suspend fun prepareAndShowPlan(
        steps: List<ai.eqo.core.agent.LoopStep>,
        missing: List<String> = emptyList(),
    ) {
        val generation = preparationGeneration
        findViewById<TextView>(R.id.task_preview).text =
            TaskPlanPreview.describe(steps) + permissionPreview(steps)
        if (!portedActions.prepareRuntimeAccess(steps)) {
            findViewById<TextView>(R.id.task_state).text =
                "Permission was not granted. No run started. Tap Start to explicitly try this plan again."
            return
        }
        if (!portedActions.prepareFileAccess(steps)) {
            findViewById<TextView>(R.id.task_state).text = "Turn on All files access for EQO, then try this plan again."
            return
        }
        val prepared = portedActions.prepareRecipients(steps)
        if (generation == preparationGeneration && !isFinishing && !isDestroyed) {
            showPlan(ApprovedTaskPlan(prepared.steps), prepared.names, missing)
        }
    }

    private fun showPlan(
        plan: ApprovedTaskPlan,
        recipientNames: Map<String, String> = emptyMap(),
        missing: List<String> = emptyList(),
    ) {
        val steps = plan.steps()
        renderSteps(steps.map { StepProgress(it.stepId, it.action.name, StepProgressState.PENDING) })
        val notice =
            if (missing.isEmpty()) {
                ""
            } else {
                ai.eqo.core.agent.TaskDisplayText
                    .escape(missing.joinToString(", ")) +
                    " is not installed on this phone, so this plan uses Chrome instead.\n\n"
            }
        val preview = notice + TaskPlanPreview.describe(steps, recipientNames) + permissionPreview(steps)
        // Never log preview text: even debug plans can contain contact names and destinations.
        findViewById<TextView>(R.id.task_preview).text = preview
        if (!PlanApprovalSettings.requiredFor(this, steps)) {
            startRun(plan)
            return
        }
        PlanFallbackDialog(this, ::startRun).show(plan, preview, missing).also { prepareTaskDialog(it) }
    }

    private fun planningError(failure: LLMException): Int = RunStatusMapping.planning(failure.error, failure.timedOut)

    private fun permissionPreview(steps: List<ai.eqo.core.agent.LoopStep>): String {
        val permissions = portedActions.plannedRuntimePermissions(steps)
        return if (permissions.isEmpty()) {
            ""
        } else {
            permissions.joinToString("\n", prefix = "\n\nAccess needed before this plan runs:\n") { it.explanation }
        }
    }

    override fun onDestroy() {
        voiceInput?.close()
        debugPlanReceiver?.let { unregisterReceiver(it) }
        TaskRunSession.observer = null
        TaskRunSession.permissionRequester = null
        controller?.stop()
        scope.cancel()
        actionPermissions.close()
        mainHandler.removeCallbacksAndMessages(null)
        countdown.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private class MissingTaskKey : Exception()

    companion object {
        /** Sample-task intent extra: unused for now, reserved for user-submitted plans. */
        const val EXTRA_PLAN = "ai.eqo.task.PLAN"

        /** Countdown and layout units, named so no magic numbers sit in the render code. */
        private const val MS_PER_SECOND = 1000L

        private const val PADDING_PX = 8
    }
}
