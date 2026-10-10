// Origin: EQO Files v2, bounded read-only attachment resolution in shared storage.
package ai.eqo.actions.impl

import ai.eqo.core.agent.AttachmentFailure
import ai.eqo.core.agent.AttachmentSearch
import java.io.File
import java.time.Instant
import java.time.ZoneId

internal sealed interface FileSearchResult {
    data class Matches(
        val files: List<File>,
    ) : FileSearchResult

    data class Refused(
        val message: String,
    ) : FileSearchResult
}

internal class AttachmentFileSearch(
    private val layout: SharedStorageLayout,
    private val accessGranted: () -> Boolean,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val catalog: SharedStorageCatalog? = null,
    private val clockNanos: () -> Long = System::nanoTime,
) {
    fun search(query: AttachmentSearch): FileSearchResult {
        if (accessGranted()) {
            try {
                catalog?.refresh(query.rescan)
            } catch (_: RuntimeException) {
                // A broken media index must not prevent the bounded filesystem fallback.
            }
        }
        val result = scan(query)
        val empty = (result as? FileSearchResult.Matches)?.files?.isEmpty() == true
        return if (empty && catalog != null && !query.rescan) {
            search(query.copy(rescan = true))
        } else {
            result
        }
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth")
    // Bounded BFS with independent permission, entry, depth, deadline and result-count guards.
    private fun scan(query: AttachmentSearch): FileSearchResult {
        val starts =
            try {
                layout.searchFolders(query.folder ?: typeFolder(query.type))
            } catch (_: SecurityException) {
                return FileSearchResult.Refused(AttachmentFailure.reason("attachment_not_allowed"))
            }
        if (!accessGranted() && starts.any { !layout.isOwnArea(it) }) {
            return FileSearchResult.Refused(AttachmentFailure.reason("needs_all_files_access"))
        }
        val deadline = clockNanos() + MAX_NANOS
        val queue = ArrayDeque<Pair<File, Int>>()
        starts.filter { it.isDirectory }.forEach { queue.add(it to 0) }
        // MediaStore is primary for MIME, bucket and time metadata. The filesystem fallback also
        // checks uniqueness: an incomplete/stale media index must never make multiple files look like one.
        val found = linkedSetOf<File>()
        catalog
            ?.indexedFiles()
            ?.filter { it.isFile && layout.isAllowed(it) && !layout.hasLinkedAncestor(it) }
            ?.filter { file ->
                val withinRoots = starts.any { file.toPath().startsWith(it.toPath()) }
                val indexedScreenshot = query.folder == null && query.type == "screenshot"
                (withinRoots || indexedScreenshot) && matches(file, query)
            }?.forEach { found += it }
        if (found.size > MAX_MATCHES) return FileSearchResult.Refused(TOO_MANY)
        var visited = 0
        while (queue.isNotEmpty()) {
            val (dir, depth) = queue.removeFirst()
            val children =
                try {
                    dir.listFiles()
                } catch (_: SecurityException) {
                    null
                } ?: return FileSearchResult.Refused(AttachmentFailure.reason("folder_not_readable"))
            for (file in children) {
                if (++visited > MAX_VISITED || clockNanos() >= deadline) {
                    return FileSearchResult.Refused(INCOMPLETE)
                }
                if (!searchable(file)) continue
                if (file.isDirectory) {
                    if (depth >= MAX_DEPTH) return FileSearchResult.Refused(INCOMPLETE)
                    queue.add(file to depth + 1)
                } else if (file.isFile && matches(file, query)) {
                    found += file
                    if (found.size > MAX_MATCHES) return FileSearchResult.Refused(TOO_MANY)
                }
            }
        }
        val sorted = found.sortedWith(compareByDescending<File> { modifiedMillis(it) }.thenBy { it.name })
        return FileSearchResult.Matches(sorted)
    }

    private fun searchable(file: File): Boolean {
        val visible = !file.name.startsWith('.') && !layout.hasLinkedAncestor(file)
        return visible && layout.isAllowed(file)
    }

    private fun matches(
        file: File,
        query: AttachmentSearch,
    ): Boolean {
        val name = file.name.lowercase()
        if (query.words.any { !name.contains(it.lowercase()) } || !matchesType(file, query.type)) return false
        val day = Instant.ofEpochMilli(modifiedMillis(file)).atZone(zone).toLocalDate()
        return query.firstDay == null || (!day.isBefore(query.firstDay) && !day.isAfter(query.lastDay))
    }

    private fun matchesType(
        file: File,
        type: String?,
    ): Boolean {
        val mime = catalog?.metadata(file)?.mime
        return when (type) {
            null -> true
            "screenshot" ->
                layout.aliases.matchesType("image", file, mime) &&
                    (belongs(file, type) || file.name.startsWith("Screenshot_", ignoreCase = true))
            "camera", "photo" -> layout.aliases.matchesType("image", file, mime) && belongs(file, "camera")
            "gallery" -> layout.aliases.matchesType("image", file, mime)
            "download", "downloads" -> belongs(file, "downloads")
            else -> layout.aliases.matchesType(type, file, mime)
        }
    }

    private fun typeFolder(type: String?): String? =
        when (type) {
            "screenshot" -> "screenshot"
            "photo", "camera" -> "camera"
            "download", "downloads" -> "downloads"
            else -> null
        }

    fun modifiedMillis(file: File): Long = screenshotTime(file) ?: catalog?.modifiedMillis(file) ?: file.lastModified()

    private fun screenshotTime(file: File): Long? =
        SCREENSHOT_TIME.find(file.name)?.let { match ->
            try {
                val date = java.time.LocalDateTime.parse(match.groupValues[1], SCREENSHOT_FORMAT)
                val fraction = match.groupValues[2].toLongOrNull()?.times(MILLIS_PER_CENTISECOND) ?: 0L
                date.atZone(zone).toInstant().toEpochMilli() + fraction
            } catch (_: java.time.DateTimeException) {
                null
            }
        }

    private fun belongs(
        file: File,
        group: String,
    ): Boolean =
        catalog?.belongs(file, group) ?: layout.aliases.folders[group].orEmpty().any {
            file.relativeTo(layout.root).invariantSeparatorsPath.startsWith(it.trimEnd('/') + "/", ignoreCase = true)
        }

    companion object {
        private const val MAX_VISITED = 50_000
        private const val MAX_DEPTH = 8
        private const val MILLIS_PER_CENTISECOND = 10L
        private const val MAX_MATCHES = 200
        private const val MAX_NANOS = 2_000_000_000L
        private val INCOMPLETE = AttachmentFailure.reason("search_incomplete")
        private val TOO_MANY = AttachmentFailure.reason("too_many_matches")
        private val SCREENSHOT_FORMAT =
            java.time.format.DateTimeFormatter
                .ofPattern("uuuu-MM-dd-HH-mm-ss")
                .withResolverStyle(java.time.format.ResolverStyle.STRICT)
        private val SCREENSHOT_TIME =
            Regex(
                "^Screenshot_(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2})(?:-(\\d{2}))?(?:_|\\.)",
                RegexOption.IGNORE_CASE,
            )
    }
}
