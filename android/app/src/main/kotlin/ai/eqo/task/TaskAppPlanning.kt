// Origin: EQO shared pre-approval app resolution and existing missing-app fallback integration.
package ai.eqo.task

import ai.eqo.actions.impl.LaunchableAppResolver
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.UntrustedScreenText
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
        val packages = prepared.steps.mapNotNull { it.action.params["packageName"] }.toSet()
        val selectedLabels =
            resolver
                .installedApps()
                .filter { it.first in packages }
                .map { it.second }
                .toSet()
        val fallback =
            MissingAppFallback { name ->
                name !in prepared.rejected && (name in selectedLabels || resolver.resolve(name) != null)
            }
        return fallback.prepare(request, prepared.steps) { constrained -> resolve(replan(constrained)).steps }
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
