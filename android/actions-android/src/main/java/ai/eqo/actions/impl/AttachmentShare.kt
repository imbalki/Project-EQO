// Origin: EQO files-attachments, turns an `attachment` parameter into files another app may read.
package ai.eqo.actions.impl

import ai.eqo.core.agent.AttachmentFailure
import ai.eqo.core.agent.AttachmentSearch
import ai.eqo.core.agent.AttachmentSpec
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

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
    private data class Source(
        val file: File,
        val display: AttachmentChoice,
        val picked: Boolean = false,
        val mime: String? = null,
    )

    /** Picker copies are temporary; every exit, including Stop, removes them and releases the grant. */
    suspend fun prepareOnIo(raw: String?): PreparedShare {
        val outgoing = mutableListOf<File>()
        var handedOver = false
        try {
            val result = withContext(Dispatchers.IO) { prepareOwned(raw, outgoing) }
            handedOver = true
            return result
        } finally {
            if (!handedOver) staging.discard(outgoing)
        }
    }

    private suspend fun prepareOwned(
        raw: String?,
        outgoing: MutableList<File>,
    ): PreparedShare {
        val picked = mutableListOf<File>()
        return try {
            prepareConfirmed(raw, picked, outgoing)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: AttachmentTooLargeException) {
            refused("attachment_too_large")
        } catch (_: SecurityException) {
            refused("provider_failed")
        } catch (_: java.io.IOException) {
            refused("staging_failed")
        } catch (_: RuntimeException) {
            refused("provider_failed")
        } finally {
            staging.discard(picked)
        }
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    // Independent picker, confirmation, cancellation and revalidation guards exit before any route launch.
    private suspend fun prepareConfirmed(
        raw: String?,
        picked: MutableList<File>,
        outgoing: MutableList<File>,
    ): PreparedShare {
        staging.sweep()
        if (AttachmentSpec.errors(raw).isNotEmpty()) return refused("attachment_invalid")
        val sources = mutableListOf<Source>()
        for (reference in AttachmentSpec.parse(raw)) {
            val located = select(reference, locate(reference))
            val source =
                if (located is Located.Found) {
                    Source(located.file, choice(located.file))
                } else if (located is Located.Missing && shouldPick(located.message)) {
                    pickSource(reference, picked)
                } else {
                    return PreparedShare.Refused(
                        (located as? Located.Missing)?.message
                            ?: AttachmentFailure.reason("attachment_selection_required"),
                    )
                }
            sources += source ?: return refused("attachment_cancelled")
        }
        if (sources.isNotEmpty() && selection == null) return refused("attachment_selection_required")
        var decision =
            if (sources.isEmpty()) {
                AttachmentDecision.SEND
            } else {
                selection?.confirm(sources.map { it.display }) ?: AttachmentDecision.CANCEL
            }
        while (decision == AttachmentDecision.DIFFERENT) {
            val replacement =
                pickSource(AttachmentSpec.parse(raw).firstOrNull().orEmpty(), picked)
                    ?: return refused("attachment_cancelled")
            sources.clear()
            sources += replacement
            decision = selection?.confirm(sources.map { it.display }) ?: AttachmentDecision.CANCEL
        }
        if (decision != AttachmentDecision.SEND) return refused("attachment_cancelled")
        if (sources.any { !available(it) }) return refused("attachment_unavailable")
        val context = coroutineContext
        val result = stageAll(sources.map { it.file }, outgoing) { context.ensureActive() }
        return if (result is PreparedShare.Ready) {
            PreparedShare.Ready(
                result.files.zip(sources).map { (file, source) ->
                    file.copy(mimeType = source.mime ?: file.mimeType, displayName = source.display.name)
                },
            )
        } else {
            result
        }
    }

    private fun available(source: Source): Boolean =
        if (source.picked) {
            source.file.isFile && source.file.canRead()
        } else {
            val reference = if (source.file == recentScreenshot()) "last_screenshot" else source.file.path
            val located = locate(reference)
            located is Located.Found && located.file == source.file
        }

    private suspend fun pickSource(
        reference: String,
        picked: MutableList<File>,
    ): Source? {
        val document = selection?.pick(initialFolder(reference)) ?: return null
        return document.use {
            if (it.choice.bytes > MAX_TOTAL_BYTES) throw AttachmentTooLargeException()
            val context = coroutineContext
            val file = staging.stage(it.choice.name, it.open) { context.ensureActive() }
            picked += file
            Source(file, it.choice.copy(bytes = file.length()), true, it.mime)
        }
    }

    private fun initialFolder(reference: String): String =
        if (reference.contains("screenshot", ignoreCase = true)) "DCIM/Screenshots" else "Download"

    private fun shouldPick(reason: String): Boolean =
        AttachmentFailure.kind(reason) in
            setOf(
                "no_matching_file",
                "folder_not_readable",
                "search_incomplete",
                "too_many_matches",
                "needs_all_files_access",
                "attachment_unavailable",
            )

    private fun refused(kind: String) = PreparedShare.Refused(AttachmentFailure.reason(kind))

    private suspend fun select(
        reference: String,
        located: Located,
    ): Located {
        if (located !is Located.Candidates || selection == null) return located
        val offered = located.files.take(MAX_CHOICES)
        val index = selection.choose(reference.substringAfter(':'), offered.map(::choice))
        return offered.getOrNull(index ?: -1)?.let { locate(it.path) }
            ?: Located.Missing(AttachmentFailure.reason("attachment_cancelled"))
    }

    private fun choice(file: File) =
        AttachmentChoice(
            file.name,
            AttachmentFileSearch(layout, accessGranted, catalog = catalog).modifiedMillis(file),
            file.length(),
        )

    private fun ambiguity(files: List<File>): String =
        AttachmentFailure.reason(if (files.isEmpty()) "no_matching_file" else "attachment_selection_required")

    /** Checks every reference, then stages copies. Nothing is staged unless every reference is acceptable. */
    fun prepare(raw: String?): PreparedShare {
        staging.sweep()
        val invalid = AttachmentSpec.errors(raw).firstOrNull()?.let { AttachmentFailure.reason("attachment_invalid") }
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

    private fun stageAll(
        sources: List<File>,
        staged: MutableList<File> = mutableListOf(),
        checkActive: () -> Unit = {},
    ): PreparedShare {
        if (sources.sumOf { it.length() } > MAX_TOTAL_BYTES) {
            return refused("attachment_too_large")
        }
        return try {
            sources.forEach { source ->
                staged +=
                    staging.stage(source.name, {
                        java.nio.file.Files
                            .newInputStream(source.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    }, checkActive)
            }
            checkActive()
            PreparedShare.Ready(
                staged.zip(sources).map { (copy, source) ->
                    val mime = catalog?.metadata(source)?.mime?.takeIf { it.contains('/') } ?: mimeFor(source.name)
                    ShareFile(uriFor(copy), mime, source.name, copy)
                },
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            staging.discard(staged)
            throw cancelled
        } catch (_: AttachmentTooLargeException) {
            staging.discard(staged)
            refused("attachment_too_large")
        } catch (_: java.io.IOException) {
            staging.discard(staged)
            refused("staging_failed")
        } catch (_: RuntimeException) {
            staging.discard(staged)
            refused("provider_failed")
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

    private fun locate(reference: String): Located =
        try {
            locateSafe(reference)
        } catch (_: SecurityException) {
            Located.Missing(AttachmentFailure.reason("folder_not_readable"))
        } catch (_: RuntimeException) {
            Located.Missing(AttachmentFailure.reason("search_incomplete"))
        }

    private fun locateSafe(reference: String): Located {
        if (AttachmentSearch.isSearch(reference)) return find(reference)
        val screenshot = AttachmentSpec.isLastScreenshot(reference)
        val file = if (screenshot) recentScreenshot() else inSharedStorage(reference)
        return when {
            file == null && screenshot -> galleryScreenshot()
            file == null -> Located.Missing(AttachmentFailure.reason("attachment_not_allowed"))
            layout.hasLinkedAncestor(file) -> Located.Missing(AttachmentFailure.reason("attachment_not_allowed"))
            // Outside EQO's own folders Android only lets an app read shared storage with All files access.
            !screenshot && !accessGranted() && !layout.isOwnArea(file) -> Located.Missing(NEEDS_ACCESS)
            !file.exists() -> Located.Missing(AttachmentFailure.reason("attachment_unavailable"))
            !file.isFile -> Located.Missing(AttachmentFailure.reason("attachment_unavailable"))
            !file.canRead() -> Located.Missing(AttachmentFailure.reason("attachment_unavailable"))
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
                    0 -> Located.Missing(AttachmentFailure.reason("no_matching_file"))
                    1 -> locate(result.files.single().path)
                    else -> {
                        val newest = result.files.first()
                        val matches =
                            if (AttachmentSearch.parse(reference).latest) {
                                result.files.filter { choice(it).modifiedMillis == choice(newest).modifiedMillis }
                            } else {
                                result.files
                            }
                        if (matches.size == 1) locate(newest.path) else Located.Candidates(matches)
                    }
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
        private val NEEDS_ACCESS = AttachmentFailure.reason("needs_all_files_access")
        private val NO_SCREENSHOT = AttachmentFailure.reason("no_matching_file")

        fun mimeFor(name: String): String =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                ?: "application/octet-stream"
    }
}
