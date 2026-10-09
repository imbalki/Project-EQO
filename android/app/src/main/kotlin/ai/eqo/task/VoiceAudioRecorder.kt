// Origin: EQO-authored bounded 16 kHz mono PCM/WAV recording; no audio or transcript logging.
package ai.eqo.task

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.annotation.SuppressLint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal interface VoiceRecording {
    val file: File

    fun start(onLimit: () -> Unit)

    suspend fun finish(): File

    fun cancel()
}

internal class VoiceAudioRecorder(
    cache: File,
    private val scope: CoroutineScope,
) : VoiceRecording {
    override val file: File = File.createTempFile("eqo-voice-", ".wav", cache)

    @Volatile private var stopping = false
    private var recorder: AudioRecord? = null
    private var writer: Job? = null
    private var failure = false

    // TaskVoiceInput checks the user-granted permission before invoking start; revocation is caught there.
    @SuppressLint("MissingPermission")
    override fun start(onLimit: () -> Unit) {
        val minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0)
        val audio =
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimum, BUFFER_BYTES),
            )
        recorder = audio
        check(audio.state == AudioRecord.STATE_INITIALIZED)
        audio.startRecording()
        writer =
            scope.launch(Dispatchers.IO) {
                try {
                    RandomAccessFile(file, "rw").use { output ->
                        output.write(ByteArray(WAV_HEADER_BYTES))
                        val buffer = ByteArray(BUFFER_BYTES)
                        var written = 0
                        while (!stopping && written < MAX_BYTES) {
                            val count = audio.read(buffer, 0, minOf(buffer.size, MAX_BYTES - written))
                            if (count < 0) {
                                check(stopping)
                                break
                            }
                            if (count > 0) {
                                output.write(buffer, 0, count)
                                written += count
                            }
                        }
                        output.seek(0)
                        output.write(wavHeader(written))
                        if (!stopping) onLimit()
                    }
                } catch (_: Exception) {
                    failure = true
                    if (!stopping) onLimit()
                }
            }
    }

    override suspend fun finish(): File {
        stopAudio()
        writer?.join()
        releaseAudio()
        check(!failure && file.length() > WAV_HEADER_BYTES) { "Audio recording failed" }
        return file
    }

    override fun cancel() {
        stopAudio()
        // The worker releases its own file handle before the final deletion as well.
        writer?.invokeOnCompletion { file.delete() }
        writer?.cancel()
        releaseAudio()
        file.delete()
    }

    @Synchronized
    private fun stopAudio() {
        stopping = true
        runCatching { recorder?.stop() }
    }

    @Synchronized
    private fun releaseAudio() {
        recorder?.release()
        recorder = null
    }

    companion object {
        const val RATE = 16000
        const val MAX_BYTES = RATE * 2 * 60
        const val MAX_DURATION_MILLIS = 60000L
        private const val STALE_AGE_MILLIS = 300000L
        private const val WAV_HEADER_BYTES = 44
        private const val BUFFER_BYTES = 4096

        fun removeStaleFiles(
            cache: File,
            now: Long = System.currentTimeMillis(),
        ) {
            cache
                .listFiles()
                ?.filter {
                    it.name.startsWith("eqo-voice-") && it.extension == "wav" && now - it.lastModified() > STALE_AGE_MILLIS
                }?.forEach { it.delete() }
        }

        // Fixed RIFF/WAV binary format field sizes and PCM constants, not tunable policy.
        @Suppress("MagicNumber")
        fun wavHeader(bytes: Int): ByteArray =
            ByteBuffer
                .allocate(44)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    put("RIFF".toByteArray(Charsets.US_ASCII))
                    putInt(bytes + 36)
                    put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
                    putInt(16)
                    putShort(1)
                    putShort(1)
                    putInt(RATE)
                    putInt(RATE * 2)
                    putShort(2)
                    putShort(16)
                    put("data".toByteArray(Charsets.US_ASCII))
                    putInt(bytes)
                }.array()
    }
}

/** File lifetime encloses the entire provider call, including exceptions and coroutine cancellation. */
internal suspend fun transcribeTemporaryAudio(
    file: File,
    transcribe: suspend (File) -> String,
): String =
    try {
        transcribe(file)
    } finally {
        file.delete()
    }
