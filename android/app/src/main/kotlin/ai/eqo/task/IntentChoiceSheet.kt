// Origin: EQO intent-understanding v1; user-only clarification before planning or permissions.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.agent.InstalledAppResolver
import ai.eqo.core.agent.TaskIntent
import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Only explicit choices/free text answer; cancel, background and destruction cannot approve or start a task. */
internal class IntentChoiceSheet(
    private val activity: Activity,
    private val protect: (AlertDialog) -> Unit,
) {
    private var active: AlertDialog? = null
    private var cancelAnswer: (() -> Unit)? = null

    suspend fun ask(intent: TaskIntent): String? =
        suspendCancellableCoroutine { continuation ->
            fun completeAnswer(value: String?) {
                if (continuation.isActive) continuation.resume(value)
                cancelAnswer = null
                active = null
            }
            val input =
                EditText(activity).apply {
                    setHint(R.string.intent_clarification_hint)
                    setSaveEnabled(false)
                }
            val options = intent.options.toTypedArray()
            val dialog =
                AlertDialog
                    .Builder(activity)
                    .setTitle(intent.clarificationQuestion())
                    .setView(input)
                    .setItems(options) { _, index ->
                        completeAnswer(options[index])
                    }.setPositiveButton(R.string.intent_clarification_answer, null)
                    .setNegativeButton(android.R.string.cancel) { _, _ -> completeAnswer(null) }
                    .setOnCancelListener { completeAnswer(null) }
                    .create()
            active = dialog
            cancelAnswer = { completeAnswer(null) }
            dialog.setOnShowListener {
                protect(dialog)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val answer = input.text.toString().trim()
                    if (answer.isNotEmpty() && continuation.isActive) {
                        completeAnswer(answer)
                        dialog.dismiss()
                    }
                }
            }
            continuation.invokeOnCancellation {
                activity.runOnUiThread {
                    if (active === dialog) {
                        active = null
                        cancelAnswer = null
                    }
                    dialog.dismiss()
                }
            }
            dialog.show()
        }

    /**
     * A role question ("Which browser?"): the answer is followed by "Use <app>?" with Always / This time / Cancel.
     * Always stores the package under the role; This time stores nothing; Cancel at either step stops the task.
     * [apps] are (package, label) pairs. A free-text answer that is not one installed app is passed through unasked.
     */
    suspend fun askRole(
        intent: TaskIntent,
        apps: List<Pair<String, String>>,
        memory: RememberedApps,
    ): String? {
        val answer = ask(intent) ?: return null
        val label = InstalledAppResolver.resolve(answer, apps.map { it.second })
        val app = apps.firstOrNull { it.second == label }
        val role = intent.role
        return if (role == null || app == null) {
            answer
        } else {
            when (askScope(app.second)) {
                AppScope.ALWAYS -> answer.also { memory.rememberRole(role, app.first) }
                AppScope.THIS_TIME -> answer
                AppScope.CANCELLED -> null
            }
        }
    }

    private suspend fun askScope(label: String): AppScope =
        suspendCancellableCoroutine { continuation ->
            fun finish(scope: AppScope) {
                if (continuation.isActive) continuation.resume(scope)
                cancelAnswer = null
                active = null
            }
            val dialog = appScopeDialog(activity, label, ::finish)
            active = dialog
            cancelAnswer = { finish(AppScope.CANCELLED) }
            dialog.setOnShowListener { protect(dialog) }
            continuation.invokeOnCancellation {
                activity.runOnUiThread {
                    if (active === dialog) {
                        active = null
                        cancelAnswer = null
                    }
                    dialog.dismiss()
                }
            }
            dialog.show()
        }

    fun cancel() {
        val dialog = active
        cancelAnswer?.invoke()
        dialog?.dismiss()
    }
}
