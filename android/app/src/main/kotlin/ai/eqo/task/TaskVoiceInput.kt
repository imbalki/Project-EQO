// Origin: EQO-authored voice adapters. AI audio is ephemeral; no audio or draft text logging.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.llm.InputAudio
import ai.eqo.core.llm.providers.AudioUnsupportedException
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

internal class TaskVoiceInput(
    private val activity: Activity,
    private val recordingFactory: (File, CoroutineScope) -> VoiceRecording = ::VoiceAudioRecorder,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val transcribe: suspend (File, String) -> String = { file, language ->
        val model = StudyModelChoice.read(activity) ?: error("Set up a model first")
        TaskPlanningRuntime.voiceProvider(activity).transcribe(
            model,
            InputAudio(Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)),
            language,
        )
    },
) {
    private val mic = activity.findViewById<Button>(R.id.task_voice_button)
    private val status = activity.findViewById<TextView>(R.id.task_voice_state)
    private var recognizer: SpeechRecognizer? = null
    private var rationale: AlertDialog? = null
    private var resumed = true
    private var pendingGrant: Boolean? = null
    private val settings = VoiceSettings(activity)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var recording: VoiceRecording? = null
    private var transcription: Job? = null
    private var consentPending = false
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
            readDraft = { activity.findViewById<EditText>(R.id.task_request).text.toString() },
            stopListening = ::stopListening,
            cancelProcessing = ::cancelAudio,
        )

    init {
        presenter.availability(engineAvailable())
        mic.setOnClickListener {
            if (settings.engine == VoiceEngine.OPENROUTER && !settings.consent) {
                askAudioConsent()
            } else {
                presenter.tap(micGranted())
            }
        }
    }

    private fun micGranted(): Boolean =
        activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun onPermissionResult(requestCode: Int) {
        if (requestCode == REQUEST_CODE) {
            val granted = micGranted()
            if (resumed) presenter.permissionResult(granted) else pendingGrant = granted
        }
    }

    fun refreshAvailability() {
        resumed = true
        if (pendingGrant == null) presenter.availability(engineAvailable())
        pendingGrant?.let { presenter.permissionResult(it) }
        pendingGrant = null
    }

    fun pause() {
        resumed = false
        presenter.cancel()
        releaseRecognizer()
        cancelAudio()
        if (consentPending) rationale?.dismiss()
        consentPending = false
    }

    fun close() {
        pause()
        pendingGrant = null
        presenter.permissionResult(false)
        rationale?.dismiss()
        rationale = null
        scope.cancel()
    }

    private fun requestPermission() {
        rationale =
            AlertDialog
                .Builder(activity)
                .setTitle(R.string.voice_permission_title)
                .setMessage(
                    if (settings.engine == VoiceEngine.PHONE) {
                        R.string.voice_permission_message
                    } else {
                        R.string.voice_ai_disclosure
                    },
                ).setPositiveButton(android.R.string.ok) { _, _ ->
                    activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
                }.setNegativeButton(android.R.string.cancel) { _, _ -> presenter.permissionResult(false) }
                .setOnCancelListener { presenter.permissionResult(false) }
                .show()
    }

    private fun render(state: VoiceInputState) {
        mic.isEnabled = state !in VoiceInputState.BLOCKS_TAP
        mic.setText(if (state == VoiceInputState.LISTENING) R.string.voice_stop else R.string.voice_button)
        mic.contentDescription = mic.text
        status.setText(
            when (state) {
                VoiceInputState.READY -> R.string.voice_ready
                VoiceInputState.PERMISSION_NEEDED -> R.string.voice_permission_title
                VoiceInputState.LISTENING -> R.string.voice_listening
                VoiceInputState.PROCESSING -> R.string.voice_processing
                VoiceInputState.REVIEW -> R.string.voice_review
                VoiceInputState.NOT_CAUGHT -> R.string.voice_not_caught
                VoiceInputState.NOT_ALLOWED -> R.string.voice_not_allowed
                VoiceInputState.UNAVAILABLE -> R.string.voice_unavailable
                VoiceInputState.ERROR -> R.string.voice_error
            },
        )
    }

    private fun startListening() {
        if (settings.engine == VoiceEngine.OPENROUTER) {
            startAudio()
            return
        }
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

                    override fun onEndOfSpeech() {
                        awaitFinal(speech)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (recognizer !== speech) return
                        presenter.partial(
                            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(),
                        )
                    }

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?,
                    ) = Unit

                    override fun onError(error: Int) {
                        if (recognizer !== speech) return
                        Log.i("EqoVoice", "voice error class=$error")
                        presenter.error(errorState(error))
                        releaseRecognizer()
                    }

                    override fun onResults(results: Bundle?) {
                        if (recognizer !== speech) return
                        val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        presenter.result(heard?.firstOrNull())
                        releaseRecognizer()
                    }
                },
            )
            Log.i("EqoVoice", "voice started")
            speech.startListening(recognitionIntent(VoiceSettings(activity).language))
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

    private fun engineAvailable(): Boolean =
        settings.engine == VoiceEngine.OPENROUTER || SpeechRecognizer.isRecognitionAvailable(activity)

    private fun askAudioConsent() {
        if (consentPending) return
        consentPending = true
        rationale =
            AlertDialog
                .Builder(activity)
                .setTitle(R.string.voice_ai_consent_title)
                .setMessage(R.string.voice_ai_disclosure)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    consentPending = false
                    if (resumed) {
                        settings.consent = true
                        presenter.tap(micGranted())
                    }
                }.setNegativeButton(android.R.string.cancel) { _, _ -> consentPending = false }
                .setOnCancelListener { consentPending = false }
                .show()
    }

    private fun startAudio() {
        try {
            val session = recordingFactory(activity.cacheDir, scope)
            recording = session
            session.start {
                handler.post { if (recording === session && resumed) stopListening() }
            }
        } catch (_: Exception) {
            cancelAudio()
            presenter.error(VoiceInputState.ERROR)
            status.setText(R.string.voice_ai_error)
        }
    }

    private fun stopListening() {
        val session = recording
        if (session == null) {
            val speech = recognizer ?: return
            speech.stopListening()
            awaitFinal(speech)
            return
        }
        if (transcription?.isActive == true) return
        presenter.processing()
        val language = settings.language
        transcription =
            scope.launch {
                try {
                    val text =
                        withContext(ioDispatcher) {
                            val file = session.finish()
                            transcribeTemporaryAudio(file) { transcribe(it, language) }
                        }
                    if (recording === session && resumed) presenter.result(text)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: AudioUnsupportedException) {
                    if (recording === session && resumed) {
                        presenter.error(VoiceInputState.ERROR)
                        status.setText(R.string.voice_ai_unsupported)
                        offerPhone()
                    }
                } catch (_: Exception) {
                    if (recording === session && resumed) {
                        presenter.error(VoiceInputState.ERROR)
                        status.setText(R.string.voice_ai_error)
                        offerPhone()
                    }
                } finally {
                    session.cancel()
                    if (recording === session) recording = null
                }
            }
    }

    private fun offerPhone() {
        rationale =
            AlertDialog
                .Builder(activity)
                .setMessage(status.text)
                .setPositiveButton(R.string.voice_use_phone) { _, _ ->
                    settings.engine = VoiceEngine.PHONE
                    presenter.availability(engineAvailable())
                }.setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    private fun cancelAudio() {
        val session = recording
        recording = null
        transcription?.cancel()
        transcription = null
        session?.cancel()
    }

    private fun awaitFinal(speech: SpeechRecognizer) {
        handler.postDelayed({
            if (recognizer === speech) {
                presenter.result(null)
                releaseRecognizer()
            }
        }, 5000)
    }

    companion object {
        const val REQUEST_CODE = 6902

        fun recognitionIntent(language: String = Locale.getDefault().toLanguageTag()): Intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)

        fun errorState(error: Int): VoiceInputState =
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceInputState.NOT_ALLOWED
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceInputState.NOT_CAUGHT
                else -> VoiceInputState.ERROR
            }
    }
}
