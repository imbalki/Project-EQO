/*
 * EQO (TASK-012, issue #17): the static approval policy. Approval before
 * sensitive or irreversible actions is decided by a STATIC policy over the
 * EXECUTED action (verbs/targets), never by model output.
 */
package ai.eqo.core.agent

/**
 * Static policy answering "does this executed action need the user's approval?".
 *
 * The decision input is the [ExecutedAction] that will actually run — its verb
 * and its resolved targets — not the model's plan text, not the model's claim
 * about safety ([ExecutedAction.plannerClaim] is ignored by design). A model
 * reply claiming "safe, no approval needed" cannot skip the gate
 * (`SensitivityApprovalPolicyTest`).
 *
 * Structure note (issue #42, owner decision pending): a hard-block list of
 * banking/authenticator/crypto apps is intentionally NOT here. [AppBlockPolicy]
 * is the seam one would plug such a list into; the default allows everything,
 * and the loop consults the seam before dispatch without any other change.
 */
object SensitivityApprovalPolicy {
    // Only these known, non-outward operations may skip approval. Unknown verbs,
    // coordinate taps (no semantic target), and compound macros/routines fail closed.
    private val NON_OUTWARD_VERBS =
        setOf(
            "tap",
            "scroll",
            "observe",
            "type_text",
            "click_text",
            "click_id",
            "back",
            "home",
        )

    /** Verb tokens (substring match on the action name) that are sensitive. */
    private val SENSITIVE_VERBS =
        setOf(
            "pay",
            "send",
            "delete",
            "install",
            "share",
            "login",
            "log_in",
            "signin",
            "sign_in",
            "purchase",
            "transfer",
            "uninstall",
            "format",
            "wipe",
            "post",
            "publish",
        )

    /** Target tokens (substring match on resolved params) that are sensitive. */
    private val SENSITIVE_TARGETS =
        setOf(
            "pay",
            "payment",
            "delete",
            "install",
            "share",
            "login",
            "password",
            "otp",
            "card",
            "bank",
            "crypto",
        )

    /** True when the user must approve this action before it is executed. */
    fun requiresApproval(action: ExecutedAction): Boolean {
        if (action.irreversible) return true
        return requiresApproval(action.name, action.params)
    }

    /** Pure form: same decision, no model output anywhere in the input. */
    fun requiresApproval(
        actionName: String,
        params: Map<String, String> = emptyMap(),
    ): Boolean {
        val name = actionName.lowercase()
        if (name !in NON_OUTWARD_VERBS || SENSITIVE_VERBS.any { name.contains(it) }) return true
        val targets =
            buildString {
                params.values.forEach { append(it.lowercase()).append(' ') }
            }
        return SENSITIVE_TARGETS.any { targets.contains(it) }
    }
}

/** Decision of the optional app hard-block seam (issue #42). */
sealed class AppBlockDecision {
    data object Allow : AppBlockDecision()

    data class Block(
        val reason: String,
    ) : AppBlockDecision()
}

/**
 * Seam for a hard-block list of apps (banking/authenticator/crypto) — issue #42,
 * owner decision pending, deliberately NOT implemented here. The loop consults
 * this before dispatch, so adding a list later needs no loop change.
 */
fun interface AppBlockPolicy {
    fun decisionFor(packageName: String?): AppBlockDecision

    companion object {
        /** Default: no app is hard-blocked. */
        val ALLOW_ALL: AppBlockPolicy = AppBlockPolicy { AppBlockDecision.Allow }
    }
}
