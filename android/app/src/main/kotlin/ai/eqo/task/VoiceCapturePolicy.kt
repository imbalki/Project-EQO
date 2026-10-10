// Origin: EQO-authored sample-clock silence/cap policy and level measurement; no audio retained.
package ai.eqo.task

import kotlin.math.abs

/** Consume 16 kHz mono signed little-endian PCM. Silence starts at capture, including initial silence. */
internal class VoiceCapturePolicy {
    private var samples = 0L
    private var silentSamples = 0L
    var level: Int = 0
        private set
    val elapsedMillis: Long get() = samples * MILLIS_PER_SECOND / SAMPLE_RATE
    val shouldStop: Boolean
        get() = elapsedMillis >= MAX_DURATION_MILLIS || silentSamples >= SILENCE_SAMPLES

    fun accept(
        buffer: ByteArray,
        count: Int,
    ) {
        require(count in 0..buffer.size && count % BYTES_PER_SAMPLE == 0)
        var peak = 0
        for (index in 0 until count step BYTES_PER_SAMPLE) {
            val sample = ((buffer[index].toInt() and BYTE_MASK) or (buffer[index + 1].toInt() shl BYTE_BITS)).toShort()
            peak = maxOf(peak, abs(sample.toInt()))
        }
        val frames = count / BYTES_PER_SAMPLE
        samples += frames
        silentSamples = if (peak >= SPEECH_THRESHOLD) 0 else silentSamples + frames
        level = (peak * LEVEL_MAX / PCM_MAX).coerceAtMost(LEVEL_MAX)
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val MAX_DURATION_MILLIS = 90000L
        const val SILENCE_MILLIS = 6000L
        private const val MILLIS_PER_SECOND = 1000L
        private const val SILENCE_SAMPLES = SAMPLE_RATE * SILENCE_MILLIS / MILLIS_PER_SECOND
        private const val SPEECH_THRESHOLD = 650
        private const val BYTES_PER_SAMPLE = 2
        private const val BYTE_MASK = 255
        private const val BYTE_BITS = 8
        private const val PCM_MAX = 32768
        private const val LEVEL_MAX = 100
    }
}
