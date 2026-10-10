package ai.eqo.task

import ai.eqo.accessibility.UntrustedScreenText
import ai.eqo.actions.impl.ActionPermission
import ai.eqo.actions.impl.AndroidActionRegistry
import ai.eqo.actions.impl.LaunchableAppResolver
import ai.eqo.actions.impl.PermissionRequester
import ai.eqo.core.agent.ApprovedTaskPlan
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class MiniAccessNeeded : Exception()

/** Check-only preflight: missing access is obtained in Full EQO before this panel can approve. */
internal object MiniTaskPreparation {
    suspend fun prepare(
        context: Context,
        request: String,
    ): ApprovedTaskPlan {
        val registry =
            AndroidActionRegistry.create(
                context,
                PermissionRequester { access ->
                    when (access) {
                        is ActionPermission.Runtime ->
                            context.checkSelfPermission(access.name) == PackageManager.PERMISSION_GRANTED
                        is ActionPermission.SpecialAccess -> access.isGranted()
                    }
                },
            )
        val result =
            withContext(Dispatchers.IO) {
                val planner = TaskPlanningRuntime.planner(context, registry.enabledActionNames) ?: error("No model")
                val resolver = LaunchableAppResolver(context.packageManager)
                MissingAppFallback { resolver.resolve(it) != null }.prepare(
                    request,
                    planner.plan(request, UntrustedScreenText.wrap("")),
                ) { constrained -> planner.plan(constrained, UntrustedScreenText.wrap("")) }
            }
        val steps = (result as? MissingAppFallback.Result.Ready)?.steps ?: error("Plan unavailable")
        if (!registry.prepareRuntimeAccess(steps) || !registry.prepareFileAccess(steps)) throw MiniAccessNeeded()
        return ApprovedTaskPlan(registry.prepareRecipients(steps).steps)
    }
}
