// Origin: EQO-authored audio-note policy; no Android or network dependencies.
package ai.eqo.handle

internal data class RecordingLimit(
    val millis: Long?,
) {
    companion object {
        private const val MINUTE_MS = 60_000L

        fun parse(text: String): RecordingLimit? {
            val value = text.trim()
            return when {
                value.isEmpty() || value.equals("no limit", ignoreCase = true) || value == "0" -> RecordingLimit(null)
                else ->
                    value
                        .toLongOrNull()
                        ?.takeIf { it > 0 && it <= Long.MAX_VALUE / MINUTE_MS }
                        ?.let { RecordingLimit(it * MINUTE_MS) }
            }
        }
    }
}

internal enum class RecordingPhase { IDLE, RECORDING, SAVED, FAILED }

/** Stop is idempotent; reaching a limit saves rather than discards the audio. */
internal class RecordingState(
    private val limit: RecordingLimit,
) {
    var phase = RecordingPhase.IDLE
        private set
    var elapsed = 0L
        private set
    var limitReached = false
        private set

    fun start(allowed: Boolean): Boolean {
        if (phase != RecordingPhase.IDLE) return false
        phase = if (allowed) RecordingPhase.RECORDING else RecordingPhase.FAILED
        return allowed
    }

    fun tick(elapsed: Long): Boolean {
        this.elapsed = maxOf(this.elapsed, elapsed)
        limitReached = limit.millis?.let { this.elapsed >= it } == true
        return phase == RecordingPhase.RECORDING && limitReached
    }

    fun stop(saved: Boolean) {
        if (phase == RecordingPhase.RECORDING) phase = if (saved) RecordingPhase.SAVED else RecordingPhase.FAILED
    }
}

/** Existing voice endpoint is WAV-only and bounded. No partial/first-90-second transcription. */
internal object RecordingTranscriptionPolicy {
    const val MAX_MILLIS = 90_000L

    fun supported(duration: Long): Boolean = duration in 1..MAX_MILLIS

    suspend fun transcribe(
        duration: Long,
        decode: suspend () -> ByteArray,
        send: suspend (ByteArray) -> String,
    ): String {
        require(supported(duration))
        return send(decode())
    }
}

/** Calls, foreign captures and silencing fail closed. */
internal data class RecordingCaptureSafety(
    val normalAudioMode: Boolean,
    val dictationBusy: Boolean,
    val foreignCapture: Boolean,
    val silenced: Boolean,
    val takeover: Boolean,
) {
    fun allowed(): Boolean = normalAudioMode && !dictationBusy && !foreignCapture && !silenced && !takeover
}
