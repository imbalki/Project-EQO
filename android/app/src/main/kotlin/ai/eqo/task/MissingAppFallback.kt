// Origin: EQO missing-app pre-approval fallback.
package ai.eqo.task

import ai.eqo.core.agent.LoopStep
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

/** Local preflight is separate from the explicitly requested planner call. Never takes an approved plan. */
internal class MissingAppFallback(
    private val installed: (String) -> Boolean,
) {
    sealed interface Result {
        data class Ready(
            val steps: List<LoopStep>,
            val missing: List<String> = emptyList(),
        ) : Result

        data class Stopped(
            val message: String,
        ) : Result
    }

    fun missing(steps: List<LoopStep>): List<String> =
        steps
            .filter { it.action.name == "OPEN_APP" }
            .map {
                it.action.params["appName"]
                    .orEmpty()
                    .trim()
            }.distinctBy { it.lowercase() }
            .filterNot(installed)

    // Bounded preflight exits are intentionally explicit; none execute a task.
    @Suppress("ReturnCount")
    suspend fun prepare(
        request: String,
        first: List<LoopStep>,
        replan: suspend (String) -> List<LoopStep>,
    ): Result {
        val absent = missing(first)
        if (absent.isEmpty()) return Result.Ready(first)
        if (!installed("Chrome")) return Result.Stopped("Chrome is not installed on this phone. Nothing ran.")
        val next =
            replan(
                request + "\n\nEQO planning constraint: these apps are not installed: " +
                    absent.joinToString(", ") + ". Do this task in Chrome instead. " +
                    "Use OPEN_URL with HTTPS web search URLs and browser=chrome. Do not use the missing apps.",
            )
        if (missing(next).isNotEmpty()) {
            return Result.Stopped("The new plan still needs an app that is not installed. Nothing ran.")
        }
        if (!installed("Chrome")) return Result.Stopped("Chrome is not installed on this phone. Nothing ran.")
        if (next.none { it.action.name == "OPEN_URL" }) {
            return Result.Stopped("EQO could not make a Chrome search plan. Nothing ran.")
        }
        // Pin URLs to Chrome BEFORE preview/approval; never redirect an approved plan.
        return Result.Ready(
            next.map { step ->
                if (step.action.name == "OPEN_URL") {
                    step.copy(action = step.action.copy(params = step.action.params + ("browser" to "chrome")))
                } else {
                    step
                }
            },
            absent,
        )
    }
}

internal class PlanFallbackDialog(
    private val activity: android.app.Activity,
    private val approve: (ai.eqo.core.agent.ApprovedTaskPlan) -> Unit,
) {
    fun show(
        plan: ai.eqo.core.agent.ApprovedTaskPlan,
        preview: String,
        missing: List<String>,
    ): android.app.AlertDialog {
        val builder =
            android.app.AlertDialog
                .Builder(activity)
                .setTitle(ai.eqo.R.string.task_plan_title)
                .setMessage(preview)
                .setPositiveButton(ai.eqo.R.string.task_approval_approve) { _, _ -> approve(plan) }
                .setNegativeButton(ai.eqo.R.string.task_approval_reject) { _, _ ->
                    activity
                        .findViewById<android.widget.TextView>(ai.eqo.R.id.task_state)
                        .setText(ai.eqo.R.string.task_rejected)
                }
        missing.firstOrNull()?.let { app ->
            val label = "Install ${ai.eqo.core.agent.TaskDisplayText.escape(app)} from Play Store"
            builder.setNeutralButton(label) { _, _ ->
                val opened = StoreSearchOpener { activity.startActivity(it) }.search(app)
                activity.findViewById<android.widget.TextView>(ai.eqo.R.id.task_state).text =
                    if (opened) {
                        "Play Store search opened. The task did not run."
                    } else {
                        "Could not open Play Store search. Nothing ran."
                    }
            }
        }
        return builder.show()
    }
}

/** This explicit secondary choice opens only a store search, never the task or an installer. */
internal class StoreSearchOpener(
    private val open: (Intent) -> Unit,
) {
    fun search(app: String): Boolean {
        val query = Uri.encode(app)
        return try {
            open(Intent(Intent.ACTION_VIEW, "market://search?q=$query".toUri()))
            true
        } catch (_: ActivityNotFoundException) {
            try {
                open(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$query&c=apps")))
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }
}
