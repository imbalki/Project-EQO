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
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.UserResumeConfirmation
import ai.eqo.data.models.PlanStatus
import ai.eqo.helper.client.HelperActivationState
import ai.eqo.onboarding.StudySetup
import ai.eqo.study.ApprovalOutcome
import ai.eqo.study.ApprovalRequest
import ai.eqo.study.RunReceipt
import ai.eqo.study.StepProgress
import ai.eqo.study.StepProgressState
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

// One screen owns run control + approval cards + receipts; splitting it would scatter the
// SF-4 mint site across files (same reasoning as ActionLoop's own @Suppress precedent).
@Suppress("TooManyFunctions")
class TaskActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default)
    private var controller: StudyTaskController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.task_screen)
        findViewById<Button>(R.id.task_start_button).setOnClickListener { startRun() }
        findViewById<Button>(R.id.task_pause_button).setOnClickListener { controller?.pause() }
        findViewById<Button>(R.id.task_stop_button).setOnClickListener { controller?.stop() }
        findViewById<Button>(R.id.task_takeover_button).setOnClickListener { controller?.takeover() }
        val resumeButton = findViewById<Button>(R.id.task_resume_button)
        protectConfirmationTouches(resumeButton)
        resumeButton.setOnClickListener { confirmResume() }
        renderSteps(sampleSteps().map { StepProgress(it.stepId, it.action.name, StepProgressState.PENDING) })
        findViewById<TextView>(R.id.task_state).text = ""
    }

    /**
     * The one resume confirmation path (SF-4): a dialog the owner taps, and the token is
     * minted here — in the gesture handler — never in loop, recovery or agent code.
     */
    private fun confirmResume() {
        AlertDialog
            .Builder(this)
            .setTitle(R.string.task_resume_confirm_title)
            .setMessage(R.string.task_resume_confirm_message)
            .setPositiveButton(R.string.task_resume_confirm_yes) { _, _ ->
                val confirmation = UserResumeConfirmation.forExplicitUserConfirmation(SystemClock.elapsedRealtime())
                controller?.resume(confirmation)
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { protectConfirmationDialog(it) }
    }

    private fun startRun() {
        val steps = sampleSteps()
        renderSteps(steps.map { StepProgress(it.stepId, it.action.name, StepProgressState.PENDING) })
        val permissionCheck =
            StudyPermissionCheck(
                manifestPermissionGranted = { permission ->
                    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                },
                accessibilityServiceEnabled = { StudySetup.accessibilityServiceEnabled(applicationContext) },
                helperBinderAlive = { StudySetup.helper.state == HelperActivationState.State.ACTIVE },
            )
        val smsOpener = SmsDraftOpener { intent -> startActivity(intent) }
        val executor =
            StudyActionExecutor(
                EqoAutomationPort(
                    automation = { liveAutomation() },
                    composeDraft = { recipient, body -> smsOpener.open(recipient, body) },
                ),
            )
        val newController =
            StudyTaskController(
                steps = steps,
                permissionCheck = permissionCheck,
                approvalGate = StudyApprovalGate(approvalSurface(), nowMs = { System.currentTimeMillis() }),
                executor = executor,
                observe = {
                    val result = liveAutomation()?.observe()
                    (result as? ai.eqo.accessibility.A11yResult.Success)?.detail ?: ""
                },
                onPlanStatus = { status -> mainHandler.post { renderPlanStatus(status) } },
                onStepProgress = { progress -> mainHandler.post { renderStep(progress) } },
            )
        controller = newController
        scope.launch {
            val receipt = newController.run()
            mainHandler.post { renderReceipt(receipt) }
        }
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
                        append("Action: ").append(request.action).append('\n')
                        append("Target: ").append(request.target).append('\n')
                        append("App: ").append(request.app).append("\n\n")
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
                                continuation.resume(ApprovalOutcome.Rejected("you rejected this step"))
                            }
                        }.setOnCancelListener {
                            if (settled.compareAndSet(false, true)) {
                                countdown.removeCallbacksAndMessages(null)
                                continuation.resume(ApprovalOutcome.TimedOut)
                            }
                        }.show()
                        .also { protectConfirmationDialog(it) }
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
        findViewById<TextView>(R.id.task_state).text = status.name
    }

    private fun renderSteps(steps: List<StepProgress>) {
        val container = findViewById<LinearLayout>(R.id.task_steps_container)
        container.removeAllViews()
        steps.forEach { step -> container.addView(stepView(step)) }
    }

    private fun renderStep(step: StepProgress) {
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
                StepProgressState.PENDING -> getString(R.string.state_not_set_up)
                StepProgressState.RUNNING -> getString(R.string.state_checking)
                StepProgressState.DONE -> getString(R.string.state_ready)
                StepProgressState.FAILED -> getString(R.string.state_needs_attention)
                StepProgressState.UNKNOWN -> getString(R.string.task_unknown_result)
            }
        return "${step.name} — $state" + if (step.detail.isBlank()) "" else "\n${step.detail}"
    }

    /** The end-of-run receipt: what happened, what did not, what is unknown (REQ-TASK-06). */
    private fun renderReceipt(receipt: RunReceipt) {
        renderSteps(receipt.steps)
        val text =
            buildString {
                append("Terminal: ").append(receipt.terminal).append('\n')
                val executed = receipt.executedStepIds.joinToString(", ").ifBlank { "(none)" }
                append("Executed: ").append(executed).append('\n')
                val notExecuted = receipt.notExecutedStepIds.joinToString(", ").ifBlank { "(none)" }
                append("Did not run: ").append(notExecuted).append('\n')
                if (receipt.unknownResultStepIds.isNotEmpty()) {
                    append("Unknown result — verify manually: ")
                        .append(receipt.unknownResultStepIds.joinToString(", "))
                }
            }
        findViewById<TextView>(R.id.task_receipt).text = text
    }

    /**
     * The study sample plan: reversible automation verbs plus one outward step that is
     * compose-only (REQ-SMS-01) and therefore always passes the approval card first
     * (TASK-012 B2: outward/irreversible actions need approval).
     */
    private fun sampleSteps(): List<LoopStep> =
        listOf(
            LoopStep(
                stepId = "1-observe",
                action = ExecutedAction(name = "observe", expectedPostconditions = listOf("screen text captured")),
            ),
            LoopStep(
                stepId = "2-scroll",
                action = ExecutedAction(name = "scroll", params = mapOf("direction" to "down")),
            ),
            LoopStep(
                stepId = "3-compose-draft",
                action =
                    ExecutedAction(
                        name = "compose_sms",
                        params = mapOf("to" to "", "body" to "EQO study draft — nothing is sent without you"),
                        irreversible = false,
                        expectedPostconditions = listOf("messaging composer opened with the draft"),
                    ),
            ),
        )

    companion object {
        /** Sample-task intent extra: unused for now, reserved for user-submitted plans. */
        const val EXTRA_PLAN = "ai.eqo.task.PLAN"

        /** Countdown and layout units, named so no magic numbers sit in the render code. */
        private const val MS_PER_SECOND = 1000L

        private const val PADDING_PX = 8
    }
}
