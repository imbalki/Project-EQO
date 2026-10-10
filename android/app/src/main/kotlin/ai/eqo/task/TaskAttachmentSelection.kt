// Origin: EQO Files v2, foreground human file choice and exact-name disclosure before send.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.actions.impl.AttachmentChoice
import ai.eqo.actions.impl.AttachmentDecision
import ai.eqo.actions.impl.AttachmentSelection
import ai.eqo.actions.impl.PickedAttachment
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
    val picker = TaskDocumentPicker(activity)
    private var foreground = false
    private var dialog: AlertDialog? = null

    fun foreground() {
        foreground = true
        picker.foreground()
    }

    fun background() {
        foreground = false
        dialog?.cancel()
        dialog = null
    }

    override suspend fun choose(
        search: String,
        files: List<AttachmentChoice>,
    ): Int? =
        ask(
            activity.getString(R.string.attach_choose_title),
            files.take(MAX_CHOICES).map(::label),
            TaskDisplayText.escape(search),
        )

    override suspend fun showResolved(files: List<AttachmentChoice>): Boolean {
        val decision = confirm(files)
        return decision == AttachmentDecision.SEND
    }

    override suspend fun confirm(files: List<AttachmentChoice>): AttachmentDecision =
        withContext(Dispatchers.Main.immediate) {
            if (!available()) return@withContext AttachmentDecision.CANCEL
            val names = files.joinToString(", ") { TaskDisplayText.escape(it.name) }
            val feedback = activity.findViewById<TextView>(R.id.task_control_feedback)
            feedback.text = activity.getString(R.string.attach_ready_feedback, names)
            val details = files.joinToString("\n\n", transform = ::label)
            val result =
                ask(
                    activity.getString(R.string.attach_ready_title),
                    null,
                    activity.getString(R.string.attach_confirm_message, details),
                )
            when (result) {
                0 -> AttachmentDecision.SEND
                DIFFERENT -> AttachmentDecision.DIFFERENT
                else -> AttachmentDecision.CANCEL
            }
        }

    override suspend fun pick(initialFolder: String): PickedAttachment? =
        withContext(Dispatchers.Main.immediate) {
            if (available()) picker.pick(initialFolder) else null
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
                        builder
                            .setMessage(message)
                            .setPositiveButton(R.string.attach_send) { _, _ -> finish(0) }
                            .setNeutralButton(R.string.attach_different) { _, _ -> finish(DIFFERENT) }
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
        return activity.resources.getQuantityString(
            R.plurals.attach_file_metadata,
            file.bytes.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
            TaskDisplayText.escape(file.name),
            if (file.modifiedMillis > 0) day.toString() else activity.getString(R.string.attach_date_unknown),
            file.bytes,
        )
    }

    companion object {
        private const val DIFFERENT = -2
        private const val MAX_CHOICES = 8
        private const val CHOICE_TIMEOUT_MS = 60_000L
    }
}
