// Origin: EQO intent-understanding v1; user-only clarification before planning or permissions.
package ai.eqo.task

import ai.eqo.R
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

    fun cancel() {
        val dialog = active
        cancelAnswer?.invoke()
        dialog?.dismiss()
    }
}
