// Origin: EQO files-attachments, non-exported provider that serves staged attachment copies.
package ai.eqo.actions.impl

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Serves only the staged copies under [stagingRoot] (see the `eqo_shared_paths` resource). It is a
 * separate class from the app's existing provider so the two keep separate authorities and path lists.
 * It is not exported: a receiving app can read a file only through a per-URI read grant that EQO gives it.
 */
class EqoSharedFileProvider : FileProvider() {
    companion object {
        const val AUTHORITY = "ai.eqo.app.sharedfiles"
        private const val FOLDER = "shares"

        internal fun stagingRoot(context: Context): File {
            val cache = context.externalCacheDir ?: context.cacheDir
            return File(cache, FOLDER).apply { mkdirs() }
        }

        internal fun uriFor(
            context: Context,
            staged: File,
        ): Uri = getUriForFile(context, AUTHORITY, staged)
    }
}
