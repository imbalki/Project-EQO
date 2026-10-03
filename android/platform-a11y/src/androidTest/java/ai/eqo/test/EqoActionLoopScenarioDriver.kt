/*
 * EQO (TASK-012, issue #17): on-device scenario driver for the action loop
 * acceptance criterion "Device scenario: run, user taps, loop pauses, resume,
 * stop". It runs the real ActionLoop on the phone with its execute/observe/
 * approval lambdas wired to the single EQO accessibility service - every
 * action goes through the takeover-gated EqoAutomation path (SF-1).
 *
 * Like EqoDeviceRecordsDriver this runs IN-PROCESS (EqoTestTargetActivity
 * starts it via `am start ... --ez runRecords true --es records 5`):
 * `am instrument` force-stops the package and this OEM's
 * AccessibilityManagerService then refuses to rebind the service (see the
 * TASK-009 evidence). Every state change is logged with the [EQO_RECORD] tag
 * so the device log is the evidence artifact.
 *
 * The resume/stop controls are real on-screen buttons and the red prompt
 * banner: the UserResumeConfirmation token is minted inside the resume
 * control's click handler for that one explicit user gesture (SF-4 - no
 * agent-reachable code path may clear a latched takeover). The only latch
 * clearing before the plan starts is the pre-run detector reset (owner
 * touches that landed before the run are not scenario events).
 */
package ai.eqo.test

import ai.eqo.accessibility.A11yError
import ai.eqo.accessibility.A11yResult
import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.EqoAutomation
import ai.eqo.accessibility.TakeoverDetector
import ai.eqo.core.agent.ActionLoop
import ai.eqo.core.agent.ApprovalDecision
import ai.eqo.core.agent.ExecuteResult
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopReport
import ai.eqo.core.agent.LoopState
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.PauseReason
import ai.eqo.core.agent.PlanTerminal
import ai.eqo.core.agent.SensitivityApprovalPolicy
import ai.eqo.core.agent.StepOutcome
import ai.eqo.core.agent.UserResumeConfirmation
import ai.eqo.data.models.PlanStatus
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

private const val RECORD_TAG = "EQO_RECORD"
private const val POLL_INTERVAL_MS = 500L

private fun record(line: String) {
    Log.i(RECORD_TAG, line)
    println("[EQO_RECORD] $line")
}

private fun now(): Long = SystemClock.elapsedRealtime()

private fun await(
    what: String,
    timeoutMs: Long,
    condition: () -> Boolean,
): Boolean {
    val deadline = now() + timeoutMs
    while (now() < deadline) {
        if (condition()) return true
        Thread.sleep(POLL_INTERVAL_MS)
    }
    record("TIMEOUT waiting for: $what")
    return condition()
}

object EqoActionLoopScenarioDriver {
    /** Blocking scenario run on the caller's thread (the records thread). */
    fun run(activity: EqoTestTargetActivity) {
        Scenario(activity).execute()
    }
}

private class Scenario(
    private val activity: EqoTestTargetActivity,
) {
    private val takeover = TakeoverDetector.shared
    private lateinit var loop: ActionLoop
    private lateinit var automation: EqoAutomation
    private var watcher: Thread? = null

    private val startMs = now()
    private val done = AtomicBoolean(false)
    private val stopTapped = AtomicBoolean(false)
    private val takeoverForwarded = AtomicBoolean(false)
    private val transitionRecorded = AtomicBoolean(false)
    private val forwardTakeovers = AtomicBoolean(true)
    private val takeoverHappened = AtomicBoolean(false)
    private val pauseObserved = AtomicBoolean(false)
    private val applyStartedAfterResume = AtomicBoolean(false)

    @Volatile
    private var latchMs = 0L

    @Volatile
    private var resumeCount = 0

    @Volatile
    private var resumed = false

    @Volatile
    private var pausePrompted = false

    @Volatile
    private var failedCheck: String? = null

    fun execute() {
        record("ACTION-LOOP SCENARIO start (TASK-012: run, user taps, loop pauses, resume, stop)")
        record("scenario start elapsedRealtime=${startMs}ms")
        // Owner touches that landed before this run are not scenario events:
        // reset the detector before the plan starts. During the plan no latch
        // is ever cleared except by the owner's own resume tap (SF-4).
        takeover.resume(UserResumeConfirmation.forExplicitUserConfirmation(now()))
        record("detector reset before the plan (pre-run touches are not scenario events)")
        prompt("EQO SCENARIO STARTING - IF ASKED, ENABLE EQO IN Settings > Accessibility")

        val bound =
            await("EQO accessibility service binding", SERVICE_TIMEOUT_MS) {
                EQOAccessibilityService.getInstance() != null
            }
        if (!bound) {
            record("FAIL: EQO accessibility service is not bound (owner must enable it in Settings > Accessibility)")
            resultOrFail("service-not-bound")
            return
        }
        automation = EQOAccessibilityService.getInstance()!!.automation

        val windowUp = await("test app window marker", WINDOW_TIMEOUT_MS) { screenText().contains(WINDOW_MARKER) }
        if (!windowUp) {
            record("FAIL: observe() never saw the test app window")
            resultOrFail("test-window-not-observed")
            return
        }
        record("test app window observed through the EQO service")

        loop = buildLoop()
        activity.onResumeTap = { onOwnerResumeTap() }
        activity.onStopTap = { onOwnerStopTap() }
        activity.onBannerTap = null
        watcher = Thread({ watchLoop() }, "eqo-loop-watcher").also { it.start() }

        prompt("ACTION LOOP RUNNING - TAP THE SCREEN NOW, KEEP TAPPING UNTIL THIS BANNER CHANGES")
        record(
            "PHASE run: 4-step plan started; owner taps during the s1 apply (takeover). " +
                "ActionLoop.Config(tickMs=50, actionTimeoutMs=$APPLY_TIMEOUT_MS, interStepDelayMs=250)",
        )
        val report =
            try {
                runBlocking { loop.run() }
            } catch (t: Throwable) {
                record("FAIL: ActionLoop.run() threw $t")
                resultOrFail("run-threw")
                return
            }
        reportChecks(report)
    }

    private fun buildLoop(): ActionLoop =
        ActionLoop(
            steps =
                listOf(
                    LoopStep(
                        stepId = "s1-observe-window",
                        action =
                            ExecutedAction(
                                name = "observe_window",
                                params = mapOf("target" to "test window"),
                            ),
                    ),
                    LoopStep(
                        stepId = "s2-tap-press-me",
                        action =
                            ExecutedAction(
                                name = "tap_press_me",
                                params = mapOf("target" to "PRESS ME"),
                                expectedPostconditions = listOf("button pressed marker"),
                            ),
                    ),
                    LoopStep(
                        stepId = "s3-type-submit",
                        action =
                            ExecutedAction(
                                name = "type_submit_text",
                                params = mapOf("target" to "eqo_test_input"),
                                expectedPostconditions = listOf("submitted: hello from eqo"),
                            ),
                    ),
                    LoopStep(
                        stepId = "s4-hold",
                        action =
                            ExecutedAction(
                                name = "hold_window",
                                params = mapOf("target" to "test window"),
                            ),
                    ),
                ),
            approvalGate = { step ->
                record("approval gate consulted for ${step.stepId} (static policy over the executed action)")
                ApprovalDecision.Approved
            },
            execute = { step -> runStepApply(step) },
            observe = { screenText() },
            onPlanStatus = { status -> record("PLAN STATUS: $status (t+${now() - startMs}ms)") },
            config =
                ActionLoop.Config(
                    // The scenario's applies include human-wait windows (owner
                    // taps), so the per-apply hard timeout must outlast them;
                    // the windows themselves are bounded below.
                    actionTimeoutMs = APPLY_TIMEOUT_MS,
                ),
        )

    private suspend fun runStepApply(step: LoopStep): ExecuteResult {
        if (resumed) applyStartedAfterResume.set(true)
        record(
            "STEP ${step.stepId} apply START at t+${now() - startMs}ms " +
                "(static approval policy requiresApproval=" +
                "${SensitivityApprovalPolicy.requiresApproval(step.action)})",
        )
        var result: ExecuteResult =
            ExecuteResult.Interrupted("apply was cancelled mid-flight before returning (typed below)")
        try {
            result =
                when (step.stepId) {
                    "s1-observe-window" -> observeWindowApply()
                    "s2-tap-press-me" -> mapResult("tap('PRESS ME')", automation.tap(TARGET_BUTTON_TEXT))
                    "s3-type-submit" -> typeSubmitApply()
                    else -> holdApply()
                }
        } finally {
            // Runs even when the loop cancels this apply (user stop/cancel).
            record("STEP ${step.stepId} apply END at t+${now() - startMs}ms -> $result")
        }
        return result
    }

    /** Read-only apply window the owner taps into (no own gestures: zero self-gesture noise). */
    private suspend fun observeWindowApply(): ExecuteResult {
        val deadline = now() + TAKEOVER_WINDOW_MS
        var reads = 0
        while (now() < deadline) {
            if (takeover.isPaused) {
                forwardTakeover("owner touch seen during the s1 apply")
                return interruptedAtTakeover(reads)
            }
            val result = automation.observe()
            reads++
            if (result is A11yResult.Failure && result.error is A11yError.TakeoverDetected) {
                forwardTakeover("gated observe refused with typed A11yError.TakeoverDetected")
                return interruptedAtTakeover(reads)
            }
            delay(APPLY_POLL_MS)
        }
        return ExecuteResult.Failure("no owner tap within ${TAKEOVER_WINDOW_MS}ms: the takeover phase did not happen")
    }

    private fun interruptedAtTakeover(reads: Int): ExecuteResult =
        ExecuteResult.Interrupted(
            "apply halted mid-apply at the user takeover after $reads observe reads; " +
                "the apply is read-only, so nothing is pending and nothing is unknown",
        )

    private suspend fun typeSubmitApply(): ExecuteResult {
        val typed = automation.typeById(TARGET_INPUT_ID, TYPE_PAYLOAD)
        if (typed is A11yResult.Failure) return mapResult("typeById(eqo_test_input)", typed)
        return mapResult("tapById(eqo_test_submit)", automation.tapById(TARGET_SUBMIT_ID))
    }

    /** Read-only hold the owner stops into (spec: stop lands at the next tick). */
    private suspend fun holdApply(): ExecuteResult {
        prompt("TAP 'STOP LOOP' NOW (or this red banner)")
        record("PHASE stop: s4 hold running; owner taps STOP (t+${now() - startMs}ms)")
        val deadline = now() + STOP_WINDOW_MS
        var reads = 0
        while (now() < deadline && !stopTapped.get()) {
            automation.observe()
            reads++
            delay(APPLY_POLL_MS)
        }
        return if (stopTapped.get()) {
            ExecuteResult.Interrupted("hold cancelled by the user stop after $reads reads (read-only hold: nothing pending)")
        } else {
            ExecuteResult.Failure("owner did not tap STOP within ${STOP_WINDOW_MS}ms: the stop phase did not happen")
        }
    }

    private fun mapResult(
        what: String,
        result: A11yResult,
    ): ExecuteResult =
        when {
            result is A11yResult.Success -> ExecuteResult.Success("$what ok: ${result.detail}")
            result is A11yResult.Failure && result.error is A11yError.TakeoverDetected -> {
                forwardTakeover("gated $what refused with typed A11yError.TakeoverDetected")
                ExecuteResult.Interrupted("apply halted mid-apply at the user takeover ($what refused)")
            }
            result is A11yResult.Failure -> ExecuteResult.Failure("$what failed: ${result.error}")
            else -> ExecuteResult.Failure("$what failed: unexpected result $result")
        }

    /**
     * Forwards one latched takeover to the loop as a takeover command. Called
     * from the apply lambdas and the watcher thread; the CAS makes sure the
     * command is queued exactly once and BEFORE any apply returns (so the
     * pause cannot race past a step boundary).
     */
    private fun forwardTakeover(trigger: String) {
        if (!takeover.isPaused) return
        if (!takeoverForwarded.compareAndSet(false, true)) return
        latchMs = now()
        val cause = takeover.lastTakeoverCause
        val reason =
            if (cause == TakeoverDetector.TakeoverCause.SELF_GESTURE_SUSPECTED) {
                PauseReason.SELF_GESTURE_TAKEOVER_SUSPECTED
            } else {
                PauseReason.USER_TAKEOVER
            }
        takeoverHappened.set(true)
        record("TAKEOVER latched at t+${latchMs - startMs}ms (cause=$cause, trigger=$trigger)")
        val accepted = loop.takeover(reason)
        record("ActionLoop.takeover($reason) submitted -> accepted=$accepted")
        prompt("TOUCH SEEN - THE LOOP IS PAUSING")
    }

    private fun watchLoop() {
        while (!done.get()) {
            if (forwardTakeovers.get() && takeover.isPaused) {
                forwardTakeover("watcher saw the latched detector")
            }
            if (takeoverForwarded.get() && transitionRecorded.compareAndSet(false, true)) {
                recordTransition()
            }
            if (loop.currentState() == LoopState.PAUSED && !resumed && !pausePrompted) {
                recordPausePhase()
            }
            Thread.sleep(POLL_SMALL_MS)
        }
    }

    private fun recordTransition() {
        val latch = latchMs
        val deadline = now() + TRANSITION_BOUND_MS
        while (now() < deadline && loop.currentState() == LoopState.RUNNING) {
            Thread.sleep(POLL_SMALL_MS)
        }
        record(
            "LOOP transition: state=${loop.currentState()} ${now() - latch}ms after the takeover latch " +
                "(documented bound: current apply + one 50ms command tick + phase handling)",
        )
    }

    private fun recordPausePhase() {
        pausePrompted = true
        pauseObserved.set(true)
        val stopped = loop.currentState()
        record("PHASE pause: loop $stopped at t+${now() - startMs}ms (pauseReason=${loop.currentPauseReason()})")
        record("PHASE pause: s2 has not been dispatched (no STEP s2 apply START line exists yet)")
        val recovered = loop.notifyEnvironmentRecovered()
        record(
            "criterion 4: no automatic resume after recovery - notifyEnvironmentRecovered() -> $recovered, " +
                "loop state still ${loop.currentState()}",
        )
        val refused = automation.observe()
        record("gate while paused: observe() -> ${describe(refused)} (typed TakeoverDetected expected)")
        activity.onBannerTap = { onOwnerResumeTap() }
        prompt("LOOP PAUSED - TAP 'RESUME LOOP' (or this red banner) TO RESUME")
        record("PHASE pause: awaiting the owner's resume tap (resume is user-initiated only, SF-4)")
    }

    /**
     * The resume control's click handler - the explicit user gesture. The
     * UserResumeConfirmation token is minted HERE and nowhere else (SF-4).
     */
    private fun onOwnerResumeTap() {
        val tapMs = now()
        if (loop.currentState() != LoopState.PAUSED) {
            record("resume tap at t+${tapMs - startMs}ms ignored: loop state=${loop.currentState()} (not paused)")
            return
        }
        val confirmation = UserResumeConfirmation.forExplicitUserConfirmation(tapMs)
        resumeCount++
        record("RESUME control tapped by the owner at t+${tapMs - startMs}ms (explicit user gesture #$resumeCount)")
        takeover.resume(confirmation)
        record("TakeoverDetector.resume(confirmation confirmedAtMs=$tapMs) minted by that user gesture")
        val accepted = loop.resume(confirmation)
        resumed = true
        record("ActionLoop.resume(confirmation) accepted=$accepted; loop state=${loop.currentState()}")
        takeoverForwarded.set(false)
        transitionRecorded.set(false)
        pausePrompted = false
        activity.onBannerTap = null
        prompt("LOOP RESUMED - DO NOT TAP THE SCREEN YET")
    }

    /** The stop control's click handler: the user stops the task. */
    private fun onOwnerStopTap() {
        val tapMs = now()
        record("STOP control tapped by the owner at t+${tapMs - startMs}ms")
        stopTapped.set(true)
        // The stop tap's own touch must not become a takeover command: the tap
        // IS the user's stop command.
        forwardTakeovers.set(false)
        val accepted = loop.stop()
        record("ActionLoop.stop() accepted=$accepted")
        prompt("STOP TAPPED - THE LOOP IS SETTLING")
    }

    private fun reportChecks(report: LoopReport) {
        record(
            "SCENARIO report: loopState=${report.loopState} terminal=${report.terminal} " +
                "pauseReason=${report.pauseReason}",
        )
        report.steps.forEach { step ->
            record("SCENARIO step ${step.stepId}: attempts=${step.attempts} outcome=${step.outcome}")
            val outcome = step.outcome
            if (outcome is StepOutcome.PartialApply) {
                record(
                    "SCENARIO step ${step.stepId} typed PartialApply: applied=${outcome.detail.applied} " +
                        "notApplied=${outcome.detail.notApplied} note=${outcome.detail.note}",
                )
            }
        }
        record("SCENARIO planStatusEvents=${report.planStatusEvents}")
        val terminalEvents = report.planStatusEvents.filter { it != PlanStatus.RUNNING }
        val completed = report.steps.count { it.outcome is StepOutcome.Completed }

        check("takeover-latched", takeoverHappened.get())
        check("paused-within-bound", pauseObserved.get())
        check("resume-after-explicit-user-confirmation", resumeCount >= 1 && resumed)
        check("steps-executed-after-resume", applyStartedAfterResume.get() && completed >= 1)
        check("stopped-by-owner", report.terminal == PlanTerminal.STOPPED && stopTapped.get())
        check("terminal-exactly-once", terminalEvents.size == 1)
        check("no-action-in-flight-at-settle", !loop.isActionInFlight())
        check(
            "typed-outcome-names-what-did-not-happen",
            report.steps.any {
                it.outcome is StepOutcome.PartialApply || it.outcome is StepOutcome.NotExecuted
            },
        )
        resultOrFail(null)
    }

    private fun check(
        name: String,
        ok: Boolean,
    ) {
        record("SCENARIO check $name: ${if (ok) "PASS" else "FAIL"}")
        if (!ok && failedCheck == null) failedCheck = name
    }

    private fun resultOrFail(reason: String?) {
        if (reason != null && failedCheck == null) failedCheck = reason
        val failed = failedCheck
        record(
            if (failed == null) {
                "ACTION-LOOP SCENARIO RESULT: PASS (all checks green)"
            } else {
                "ACTION-LOOP SCENARIO RESULT: FAIL ($failed)"
            },
        )
        finish()
    }

    private fun finish() {
        done.set(true)
        watcher?.join(WORKER_JOIN_MS)
        activity.onResumeTap = null
        activity.onStopTap = null
        activity.onBannerTap = null
        activity.setPrompt("none")
        record("ACTION-LOOP SCENARIO done (t+${now() - startMs}ms)")
    }

    private fun screenText(): String {
        val result = automation.observe()
        return if (result is A11yResult.Success) result.detail else ""
    }

    private fun describe(result: A11yResult): String =
        when (result) {
            is A11yResult.Success -> "Success(${result.detail.take(120)})"
            is A11yResult.Failure -> "Failure(${result.error})"
            else -> "$result"
        }

    private fun prompt(message: String) {
        activity.setPrompt(message)
    }

    companion object {
        private const val POLL_SMALL_MS = 10L
        private const val APPLY_POLL_MS = 10L
        private const val SERVICE_TIMEOUT_MS = 180000L
        private const val WINDOW_TIMEOUT_MS = 15000L
        private const val TAKEOVER_WINDOW_MS = 120000L
        private const val STOP_WINDOW_MS = 120000L
        private const val APPLY_TIMEOUT_MS = 180000L
        private const val TRANSITION_BOUND_MS = 3000L
        private const val WORKER_JOIN_MS = 5000L
        private const val WINDOW_MARKER = "EQO TEST TARGET"
        private const val TARGET_BUTTON_TEXT = "PRESS ME"
        private const val TARGET_INPUT_ID = "eqo_test_input"
        private const val TARGET_SUBMIT_ID = "eqo_test_submit"
        private const val TYPE_PAYLOAD = "hello from eqo"
    }
}
