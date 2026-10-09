// Origin: EQO Files v2, foreground human file choice and exact-name disclosure before send.
package ai.eqo.task

import ai.eqo.R

import ai.eqo.actions.impl.AttachmentChoice
import ai.eqo.actions.impl.AttachmentSelection
import ai.eqo.core.agent.TaskDisplayText
import android.app.Activity
import android.app.AlertDialog
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.resume

internal class TaskAttachmentSelection(
    private val activity: Activity,
    private val prepareDialog: (AlertDialog) -> Unit,
) : AttachmentSelection {
    private var foreground = false
    private var dialog: AlertDialog? = null

    fun foreground() {
        foreground = true
    }

    fun background() {
        foreground = false
        dialog?.cancel()
        dialog = null
    }

    override suspend fun choose(
        search: String,
        files: List<AttachmentChoice>,
    ): Int? = ask("Choose the file to attach", files.take(MAX_CHOICES).map(::label), TaskDisplayText.escape(search))

    override suspend fun showResolved(files: List<AttachmentChoice>): Boolean {
        val names = files.joinToString(", ") { TaskDisplayText.escape(it.name) }
        return withContext(Dispatchers.Main.immediate) {
            if (!available()) return@withContext false
            activity.findViewById<TextView>(R.id.task_control_feedback).text = "Ready to attach: $names"
            ask("Ready to attach", null, "$names\n\nContinue with these files? EQO will then try to press Send.") == 0
        }
    }

    private fun available(): Boolean = foreground && !activity.isFinishing && !activity.isDestroyed

    private suspend fun ask(
        title: String,
        items: List<String>?,
        message: String,
    ): Int? =
        withContext(Dispatchers.Main.immediate) {
            if (!available()) return@withContext null
            withTimeoutOrNull(CHOICE_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val builder = AlertDialog.Builder(activity).setTitle(title)

                    fun finish(index: Int?) {
                        if (continuation.isActive) continuation.resume(index)
                    }
                    if (items == null) {
                        builder.setMessage(message).setPositiveButton("Continue") { _, _ -> finish(0) }
                    } else {
                        builder.setItems(items.toTypedArray()) { _, index -> finish(index) }
                    }
                    val shown =
                        builder
                            .setNegativeButton(android.R.string.cancel) { _, _ -> finish(null) }
                            .setOnCancelListener { finish(null) }
                            .create()
                    dialog = shown
                    continuation.invokeOnCancellation { activity.runOnUiThread { shown.dismiss() } }
                    shown.show()
                    prepareDialog(shown)
                }
            }
        }

    private fun label(file: AttachmentChoice): String {
        val day = Instant.ofEpochMilli(file.modifiedMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return "${TaskDisplayText.escape(file.name)}\n$day · ${file.bytes} bytes"
    }

    companion object {
        private const val MAX_CHOICES = 8
        private const val CHOICE_TIMEOUT_MS = 60_000L
    }
}
