// Origin: EQO Files v2, foreground human file choice and exact-name disclosure before send.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.accessibility.TakeoverDetector
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * File choices belong to the owner and may take as long as they like: no pop-up closes on a short timer.
 * Leaving the screen (screen off, another app) keeps the question open; only Cancel, Stop, a long safety
 * limit or the screen being destroyed end it. While the owner answers, the run is not "mid-action".
 */
@Suppress("TooManyFunctions") // Chooser, confirmation and picker share one wait/cancel lifecycle on purpose.
internal class TaskAttachmentSelection(
    private val activity: Activity,
    private val prepareDialog: (AlertDialog) -> Unit,
    private val takeover: TakeoverDetector = TakeoverDetector.shared,
) : AttachmentSelection {
    val picker = TaskDocumentPicker(activity)
    private var foreground = false
    private var dialog: AlertDialog? = null

    private val waiting = AtomicInteger()

    fun foreground() {
        foreground = true
        picker.foreground()
    }

    /** The screen was left. A question already on screen stays until the owner answers it. */
    fun background() {
        foreground = false
    }

    /** True while a chooser, confirmation or system picker waits for the owner. */
    fun isWaiting(): Boolean = waiting.get() > 0

    /** Stop ends the wait: the open question is dismissed and counts as cancelled. */
    fun cancelWaiting() {
        activity.runOnUiThread { dismissOpenQuestion() }
    }

    /** The screen is gone for good: nothing is left to answer. */
    fun close() {
        dismissOpenQuestion()
    }

    private fun dismissOpenQuestion() {
        dialog?.cancel()
        dialog = null
        picker.cancelPending()
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
            if (available()) {
                ownerChoice { withTimeoutOrNull(OWNER_WAIT_LIMIT_MS) { picker.pick(initialFolder) } }
            } else {
                null
            }
        }

    /** The owner is answering a question EQO asked: their touches are the answer, not a takeover. */
    private suspend fun <T> ownerChoice(block: suspend () -> T): T {
        waiting.incrementAndGet()
        takeover.beginOwnerChoice()
        try {
            return block()
        } finally {
            takeover.endOwnerChoice()
            if (waiting.decrementAndGet() == 0) dialog = null
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
            ownerChoice { askOwner(title, items, message) }
        }

    private suspend fun askOwner(
        title: String,
        items: List<String>?,
        message: String,
    ): Int? =
        withTimeoutOrNull(OWNER_WAIT_LIMIT_MS) {
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
                // A stray tap beside the pop-up must never answer for the owner.
                shown.setCanceledOnTouchOutside(false)
                dialog = shown
                continuation.invokeOnCancellation { activity.runOnUiThread { shown.dismiss() } }
                shown.show()
                prepareDialog(shown)
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

        /** A safety net only: ten minutes of no answer ends the question as cancelled. */
        private const val OWNER_WAIT_LIMIT_MS = 10 * 60_000L
    }
}
