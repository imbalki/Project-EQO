// Origin: EQO missing-app pre-approval fallback.
package ai.eqo.task

import ai.eqo.core.agent.LoopStep
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

/** Local preflight is separate from the explicitly requested planner call. Never takes an approved plan. */
internal class MissingAppFallback(
    private val installedPackage: ((String) -> Boolean)? = null,
    private val installed: (String) -> Boolean,
) {
    constructor(
        resolver: ai.eqo.actions.impl.LaunchableAppResolver,
        rejected: Set<String> = emptySet(),
    ) : this(
        { pkg -> resolver.resolvePackage(pkg) != null },
        { name ->
            name !in rejected &&
                if (name.equals("Chrome", true)) {
                    resolver.resolvePackage("com.android.chrome") != null
                } else {
                    resolver.resolve(name) != null
                }
        },
    )

    private companion object {
        val WEB_STEPS = setOf("OPEN_URL", "WEB_SEARCH")
    }

    sealed interface Result {
        data class Ready(
            val steps: List<LoopStep>,
            val missing: List<String> = emptyList(),
        ) : Result

        data class Stopped(
            val message: String,
            val storeSearch: List<String> = emptyList(),
        ) : Result
    }

    fun missing(steps: List<LoopStep>): List<String> =
        steps
            .mapNotNull { step ->
                val name = AppPlanResolution.appName(step)?.trim()
                val pkg = step.action.params["packageName"]
                val present =
                    if (pkg != null && installedPackage != null) {
                        installedPackage.invoke(pkg)
                    } else {
                        name != null && installed(name)
                    }
                name?.takeUnless { present }
            }.distinctBy { it.lowercase() }

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
                    "Use OPEN_URL or WEB_SEARCH with browser=chrome. Do not use the missing apps.",
            )
        if (missing(next).isNotEmpty()) {
            return Result.Stopped("The new plan still needs an app that is not installed. Nothing ran.")
        }
        if (!installed("Chrome")) return Result.Stopped("Chrome is not installed on this phone. Nothing ran.")
        if (next.none { it.action.name in WEB_STEPS }) {
            return Result.Stopped("EQO could not make a Chrome search plan. Nothing ran.")
        }
        // Pin URLs to Chrome BEFORE preview/approval; never redirect an approved plan.
        return Result.Ready(
            next.map { step ->
                if (step.action.name in WEB_STEPS) {
                    // Any earlier pin of another browser is dropped: the preview names Chrome, so Chrome runs.
                    val params = step.action.params - "packageName" + ("browser" to "chrome")
                    step.copy(action = step.action.copy(params = params))
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
    /** A rejected chooser still offers store search when no usable browser plan exists. */
    fun showRecovery(result: MissingAppFallback.Result.Stopped): android.app.AlertDialog {
        val app = result.storeSearch.first()
        val dialog =
            android.app.AlertDialog
                .Builder(activity)
                .setTitle(ai.eqo.R.string.app_chooser_title)
                .setMessage(result.message)
                .setPositiveButton(
                    activity.getString(
                        ai.eqo.R.string.app_chooser_install,
                        ai.eqo.core.agent.TaskDisplayText
                            .escape(app),
                    ),
                ) { _, _ -> openStore(app) }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    activity
                        .findViewById<android.widget.TextView>(ai.eqo.R.id.task_state)
                        .setText(ai.eqo.R.string.app_chooser_cancelled)
                }.setOnCancelListener {
                    activity
                        .findViewById<android.widget.TextView>(ai.eqo.R.id.task_state)
                        .setText(ai.eqo.R.string.app_chooser_cancelled)
                }.show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).filterTouchesWhenObscured = true
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).filterTouchesWhenObscured = true
        protectConfirmationTouches(dialog.findViewById(android.R.id.content))
        return dialog
    }

    private fun openStore(app: String) {
        val opened = StoreSearchOpener { activity.startActivity(it) }.search(app)
        activity.findViewById<android.widget.TextView>(ai.eqo.R.id.task_state).setText(
            if (opened) ai.eqo.R.string.app_chooser_store_opened else ai.eqo.R.string.app_chooser_store_failed,
        )
    }

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
            val label =
                activity.getString(
                    ai.eqo.R.string.app_chooser_install,
                    ai.eqo.core.agent.TaskDisplayText
                        .escape(app),
                )
            builder.setNeutralButton(label) { _, _ ->
                openStore(app)
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
                val web = "https://play.google.com/store/search?q=$query&c=apps".toUri()
                open(Intent(Intent.ACTION_VIEW, web))
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }
}
