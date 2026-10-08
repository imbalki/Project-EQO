// Origin: EQO files-attachments, read-only FIND_FILES and LIST_FILES over shared storage.
package ai.eqo.actions.impl

import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset

/** Outcome of a read-only look at shared storage. Names and sizes only; contents are never read. */
internal sealed interface BrowseResult {
    data class Listing(
        val text: String,
    ) : BrowseResult

    data class Refused(
        val message: String,
    ) : BrowseResult
}

internal class SharedFileBrowser(
    private val layout: SharedStorageLayout,
    private val accessGranted: () -> Boolean,
) {
    @Suppress("ReturnCount") // guard clauses: each refusal returns its own message
    fun list(folder: String?): BrowseResult {
        val dir =
            try {
                layout.folder(folder)
            } catch (_: SecurityException) {
                return BrowseResult.Refused(OUTSIDE)
            }
        gate(dir)?.let { return it }
        if (!dir.isDirectory) return BrowseResult.Refused("That folder does not exist.")
        val entries = dir.listFiles()?.filter { layout.isAllowed(it) && !it.name.startsWith(".") }
        if (entries == null) return BrowseResult.Refused("EQO could not read that folder.")
        val sorted = entries.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        val lines = sorted.take(MAX_LISTED).map { "${it.name} [${kind(it)}] (${it.length()} bytes)" }
        val more = if (sorted.size > MAX_LISTED) "\n...and ${sorted.size - MAX_LISTED} more." else ""
        val body = if (lines.isEmpty()) "Folder is empty." else lines.joinToString("\n") + more
        return BrowseResult.Listing("Folder: ${dir.path}\n$body")
    }

    @Suppress("ReturnCount") // guard clauses: each refusal returns its own message
    fun find(
        query: String,
        folder: String?,
    ): BrowseResult {
        val needle = query.trim()
        if (needle.isEmpty() || needle.length > MAX_QUERY) return BrowseResult.Refused(BAD_QUERY)
        val start =
            try {
                layout.folder(folder)
            } catch (_: SecurityException) {
                return BrowseResult.Refused(OUTSIDE)
            }
        gate(start)?.let { return it }
        if (!start.isDirectory) return BrowseResult.Refused("That folder does not exist.")
        val found = search(start, needle.lowercase())
        if (found.isEmpty()) return BrowseResult.Listing("No files found matching \"$needle\" in ${start.path}.")
        val newestFirst = found.sortedByDescending { it.lastModified() }
        val lines = newestFirst.map { "${it.name} (${it.length()} bytes, ${day(it)}) ${it.path}" }
        return BrowseResult.Listing("Found ${found.size} match(es) in ${start.path}:\n" + lines.joinToString("\n"))
    }

    private fun gate(dir: File): BrowseResult.Refused? =
        if (accessGranted() || layout.isOwnArea(dir)) null else BrowseResult.Refused(NEEDS_ACCESS)

    private fun search(
        start: File,
        needle: String,
    ): List<File> {
        val results = mutableListOf<File>()
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(start to 0)
        var visited = 0
        while (queue.isNotEmpty() && results.size < MAX_RESULTS && visited < MAX_VISITED) {
            val (dir, depth) = queue.removeFirst()
            val children = dir.listFiles().orEmpty()
            visited += children.size
            val (dirs, files) = children.filter(::isSearchable).partition { it.isDirectory }
            if (depth < MAX_DEPTH) dirs.forEach { queue.add(it to depth + 1) }
            files
                .filter { it.name.lowercase().contains(needle) }
                .take(MAX_RESULTS - results.size)
                .forEach { results += it }
        }
        return results
    }

    private fun isSearchable(file: File): Boolean =
        !file.name.startsWith(".") && layout.isAllowed(file) && !Files.isSymbolicLink(file.toPath())

    private fun kind(file: File): String = if (file.isDirectory) "DIR" else "FILE"

    private fun day(file: File): String =
        Instant
            .ofEpochMilli(file.lastModified())
            .atOffset(ZoneOffset.UTC)
            .toLocalDate()
            .toString()

    companion object {
        const val NEEDS_ACCESS =
            "EQO needs All files access to look there. Turn it on in Set up EQO, row \"All files access\"."
        private const val BAD_QUERY = "Give a short part of the file name to look for."
        const val OUTSIDE = "That location is not part of shared storage EQO may look in."
        private const val MAX_LISTED = 200
        private const val MAX_RESULTS = 50
        private const val MAX_VISITED = 50_000
        private const val MAX_DEPTH = 8
        private const val MAX_QUERY = 200
    }
}
