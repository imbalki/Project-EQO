// Origin: EQO TASK-078 (#20), the one path by which saved macros and routines dispatch their steps.
package ai.eqo.actions.impl

import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.ActionCategory
import ai.eqo.core.agent.ActionDefinition
import ai.eqo.core.agent.ActionRisk
import ai.eqo.core.agent.ActionRiskPolicy
import ai.eqo.core.agent.ActionSchema
import ai.eqo.core.agent.ActionSequenceExecutor

/**
 * A saved macro or routine runs after one approval of its own name, so its inner steps cannot be shown to the owner
 * one by one. Inner steps therefore go back through the same registry (same validation and permission requests), but
 * only actions that are read-only or reversible are allowed. Anything that sends, calls, taps, types, deletes or
 * spends, any macro/routine action (no recursion, no self-editing), and AUTO_REPLY_TOGGLE need their own approval.
 */
internal object NestedStepPolicy {
    private val denied = setOf("AUTO_REPLY_TOGGLE")

    private fun isSensitive(definition: ActionDefinition): Boolean =
        ActionRiskPolicy.forDefinition(definition).severity >= ActionRisk.SENSITIVE.severity

    fun refusalReason(name: String): String? {
        val definition = ActionSchema.getAction(name)
        return when {
            definition == null -> "$name is not a known action."
            definition.category == ActionCategory.MACRO -> "$name cannot run inside a macro or routine."
            name in denied || definition.neverAutoApprove || isSensitive(definition) ->
                "$name needs its own approval and cannot run inside a macro or routine."
            else -> null
        }
    }
}

/** Builds the shared [ActionSequenceExecutor]; the registry is supplied after construction to break the cycle. */
internal class NestedActionRunner(
    private val registry: () -> AndroidActionRegistry?,
) {
    val sequence =
        ActionSequenceExecutor(
            executeAction = { name, params, _ -> dispatch(name, params) },
            hasAction = { refusalReason(it) == null },
        )

    /** Why [name] may not run as an inner step, or null when it may. */
    fun refusalReason(name: String): String? =
        NestedStepPolicy.refusalReason(name)
            ?: "$name is not available.".takeIf { registry()?.enabledActionNames?.contains(name) != true }

    private suspend fun dispatch(
        name: String,
        params: Map<String, String>,
    ): ActionResult =
        refusalReason(name)?.let { ActionResult.Failure(it) }
            ?: registry()?.execute(name, params)
            ?: ActionResult.Failure("The action registry is not available.")
}
