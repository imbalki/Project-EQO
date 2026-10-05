/*
 * EQO (TASK-015, issue #20): the task screen (UF-09 / UF-10, REQ-TASK-01..07).
 *
 * Progress per step, contextual approval cards with the 60s countdown, Pause != Stop,
 * takeover, honest "unknown result" rows and an end-of-run receipt. The loop is
 * core-agent's ActionLoop, wired with the REAL permission check and the REAL approval
 * path (see `StudyLoopWiring`).
 *
 * SF-4: `UserResumeConfirmation.forExplicitUserConfirmation` is minted in exactly ONE
 * place in this app — the resume button's click handler below — so a resume can only
 * follow an actual user gesture. The SF-4 guard test allowlists exactly this file.
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
@Suppress("TooManyFunctions")
class TaskActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var controller: StudyTaskController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.task_screen)
        findViewById<Button>(R.id.task_start_button).setOnClickListener { planRequest() }
        findViewById<Button>(R.id.task_pause_button).setOnClickListener { control { it.pause() } }
        findViewById<Button>(R.id.task_stop_button).setOnClickListener { control { it.stop() } }
        findViewById<Button>(R.id.task_takeover_button).setOnClickListener { control { it.takeover() } }
        val resumeButton = findViewById<Button>(R.id.task_resume_button)
        protectConfirmationTouches(resumeButton)
        resumeButton.setOnClickListener { confirmResume() }
        renderSteps(emptyList())
        protectConfirmationTouches(findViewById<Button>(R.id.task_start_button))
        renderPlanStatus(PlanStatus.PENDING)
        findViewById<TextView>(R.id.task_state).setText(R.string.task_idle)
        findViewById<Button>(R.id.task_setup_button).setOnClickListener {
            controller?.takeover()
            startActivity(Intent(this, SetupHubActivity::class.java))
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        TakeoverDetector.shared.setControlTouchExclusion(
            if (hasFocus) ({ x, y -> touchesControl(x, y) }) else null,
        )
    }

    override fun onPause() {
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
        if (controller?.currentState() != LoopState.PAUSED) {
            renderControlFeedback(TaskControlFeedback.NOT_PAUSED)
            return
        }
        AlertDialog
            .Builder(this)
            .setTitle(R.string.task_resume_confirm_title)
            .setMessage(R.string.task_resume_confirm_message)
            .setPositiveButton(R.string.task_resume_confirm_yes) { _, _ ->
                val confirmation = UserResumeConfirmation.forExplicitUserConfirmation(SystemClock.elapsedRealtime())
                controller?.resume(confirmation)
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { prepareTaskDialog(it) }
    }

    private fun startRun(approved: ApprovedTaskPlan) {
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
                observe = {
                    val result = liveAutomation()?.observe()
                    (result as? ai.eqo.accessibility.A11yResult.Success)?.detail ?: ""
                },
                onPlanStatus = { status -> mainHandler.post { renderPlanStatus(status) } },
                onStepProgress = { progress -> mainHandler.post { renderStep(progress) } },
                config = SamplePractice.config,
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
        findViewById<TextView>(R.id.task_state).text = planLabel(status.name)
        val running = status == PlanStatus.RUNNING
        val paused = status == PlanStatus.PAUSED
        findViewById<Button>(R.id.task_start_button).isEnabled = !running && !paused
        // Keep controls tappable: unavailable commands explain why rather than silently ignoring taps.
        if (!running) findViewById<TextView>(R.id.task_practice_countdown).text = ""
    }

    private fun control(request: (StudyTaskController) -> Boolean) {
        val active = controller
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
                StepProgressState.UNKNOWN -> getString(R.string.task_unknown_result)
            }
        return getString(R.string.setup_row_format, actionLabel(step.name), state, stepDetail(step))
    }

    /** The end-of-run receipt: what happened, what did not, what is unknown (REQ-TASK-06). */
    private fun renderReceipt(receipt: RunReceipt) {
        findViewById<TextView>(R.id.task_state).text = planLabel(receipt.terminal)
        renderSteps(receipt.steps)

        fun names(ids: List<String>): String =
            ids
                .joinToString(", ") { id -> actionLabel(receipt.steps.first { it.stepId == id }.name) }
                .ifBlank { getString(R.string.task_none) }
        val text =
            getString(
                R.string.task_receipt_format,
                planLabel(receipt.terminal),
                names(receipt.executedStepIds),
                names(receipt.notExecutedStepIds),
            ) +
                if (receipt.unknownResultStepIds.isEmpty()) {
                    ""
                } else {
                    getString(R.string.task_receipt_unknown, names(receipt.unknownResultStepIds))
                }
        findViewById<TextView>(R.id.task_receipt).text = text
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

    private fun actionLabel(action: String): String =
        getString(
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

    private fun planLabel(status: String): String =
        getString(
            when (status) {
                "PROPOSED", "PENDING", "NONE" -> R.string.task_pending
                "RUNNING" -> R.string.task_running
                "COMPLETED" -> R.string.task_completed
                "FAILED" -> R.string.task_failed
                "PAUSED" -> R.string.task_paused
                "STOPPED" -> R.string.task_stopped
                "CANCELLED" -> R.string.task_cancelled
                else -> R.string.task_unknown_result
            },
        )

    private fun stepDetail(step: StepProgress): String =
        when {
            step.detail == FailureClass.A11Y_LOST.repair -> getString(R.string.accessibility_disabled)
            step.detail == FailureClass.BINDER_DEAD.repair -> getString(R.string.task_helper_lost)
            step.detail.startsWith("Android permission ") -> getString(R.string.task_permission_missing)
            step.state == StepProgressState.PENDING && step.detail.isNotBlank() -> getString(R.string.task_did_not_run)
            step.state == StepProgressState.UNKNOWN -> getString(R.string.task_unknown_result)
            step.state == StepProgressState.FAILED -> getString(R.string.task_step_failed)
            step.state == StepProgressState.DONE &&
                step.name in
                setOf(
                    "compose_sms",
                    "compose_email",
                )
            -> getString(R.string.task_draft_opened)
            else -> ""
        }

    private var planning = false

    private fun planRequest() {
        val active = controller?.currentState() in setOf(LoopState.RUNNING, LoopState.PAUSED)
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

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun makePlan(request: String) {
        try {
            val steps =
                withContext(Dispatchers.IO) {
                    val planner = TaskPlanningRuntime.planner(applicationContext) ?: throw MissingTaskKey()
                    // Never capture EQO's key/setup/request UI; execution observations stay local.
                    planner.plan(request, UntrustedScreenText.wrap(""))
                }
            if (!isFinishing && !isDestroyed) showPlan(ApprovedTaskPlan(steps))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: MissingTaskKey) {
            findViewById<TextView>(R.id.task_state).setText(R.string.task_key_needed)
        } catch (failure: LLMException) {
            findViewById<TextView>(R.id.task_state).setText(planningError(failure))
        } catch (_: Exception) {
            // No raw exception/model output/request is logged or displayed.
            findViewById<TextView>(R.id.task_state).setText(R.string.task_plan_invalid)
        } finally {
            planning = false
            findViewById<Button>(R.id.task_start_button).isEnabled = true
        }
    }

    private fun showPlan(plan: ApprovedTaskPlan) {
        val steps = plan.steps()
        renderSteps(steps.map { StepProgress(it.stepId, it.action.name, StepProgressState.PENDING) })
        val preview = TaskPlanPreview.describe(steps)
        findViewById<TextView>(R.id.task_preview).text = preview
        AlertDialog
            .Builder(this)
            .setTitle(R.string.task_plan_title)
            .setMessage(preview)
            .setPositiveButton(R.string.task_approval_approve) { _, _ ->
                // Execute the same immutable snapshot described above, never a replan or display text.
                startRun(plan)
            }.setNegativeButton(R.string.task_approval_reject) { _, _ ->
                findViewById<TextView>(R.id.task_state).setText(R.string.task_rejected)
            }.show()
            .also { prepareTaskDialog(it) }
    }

    private fun planningError(failure: LLMException): Int =
        when (failure.error) {
            ai.eqo.core.llm.error.LLMError.AuthMissing,
            ai.eqo.core.llm.error.LLMError.AuthInvalid,
            -> R.string.model_error_auth
            ai.eqo.core.llm.error.LLMError.RateLimited -> R.string.model_error_rate
            ai.eqo.core.llm.error.LLMError.QuotaExhausted -> R.string.model_error_credit
            ai.eqo.core.llm.error.LLMError.ModelUnavailable -> R.string.model_error_model
            else -> R.string.task_call_failed
        }

    override fun onDestroy() {
        controller?.stop()
        scope.cancel()
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
