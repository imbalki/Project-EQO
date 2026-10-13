// Origin: EQO-authored user-only dictation state machine; no transcript or audio logging.
package ai.eqo.task

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

internal enum class DictationState { IDLE, RECORDING, TRANSCRIBING, INSERTING, INSERTED, FAILED, CANCELLED }

internal enum class DictationFailure { KEY, MODEL, CONSENT, PERMISSION, TARGET, RECORDING, PROVIDER, COPIED }

internal data class DictationSetup(
    val key: Boolean,
    val model: Boolean,
    val consent: Boolean,
    val permission: Boolean,
) {
    fun failure(): DictationFailure? =
        when {
            !key -> DictationFailure.KEY
            !model -> DictationFailure.MODEL
            !consent -> DictationFailure.CONSENT
            !permission -> DictationFailure.PERMISSION
            else -> null
        }
}

/** Owns exactly one recording and one captured target; callbacks from cancelled sessions cannot insert. */
internal class HandleDictationSession(
    private val scope: CoroutineScope,
    private val recordingFactory: () -> VoiceRecording,
    private val transcribe: suspend (File) -> String,
    private val changed: (DictationState, DictationFailure?) -> Unit,
) {
    var state = DictationState.IDLE
        private set
    private var recording: VoiceRecording? = null
    private var work: Job? = null
    private var cap: Job? = null
    private var generation = 0
    private var insert: (suspend (String) -> Boolean)? = null

    fun start(
        setup: DictationSetup,
        inserter: (suspend (String) -> Boolean)?,
    ) {
        cancel()
        val failure = setup.failure() ?: if (inserter == null) DictationFailure.TARGET else null
        if (failure != null) {
            publish(DictationState.FAILED, failure)
        } else {
            begin(inserter!!)
        }
    }

    private fun begin(inserter: suspend (String) -> Boolean) {
        try {
            insert = inserter
            val session = recordingFactory()
            recording = session
            val token = generation
            session.start { scope.launch { if (token == generation) cancel() } }
            publish(DictationState.RECORDING)
            cap =
                scope.launch {
                    delay(VoiceAudioRecorder.MAX_DURATION_MILLIS)
                    if (token == generation) cancel()
                }
        } catch (_: Exception) {
            release()
            publish(DictationState.FAILED, DictationFailure.RECORDING)
        }
    }

    fun stop() {
        if (state != DictationState.RECORDING) return
        val session = recording ?: return
        cap?.cancel()
        session.stopCapture()
        val token = generation
        val inserter = insert!!
        publish(DictationState.TRANSCRIBING)
        work = scope.launch { complete(session, token, inserter) }
    }

    // Sanitize all recorder/provider exceptions at the UI boundary; never forward exception payloads.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun complete(
        session: VoiceRecording,
        token: Int,
        inserter: suspend (String) -> Boolean,
    ) {
        try {
            val text = transcribeTemporaryAudio(session.finish(), transcribe)
            if (token != generation) return
            if (text.isBlank()) {
                publish(DictationState.FAILED, DictationFailure.PROVIDER)
            } else {
                // Paste is an irreversible commit: stop offering Cancel before the first field action.
                publish(DictationState.INSERTING)
                if (inserter(text)) {
                    publish(DictationState.INSERTED)
                } else {
                    publish(DictationState.FAILED, DictationFailure.COPIED)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (token == generation) publish(DictationState.FAILED, DictationFailure.PROVIDER)
        } finally {
            session.cancel()
            if (recording === session) release()
        }
    }

    fun cancel() {
        if (state == DictationState.INSERTING) return
        generation++
        work?.cancel()
        work = null
        cap?.cancel()
        cap = null
        val wasBusy = state == DictationState.RECORDING || state == DictationState.TRANSCRIBING
        release()
        if (wasBusy) publish(DictationState.CANCELLED)
    }

    private fun release() {
        recording?.cancel()
        recording = null
        insert = null
    }

    private fun publish(
        value: DictationState,
        failure: DictationFailure? = null,
    ) {
        state = value
        changed(value, failure)
    }
}
