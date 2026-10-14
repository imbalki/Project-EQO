// Origin: EQO shared pre-approval app resolution and existing missing-app fallback integration.
package ai.eqo.task

import ai.eqo.accessibility.UntrustedScreenText
import ai.eqo.actions.impl.LaunchableAppResolver
import ai.eqo.core.agent.IntentPlanningSession
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.RegistryPlanVocabulary
import ai.eqo.core.agent.TaskIntent
import android.app.Activity
import org.json.JSONArray
import org.json.JSONObject

internal class AppChoiceCancelled : RuntimeException()

internal class FallbackClarificationStopped : RuntimeException()

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
        clarificationBudget: Int = IntentPlanningSession.MAX_ROUNDS,
        ask: suspend (TaskIntent) -> String? = { null },
        replan: suspend (String) -> List<LoopStep>,
    ): MissingAppFallback.Result {
        val prepared = resolve(first)
        val fallback = MissingAppFallback(resolver, prepared.rejected)
        val result =
            prepareFallback(request, prepared, fallback) { constrained ->
                clarifyFallback(constrained, clarificationBudget, ask, replan)
            }
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
            fallback.prepare(request, prepared.steps, replan)
        } catch (stopped: FallbackClarificationStopped) {
            throw stopped
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (cancelled: AppChoiceCancelled) {
            throw cancelled
        } catch (failure: Exception) {
            if (prepared.storeSearch.isEmpty()) throw failure
            MissingAppFallback.Result.Stopped("EQO could not make a Chrome search plan. Nothing ran.")
        }

    // Discard question-bearing proposals before app choices or browser validation; nothing is executed.
    private suspend fun clarifyFallback(
        request: String,
        budget: Int,
        ask: suspend (TaskIntent) -> String?,
        replan: suspend (String) -> List<LoopStep>,
    ): List<LoopStep> {
        var enriched = request
        var rounds = 0
        var proposal = replan(enriched)
        while (true) {
            val question = RegistryPlanVocabulary.clarification(proposal) ?: break
            if (rounds >= budget.coerceIn(0, IntentPlanningSession.MAX_ROUNDS)) {
                throw FallbackClarificationStopped()
            }
            val answer = ask(question)?.takeIf { it.isNotBlank() } ?: throw FallbackClarificationStopped()
            rounds++
            enriched += "\nUser clarification $rounds: $answer"
            proposal = replan(enriched)
        }
        return resolve(proposal).steps
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
