// Origin: EQO files-attachments, where the file actions may look and which places are never shared.
package ai.eqo.actions.impl

import java.io.File
import java.nio.file.Files

/**
 * Maps the folder names an owner or planner uses ("Downloads", "Documents/Taxes", a full path) onto real
 * folders under shared storage, and decides which files may ever be listed or attached.
 *
 * Never allowed: anything outside shared storage (that includes EQO's own private data and settings),
 * and other apps' folders under Android/data and Android/obb. EQO's own external files folder is the one
 * exception inside Android/data. Pure java.io so it can be tested with temporary folders.
 */
internal class SharedStorageLayout(
    root: File,
    ownAppAreas: List<File> = emptyList(),
    stagingAreas: List<File> = emptyList(),
    val aliases: SharedFolderAliases = SharedFolderAliases.EMPTY,
    private val isLink: (File) -> Boolean = { Files.isSymbolicLink(it.toPath()) },
) {
    var learnedFolders: Map<String, List<String>> = emptyMap()
    val root: File = canonical(root)
    private val ownAreas = ownAppAreas.map(::canonical)
    private val staging = stagingAreas.map(::canonical)
    private val otherApps = listOf(File(this.root, "Android/data"), File(this.root, "Android/obb")).map(::canonical)

    /** Resolves a folder name or path. Blank means the whole of shared storage. */
    fun folder(name: String?): File {
        val trimmed = name?.trim().orEmpty().trimEnd('/')
        if (trimmed.isEmpty() || trimmed == ".") return root
        return checked(locate(trimmed))
    }

    /** Resolves a file reference (full path, or a path starting with a well-known folder name). */
    fun file(reference: String): File = checked(locate(reference.trim()))

    fun searchFolders(name: String?): List<File> {
        val reference = name?.trim().orEmpty().trimEnd('/')
        // Validate traversal and absolute-path boundaries before expanding aliases.
        val exact = folder(name)
        if (reference.isEmpty() || reference == ".") return listOf(exact)
        return aliases.searchFolders(root, reference, learnedFolders).map(::checked).ifEmpty { listOf(exact) }
    }

    /** True for places EQO itself writes to, which need no All files access to read. */
    fun isOwnArea(file: File): Boolean = ownAreas.any { inside(canonical(file), it) }

    fun isAllowed(file: File): Boolean {
        val path = canonical(file)
        val insideOtherApp = otherApps.any { inside(path, it) } && ownAreas.none { inside(path, it) }
        return inside(path, root) && staging.none { inside(path, it) } && !insideOtherApp
    }

    fun hasLinkedAncestor(file: File): Boolean {
        var segment: File? = file.absoluteFile
        while (segment != null) {
            if (isLink(segment)) return true
            segment = segment.parentFile
        }
        return false
    }

    private fun checked(file: File): File {
        if (hasLinkedAncestor(file)) throw SecurityException("Links cannot be shared.")
        val path = canonical(file)
        if (!isAllowed(path)) throw SecurityException("That location is not part of shared storage EQO may use.")
        return path
    }

    private fun locate(reference: String): File {
        require(reference.isNotEmpty()) { "Empty path" }
        if (reference.split('/', '\\').any { it == ".." }) throw SecurityException("Traversal cannot be shared.")
        if (File(reference).isAbsolute || reference.startsWith("/")) return File(reference)
        val segments = reference.split('/').filter { it.isNotEmpty() }
        return aliases.folder(root, reference, learnedFolders)
            ?: segments.fold(root) { dir, part -> File(dir, part) }
    }

    companion object {
        /** Folder the dedicated EQO screenshots go into, relative to shared storage. */
        const val SCREENSHOT_FOLDER = "Pictures/EQO"

        fun canonical(file: File): File =
            try {
                file.canonicalFile
            } catch (_: java.io.IOException) {
                file.absoluteFile
            }

        private fun inside(
            file: File,
            folder: File,
        ): Boolean = file.path == folder.path || file.path.startsWith(folder.path + File.separator)
    }
}
