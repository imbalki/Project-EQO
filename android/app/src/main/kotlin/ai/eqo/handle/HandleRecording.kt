// Origin: EQO-authored local audio-note controls; uploads only on explicit Transcribe with existing AI voice consent.
package ai.eqo.handle

import ai.eqo.R
import ai.eqo.accessibility.handle.EdgeHandleFeatures
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.llm.InputAudio
import ai.eqo.core.security.AndroidProviderCredentialStore
import ai.eqo.core.security.CredentialStoreResult
import ai.eqo.core.security.ProviderCredentialId
import ai.eqo.task.TaskActivity
import ai.eqo.task.TaskPlanningRuntime
import ai.eqo.task.VoiceEngine
import ai.eqo.task.VoiceModelPicker
import ai.eqo.task.VoiceSettings
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions") // One user-only card adapter; network work delegates to the existing voice provider.
internal object HandleRecording {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var saved: Uri? = null
    private var duration = 0L
    private var transcript: String? = null
    private var pendingNote: String? = null
    private var transcribing = false
    private val handler = Handler(Looper.getMainLooper())
    private var confirmationTimeout: Runnable? = null
    val transcriptionActive: Boolean
        get() = transcribing

    fun tap(context: Context) {
        Log.i("EqoRun", "handle=record start=user_tap")
        if (transcribing) {
            feedback(context, R.string.dictation_transcribing, "busy", "transcription_active")
            return
        }
        if (EdgeHandleFeatures.recordingActive) {
            stop(context)
        } else {
            openConfirmation(context)
        }
    }

    private fun openConfirmation(context: Context) {
        confirmationShown()
        val app = context.applicationContext
        val timeout =
            Runnable { feedback(app, R.string.recording_confirmation_blocked, "refused", "confirmation_not_visible") }
        confirmationTimeout = timeout
        handler.postDelayed(timeout, CONFIRMATION_TIMEOUT_MS)
        runCatching {
            context.startActivity(
                Intent(context, EdgeHandleSettingsActivity::class.java)
                    .putExtra(START_RECORDING, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            confirmationShown()
            feedback(context, R.string.recording_confirmation_blocked, "refused", "confirmation_launch_blocked")
        }
    }

    fun confirmationShown() {
        confirmationTimeout?.let(handler::removeCallbacks)
        confirmationTimeout = null
    }

    fun feedback(
        context: Context,
        resource: Int,
        result: String,
        reason: String,
    ) {
        Log.i("EqoRun", "handle=record result=$result reason=$reason")
        message(context.getString(resource))
        Toast.makeText(context, resource, Toast.LENGTH_LONG).show()
    }

    fun stop(context: Context) {
        runCatching {
            context.startService(Intent(context, RecordingService::class.java).setAction(RecordingService.STOP))
        }.onFailure { feedback(context, R.string.recording_refused, "refused", "stop_service_blocked") }
    }

    fun saved(
        context: Context,
        uri: Uri,
        name: String,
        elapsed: Long,
    ) {
        saved = uri
        duration = elapsed
        transcript = null
        message(context.getString(R.string.recording_saved, name, elapsed / SECOND_MS))
    }

    fun message(text: String) {
        EdgeHandleFeatures.recordingMessage = text
        EdgeHandleFeatures.recordingHasAudio = saved != null
        EdgeHandleFeatures.recordingHasTranscript = transcript != null
        EdgeHandleFeatures.recordingChanged()
    }

    fun dismiss() {
        if (transcribing) return
        saved = null
        transcript = null
        message("")
    }

    fun transcribe(context: Context) {
        val uri = saved ?: return
        if (transcribing) return
        val failure = transcriptionFailure(context)
        if (failure != null) {
            message(context.getString(failure))
        } else {
            transcribing = true
            message(context.getString(R.string.dictation_transcribing))
            scope.launch { requestTranscript(context.applicationContext, uri) }
        }
    }

    internal fun transcriptionFailure(context: Context): Int? {
        val settings = VoiceSettings(context)
        return when {
            !RecordingTranscriptionPolicy.supported(duration) -> R.string.recording_transcription_limit
            !settings.consent -> R.string.dictation_consent
            settings.engine != VoiceEngine.OPENROUTER || settings.audioModel.isNullOrBlank() -> R.string.dictation_model
            !hasKey(context) -> R.string.dictation_key
            else -> null
        }
    }

    private fun hasKey(context: Context): Boolean {
        val key = AndroidProviderCredentialStore(context).read(ProviderCredentialId.ApiKey("openrouter"))
        return !(key as? CredentialStoreResult.Success)?.value.isNullOrBlank()
    }

    internal fun showTranscript(text: String) {
        transcript = text
        message(text)
    }

    @Suppress("TooGenericExceptionCaught") // Never show provider exceptions or log transcript/audio.
    private suspend fun requestTranscript(context: Context, uri: Uri) {
        try {
            val text =
                withContext(Dispatchers.IO) {
                    check(transcriptionFailure(context) == null)
                    val settings = VoiceSettings(context)
                    RecordingTranscriptionPolicy.transcribe(duration, { RecordingWav.decode(context, uri) }) { wav ->
                        check(VoiceSettings(context).consent)
                        TaskPlanningRuntime.voiceProvider(context).transcribe(
                            checkNotNull(settings.audioModel),
                            InputAudio(Base64.encodeToString(wav, Base64.NO_WRAP)),
                            settings.language,
                            VoiceModelPicker.loadModels(context),
                        )
                    }
                }
            showTranscript(text)
        } catch (_: Exception) {
            message(context.getString(R.string.recording_transcription_failed))
        } finally {
            transcribing = false
        }
    }

    fun share(context: Context) {
        val uri = saved ?: return
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("audio/mp4")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .apply { clipData = ClipData.newRawUri("EQO recording", uri) }
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { message(context.getString(R.string.recording_share_failed)) }
    }

    fun copy(context: Context) {
        val text = transcript ?: return
        val clip = ClipData.newPlainText("EQO transcript", text)
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    fun keep(context: Context) {
        if (ai.eqo.task.TaskRunSession.controller != null) {
            message(context.getString(R.string.recording_keep_busy))
            return
        }
        pendingNote = transcript ?: return
        context.startActivity(Intent(context, TaskActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun takeNotePlan(): List<LoopStep>? {
        val text = pendingNote ?: return null
        pendingNote = null
        val action = ExecutedAction("CREATE_NOTE", mapOf("appName" to "Keep", "body" to text), irreversible = true)
        return listOf(LoopStep("recording-note", action))
    }

    const val START_RECORDING = "ai.eqo.handle.START_RECORDING"
    private const val SECOND_MS = 1000L
    private const val CONFIRMATION_TIMEOUT_MS = 3000L
}
