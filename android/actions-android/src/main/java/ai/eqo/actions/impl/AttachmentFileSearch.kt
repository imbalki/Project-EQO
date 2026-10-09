// Origin: EQO Files v2, bounded read-only attachment resolution in shared storage.
package ai.eqo.actions.impl

import ai.eqo.core.agent.AttachmentSearch
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId

internal sealed interface FileSearchResult {
    data class Matches(val files: List<File>) : FileSearchResult
    data class Refused(val message: String) : FileSearchResult
}

internal class AttachmentFileSearch(
    private val layout: SharedStorageLayout,
    private val accessGranted: () -> Boolean,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clockNanos: () -> Long = System::nanoTime,
) {
    @Suppress("ReturnCount") // Permission, location and incomplete scans fail closed independently.
    fun search(query: AttachmentSearch): FileSearchResult {
        val start = try {
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
        val found = mutableListOf<File>()
        var visited = 0
        while (queue.isNotEmpty()) {
            val (dir, depth) = queue.removeFirst()
            val children = dir.listFiles() ?: return FileSearchResult.Refused(INCOMPLETE)
            for (file in children) {
                if (++visited > MAX_VISITED || clockNanos() >= deadline) {
                    return FileSearchResult.Refused(INCOMPLETE)
                }
                if (file.name.startsWith('.') || Files.isSymbolicLink(file.toPath()) || !layout.isAllowed(file)) continue
                if (file.isDirectory) {
                    if (depth >= MAX_DEPTH) return FileSearchResult.Refused(INCOMPLETE)
                    queue.add(file to depth + 1)
                } else if (file.isFile && matches(file, query)) {
                    found += file
                    if (found.size > MAX_MATCHES) return FileSearchResult.Refused(TOO_MANY)
                }
            }
        }
        return FileSearchResult.Matches(found.sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name }))
    }

    private fun matches(file: File, query: AttachmentSearch): Boolean {
        val name = file.name.lowercase()
        if (query.words.any { !name.contains(it.lowercase()) }) return false
        if (!matchesType(file, query.type)) return false
        val day = Instant.ofEpochMilli(file.lastModified()).atZone(zone).toLocalDate()
        return query.firstDay == null || (!day.isBefore(query.firstDay) && !day.isAfter(query.lastDay))
    }

    private fun matchesType(file: File, type: String?): Boolean {
        val extension = file.extension.lowercase()
        return when (type) {
            null -> true
            "screenshot" -> extension in IMAGES && file.parentFile?.let {
                it == File(layout.root, "Pictures/Screenshots") || it == File(layout.root, "DCIM/Screenshots") ||
                    it == File(layout.root, SharedStorageLayout.SCREENSHOT_FOLDER)
            } == true
            "image" -> extension in IMAGES
            "pdf" -> extension == "pdf"
            "doc" -> extension in setOf("doc", "docx", "odt", "rtf", "txt", "xls", "xlsx", "ppt", "pptx")
            "video" -> extension in setOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v")
            "audio" -> extension in setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "amr")
            else -> false
        }
    }

    companion object {
        private val IMAGES = setOf("png", "jpg", "jpeg", "webp", "heic", "heif", "gif", "bmp")
        private const val MAX_VISITED = 50_000
        private const val MAX_DEPTH = 8
        private const val MAX_MATCHES = 200
        private const val MAX_NANOS = 2_000_000_000L
        private const val INCOMPLETE = "File search could not finish safely. Specify a narrower folder or name."
        private const val TOO_MANY = "Too many files match. Specify a narrower folder, date, type or name."
    }
}
