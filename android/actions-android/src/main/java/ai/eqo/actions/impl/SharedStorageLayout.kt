// Origin: EQO files-attachments, where the file actions may look and which places are never shared.
package ai.eqo.actions.impl

import java.io.File

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
) {
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

    /** True for places EQO itself writes to, which need no All files access to read. */
    fun isOwnArea(file: File): Boolean = ownAreas.any { inside(canonical(file), it) }

    fun isAllowed(file: File): Boolean {
        val path = canonical(file)
        val insideOtherApp = otherApps.any { inside(path, it) } && ownAreas.none { inside(path, it) }
        return inside(path, root) && staging.none { inside(path, it) } && !insideOtherApp
    }

    private fun checked(file: File): File {
        val path = canonical(file)
        if (!isAllowed(path)) throw SecurityException("That location is not part of shared storage EQO may use.")
        return path
    }

    private fun locate(reference: String): File {
        require(reference.isNotEmpty()) { "Empty path" }
        if (reference.startsWith("/")) return File(reference)
        val segments = reference.split('/').filter { it.isNotEmpty() }
        val first = segments.first().lowercase()
        val mapped = NAMED_FOLDERS[first]
        val rest = segments.drop(1)
        return when {
            mapped != null -> rest.fold(File(root, mapped)) { dir, part -> File(dir, part) }
            else -> segments.fold(root) { dir, part -> File(dir, part) }
        }
    }

    companion object {
        /** Folder the dedicated EQO screenshots go into, relative to shared storage. */
        const val SCREENSHOT_FOLDER = "Pictures/EQO"

        private val NAMED_FOLDERS =
            mapOf(
                "download" to "Download",
                "downloads" to "Download",
                "document" to "Documents",
                "documents" to "Documents",
                "picture" to "Pictures",
                "pictures" to "Pictures",
                "photos" to "DCIM",
                "dcim" to "DCIM",
                "camera" to "DCIM/Camera",
                "screenshots" to "Pictures/Screenshots",
                "eqo" to SCREENSHOT_FOLDER,
                "movies" to "Movies",
                "music" to "Music",
            )

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
