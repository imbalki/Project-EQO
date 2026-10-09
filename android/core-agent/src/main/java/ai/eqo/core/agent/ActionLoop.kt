/*
 * EQO (TASK-012, issue #17): one Kotlin loop — permission check, approval,
 * execute, observe, verify, repeat. The user can pause, stop or take over at
 * any moment and always knows what did and did not happen.
 */
package ai.eqo.core.agent

import ai.eqo.data.models.PlanStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * The action loop.
 *
 * Phase order per step: permission check -> static approval -> execute ->
 * observe -> verify -> repeat. Every suspension point re-reads the command
 * state, so a user pause/stop/takeover lands within a bounded time (see
 * [Config]): pause and takeover take effect after the current step finishes
 * (the UX copy says exactly that), stop/cancel take effect at the next tick for
 * reversible actions and after completion for irreversible ones — an
 * irreversible action is completed OR was cancelled before dispatch, never
 * killed silently mid-apply.
 *
 * Guarantees (each has a test):
 *  - transitions are bounded in virtual time (`ActionLoopTransitionsTest`);
 *  - no action is mid-flight at settle and the plan reaches a terminal status
 *    exactly once (`ActionLoopSettleTest`, `ActionLoopRaceTest`);
 *  - the verifier confirms postconditions or records a typed partial-apply
 *    result (`StepVerifierTest`);
 *  - resume happens only after explicit user confirmation
 *    ([resume] takes a [UserResumeConfirmation]; recovery never resumes:
 *    `ActionLoopResumeTest`);
 *  - irreversible actions are never automatically retried
 *    (`SensitivityApprovalPolicyTest` / `ActionLoopSettleTest`).
 *
 * @param approvalGate asks the user when [SensitivityApprovalPolicy] demands
 *   approval for the executed action.
 * @param execute dispatches one executed action.
 * @param observe reads the screen after the action (untrusted data).
 */
@Suppress("LongParameterList", "TooManyFunctions") // one deliberate loop state machine; see TASK-012 evidence
class ActionLoop(
    private val steps: List<LoopStep>,
    private val approvalGate: suspend (LoopStep) -> ApprovalDecision,
    private val execute: suspend (LoopStep) -> ExecuteResult,
    private val observe: suspend () -> String,
    private val verifier: StepVerifier = StepVerifier(),
    private val permissionCheck: suspend (LoopStep) -> PermissionDecision = { PermissionDecision.Granted },
    private val appBlockPolicy: AppBlockPolicy = AppBlockPolicy.ALLOW_ALL,
    private val onPlanStatus: (PlanStatus) -> Unit = {},
    private val config: Config = Config(),
    private val onInterStepWait: (nextStep: Int, total: Int, remainingMs: Long) -> Unit = { _, _, _ -> },
    private val externalTakeoverReason: () -> PauseReason? = { null },
    private val onResumeConfirmed: (UserResumeConfirmation) -> Unit = {},
    private val onDiagnostic: (String) -> Unit = {},
) {
    data class Config(
        /** Command-poll granularity; every transition is bounded in these. */
        val tickMs: Long = 50,
        /** Hard bound on one apply; beyond it the apply is typed as interrupted. */
        val actionTimeoutMs: Long = 5_000,
        /** Settle wait between steps (commands are honoured here). */
        val interStepDelayMs: Long = 250,
        val delayAfterLastStep: Boolean = true,
        val maxAttemptsReversible: Int = 2,
        val maxAttemptsIrreversible: Int = 1,
    )

    private sealed interface Command

    private data object CmdPause : Command

    private data class CmdTakeover(
        val reason: PauseReason,
    ) : Command

    private data object CmdStop : Command

    private data object CmdCancel : Command

    private val lock = Any()

    private val commandQueue = ArrayDeque<Command>()

    private val state = AtomicReference(LoopState.RUNNING)

    private val pauseReason = AtomicReference<PauseReason?>(null)

    private val terminal = AtomicReference<PlanTerminal?>(null)

    private val inFlight = AtomicInteger(0)

    private val started = AtomicBoolean(false)

    private val planStatusEvents = mutableListOf<PlanStatus>()

    @Volatile
    private var lastResumeConfirmation: UserResumeConfirmation? = null

    /** User pause: takes effect after the current step finishes. */
    fun pause(): Boolean = submit(CmdPause)

    /**
     * User takeover (a touch while EQO is mid-action, or a suspected
     * self-gesture latch — security note N-3). Pauses visibly; never kills the
     * task silently.
     */
    fun takeover(reason: PauseReason = PauseReason.USER_TAKEOVER): Boolean {
        require(reason == PauseReason.USER_TAKEOVER || reason == PauseReason.SELF_GESTURE_TAKEOVER_SUSPECTED) {
            "takeover reason must be a takeover cause, got $reason"
        }
        return submit(CmdTakeover(reason))
    }

    /** User stop: the task ends after the current apply settles. */
    fun stop(): Boolean = submit(CmdStop)

    /** User cancel: the task aborts; in-flight reversible applies are interrupted. */
    fun cancel(): Boolean = submit(CmdCancel)

    /**
     * Resume only after explicit user confirmation (spec criterion 4): the
     * caller must pass a [UserResumeConfirmation] minted by the user-facing
     * control surface. There is no parameterless overload and recovery code
     * cannot produce a token (`TakeoverResumeUserOnlyTest`).
     */
    fun resume(confirmation: UserResumeConfirmation): Boolean {
        synchronized(lock) {
            if (terminal.get() != null || state.get() != LoopState.PAUSED) return false
            onResumeConfirmed(confirmation)
            lastResumeConfirmation = confirmation
            pauseReason.set(null)
            state.set(LoopState.RUNNING)
            emitPlanStatus(PlanStatus.RUNNING)
            return true
        }
    }

    /**
     * Environment recovery (accessibility re-bound, service reconnected, ...)
     * NEVER resumes a paused loop: no automatic resume after recovery. Returns
     * false always; the user must call [resume] with explicit confirmation.
     */
    @Suppress("FunctionOnlyReturningConstant") // the constant IS the spec rule
    fun notifyEnvironmentRecovered(): Boolean = false

    /** True while an action apply is in flight (never true at settle). */
    fun isActionInFlight(): Boolean = inFlight.get() > 0

    fun currentState(): LoopState = state.get()

    fun currentPauseReason(): PauseReason? = pauseReason.get()

    /** Runs the plan to a terminal state and reports what did/didn't happen. */
    @Suppress("LoopWithTooManyJumpStatements") // each break names a settle reason
    suspend fun run(): LoopReport {
        check(started.compareAndSet(false, true)) { "ActionLoop.run() may only be called once" }
        emitPlanStatus(PlanStatus.RUNNING)
        val records = mutableListOf<StepRecord>()
        for ((index, step) in steps.withIndex()) {
            if (!settleBeforeStep()) {
                records += StepRecord(step.stepId, StepOutcome.NotExecuted("loop settled before dispatch"), 0)
                records += remainingNotExecuted(step)
                break
            }
            onDiagnostic("step=${index + 1} phase=start")
            val record = runStep(step)
            onDiagnostic("step=${index + 1} outcome=${record.outcome.diagnosticKind}")
            records += record
            // A typed partial-apply on an irreversible action stops the plan:
            // the effect is uncertain, so nothing else may run on top of it.
            val halted =
                when (record.outcome) {
                    is StepOutcome.Completed -> false
                    is StepOutcome.PartialApply -> step.action.irreversible
                    else -> true
                }
            if (halted) {
                if (terminal.get() == null) {
                    terminalize(PlanTerminal.FAILED)
                }
                records += remainingNotExecuted(step)
                break
            }
            if (!delayBetweenSteps(index + 2)) {
                records += remainingNotExecuted(step)
                break
            }
        }
        if (terminal.get() == null && awaitDispatchReady()) {
            terminalize(PlanTerminal.COMPLETED)
        }
        return report(records)
    }

    private fun remainingNotExecuted(after: LoopStep): List<StepRecord> {
        val index = steps.indexOfFirst { it.stepId == after.stepId }
        return steps
            .drop(index + 1)
            .map { StepRecord(it.stepId, StepOutcome.NotExecuted("earlier step did not complete"), 0) }
    }

    @Suppress("ReturnCount") // every outcome returns its own typed record
    private suspend fun runStep(step: LoopStep): StepRecord {
        var attempts = 0
        while (true) {
            if (!awaitDispatchReady()) {
                return StepRecord(step.stepId, StepOutcome.NotExecuted("loop settled before dispatch"), attempts)
            }
            attempts++
            val gate = gateCheck(step)
            if (gate != null) return StepRecord(step.stepId, gate, attempts)
            val apply =
                runApply(step)
                    ?: return StepRecord(
                        step.stepId,
                        StepOutcome.NotExecuted("loop settled before dispatch"),
                        attempts - 1,
                    )
            onDiagnostic("step=${steps.indexOf(step) + 1} execute=${apply.diagnosticKind} code=${apply.diagnosticCode}")
            val observed =
                try {
                    observe()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    ""
                }
            val outcome =
                if (apply is ExecuteResult.Interrupted) {
                    verifier.interruptedApply(step.action, apply.note)
                } else {
                    verifier.verify(step.action, apply, observed)
                }
            onDiagnostic("step=${steps.indexOf(step) + 1} verification=${outcome.diagnosticCode(apply, step.action)}")
            // A pause/takeover that landed during this step takes effect now,
            // after the step finished — the loop must not start another action.
            val canContinue = settleBeforeStep()
            when (outcome) {
                is StepVerifier.Outcome.Confirmed ->
                    return StepRecord(
                        step.stepId,
                        StepOutcome.Completed(successSummary(step)),
                        attempts,
                    )
                is StepVerifier.Outcome.Partial ->
                    return StepRecord(step.stepId, StepOutcome.PartialApply(outcome.detail), attempts)
                is StepVerifier.Outcome.Failed ->
                    if (canContinue && isRetryable(step, outcome, attempts)) {
                        continue
                    } else {
                        return StepRecord(step.stepId, StepOutcome.Failed(outcome.reason), attempts)
                    }
            }
        }
    }

    private fun successSummary(step: LoopStep): String =
        if (step.action.expectedPostconditions.isEmpty()) {
            "executor reported success; no independent postconditions"
        } else {
            "executor reported success; ${StepVerifier.SCREEN_EVIDENCE_WARNING}"
        }

    /**
     * Retries are for reversible, transient failures only. An irreversible
     * action is never automatically retried (spec scope), however the failure
     * is classified.
     */
    private fun isRetryable(
        step: LoopStep,
        outcome: StepVerifier.Outcome.Failed,
        attempts: Int,
    ): Boolean = !step.action.irreversible && outcome.transient && attempts < config.maxAttemptsReversible

    @Suppress("ReturnCount") // each gate failure returns its typed outcome
    private suspend fun gateCheck(step: LoopStep): StepOutcome? {
        val permission = permissionCheck(step)
        onDiagnostic("step=${steps.indexOf(step) + 1} permission=${permission.diagnosticKind}")
        if (permission is PermissionDecision.Denied) {
            return StepOutcome.Failed("permission denied: ${permission.reason}")
        }
        when (val block = appBlockPolicy.decisionFor(step.action.params["package"])) {
            is AppBlockDecision.Block -> return StepOutcome.Failed("app hard-blocked: ${block.reason}")
            AppBlockDecision.Allow -> Unit
        }
        if (SensitivityApprovalPolicy.requiresApproval(step.action)) {
            when (val decision = approvalGate(step)) {
                is ApprovalDecision.Rejected ->
                    return StepOutcome.Failed("approval not granted: ${decision.reason}")
                ApprovalDecision.Approved -> Unit
            }
        }
        return null
    }

    /**
     * Executes one apply with in-flight tracking, a hard timeout and the
     * irreversible-action rule: an irreversible apply is never cancelled
     * mid-flight (it completes, or it was cancelled before dispatch).
     */
    @Suppress("TooGenericExceptionCaught") // executor errors map to typed failures
    private suspend fun runApply(step: LoopStep): ExecuteResult? =
        coroutineScope {
            if (!awaitDispatchReady()) return@coroutineScope null
            inFlight.incrementAndGet()
            // Start inline: no scheduler suspension between command drain and execute.
            // Only this run coroutine applies pause/terminal transitions; external
            // commands enqueue and concurrent resume can only make PAUSED -> RUNNING.
            val job = async(start = CoroutineStart.UNDISPATCHED) { execute(step) }
            var timedOut = false
            var cancelledMidApply = false
            var waitedMs = 0L
            // File choice and exact-name confirmation each allow a minute, only on approved file sends.
            val timeoutMs = if (step.action.name in AttachmentSpec.ACTIONS &&
                AttachmentSpec.parse(step.action.params[AttachmentSpec.PARAM]).isNotEmpty()
            ) maxOf(config.actionTimeoutMs, 150_000L) else config.actionTimeoutMs
            try {
                while (job.isActive) {
                    delay(config.tickMs)
                    waitedMs += config.tickMs
                    val command = pollCommand()
                    when {
                        command is CmdStop || command is CmdCancel ->
                            if (step.action.irreversible) {
                                // An irreversible apply completes OR was cancelled before
                                // dispatch - it is never killed mid-flight. The command waits
                                // for the apply to settle and lands at the next checkpoint.
                                enqueue(command)
                            } else {
                                applyCommand(command)
                                job.cancel()
                                cancelledMidApply = true
                            }
                        command is CmdPause || command is CmdTakeover -> enqueue(command)
                        waitedMs >= timeoutMs -> {
                            job.cancel()
                            timedOut = true
                        }
                    }
                }
                try {
                    job.await()
                } catch (e: CancellationException) {
                    when {
                        timedOut ->
                            ExecuteResult.Interrupted("apply timed out mid-apply after ${timeoutMs}ms")
                        cancelledMidApply ->
                            ExecuteResult.Interrupted("apply cancelled mid-apply by the user; effect unknown")
                        else -> throw e
                    }
                } catch (e: Exception) {
                    ExecuteResult.Failure("execution error: ${e.message}", transient = false)
                }
            } finally {
                inFlight.decrementAndGet()
            }
        }

    /** No dispatch while paused or after terminal, including retries and suspended gates. */
    private suspend fun awaitDispatchReady(): Boolean {
        var ready = settleBeforeStep()
        while (ready && state.get() == LoopState.PAUSED) {
            ready = awaitResume() && settleBeforeStep()
        }
        return ready && state.get() == LoopState.RUNNING && terminal.get() == null
    }

    /** Applies pending commands. False = the loop has settled terminally. */
    private fun settleBeforeStep(): Boolean {
        while (true) {
            val command = pollCommand() ?: break
            applyCommand(command)
        }
        if (terminal.get() == null && state.get() == LoopState.RUNNING) {
            externalTakeoverReason()?.let { applyCommand(CmdTakeover(it)) }
        }
        return terminal.get() == null
    }

    @Suppress("ReturnCount") // false returns are settle points, not style
    private suspend fun delayBetweenSteps(nextStep: Int): Boolean {
        // Keep the optional last-step wait policy here, with the pacing itself.
        if (nextStep > steps.size && !config.delayAfterLastStep) return true
        onDiagnostic("pacing=start next=$nextStep delay_ms=${config.interStepDelayMs}")
        var waitedMs = 0L
        while (waitedMs < config.interStepDelayMs) {
            if (!settleBeforeStep()) return false
            if (state.get() == LoopState.PAUSED && !awaitResume()) return false
            if (nextStep <= steps.size) {
                onInterStepWait(nextStep, steps.size, config.interStepDelayMs - waitedMs)
            }
            delay(config.tickMs)
            waitedMs += config.tickMs
        }
        onDiagnostic("pacing=end next=$nextStep")
        return settleBeforeStep()
    }

    /** Suspends while paused. False = the loop settled terminally meanwhile. */
    private suspend fun awaitResume(): Boolean {
        while (state.get() == LoopState.PAUSED) {
            if (!settleBeforeStep()) return false
            delay(config.tickMs)
        }
        return terminal.get() == null
    }

    private fun applyCommand(command: Command) {
        synchronized(lock) {
            when (command) {
                CmdStop -> terminalize(PlanTerminal.STOPPED)
                CmdCancel -> terminalize(PlanTerminal.CANCELLED)
                CmdPause ->
                    if (terminal.get() == null && state.get() == LoopState.RUNNING) {
                        pauseReason.set(PauseReason.USER_PAUSE)
                        state.set(LoopState.PAUSED)
                        emitPlanStatus(PlanStatus.PAUSED)
                    }
                is CmdTakeover ->
                    if (terminal.get() == null && state.get() == LoopState.RUNNING) {
                        onDiagnostic("takeover=${command.reason.name}")
                        pauseReason.set(command.reason)
                        state.set(LoopState.PAUSED)
                        emitPlanStatus(PlanStatus.PAUSED)
                    }
            }
        }
    }

    private fun terminalize(status: PlanTerminal) {
        // CAS, state and notification are one transition relative to resume.
        // Otherwise a confirmation callback can let resume overwrite terminal state.
        synchronized(lock) {
            if (terminal.compareAndSet(null, status)) {
                state.set(if (status == PlanTerminal.CANCELLED) LoopState.CANCELLED else LoopState.STOPPED)
                emitPlanStatus(status.toPlanStatus())
            }
        }
    }

    private fun emitPlanStatus(status: PlanStatus) {
        synchronized(lock) {
            planStatusEvents += status
            onDiagnostic("status=${status.name}")
            onPlanStatus(status)
        }
    }

    private fun submit(command: Command): Boolean {
        synchronized(lock) {
            if (terminal.get() != null) return false
            commandQueue.addLast(command)
            return true
        }
    }

    private fun enqueue(command: Command) {
        synchronized(lock) { commandQueue.addFirst(command) }
    }

    private fun pollCommand(): Command? = synchronized(lock) { commandQueue.removeFirstOrNull() }

    private fun report(records: List<StepRecord>): LoopReport =
        synchronized(lock) {
            LoopReport(
                loopState = state.get(),
                terminal = terminal.get(),
                pauseReason = pauseReason.get(),
                steps = records.toList(),
                planStatusEvents = planStatusEvents.toList(),
            )
        }
}

private val ExecuteResult.diagnosticKind: String
    get() =
        when (this) {
            is ExecuteResult.Success -> "Success"
            is ExecuteResult.Failure -> "Failure"
            is ExecuteResult.Interrupted -> "Interrupted"
        }

private val StepOutcome.diagnosticKind: String
    get() =
        when (this) {
            is StepOutcome.Completed -> "Completed"
            is StepOutcome.PartialApply -> "PartialApply"
            is StepOutcome.Failed -> "Failed"
            is StepOutcome.NotExecuted -> "NotExecuted"
        }

private val ExecuteResult.diagnosticCode: String
    get() =
        when (this) {
            is ExecuteResult.Success -> "executor_success"
            is ExecuteResult.Failure -> "execution_failed"
            is ExecuteResult.Interrupted -> "apply_interrupted_effect_unknown"
        }

private fun StepVerifier.Outcome.diagnosticCode(
    apply: ExecuteResult,
    action: ExecutedAction,
): String =
    when (this) {
        is StepVerifier.Outcome.Confirmed ->
            if (action.expectedPostconditions.isEmpty()) {
                "executor_only_no_independent_postconditions"
            } else {
                "untrusted_screen_match"
            }
        is StepVerifier.Outcome.Partial ->
            if (apply is ExecuteResult.Interrupted) "interrupted_effect_unknown" else "postconditions_unobserved"
        is StepVerifier.Outcome.Failed -> "failed"
    }

private val PermissionDecision.diagnosticKind: String
    get() =
        when (this) {
            PermissionDecision.Granted -> "Granted"
            is PermissionDecision.Denied -> "Denied"
        }
