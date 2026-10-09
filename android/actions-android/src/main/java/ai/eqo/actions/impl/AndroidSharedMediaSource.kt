// Origin: EQO Files v2, bounded MediaStore metadata and local folder-map preferences.
package ai.eqo.actions.impl

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

internal class AndroidSharedMediaSource(
    private val context: Context,
) : SharedMediaSource {
    override fun entries(): List<SharedMediaEntry> {
        val entries = linkedMapOf<String, SharedMediaEntry>()
        val cancellation = CancellationSignal()
        val handler = Handler(Looper.getMainLooper())
        val cancel = Runnable { cancellation.cancel() }
        val deadline = System.nanoTime() + MAX_NANOS
        handler.postDelayed(cancel, TIMEOUT_MS)
        try {
            val collections =
                listOf(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    MediaStore.Files.getContentUri("external"),
                )
            for (uri in collections) {
                if (System.nanoTime() >= deadline || entries.size >= MAX_ENTRIES) break
                read(uri, cancellation, deadline).take(MAX_ENTRIES - entries.size).forEach { entry ->
                    val key = entry.relativePath.trimEnd('/') + "/" + entry.name
                    val previous = entries[key]
                    entries[key] = entry.copy(bucket = entry.bucket ?: previous?.bucket,
                        mime = entry.mime ?: previous?.mime)
                }
            }
        } finally {
            handler.removeCallbacks(cancel)
        }
        return entries.values.toList()
    }

    private fun read(
        uri: Uri,
        signal: CancellationSignal,
        deadline: Long,
    ): List<SharedMediaEntry> =
        try {
            query(uri, signal, deadline, withBucket = true)
        } catch (_: IllegalArgumentException) {
            // Some OEM Files/Downloads providers do not expose the Images bucket column.
            try {
                query(uri, signal, deadline, withBucket = false)
            } catch (_: RuntimeException) {
                emptyList()
            }
        } catch (_: RuntimeException) {
            // Permission/provider absence: shared filesystem remains the bounded fallback, never Android/data.
            emptyList()
        }

    private fun query(
        uri: Uri,
        signal: CancellationSignal,
        deadline: Long,
        withBucket: Boolean,
    ): List<SharedMediaEntry> {
        val columns =
            listOf(
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.DATE_ADDED,
            ) + if (withBucket) listOf(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME) else emptyList()
        return context.contentResolver
            .query(
                uri,
                columns.toTypedArray(),
                null,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
                signal,
            )?.use { cursor ->
                buildList {
                    while (size < MAX_ENTRIES && System.nanoTime() < deadline && cursor.moveToNext()) {
                        val relative = cursor.getString(0)
                        val name = cursor.getString(1)
                        if (relative == null || name == null) continue
                        add(
                            SharedMediaEntry(
                                relative,
                                name,
                                if (withBucket) cursor.getString(BUCKET_INDEX) else null,
                                cursor.getString(MIME_INDEX),
                                cursor.getLong(MODIFIED_INDEX) * MILLIS_PER_SECOND,
                                cursor.getLong(ADDED_INDEX) * MILLIS_PER_SECOND,
                            ),
                        )
                    }
                }
            }.orEmpty()
    }

    companion object {
        private const val MAX_ENTRIES = 20_000
        private const val TIMEOUT_MS = 2_000L
        private const val MAX_NANOS = 2_000_000_000L
        private const val MILLIS_PER_SECOND = 1_000L
        private const val MIME_INDEX = 2
        private const val MODIFIED_INDEX = 3
        private const val ADDED_INDEX = 4
        private const val BUCKET_INDEX = 5
    }
}

internal class PrefsSharedFolderMapStore(
    context: Context,
) : SharedFolderMapStore {
    private val prefs = context.getSharedPreferences("eqo_shared_folder_map", Context.MODE_PRIVATE)

    override fun read(): Map<String, List<String>>? =
        prefs.getString(KEY, null)?.let { raw ->
            try {
                val json = JSONObject(raw)
                json.keys().asSequence().associateWith { key ->
                    val values = json.getJSONArray(key)
                    (0 until values.length()).map { values.getString(it) }
                }
            } catch (_: org.json.JSONException) {
                null
            }
        }

    override fun write(map: Map<String, List<String>>) {
        val json = JSONObject()
        map.forEach { (key, values) -> json.put(key, JSONArray(values)) }
        prefs.edit { putString(KEY, json.toString()) }
    }

    companion object {
        private const val KEY = "discovered_folders_v1"
    }
}
