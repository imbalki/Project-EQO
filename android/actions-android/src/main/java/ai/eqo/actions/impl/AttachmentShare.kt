// Origin: EQO files-attachments, turns an `attachment` parameter into files another app may read.
package ai.eqo.actions.impl

import ai.eqo.core.agent.AttachmentSpec
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One file ready to hand to another app. */
internal data class ShareFile(
    val uri: Uri,
    val mimeType: String,
    val displayName: String,
    val staged: File,
)

internal sealed interface PreparedShare {
    data class Ready(
        val files: List<ShareFile>,
    ) : PreparedShare

    data class Refused(
        val message: String,
    ) : PreparedShare
}

internal class AttachmentShare(
    private val layout: SharedStorageLayout,
    private val lastScreenshot: LastScreenshotStore,
    private val staging: ShareStaging,
    private val accessGranted: () -> Boolean,
    private val uriFor: (File) -> Uri,
) {
    /** [prepare] off the caller's thread: it copies files, which can take a moment. */
    suspend fun prepareOnIo(raw: String?): PreparedShare = withContext(Dispatchers.IO) { prepare(raw) }

    /** Checks every reference, then stages copies. Nothing is staged unless every reference is acceptable. */
    fun prepare(raw: String?): PreparedShare {
        staging.sweep()
        val invalid = AttachmentSpec.errors(raw).firstOrNull()?.let { "Cannot attach: $it." }
        val located = if (invalid == null) AttachmentSpec.parse(raw).map(::locate) else emptyList()
        val problem = invalid ?: located.filterIsInstance<Located.Missing>().firstOrNull()?.message
        return if (problem != null) {
            PreparedShare.Refused(problem)
        } else {
            stageAll(located.filterIsInstance<Located.Found>().map { it.file })
        }
    }

    private fun stageAll(sources: List<File>): PreparedShare {
        if (sources.sumOf { it.length() } > MAX_TOTAL_BYTES) {
            return PreparedShare.Refused("Those files are too big to attach (limit is 25 MB in total).")
        }
        val staged = mutableListOf<File>()
        return try {
            sources.forEach { staged += staging.stage(it) }
            PreparedShare.Ready(staged.map { ShareFile(uriFor(it), mimeFor(it.name), it.name, it) })
        } catch (_: java.io.IOException) {
            staging.discard(staged)
            PreparedShare.Refused("EQO could not copy a file to attach.")
        }
    }

    /** Removes the staged copies of a share that was not handed over or is no longer needed. */
    fun discard(files: List<ShareFile>) = staging.discard(files.map { it.staged })

    private sealed interface Located {
        data class Found(
            val file: File,
        ) : Located

        data class Missing(
            val message: String,
        ) : Located
    }

    private fun locate(reference: String): Located {
        val screenshot = AttachmentSpec.isLastScreenshot(reference)
        val file = if (screenshot) lastScreenshot.get() else inSharedStorage(reference)
        return when {
            file == null && screenshot -> Located.Missing(NO_SCREENSHOT)
            file == null -> Located.Missing("That file is outside shared storage, so EQO will not attach it.")
            // Outside EQO's own folders Android only lets an app read shared storage with All files access.
            !screenshot && !accessGranted() && !layout.isOwnArea(file) -> Located.Missing(NEEDS_ACCESS)
            !file.exists() -> Located.Missing("The file ${file.name} was not found.")
            !file.isFile -> Located.Missing("${file.name} is a folder, not a file.")
            !file.canRead() -> Located.Missing("EQO could not read ${file.name}.")
            else -> Located.Found(file)
        }
    }

    private fun inSharedStorage(reference: String): File? =
        try {
            layout.file(reference)
        } catch (_: SecurityException) {
            null
        }

    companion object {
        const val MAX_TOTAL_BYTES = 25L * 1024 * 1024
        private const val NEEDS_ACCESS = SharedFileBrowser.NEEDS_ACCESS
        private const val NO_SCREENSHOT = "No EQO screenshot has been taken yet, so there is nothing to attach."

        fun mimeFor(name: String): String =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                ?: "application/octet-stream"
    }
}
