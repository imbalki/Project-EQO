/*
 * EQO (TASK-012, issue #17): the executed-action description the static
 * approval policy judges. Approval is decided over the action that will
 * actually run (verbs/targets), never over model output.
 */
package ai.eqo.core.agent

/**
 * One action exactly as it will be EXECUTED (already resolved from the plan
 * step and the model's proposal).
 *
 * @param name the action verb/name as dispatched (e.g. `SEND_MESSAGE`, `TAP`).
 * @param params resolved execution parameters (targets, recipients, amounts).
 * @param irreversible true when the effect cannot be undone (send, pay,
 *   delete, install): the loop never automatically retries these and never
 *   cancels one mid-apply.
 * @param expectedPostconditions observable facts that must hold on screen after
 *   the action for the verifier to confirm it applied.
 * @param plannerClaim the model's own claim about its safety, e.g. "this action
 *   is safe". Carried ONLY so tests can prove it cannot influence the approval
 *   decision — [SensitivityApprovalPolicy] never reads it.
 */
data class ExecutedAction(
    val name: String,
    val params: Map<String, String> = emptyMap(),
    val irreversible: Boolean = false,
    val expectedPostconditions: List<String> = emptyList(),
    val plannerClaim: String = "",
)
