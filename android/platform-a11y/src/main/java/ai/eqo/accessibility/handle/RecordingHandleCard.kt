// Origin: EQO-authored recording marker/card rendering, separated from the overlay's window lifecycle.
package ai.eqo.accessibility.handle

import ai.eqo.platform.R
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Returns only an expanded status view; elapsed updates never rebuild or interrupt the tiny touch target. */
internal fun addRecordingHandleCard(
    column: LinearLayout,
    expanded: Boolean,
    expand: () -> Unit,
): TextView? {
    val context = column.context
    if (EdgeHandleFeatures.recordingActive && !expanded) {
        column.addView(
            TextView(context).apply {
                setText(R.string.edge_recording_dot)
                setTextColor(Color.RED)
                textSize = RECORDING_TEXT_SP
                gravity = Gravity.CENTER
                contentDescription = context.getString(R.string.edge_recording_stop)
                setOnClickListener { expand() }
                setOnHoverListener { _, event ->
                    if (event.action == MotionEvent.ACTION_HOVER_ENTER) expand()
                    false
                }
            },
            LinearLayout.LayoutParams(
                (RECORDING_TOUCH_DP * context.resources.displayMetrics.density).toInt(),
                (RECORDING_DOT_HEIGHT_DP * context.resources.displayMetrics.density).toInt(),
            ),
        )
        return null
    }
    val status =
        TextView(context).apply {
            text = EdgeHandleFeatures.recordingMessage
            textSize = RECORDING_RESULT_TEXT_SP
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
            setTextIsSelectable(EdgeHandleFeatures.recordingHasTranscript)
            if (!EdgeHandleFeatures.recordingHasTranscript) maxLines = RECORDING_RESULT_LINES
        }
    val height =
        if (EdgeHandleFeatures.recordingHasTranscript) {
            (RECORDING_TRANSCRIPT_HEIGHT_DP * context.resources.displayMetrics.density).toInt()
        } else {
            LinearLayout.LayoutParams.WRAP_CONTENT
        }
    column.addView(
        ScrollView(context).apply { addView(status) },
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height),
    )
    addRecordingActions(column)
    return status
}

private fun addRecordingActions(column: LinearLayout) {
    val context = column.context
    // Full-width rows leave Transcribe and Save as Keep note readable at large font scales too.
    val actions = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    when {
        EdgeHandleFeatures.recordingActive ->
            recordingButton(actions, R.string.edge_recording_stop) { EdgeHandleFeatures.recordingStop(context) }
        EdgeHandleFeatures.recordingHasTranscript -> {
            recordingButton(actions, R.string.edge_dictation_copy) { EdgeHandleFeatures.recordingCopy(context) }
            recordingButton(actions, R.string.edge_recording_keep) { EdgeHandleFeatures.recordingKeep(context) }
        }
        EdgeHandleFeatures.recordingHasAudio -> {
            recordingButton(actions, R.string.edge_recording_transcribe) {
                EdgeHandleFeatures.recordingTranscribe(context)
            }
            recordingButton(actions, R.string.edge_screenshot_share) { EdgeHandleFeatures.recordingShare(context) }
        }
    }
    if (!EdgeHandleFeatures.recordingActive) {
        val dismissLabel =
            if (EdgeHandleFeatures.recordingHasTranscript) {
                R.string.edge_dictation_dismiss
            } else {
                R.string.edge_screenshot_done
            }
        recordingButton(actions, dismissLabel) { EdgeHandleFeatures.recordingDismiss() }
    }
    column.addView(actions)
}

private fun recordingButton(
    parent: LinearLayout,
    label: Int,
    action: () -> Unit,
) {
    val context = parent.context
    parent.addView(
        Button(context).apply {
            setText(label)
            setSingleLine(true)
            textSize = RECORDING_ACTION_TEXT_SP
            minHeight = (RECORDING_TOUCH_DP * context.resources.displayMetrics.density).toInt()
            setOnClickListener { action() }
        },
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
    )
}

private const val RECORDING_TEXT_SP = 13f
private const val RECORDING_RESULT_TEXT_SP = 14f
private const val RECORDING_ACTION_TEXT_SP = 12f
private const val RECORDING_TOUCH_DP = 48
private const val RECORDING_DOT_HEIGHT_DP = 32
private const val RECORDING_RESULT_LINES = 5
private const val RECORDING_TRANSCRIPT_HEIGHT_DP = 96
