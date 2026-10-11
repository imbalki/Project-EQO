// Origin: EQO shared pre-approval app resolution and existing missing-app fallback integration.
package ai.eqo.task

import ai.eqo.accessibility.UntrustedScreenText
import ai.eqo.actions.impl.LaunchableAppResolver
import ai.eqo.core.agent.LoopStep
import android.app.Activity
import org.json.JSONArray
import org.json.JSONObject

internal class AppChoiceCancelled : RuntimeException()

internal class TaskAppPlanning(
    activity: Activity,
    private val resolver: LaunchableAppResolver = LaunchableAppResolver(activity.packageManager),
) {
    private val memory = RememberedApps(activity)
    private val dialog = AppChoiceDialog(activity)

    suspend fun resolve(steps: List<LoopStep>): AppPlanResolution.Prepared {
        val result = AppPlanResolution(resolver, memory, dialog::choose).prepare(steps)
        if (result.cancelled) throw AppChoiceCancelled()
        return result
    }

    suspend fun prepare(
        request: String,
        first: List<LoopStep>,
        replan: suspend (String) -> List<LoopStep>,
    ): MissingAppFallback.Result {
        val prepared = resolve(first)
        val fallback = MissingAppFallback(resolver, prepared.rejected)
        val result = prepareFallback(request, prepared, fallback, replan)
        return if (result is MissingAppFallback.Result.Stopped) {
            result.copy(storeSearch = prepared.storeSearch)
        } else {
            result
        }
    }

    // Planner/network failures must not remove the explicit store choice; cancellation still propagates.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun prepareFallback(
        request: String,
        prepared: AppPlanResolution.Prepared,
        fallback: MissingAppFallback,
        replan: suspend (String) -> List<LoopStep>,
    ): MissingAppFallback.Result =
        try {
            fallback.prepare(request, prepared.steps) { constrained -> resolve(replan(constrained)).steps }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (cancelled: AppChoiceCancelled) {
            throw cancelled
        } catch (failure: Exception) {
            if (prepared.storeSearch.isEmpty()) throw failure
            MissingAppFallback.Result.Stopped("EQO could not make a Chrome search plan. Nothing ran.")
        }

    /** Same launchable inventory used by the resolver. Encoded as data, never instructions. */
    fun plannerInventory(): String {
        val apps = JSONArray()
        resolver.installedApps().forEach { (pkg, label) ->
            apps.put(JSONObject().put("package", pkg).put("label", label))
        }
        return UntrustedScreenText.wrap("Installed launcher apps (name resolution hints only): $apps")
    }
}
