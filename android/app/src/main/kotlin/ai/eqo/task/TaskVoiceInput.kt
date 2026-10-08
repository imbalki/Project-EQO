// Origin: EQO-authored Android speech adapter. Never saves audio or logs draft text.
package ai.eqo.task

import ai.eqo.R
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.util.Locale

internal class TaskVoiceInput(private val activity: Activity) {
    private val mic = activity.findViewById<Button>(R.id.task_voice_button)
    private val status = activity.findViewById<TextView>(R.id.task_voice_state)
    private var recognizer: SpeechRecognizer? = null
    private var rationale: AlertDialog? = null
    private var resumed = true
    private var pendingGrant: Boolean? = null
    private val presenter =
        VoiceInputPresenter(
            render = ::render,
            fillDraft = { text ->
                activity.findViewById<EditText>(R.id.task_request).apply {
                    setText(text)
                    setSelection(length())
                }
            },
            requestPermission = ::requestPermission,
            startListening = ::startListening,
        )

    init {
        presenter.availability(SpeechRecognizer.isRecognitionAvailable(activity))
        mic.setOnClickListener {
            presenter.tap(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        }
    }

    fun onPermissionResult(requestCode: Int) {
        if (requestCode == REQUEST_CODE) {
            val granted = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (resumed) presenter.permissionResult(granted) else pendingGrant = granted
        }
    }

    fun resume() {
        resumed = true
        pendingGrant?.let { presenter.permissionResult(it) }
        pendingGrant = null
    }

    fun pause() {
        resumed = false
        presenter.cancel()
        releaseRecognizer()
    }

    fun close() {
        pause()
        pendingGrant = null
        presenter.permissionResult(false)
        rationale?.dismiss()
        rationale = null
    }

    private fun requestPermission() {
        rationale =
            AlertDialog
                .Builder(activity)
                .setTitle(R.string.voice_permission_title)
                .setMessage(R.string.voice_permission_message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
                }.setNegativeButton(android.R.string.cancel) { _, _ -> presenter.permissionResult(false) }
                .setOnCancelListener { presenter.permissionResult(false) }
                .show()
    }

    private fun render(state: VoiceInputState) {
        mic.isEnabled = state !in setOf(VoiceInputState.UNAVAILABLE, VoiceInputState.LISTENING, VoiceInputState.PERMISSION_NEEDED)
        status.setText(
            when (state) {
                VoiceInputState.READY -> R.string.voice_ready
                VoiceInputState.PERMISSION_NEEDED -> R.string.voice_permission_title
                VoiceInputState.LISTENING -> R.string.voice_listening
                VoiceInputState.REVIEW -> R.string.voice_review
                VoiceInputState.NOT_CAUGHT -> R.string.voice_not_caught
                VoiceInputState.NOT_ALLOWED -> R.string.voice_not_allowed
                VoiceInputState.UNAVAILABLE -> R.string.voice_unavailable
                VoiceInputState.ERROR -> R.string.voice_error
            },
        )
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            presenter.error(VoiceInputState.UNAVAILABLE)
            return
        }
        try {
            val speech = SpeechRecognizer.createSpeechRecognizer(activity)
            recognizer = speech
            speech.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit

                    override fun onBeginningOfSpeech() = Unit

                    override fun onRmsChanged(rmsdB: Float) = Unit

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() = Unit

                    override fun onPartialResults(partialResults: Bundle?) = Unit

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit

                    override fun onError(error: Int) {
                        if (recognizer !== speech) return
                        Log.i("EqoVoice", "voice error class=$error")
                        presenter.error(errorState(error))
                        releaseRecognizer()
                    }

                    override fun onResults(results: Bundle?) {
                        if (recognizer !== speech) return
                        presenter.result(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
                        releaseRecognizer()
                    }
                },
            )
            Log.i("EqoVoice", "voice started")
            speech.startListening(recognitionIntent())
        } catch (_: SecurityException) {
            fail(VoiceInputState.NOT_ALLOWED)
        } catch (_: UnsupportedOperationException) {
            fail(VoiceInputState.UNAVAILABLE)
        } catch (_: IllegalStateException) {
            fail(VoiceInputState.ERROR)
        }
    }

    private fun fail(state: VoiceInputState) {
        Log.i("EqoVoice", "voice error class=${state.name}")
        presenter.error(state)
        releaseRecognizer()
    }

    private fun releaseRecognizer() {
        val speech = recognizer ?: return
        // Invalidate callbacks before cancel/destroy; late results must not overwrite typing.
        recognizer = null
        speech.cancel()
        speech.destroy()
        Log.i("EqoVoice", "voice stopped")
    }

    companion object {
        const val REQUEST_CODE = 6902

        fun recognitionIntent(): Intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)

        fun errorState(error: Int): VoiceInputState =
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceInputState.NOT_ALLOWED
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceInputState.NOT_CAUGHT
                else -> VoiceInputState.ERROR
            }
    }
}
