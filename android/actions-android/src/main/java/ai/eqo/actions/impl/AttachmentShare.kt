// Origin: EQO files-attachments, turns an `attachment` parameter into files another app may read.
package ai.eqo.actions.impl

import ai.eqo.core.agent.AttachmentSearch
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

@Suppress("LongParameterList", "TooManyFunctions")
// Separate injected storage/URI boundaries and small helpers keep selection, recency and staging auditable.
internal class AttachmentShare(
    private val layout: SharedStorageLayout,
    private val lastScreenshot: LastScreenshotStore,
    private val staging: ShareStaging,
    private val accessGranted: () -> Boolean,
    private val selection: AttachmentSelection? = null,
    private val catalog: SharedStorageCatalog? = null,
    private val uriFor: (File) -> Uri,
) {
    /** [prepare] off the caller's thread: it copies files, which can take a moment. */
    @Suppress("CyclomaticComplexMethod", "ComplexCondition")
    // Independent cancellation, disclosure and post-wait revalidation gates all fail before staging.
    suspend fun prepareOnIo(raw: String?): PreparedShare =
        withContext(Dispatchers.IO) {
            staging.sweep()
            AttachmentSpec.errors(raw).firstOrNull()?.let {
                return@withContext PreparedShare.Refused("Cannot attach: $it.")
            }
            val sources = mutableListOf<Pair<String, File>>()
            for (reference in AttachmentSpec.parse(raw)) {
                val chosen = select(reference, locate(reference))
                when (chosen) {
                    is Located.Found -> sources += reference to chosen.file
                    is Located.Missing -> return@withContext PreparedShare.Refused(chosen.message)
                    is Located.Candidates -> return@withContext PreparedShare.Refused(ambiguity(chosen.files))
                }
            }
            if (selection != null && !selection.showResolved(sources.map { choice(it.second) })) {
                return@withContext PreparedShare.Refused(
                    "Attachment confirmation unavailable or cancelled. Nothing was sent.",
                )
            }
            if (selection == null &&
                AttachmentSpec.parse(raw).any {
                    AttachmentSearch.isSearch(it) || (AttachmentSpec.isLastScreenshot(it) && recentScreenshot() == null)
                }
            ) {
                return@withContext PreparedShare.Refused("Return to EQO to confirm the matching file before sending.")
            }
            // Revalidate after a human wait, retaining the existing private last_screenshot fallback.
            if (sources.any { (reference, file) ->
                    val check =
                        locate(
                            if (AttachmentSpec.isLastScreenshot(reference) && file == recentScreenshot()) {
                                reference
                            } else {
                                file.path
                            },
                        )
                    check !is Located.Found || check.file != file
                }
            ) {
                return@withContext PreparedShare.Refused("A selected file is no longer available. Nothing was sent.")
            }
            stageAll(sources.map { it.second })
        }

    private suspend fun select(
        reference: String,
        located: Located,
    ): Located {
        if (located !is Located.Candidates || selection == null) return located
        val offered = located.files.take(MAX_CHOICES)
        val index = selection.choose(reference.substringAfter(':'), offered.map(::choice))
        return offered.getOrNull(index ?: -1)?.let { locate(it.path) }
            ?: Located.Missing("File selection unavailable or cancelled. " + ambiguity(located.files))
    }

    private fun choice(file: File) =
        AttachmentChoice(
            file.name,
            catalog?.modifiedMillis(file) ?: file.lastModified(),
            file.length(),
        )

    private fun ambiguity(files: List<File>): String =
        "Several files match: " +
            files.take(MAX_CHOICES).joinToString(", ") {
                ai.eqo.core.agent.TaskDisplayText
                    .escape(it.name)
            } + ". Specify a narrower name, folder or date; nothing was sent."

    /** Checks every reference, then stages copies. Nothing is staged unless every reference is acceptable. */
    fun prepare(raw: String?): PreparedShare {
        staging.sweep()
        val invalid = AttachmentSpec.errors(raw).firstOrNull()?.let { "Cannot attach: $it." }
        val located = if (invalid == null) AttachmentSpec.parse(raw).map(::locate) else emptyList()
        val problem =
            invalid ?: located.filterIsInstance<Located.Missing>().firstOrNull()?.message
                ?: located.filterIsInstance<Located.Candidates>().firstOrNull()?.let { ambiguity(it.files) }
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
            PreparedShare.Ready(
                staged.zip(sources).map { (copy, source) ->
                    val mime = catalog?.metadata(source)?.mime?.takeIf { it.contains('/') } ?: mimeFor(source.name)
                    ShareFile(uriFor(copy), mime, source.name, copy)
                },
            )
        } catch (_: java.io.IOException) {
            staging.discard(staged)
            PreparedShare.Refused("EQO could not copy a file to attach.")
        }
    }

    /** Removes the staged copies of a share that was not handed over or is no longer needed. */
    fun discard(files: List<ShareFile>) = staging.discard(files.map { it.staged })

    private sealed interface Located {
        data class Candidates(
            val files: List<File>,
        ) : Located

        data class Found(
            val file: File,
        ) : Located

        data class Missing(
            val message: String,
        ) : Located
    }

    private fun locate(reference: String): Located {
        if (AttachmentSearch.isSearch(reference)) return find(reference)
        val screenshot = AttachmentSpec.isLastScreenshot(reference)
        val file = if (screenshot) recentScreenshot() else inSharedStorage(reference)
        return when {
            file == null && screenshot -> galleryScreenshot()
            file == null -> Located.Missing("That file is outside shared storage, so EQO will not attach it.")
            layout.hasLinkedAncestor(file) -> Located.Missing("EQO will not attach linked files.")
            // Outside EQO's own folders Android only lets an app read shared storage with All files access.
            !screenshot && !accessGranted() && !layout.isOwnArea(file) -> Located.Missing(NEEDS_ACCESS)
            !file.exists() -> Located.Missing("The file ${file.name} was not found.")
            !file.isFile -> Located.Missing("${file.name} is a folder, not a file.")
            !file.canRead() -> Located.Missing("EQO could not read ${file.name}.")
            else -> Located.Found(file)
        }
    }

    private fun recentScreenshot(): File? =
        lastScreenshot.get()?.takeIf {
            val age = System.currentTimeMillis() - it.lastModified()
            age in 0..RECENT_SCREENSHOT_MILLIS
        }

    private fun galleryScreenshot(): Located =
        when (
            val result =
                AttachmentFileSearch(layout, accessGranted, catalog = catalog)
                    .search(AttachmentSearch.parse("find:latest,type=screenshot"))
        ) {
            is FileSearchResult.Refused -> Located.Missing(result.message)
            is FileSearchResult.Matches -> {
                val newest = result.files.firstOrNull()
                if (newest == null) {
                    Located.Missing(NO_SCREENSHOT)
                } else {
                    val time = choice(newest).modifiedMillis
                    val tied = result.files.filter { choice(it).modifiedMillis == time }
                    if (tied.size > 1) Located.Candidates(tied) else locate(newest.path)
                }
            }
        }

    private fun find(reference: String): Located =
        when (
            val result =
                AttachmentFileSearch(layout, accessGranted, catalog = catalog)
                    .search(AttachmentSearch.parse(reference))
        ) {
            is FileSearchResult.Refused -> Located.Missing(result.message)
            is FileSearchResult.Matches ->
                when (result.files.size) {
                    0 -> Located.Missing("No files found for ${AttachmentSpec.displayName(reference)}.")
                    1 -> locate(result.files.single().path)
                    else -> Located.Candidates(result.files)
                }
        }

    private fun inSharedStorage(reference: String): File? =
        try {
            layout.file(reference)
        } catch (_: SecurityException) {
            null
        }

    companion object {
        private const val MAX_CHOICES = 8
        private const val RECENT_SCREENSHOT_MILLIS = 60L * 60 * 1000
        const val MAX_TOTAL_BYTES = 25L * 1024 * 1024
        private const val NEEDS_ACCESS = SharedFileBrowser.NEEDS_ACCESS
        private const val NO_SCREENSHOT =
            "No EQO screenshot taken recently and no matching gallery screenshot. Nothing was attached."

        fun mimeFor(name: String): String =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                ?: "application/octet-stream"
    }
}
