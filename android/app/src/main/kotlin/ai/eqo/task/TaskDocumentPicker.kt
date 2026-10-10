// Origin: EQO screenshot repair, one-document system picker and temporary read-grant ownership.
package ai.eqo.task

import ai.eqo.actions.impl.AttachmentChoice
import ai.eqo.actions.impl.PickedAttachment
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class TaskDocumentPicker(
    private val activity: Activity,
) {
    private var pending: CancellableContinuation<PickedAttachment?>? = null
    private var returned: Intent? = null
    private var completed = false

    init {
        // Only attachment picker grants are read-only non-tree grants. Workspace tree grants are retained.
        if (!recovered) {
            recovered = true
            recoverReadGrants(activity)
        }
    }

    suspend fun pick(folder: String): PickedAttachment? =
        withContext(Dispatchers.Main.immediate) {
            if (pending != null || activity.isFinishing || activity.isDestroyed) return@withContext null
            suspendCancellableCoroutine { continuation ->
                pending = continuation
                continuation.invokeOnCancellation {
                    activity.runOnUiThread { clear() }
                }
                try {
                    activity.startActivityForResult(intent(folder), REQUEST)
                } catch (_: RuntimeException) {
                    clear()
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("Picker unavailable"))
                    }
                }
            }
        }

    fun onResult(
        request: Int,
        result: Int,
        data: Intent?,
    ) {
        if (request == REQUEST && pending?.isActive == true) {
            returned = data.takeIf { result == Activity.RESULT_OK }
            completed = true
        }
    }

    /** Android delivers the result before onResume; never open confirmation while still backgrounded. */
    fun foreground() {
        val continuation = pending
        if (completed && continuation?.isActive == true) {
            val data = returned
            clear()
            try {
                val picked = data?.let(::document)
                continuation.resume(picked) { _, value, _ -> value?.close() }
            } catch (_: RuntimeException) {
                continuation.resumeWithException(IllegalStateException("Document provider unavailable"))
            }
        }
    }

    fun destroy() {
        pending?.cancel()
        clear()
    }

    private fun clear() {
        pending = null
        returned = null
        completed = false
    }

    private fun document(data: Intent): PickedAttachment? {
        val uri = data.data?.takeIf { it.scheme == "content" } ?: return null
        val resolver = activity.contentResolver
        var granted = false
        return try {
            if (data.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                granted = true
            }
            val choice = metadata(uri)
            PickedAttachment(
                choice,
                resolver.getType(uri) ?: "application/octet-stream",
                { resolver.openInputStream(uri) ?: throw java.io.IOException("Document unavailable") },
                { release(uri, granted) },
            )
        } catch (_: RuntimeException) {
            release(uri, granted)
            error("Document provider unavailable")
        }
    }

    private fun metadata(uri: Uri): AttachmentChoice {
        val columns =
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        return activity.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            check(cursor.moveToFirst()) { "Document metadata unavailable" }
            val modified = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            AttachmentChoice(
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) ?: "attachment",
                if (modified >= 0 && !cursor.isNull(modified)) cursor.getLong(modified) else 0L,
                cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)),
            )
        } ?: error("Document metadata unavailable")
    }

    private fun release(
        uri: Uri,
        granted: Boolean,
    ) {
        if (granted) {
            try {
                activity.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Already revoked; never retry a send or persist a URI to compensate.
            }
        }
        activity.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    companion object {
        const val REQUEST = 8241
        private var recovered = false

        fun recoverReadGrants(activity: Activity) {
            val resolver = activity.contentResolver
            resolver.persistedUriPermissions
                .filter { it.isReadPermission && !it.isWritePermission && !DocumentsContract.isTreeUri(it.uri) }
                .forEach {
                    try {
                        resolver.releasePersistableUriPermission(it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: SecurityException) {
                        // Already revoked. No filename or URI is persisted for recovery.
                    }
                }
        }

        fun intent(folder: String): Intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                val initial =
                    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:$folder")
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial)
            }
    }
}
