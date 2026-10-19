// Origin: EQO-authored durable AAC MediaStore transaction. No temporary cache copy or automatic deletion.
package ai.eqo.handle

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal class RecordingStore(
    private val context: Context,
) {
    val name = "eqo-recording-${FORMAT.format(LocalDateTime.now())}.m4a"
    var uri: Uri? = null
        private set
    private var descriptor: ParcelFileDescriptor? = null

    fun open(): ParcelFileDescriptor {
        val values =
            ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath(Build.VERSION.SDK_INT))
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        val inserted =
            context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Storage unavailable")
        uri = inserted
        return (context.contentResolver.openFileDescriptor(inserted, "w") ?: throw IOException("Storage unavailable"))
            .also { descriptor = it }
    }

    fun publish(duration: Long) {
        close()
        val values =
            ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
                put(MediaStore.Audio.Media.DURATION, duration)
            }
        check(context.contentResolver.update(checkNotNull(uri), values, null, null) == 1)
    }

    fun close() {
        descriptor?.close()
        descriptor = null
    }

    /** Only incomplete, unplayable startup rows are discarded; saved audio is never auto-deleted. */
    fun discardIncomplete() {
        close()
        uri?.let { context.contentResolver.delete(it, null, null) }
        uri = null
    }

    companion object {
        private val FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        private const val LEGACY_PATH = "Music/EQO Recordings"
        private const val MODERN_PATH = "Recordings/EQO"

        fun relativePath(sdk: Int): String = if (sdk >= Build.VERSION_CODES.S) MODERN_PATH else LEGACY_PATH
    }
}
