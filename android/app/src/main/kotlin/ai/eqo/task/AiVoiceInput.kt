// Origin: EQO-authored opt-in AI voice session; ephemeral audio, no transcript logging.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.llm.providers.AudioIncompleteException
import ai.eqo.core.llm.providers.AudioKeyMissingException
import ai.eqo.core.llm.providers.AudioProviderException
import ai.eqo.core.llm.providers.AudioUnsupportedException
import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.ProgressBar
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
import java.io.IOException

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
    private var stopping = false
    private var startedAt = 0L
    private var level = 0

    fun activate() {
        active = true
    }

    fun tap(afterConsent: () -> Unit) {
        if (!active || consentPending) return
        if (settings.consent) {
            afterConsent()
        } else {
            consentPending = true
            dialog =
                AlertDialog
                    .Builder(activity)
                    .setTitle(R.string.voice_ai_consent_title)
                    .setMessage(R.string.voice_ai_disclosure)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        consentPending = false
                        if (active) {
                            settings.consent = true
                            afterConsent()
                        }
                    }.setNegativeButton(android.R.string.cancel) { _, _ -> consentPending = false }
                    .setOnCancelListener { consentPending = false }
                    .show()
        }
    }

    fun start() {
        try {
            check(settings.consent && active)
            val session = recordingFactory(activity.cacheDir, scope)
            recording = session
            stopping = false
            startedAt = SystemClock.elapsedRealtime()
            level = 0
            session.setProgressListener { _, measured ->
                handler.post { if (recording === session && !stopping) level = measured }
            }
            session.start {
                handler.post {
                    if (recording === session && active && !stopping) stop()
                }
            }
            handler.postDelayed({
                if (recording === session && active && !stopping) stop()
            }, VoiceAudioRecorder.MAX_DURATION_MILLIS)
            recordingStatus(session)
        } catch (_: Exception) {
            cancel()
            if (active) showFailure(R.string.voice_ai_error)
        }
    }

    fun stop() {
        val session = recording ?: return
        if (stopping) {
            cancel()
            return
        }
        stopping = true
        handler.removeCallbacksAndMessages(null)
        session.stopCapture()
        activity.findViewById<ProgressBar>(R.id.task_voice_level).visibility = View.GONE
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
                    if (recording === session && active) presenter.result(text)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: AudioUnsupportedException) {
                    failure(session, R.string.voice_ai_unsupported)
                } catch (_: AudioKeyMissingException) {
                    failure(session, R.string.voice_ai_key_missing)
                } catch (_: AudioIncompleteException) {
                    failure(session, R.string.voice_ai_incomplete)
                } catch (error: AudioProviderException) {
                    failure(session, providerFailure(error.status))
                } catch (_: IOException) {
                    failure(session, R.string.voice_ai_network)
                } catch (_: Exception) {
                    failure(session, R.string.voice_ai_error)
                } finally {
                    session.cancel()
                    if (recording === session) recording = null
                }
            }
    }

    private fun recordingStatus(session: VoiceRecording) {
        if (recording !== session || stopping || !active) return
        val elapsed = (SystemClock.elapsedRealtime() - startedAt) / MILLIS_PER_SECOND
        activity.findViewById<TextView>(R.id.task_voice_state).text =
            activity.getString(R.string.voice_recording, elapsed / SECONDS_PER_MINUTE, elapsed % SECONDS_PER_MINUTE)
        activity.findViewById<ProgressBar>(R.id.task_voice_level).apply {
            visibility = View.VISIBLE
            progress = level
        }
        handler.postDelayed({ recordingStatus(session) }, STATUS_INTERVAL_MILLIS)
    }

    private fun failure(
        session: VoiceRecording,
        message: Int,
    ) {
        if (recording !== session || !active) return
        showFailure(message)
    }

    private fun showFailure(message: Int) {
        presenter.error(VoiceInputState.ERROR)
        activity.findViewById<TextView>(R.id.task_voice_state).setText(message)
        dialog =
            AlertDialog
                .Builder(activity)
                .setMessage(message)
                .setPositiveButton(R.string.voice_use_phone) { _, _ ->
                    settings.engine = VoiceEngine.PHONE
                    presenter.availability(SpeechRecognizer.isRecognitionAvailable(activity))
                }.setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        val session = recording
        recording = null
        transcription?.cancel()
        transcription = null
        session?.cancel()
        activity.findViewById<ProgressBar>(R.id.task_voice_level).visibility = View.GONE
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

    companion object {
        private const val STATUS_INTERVAL_MILLIS = 250L
        private const val MILLIS_PER_SECOND = 1000L
        private const val SECONDS_PER_MINUTE = 60L
        private val KEY_FAILURE_STATUSES = setOf(401, 403)
        private val MODEL_FAILURE_STATUSES = setOf(400, 404, 422)

        internal fun providerFailure(status: Int): Int =
            when (status) {
                in KEY_FAILURE_STATUSES -> R.string.voice_ai_key_missing
                in MODEL_FAILURE_STATUSES -> R.string.voice_ai_unsupported
                else -> R.string.voice_ai_error
            }
    }
}
