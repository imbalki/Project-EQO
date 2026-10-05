// Origin: EQO TASK-069 (#20), coroutine-scoped registry execution permit.
package ai.eqo.actions.impl

import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import android.content.Context
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

private object RegistryPermit : CoroutineContext.Element, CoroutineContext.Key<RegistryPermit> {
    override val key: CoroutineContext.Key<*> get() = this
}

internal suspend fun requireRegistryExecution(): ActionResult.Failure? =
    if (coroutineContext[RegistryPermit] == null) {
        ActionResult.Failure("Action must run through the EQO action registry.")
    } else {
        null
    }

/** The permit follows suspension/dispatcher switches; it is never returned to callers. */
internal suspend fun executeRegistered(
    action: Action,
    params: Map<String, String>,
    context: Context,
): ActionResult = withContext(RegistryPermit) { action.execute(params, context) }
