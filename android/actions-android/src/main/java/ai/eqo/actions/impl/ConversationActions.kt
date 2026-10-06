// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51
// Origin: paths: app/src/main/java/com/opendroid/ai/actions/SystemActions.kt lines 997-1048,
// Origin: core/agent/ActionSchema.kt AGENT vocabulary; EQO non-blocking port.
package ai.eqo.actions.impl

import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import android.content.Context

internal class ConversationActions {
    fun getActions(): List<Action> = listOf(ChatAction(), AskUserAction())

    private companion object {
        const val MAX_OPTIONS = 10
    }

    private class ChatAction : Action {
        override val name = "CHAT"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            return ActionResult.Success(mapOf("message" to params.getValue("response")))
        }
    }

    private class AskUserAction : Action {
        override val name = "ASK_USER"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            // Existing loop owns the owner-input lifecycle; never await a second AgentLoop or fire unguarded Toasts.
            return ActionResult.NeedsInput(
                question = params.getValue("question"),
                options =
                    params["options"]
                        .orEmpty()
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .take(MAX_OPTIONS),
                metadata = params["paramKey"]?.let { mapOf("paramKey" to it) } ?: emptyMap(),
            )
        }
    }
}
