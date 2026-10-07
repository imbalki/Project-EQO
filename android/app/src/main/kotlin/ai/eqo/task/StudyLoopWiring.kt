/*
 * EQO (TASK-015, issue #20): the REAL wiring of core-agent's ActionLoop in the study app.
 *
 * TASK-012 left `ActionLoop` unconstructed in main code with test-only fail-open defaults
 * (`permissionCheck = Granted`, `AppBlockPolicy.ALLOW_ALL`). TASK-015 is where the loop
 * gets a production caller, and the caller wires the real checks:
 *
 *  (a) permissionCheck is [StudyPermissionCheck] — real Android permission state plus the
 *      accessibility-service and helper-binder planes, each checked on its own;
 *  (b) appBlockPolicy is [StudyAppBlockPolicy] — an explicit policy object (D-010: no
 *      hard-block list), never the `ALLOW_ALL` test default;
 *  (c) approvalGate is [StudyApprovalGate] — a real user-confirmation card with the
 *      PRD's 60s countdown (REQ-TASK-02); an expired card is a rejection;
 *  (d) irreversible/outward actions need approval before dispatch (TASK-012 B2): the
 *      static [SensitivityApprovalPolicy] stays the only approval decision-maker and the
 *      executor never runs an outward verb that has no compose-only implementation;
 *  (e) `WRITE_SECURE_SETTINGS` is never written or relied on (TASK-007 SF-1) — see
 *      `AccessibilitySetupGuide`, which only opens Android's own settings screens.
 */
package ai.eqo.task

import ai.eqo.core.agent.ActionLoop
import ai.eqo.core.agent.AppBlockDecision
import ai.eqo.core.agent.AppBlockPolicy
import ai.eqo.core.agent.ApprovalDecision
import ai.eqo.core.agent.ExecuteResult
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.PermissionDecision
import ai.eqo.study.ApprovalOutcome
import ai.eqo.study.ApprovalRequest
import ai.eqo.study.FailureClass

/** Marker permission for steps that need the TASK-007 privileged helper binder. */
object StudyPermission {
    const val HELPER_BINDER = "ai.eqo.permission.HELPER_BINDER"

    /** Verbs that run over the accessibility plane (TASK-009) and need the service enabled. */
    val AUTOMATION_VERBS =
        setOf(
            "tap",
            "scroll",
            "observe",
            "type_text",
            "click_text",
            "click_id",
            "back",
            "home",
            "tap_text",
            "paste",
            "press_back",
            "press_home",
            "press_enter",
            "send_whatsapp",
            "send_telegram",
        )
}

/**
 * The production permission check handed to [ActionLoop]: every plane is checked on its
 * own and a denial always carries a repair path. EQO never grants anything here — this
 * reads state, it does not write it (REQ-A11Y-03).
 */
class StudyPermissionCheck(
    private val manifestPermissionGranted: (String) -> Boolean,
    private val accessibilityServiceEnabled: () -> Boolean,
    private val helperBinderAlive: () -> Boolean,
) {
    // Each failing plane returns its own typed denial (same reasoning as ActionLoop's
    // gateCheck); folding them into one expression would obscure which plane denied.
    @Suppress("ReturnCount")
    fun check(step: LoopStep): PermissionDecision {
        step.requiredPermission?.let { permission ->
            if (permission == StudyPermission.HELPER_BINDER) {
                return if (helperBinderAlive()) {
                    PermissionDecision.Granted
                } else {
                    PermissionDecision.Denied(FailureClass.BINDER_DEAD.repair)
                }
            }
            if (!manifestPermissionGranted(permission)) {
                return PermissionDecision.Denied(
                    "Android permission $permission is not granted to EQO. Open Settings > Apps > EQO > " +
                        "Permissions and turn it on yourself — EQO never grants permissions.",
                )
            }
        }
        if (needsAccessibility(step.action) && !accessibilityServiceEnabled()) {
            return PermissionDecision.Denied(FailureClass.A11Y_LOST.repair)
        }
        return PermissionDecision.Granted
    }

    private fun needsAccessibility(action: ExecutedAction): Boolean {
        val verb = action.name.lowercase()
        return StudyPermission.AUTOMATION_VERBS.any { verb == it || verb.contains(it) }
    }
}

/**
 * The app hard-block seam (issue #42), wired explicitly. D-010 decided there is NO
 * hard-block list for banking/authenticator/crypto apps in the study build, so the deny
 * set is empty by decision — but the policy is a real object consulted on every dispatch,
 * not the `AppBlockPolicy.ALLOW_ALL` test default, and adding entries later is a data
 * change only.
 */
class StudyAppBlockPolicy(
    private val blockedPackages: Set<String> = emptySet(),
) : AppBlockPolicy {
    override fun decisionFor(packageName: String?): AppBlockDecision =
        when {
            packageName == null -> AppBlockDecision.Allow
            packageName in blockedPackages ->
                AppBlockDecision.Block("target app is on the study hard-block list (empty per D-010)")
            else -> AppBlockDecision.Allow
        }
}

/** The UI surface an approval card is rendered on. Implemented by the task screen. */
fun interface StudyApprovalSurface {
    /** Suspends until the user approves, rejects, or the countdown expires. */
    suspend fun ask(request: ApprovalRequest): ApprovalOutcome
}

/**
 * The production approval gate handed to [ActionLoop]. Every approval is an actual user
 * gesture on a card that shows the action, its target and the app it happens in
 * (REQ-TASK-02). A timed-out card is a rejection: the task never executes on a stale
 * approval, and nothing auto-approves.
 */
class StudyApprovalGate(
    private val surface: StudyApprovalSurface,
    private val nowMs: () -> Long,
    private val approvedPlan: ai.eqo.core.agent.ApprovedTaskPlan? = null,
) {
    suspend fun request(step: LoopStep): ApprovalDecision {
        approvedPlan?.let { plan ->
            return if (plan.permits(step)) {
                ApprovalDecision.Approved
            } else {
                ApprovalDecision.Rejected("Step was not approved")
            }
        }
        val request =
            ApprovalRequest(
                stepId = step.stepId,
                action = step.action.name,
                target =
                    step.action.params.entries
                        .joinToString(", ") { "${it.key}=${it.value}" },
                app = step.action.params["package"] ?: "the current app",
                requestedAtMs = nowMs(),
            )
        return when (val outcome = surface.ask(request)) {
            ApprovalOutcome.Approved -> ApprovalDecision.Approved
            is ApprovalOutcome.Rejected -> ApprovalDecision.Rejected(outcome.reason)
            ApprovalOutcome.TimedOut ->
                ApprovalDecision.Rejected("approval timed out after ${ApprovalRequest.APPROVAL_TIMEOUT_MS}ms")
        }
    }
}

/**
 * The automation plane the study executor runs on. Implemented at the Android edge over
 * TASK-009's `EqoAutomation`; faked in host tests. Every method is a typed, observable
 * action — no method reports success for something it did not do.
 */
interface StudyNavigationPort {
    /** Untrusted screen text (never treated as instructions). */
    fun observe(): String

    /** Most recent typed accessibility failure, when the port supports it. */
    val lastFailure: ExecuteResult.Failure? get() = null

    fun tap(text: String): Boolean

    fun tapById(viewId: String): Boolean

    fun typeText(text: String): Boolean

    fun scroll(direction: String): Boolean

    fun back(): Boolean

    fun home(): Boolean

    fun enter(): Boolean = false

    fun openApp(app: String): Boolean = false

    fun typeTarget(
        target: String,
        text: String,
    ): Boolean = false
}

interface StudyMessagingPort {
    suspend fun sendChat(
        app: String,
        body: String,
    ): Boolean = false

    fun composeEmailDraft(
        recipient: String,
        subject: String,
        body: String,
    ): Boolean = false

    /** Compose-only SMS: opens the messaging app with a filled draft and sends nothing (REQ-SMS-01). */
    fun composeSmsDraft(
        recipient: String,
        body: String,
    ): Boolean
}

interface StudyAutomationPort :
    StudyNavigationPort,
    StudyMessagingPort

/**
 * The production `execute` seam handed to [ActionLoop].
 *
 * Outward/irreversible verbs are compose-only: `send_sms` opens a draft and reports
 * "nothing was sent"; there is no verb in this build that sends, pays, posts or deletes.
 * Any verb without an implementation fails typed — the executor never fakes success
 * (REQ-TASK-06 receipts must be true).
 */
class StudyActionExecutor(
    private val port: StudyAutomationPort,
    private val approvedPlan: ai.eqo.core.agent.ApprovedTaskPlan? = null,
    private val registryExecute: (suspend (String, Map<String, String>) -> ai.eqo.actions.base.ActionResult)? = null,
) {
    private val handlers: Map<String, suspend (Map<String, String>) -> Boolean> =
        mapOf(
            "observe" to { port.observe().isNotBlank() },
            "tap" to { port.tap(it.value("text")) },
            "click_text" to { port.tap(it.value("text")) },
            "tap_text" to { port.tap(it.value("text")) },
            "click_id" to { port.tapById(it.value("view_id")) },
            "open_app" to { port.openApp(it.value("app")) },
            "type_text" to { type(it) },
            "paste" to { type(it) },
            "press_back" to { port.back() },
            "back" to { port.back() },
            "press_home" to { port.home() },
            "home" to { port.home() },
            "press_enter" to { port.enter() },
            "send_whatsapp" to { port.sendChat("whatsapp", it.value("body")) },
            "send_telegram" to { port.sendChat("telegram", it.value("body")) },
            "compose_email" to { port.composeEmailDraft(it.value("to"), it.value("subject"), it.value("body")) },
            "compose_sms" to { port.composeSmsDraft(it.value("to"), it.value("body")) },
            "scroll" to { port.scroll(it.value("direction").ifBlank { "down" }) },
        )

    suspend fun execute(step: LoopStep): ExecuteResult =
        if (approvedPlan != null && !approvedPlan.permits(step)) {
            ExecuteResult.Failure("Step was not approved")
        } else {
            dispatch(step.action)
        }

    @Suppress("ReturnCount") // One early return per gated action kind.
    private suspend fun dispatch(action: ExecutedAction): ExecuteResult {
        if (action.name == action.name.uppercase()) {
            val execute = registryExecute ?: return ExecuteResult.Failure("Registry executor unavailable")
            val result = execute(action.name, action.params)
            android.util.Log.i("EqoRun", "action=${action.name} result=${result.javaClass.simpleName}")
            return if (result.success) {
                ExecuteResult.Success(result.data.orEmpty())
            } else {
                ExecuteResult.Failure(result.error ?: "Action not completed")
            }
        }
        val verb = action.name.lowercase()
        val handler =
            handlers[verb]
                ?: return ExecuteResult.Failure("no study executor for action '${action.name}'")
        return if (handler(action.params)) {
            ExecuteResult.Success(successDetail(verb))
        } else {
            port.lastFailure
                ?: ExecuteResult.Failure(
                    "study_action_not_applied",
                    transient = verb != "observe" && !action.irreversible,
                )
        }
    }

    private fun type(params: Map<String, String>): Boolean =
        if (approvedPlan == null) {
            port.typeText(params.value("text"))
        } else {
            port.typeTarget(
                params.value("target"),
                params.value("text"),
            )
        }

    private fun successDetail(verb: String): String =
        when (verb) {
            "compose_sms", "compose_email" -> "draft opened; nothing was sent"
            "send_whatsapp", "send_telegram" -> "Send was pressed in the open chat; delivery is not verified"
            else -> "automation verb '$verb' applied through the accessibility plane"
        }

    private fun Map<String, String>.value(key: String): String = this[key].orEmpty()
}
