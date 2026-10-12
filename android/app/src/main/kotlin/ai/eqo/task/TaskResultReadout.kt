// Origin: EQO intent v1 owner addendum; transient successful-result readout, never logs or history.
package ai.eqo.task

import ai.eqo.core.agent.TaskDisplayText
import ai.eqo.study.StepProgress
import ai.eqo.study.StepProgressState
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

/** The same completed result feeds the visible line and optional speech; failures are never announced as answers. */
internal object TaskResultPresentation {
    fun lines(steps: List<StepProgress>): String =
        completed(steps).joinToString("\n") {
            "${it.name.replace('_', ' ')}: ${TaskDisplayText.escape(it.detail)}"
        }

    fun completed(steps: List<StepProgress>): List<StepProgress> =
        steps.filter { it.state == StepProgressState.DONE && it.detail.isNotBlank() }
}

internal class TaskResultReadout(
    private val spokenIds: MutableSet<String>,
    private val speak: (String) -> Unit,
) {
    fun update(
        steps: List<StepProgress>,
        voiceRequest: Boolean,
    ) {
        if (voiceRequest) {
            TaskResultPresentation.completed(steps).forEach {
                if (spokenIds.add(it.stepId)) speak(TaskDisplayText.escape(it.detail))
            }
        }
    }
}

/** Device TTS follows the existing Explain readout's language/engine policy. No new provider or persistence. */
internal class TaskResultSpeaker(
    context: Context,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val pending = mutableListOf<String>()
    private var ready = false
    private var closed = false
    private val speech =
        TextToSpeech(context.applicationContext) { status ->
            handler.post { initialized(status) }
        }

    private fun initialized(status: Int) {
        if (!closed && status == TextToSpeech.SUCCESS) {
            val language = speech.setLanguage(Locale.getDefault())
            ready = language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED
            if (ready) {
                pending.toList().forEach(::speak)
                pending.clear()
            }
        }
    }

    fun speak(text: String) {
        if (!closed && text.isNotBlank()) {
            if (ready) {
                text.chunked(TextToSpeech.getMaxSpeechInputLength()).forEachIndexed { index, chunk ->
                    speech.speak(chunk, TextToSpeech.QUEUE_ADD, null, "task-result-$index")
                }
            } else {
                pending += text
            }
        }
    }

    fun stop() {
        pending.clear()
        speech.stop()
    }

    fun close() {
        closed = true
        stop()
        handler.removeCallbacksAndMessages(null)
        speech.shutdown()
    }
}
