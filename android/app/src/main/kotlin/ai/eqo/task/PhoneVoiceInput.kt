// Origin: EQO-authored pause-tolerant phone speech session, with no audio storage.
package ai.eqo.task

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

internal class PhoneVoiceInput(
    private val activity: Activity,
    private val presenter: VoiceInputPresenter,
    private val language: () -> String,
) {
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            presenter.error(VoiceInputState.UNAVAILABLE)
            return
        }
        try {
            val speech = SpeechRecognizer.createSpeechRecognizer(activity)
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = awaitFinal(speech)
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onPartialResults(partialResults: Bundle?) {
                    if (recognizer === speech) {
                        presenter.partial(
                            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(),
                        )
                    }
                }
                override fun onError(error: Int) {
                    if (recognizer !== speech) return
                    Log.i("EqoVoice", "voice error class=$error")
                    presenter.error(errorState(error))
                    cancel()
                }
                override fun onResults(results: Bundle?) {
                    if (recognizer !== speech) return
                    presenter.result(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
                    cancel()
                }
            })
            Log.i("EqoVoice", "voice started")
            speech.startListening(intent(language()))
        } catch (_: SecurityException) {
            fail(VoiceInputState.NOT_ALLOWED)
        } catch (_: UnsupportedOperationException) {
            fail(VoiceInputState.UNAVAILABLE)
        } catch (_: IllegalStateException) {
            fail(VoiceInputState.ERROR)
        }
    }

    fun stop() {
        val speech = recognizer ?: return
        speech.stopListening()
        awaitFinal(speech)
    }

    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        val speech = recognizer ?: return
        recognizer = null
        speech.cancel()
        speech.destroy()
        Log.i("EqoVoice", "voice stopped")
    }

    private fun fail(state: VoiceInputState) {
        presenter.error(state)
        cancel()
    }

    private fun awaitFinal(speech: SpeechRecognizer) {
        handler.postDelayed({
            if (recognizer === speech) {
                presenter.result(null)
                cancel()
            }
        }, FINAL_GRACE_MILLIS)
    }

    companion object {
        private const val COMPLETE_SILENCE_MILLIS = 4000L
        private const val POSSIBLE_SILENCE_MILLIS = 3000L
        private const val MINIMUM_SPEECH_MILLIS = 5000L
        private const val FINAL_GRACE_MILLIS = 5000L

        fun intent(language: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, COMPLETE_SILENCE_MILLIS)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, POSSIBLE_SILENCE_MILLIS)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, MINIMUM_SPEECH_MILLIS)

        fun errorState(error: Int): VoiceInputState = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceInputState.NOT_ALLOWED
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceInputState.NOT_CAUGHT
            else -> VoiceInputState.ERROR
        }
    }
}
