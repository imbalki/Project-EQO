// Origin: EQO-authored voice UI and permission adapter. Only fills the draft; never submits.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.llm.InputAudio
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import android.util.Base64
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.Locale

internal class TaskVoiceInput(
    private val activity: Activity,
    recordingFactory: (File, CoroutineScope) -> VoiceRecording = ::VoiceAudioRecorder,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    transcribe: suspend (File, String) -> String = { file, language ->
        val model = VoiceModelPicker.selectedModel(activity, VoiceSettings(activity))
        TaskPlanningRuntime.voiceProvider(activity).transcribe(
            model,
            InputAudio(Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)),
            language,
        )
    },
) {
    private val mic = activity.findViewById<Button>(R.id.task_voice_button)
    private val status = activity.findViewById<TextView>(R.id.task_voice_state)
    private val draft = activity.findViewById<EditText>(R.id.task_request)
    private val settings = VoiceSettings(activity)
    private var rationale: AlertDialog? = null
    private var resumed = true
    private var pendingGrant: Boolean? = null
    private var writingDraft = false
    private val presenter: VoiceInputPresenter =
        VoiceInputPresenter(
            render = ::render,
            fillDraft = { text ->
                writingDraft = true
                try {
                    draft.setText(text)
                    draft.setSelection(draft.length())
                } finally {
                    writingDraft = false
                }
            },
            requestPermission = ::requestPermission,
            startListening = {
                if (settings.engine == VoiceEngine.OPENROUTER) audio.start() else phone.start()
            },
            readDraft = { draft.text.toString() },
            stopListening = {
                if (settings.engine == VoiceEngine.OPENROUTER) audio.stop() else phone.stop()
            },
        )
    private val phone: PhoneVoiceInput by lazy { PhoneVoiceInput(activity, presenter) { settings.language } }
    private val audio: AiVoiceInput by lazy {
        AiVoiceInput(activity, presenter, settings, recordingFactory, ioDispatcher, transcribe)
    }

    init {
        VoiceAudioRecorder.removeStaleFiles(activity.cacheDir)
        draft.doAfterTextChanged {
            if (!writingDraft) {
                presenter.cancel()
                phone.cancel()
                audio.cancel()
            }
        }
        presenter.availability(engineAvailable())
        mic.setOnClickListener {
            if (settings.engine == VoiceEngine.OPENROUTER) {
                audio.tap { presenter.tap(micGranted()) }
            } else {
                presenter.tap(micGranted())
            }
        }
    }

    private fun micGranted(): Boolean {
        val permission = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        return permission == PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionResult(requestCode: Int) {
        if (requestCode == REQUEST_CODE) {
            val granted = micGranted()
            if (resumed) presenter.permissionResult(granted) else pendingGrant = granted
        }
    }

    fun refreshAvailability() {
        resumed = true
        audio.activate()
        if (pendingGrant == null) presenter.availability(engineAvailable())
        pendingGrant?.let { presenter.permissionResult(it) }
        pendingGrant = null
    }

    fun pause() {
        resumed = false
        // Android's permission dialog itself can pause the activity. Edits/close invalidate permission intent.
        presenter.cancel(preservePermission = true)
        phone.cancel()
        audio.pause()
    }

    fun close() {
        pause()
        pendingGrant = null
        presenter.cancel()
        rationale?.dismiss()
        rationale = null
        audio.close()
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

    private fun engineAvailable(): Boolean {
        val ai = settings.engine == VoiceEngine.OPENROUTER
        return ai || SpeechRecognizer.isRecognitionAvailable(activity)
    }

    private fun render(state: VoiceInputState) {
        mic.isEnabled = state !in VoiceInputState.BLOCKS_TAP
        mic.setText(
            when (state) {
                VoiceInputState.LISTENING ->
                    if (settings.engine == VoiceEngine.OPENROUTER) {
                        R.string.voice_stop_recording
                    } else {
                        R.string.voice_stop
                    }
                VoiceInputState.PROCESSING -> android.R.string.cancel
                else -> R.string.voice_button
            },
        )
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

    companion object {
        const val REQUEST_CODE = 6902

        fun recognitionIntent(language: String = Locale.getDefault().toLanguageTag()): Intent {
            val intent = PhoneVoiceInput.intent(language)
            return intent
        }

        fun errorState(error: Int): VoiceInputState = PhoneVoiceInput.errorState(error)
    }
}
