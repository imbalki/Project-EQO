// Origin: EQO intent v1 owner addendum; transient successful-result readout, never logs or history.
package ai.eqo.task

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * One plain sentence per finished run feeds both the visible Result line and the optional speech.
 * Internal steps and executor text never reach it; a run the owner stopped has no sentence.
 */
internal object TaskResultPresentation {
    fun sentence(
        sentences: List<RunSentence>,
        resolve: (RunSentence) -> String,
    ): String? = sentences.joinToString(" ") { resolve(it) }.trim().ifBlank { null }
}

/** The run summary is spoken once per run (by id), and only when the request was started by voice. */
internal class TaskResultReadout(
    private val spokenIds: MutableSet<String>,
    private val speak: (String) -> Unit,
) {
    fun update(
        summary: String?,
        voiceRequest: Boolean,
    ) {
        if (voiceRequest && !summary.isNullOrBlank() && spokenIds.add(SUMMARY_ID)) speak(summary)
    }

    private companion object {
        const val SUMMARY_ID = "run-summary"
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
