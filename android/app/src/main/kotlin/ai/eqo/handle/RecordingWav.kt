// Origin: EQO-authored bounded local AAC -> WAV decode for the existing WAV-only AI voice endpoint.
package ai.eqo.handle

import ai.eqo.task.VoiceAudioRecorder
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import java.io.ByteArrayOutputStream

internal object RecordingWav {
    private const val TIMEOUT_US = 10_000L
    private const val DECODE_TIMEOUT_MS = 30_000L
    private const val MAX_PCM_BYTES = 16000 * 2 * 90

    // One bounded codec pump; release both native objects on all exits.
    @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
    fun decode(
        context: Context,
        uri: Uri,
    ): ByteArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track =
                (0 until extractor.trackCount).first {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            check(format.getInteger(MediaFormat.KEY_SAMPLE_RATE) == VoiceAudioRecorder.RATE)
            check(format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1)
            val decoder = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            val pcm = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val deadline = SystemClock.elapsedRealtime() + DECODE_TIMEOUT_MS
            while (!outputDone) {
                check(SystemClock.elapsedRealtime() < deadline)
                if (!inputDone) {
                    val input = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (input >= 0) {
                        val count = extractor.readSampleData(checkNotNull(decoder.getInputBuffer(input)), 0)
                        inputDone = count < 0
                        decoder.queueInputBuffer(
                            input,
                            0,
                            maxOf(0, count),
                            if (inputDone) 0 else extractor.sampleTime,
                            if (inputDone) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0,
                        )
                        if (!inputDone) extractor.advance()
                    }
                }
                val output = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                if (output >= 0) {
                    val buffer = checkNotNull(decoder.getOutputBuffer(output))
                    check(pcm.size() + info.size <= MAX_PCM_BYTES)
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val bytes = ByteArray(info.size)
                    buffer.get(bytes)
                    pcm.write(bytes)
                    outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(output, false)
                } else if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    check(decoder.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) == VoiceAudioRecorder.RATE)
                    check(decoder.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1)
                }
            }
            check(pcm.size() > 0)
            return VoiceAudioRecorder.wavHeader(pcm.size()) + pcm.toByteArray()
        } finally {
            codec?.release()
            extractor.release()
        }
    }
}
