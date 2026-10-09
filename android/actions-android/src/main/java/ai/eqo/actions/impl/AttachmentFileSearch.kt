// Origin: EQO Files v2, bounded read-only attachment resolution in shared storage.
package ai.eqo.actions.impl

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
        if (accessGranted()) catalog?.refresh(query.rescan)
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
        val start =
            try {
                layout.folder(query.folder)
            } catch (_: SecurityException) {
                return FileSearchResult.Refused(SharedFileBrowser.OUTSIDE)
            }
        if (!accessGranted() && !layout.isOwnArea(start)) {
            return FileSearchResult.Refused(SharedFileBrowser.NEEDS_ACCESS)
        }
        if (!start.isDirectory) return FileSearchResult.Matches(emptyList())
        val deadline = clockNanos() + MAX_NANOS
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(start to 0)
        // MediaStore is primary for MIME, bucket and time metadata. The filesystem fallback also
        // checks uniqueness: an incomplete/stale media index must never make multiple files look like one.
        val found = linkedSetOf<File>()
        catalog
            ?.indexedFiles()
            ?.filter { it.isFile && layout.isAllowed(it) && !layout.hasLinkedAncestor(it) }
            ?.filter { it.toPath().startsWith(start.toPath()) && matches(it, query) }
            ?.forEach { found += it }
        if (found.size > MAX_MATCHES) return FileSearchResult.Refused(TOO_MANY)
        var visited = 0
        while (queue.isNotEmpty()) {
            val (dir, depth) = queue.removeFirst()
            val children = dir.listFiles() ?: return FileSearchResult.Refused(INCOMPLETE)
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
        return FileSearchResult.Matches(found.sortedWith(compareByDescending<File> { modified(it) }.thenBy { it.name }))
    }

    private fun searchable(file: File): Boolean = !file.name.startsWith('.') && !layout.hasLinkedAncestor(file) && layout.isAllowed(file)

    private fun matches(
        file: File,
        query: AttachmentSearch,
    ): Boolean {
        val name = file.name.lowercase()
        if (query.words.any { !name.contains(it.lowercase()) } || !matchesType(file, query.type)) return false
        val day = Instant.ofEpochMilli(modified(file)).atZone(zone).toLocalDate()
        return query.firstDay == null || (!day.isBefore(query.firstDay) && !day.isAfter(query.lastDay))
    }

    private fun matchesType(
        file: File,
        type: String?,
    ): Boolean {
        val mime = catalog?.metadata(file)?.mime
        return when (type) {
            null -> true
            "screenshot", "camera" -> layout.aliases.matchesType("image", file, mime) && belongs(file, type)
            "gallery" -> layout.aliases.matchesType("image", file, mime)
            "download", "downloads" -> belongs(file, "downloads")
            else -> layout.aliases.matchesType(type, file, mime)
        }
    }

    private fun modified(file: File): Long = catalog?.modifiedMillis(file) ?: file.lastModified()

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
        private const val MAX_MATCHES = 200
        private const val MAX_NANOS = 2_000_000_000L
        private const val INCOMPLETE = "File search could not finish safely. Specify a narrower folder or name."
        private const val TOO_MANY = "Too many files match. Specify a narrower folder, date, type or name."
    }
}
