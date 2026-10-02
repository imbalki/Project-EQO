// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/core/agent/AutoApprovalPolicy.kt
package ai.eqo.core.agent

import ai.eqo.data.models.AutoMode
import ai.eqo.data.models.Plan
import ai.eqo.data.models.PlanStep

/**
 * Pure decision logic for Auto mode (upstream issue 18 spec, YOLO semantics
 * revised per #52 discussion). Mixed plans are all-or-nothing in AUTO: a plan
 * auto-runs only when EVERY step's action is granted and none is
 * neverAutoApprove. YOLO is all-in by owner decision — the user opted out of
 * every approval gate, including the neverAutoApprove guard. No mid-plan pause
 * states — a blocked plan falls back to the normal PlanProposed gate whole.
 */
object AutoApprovalPolicy {
    fun shouldAutoApprove(
        mode: AutoMode,
        granted: Set<String>,
        plan: Plan,
    ): Boolean =
        when (mode) {
            AutoMode.OFF -> false
            AutoMode.YOLO -> true
            AutoMode.AUTO ->
                plan.steps
                    .flatMap { step ->
                        listOfNotNull(step.action.takeIf { it.isNotBlank() }, step.fallback.takeIf { it.isNotBlank() })
                    }.none { ActionSchema.isNeverAutoApprove(it) || it !in granted }
        }

    /**
     * Distinct actions (in step order) that keep this plan from auto-running.
     * Includes each step's non-blank [PlanStep.fallback] — AgentLoop executes
     * fallbacks on primary failure, so they must pass the same allowlist gate.
     */
    fun blockedActions(
        granted: Set<String>,
        steps: List<PlanStep>,
    ): List<String> =
        steps
            .flatMap { step ->
                listOfNotNull(step.action.takeIf { it.isNotBlank() }, step.fallback.takeIf { it.isNotBlank() })
            }.distinct()
            .filter { it !in granted || ActionSchema.isNeverAutoApprove(it) }

    /** Only known, non-neverAutoApprove actions may ever be granted. */
    fun isGrantable(actionName: String): Boolean =
        ActionSchema.ALL_ACTIONS.any { it.name == actionName } && !ActionSchema.isNeverAutoApprove(actionName)
}
