// Origin: EQO pre-approval app resolution. No execution or model calls occur here.
package ai.eqo.task

import ai.eqo.actions.impl.AppNameMatcher
import ai.eqo.actions.impl.LaunchableAppResolver
import ai.eqo.core.agent.LoopStep

internal sealed interface AppChoice {
    data class Selected(
        val app: LaunchableAppResolver.Candidate,
    ) : AppChoice

    data object None : AppChoice

    data object Cancelled : AppChoice
}

internal class AppPlanResolution(
    private val resolver: LaunchableAppResolver,
    private val memory: RememberedApps,
    private val choose: suspend (List<LaunchableAppResolver.Candidate>) -> AppChoice,
) {
    data class Prepared(
        val steps: List<LoopStep>,
        val rejected: Set<String>,
        val cancelled: Boolean = false,
    )

    suspend fun prepare(steps: List<LoopStep>): Prepared {
        val rejected = mutableSetOf<String>()
        val resolved = mutableMapOf<String, LaunchableAppResolver.Candidate>()
        val output = mutableListOf<LoopStep>()
        for (step in steps) {
            val name = appName(step)
            if (!needsResolution(step, name)) {
                output += step
                continue
            }
            val query = requireNotNull(name)
            val candidates = resolver.candidates(query)
            val direct =
                resolved[query] ?: rememberedApp(query)
                    ?: resolver.clearWinner(candidates)
            val choice =
                when {
                    query in rejected -> AppChoice.None
                    direct != null -> AppChoice.Selected(direct)
                    candidates.isEmpty() -> AppChoice.None
                    else -> choose(candidates.take(MAX_CHOICES))
                }
            when (choice) {
                AppChoice.Cancelled -> return Prepared(emptyList(), rejected, cancelled = true)
                AppChoice.None -> {
                    rejected += query
                    output += step
                }
                is AppChoice.Selected -> {
                    if (direct == null) memory.remember(query, choice.app.packageName)
                    resolved[query] = choice.app
                    output += pin(step, choice.app)
                }
            }
        }
        return Prepared(output, rejected)
    }

    private fun needsResolution(
        step: LoopStep,
        name: String?,
    ): Boolean = name != null && !name.equals("settings", true) && step.action.params["packageName"] == null

    private fun rememberedApp(name: String): LaunchableAppResolver.Candidate? {
        val pkg = memory.packageFor(name)
        val app = resolver.installedApps().find { it.first == pkg }
        return app?.let { LaunchableAppResolver.Candidate(it.first, it.second, AppNameMatcher.ALIAS_SCORE) }
    }

    private fun pin(
        step: LoopStep,
        app: LaunchableAppResolver.Candidate,
    ): LoopStep {
        val parameter = if (step.action.name == "open_app") "app" else "appName"
        return step.copy(
            action =
                step.action.copy(
                    params =
                        step.action.params +
                            mapOf(parameter to app.label, "packageName" to app.packageName),
                ),
        )
    }

    companion object {
        const val MAX_CHOICES = 5

        fun appName(step: LoopStep): String? =
            when (step.action.name) {
                "OPEN_APP" -> step.action.params["appName"]
                "open_app" -> step.action.params["app"]
                else -> null
            }
    }
}
