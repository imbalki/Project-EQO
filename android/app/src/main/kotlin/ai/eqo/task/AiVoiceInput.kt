// Origin: EQO-authored opt-in AI voice session; ephemeral audio, no transcript logging.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.llm.providers.AudioUnsupportedException
import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.widget.TextView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal class AiVoiceInput(
    private val activity: Activity,
    private val presenter: VoiceInputPresenter,
    private val settings: VoiceSettings,
    private val recordingFactory: (File, CoroutineScope) -> VoiceRecording,
    private val ioDispatcher: CoroutineDispatcher,
    private val transcribe: suspend (File, String) -> String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var recording: VoiceRecording? = null
    private var transcription: Job? = null
    private var dialog: AlertDialog? = null
    private var consentPending = false
    private var active = true

    fun activate() { active = true }

    fun tap(afterConsent: () -> Unit) {
        if (!active || consentPending) return
        if (settings.consent) {
            afterConsent()
        } else {
            consentPending = true
            dialog = AlertDialog.Builder(activity)
                .setTitle(R.string.voice_ai_consent_title)
                .setMessage(R.string.voice_ai_disclosure)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    consentPending = false
                    if (active) {
                        settings.consent = true
                        afterConsent()
                    }
                }.setNegativeButton(android.R.string.cancel) { _, _ -> consentPending = false }
                .setOnCancelListener { consentPending = false }.show()
        }
    }

    fun start() {
        try {
            check(settings.consent && active)
            val session = recordingFactory(activity.cacheDir, scope)
            recording = session
            session.start { handler.post { if (recording === session && active) stop() } }
            handler.postDelayed({
                if (recording === session && active && transcription?.isActive != true) stop()
            }, VoiceAudioRecorder.MAX_DURATION_MILLIS)
        } catch (_: Exception) {
            cancel()
            presenter.error(VoiceInputState.ERROR)
            activity.findViewById<TextView>(R.id.task_voice_state).setText(R.string.voice_ai_error)
        }
    }

    fun stop() {
        val session = recording ?: return
        if (transcription?.isActive == true) {
            cancel()
            return
        }
        presenter.processing()
        val language = settings.language
        transcription = scope.launch {
            try {
                val text = withContext(ioDispatcher) {
                    val file = session.finish()
                    transcribeTemporaryAudio(file) { transcribe(it, language) }
                }
                if (recording === session && active) presenter.result(text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: AudioUnsupportedException) {
                failure(session, R.string.voice_ai_unsupported)
            } catch (_: Exception) {
                failure(session, R.string.voice_ai_error)
            } finally {
                session.cancel()
                if (recording === session) recording = null
            }
        }
    }

    private fun failure(session: VoiceRecording, message: Int) {
        if (recording !== session || !active) return
        presenter.error(VoiceInputState.ERROR)
        activity.findViewById<TextView>(R.id.task_voice_state).setText(message)
        dialog = AlertDialog.Builder(activity).setMessage(message)
            .setPositiveButton(R.string.voice_use_phone) { _, _ ->
                settings.engine = VoiceEngine.PHONE
                presenter.availability(SpeechRecognizer.isRecognitionAvailable(activity))
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        val session = recording
        recording = null
        transcription?.cancel()
        transcription = null
        session?.cancel()
    }

    fun pause() {
        active = false
        cancel()
        dialog?.dismiss()
        dialog = null
        consentPending = false
    }

    fun close() {
        pause()
        scope.cancel()
    }
}
