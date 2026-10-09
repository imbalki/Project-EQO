// Origin: EQO Files v2, metadata-first search and per-phone shared-folder discovery.
package ai.eqo.actions.impl

import java.io.File

internal data class SharedMediaEntry(
    val relativePath: String,
    val name: String,
    val bucket: String?,
    val mime: String?,
    val modifiedMillis: Long,
    val addedMillis: Long,
)

internal fun interface SharedMediaSource {
    fun entries(): List<SharedMediaEntry>
}

internal interface SharedFolderMapStore {
    fun read(): Map<String, List<String>>?

    fun write(map: Map<String, List<String>>)
}

/** Only folder/group metadata is persisted. File names, paths to files and searches never are. */
internal class SharedStorageCatalog(
    private val layout: SharedStorageLayout,
    private val media: SharedMediaSource,
    private val store: SharedFolderMapStore,
) {
    private var indexed = emptyMap<String, SharedMediaEntry>()
    private var learned: Map<String, List<String>>? = null

    fun refresh(rescan: Boolean = false) {
        val entries = media.entries()
        indexed =
            entries
                .mapNotNull { entry ->
                    safeFile(entry)?.let { it.path to entry }
                }.toMap()
        learned = if (rescan) null else learned ?: store.read()
        if (learned == null) {
            learned = discover(entries)
            store.write(requireNotNull(learned))
        }
        layout.learnedFolders = requireNotNull(learned)
    }

    fun indexedFiles(): List<File> = indexed.keys.map(::File)

    fun metadata(file: File): SharedMediaEntry? = indexed[file.path]

    fun modifiedMillis(file: File): Long =
        metadata(file)?.modifiedMillis?.takeIf { it > 0 }
            ?: metadata(file)?.addedMillis?.takeIf { it > 0 } ?: file.lastModified()

    fun belongs(
        file: File,
        group: String,
    ): Boolean {
        val relative = file.relativeTo(layout.root).invariantSeparatorsPath
        val folders = layout.aliases.folders[group].orEmpty() + learned?.get(group).orEmpty()
        return folders.any { relative.startsWith(it.trimEnd('/') + "/", ignoreCase = true) } ||
            metadata(file)?.bucket?.let { group in layout.aliases.groups(it) } == true
    }

    private fun safeFile(entry: SharedMediaEntry): File? {
        val unsafeName = entry.name.isBlank() || entry.name.any { it == '/' || it == '\\' || it.isISOControl() }
        val hidden = (entry.relativePath + entry.name).split('/').any { it.startsWith('.') }
        if (unsafeName || hidden) return null
        return try {
            layout.file(entry.relativePath.trimEnd('/') + "/" + entry.name)
        } catch (_: SecurityException) {
            null
        }
    }

    @Suppress("CyclomaticComplexMethod") // Two-level directory discovery plus independently classified media buckets.
    private fun discover(entries: List<SharedMediaEntry>): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableSet<String>>()
        val deadline = System.nanoTime() + MAX_NANOS
        var visited = 0

        fun withinBudget(): Boolean = ++visited <= MAX_FOLDERS && System.nanoTime() < deadline

        @Suppress("ReturnCount") // Every excluded path exits before its directory metadata is learned.
        fun add(
            path: String,
            bucket: String? = null,
            extraGroups: Set<String> = emptySet(),
        ) {
            if (path.split('/').any { it.startsWith('.') }) return
            val dir =
                try {
                    layout.folder(path)
                } catch (_: SecurityException) {
                    return
                }
            if (!layout.isAllowed(dir) || layout.hasLinkedAncestor(dir)) return
            val groups = layout.aliases.groups(path) + bucket?.let { layout.aliases.groups(it) }.orEmpty() + extraGroups
            val relative = dir.relativeTo(layout.root).invariantSeparatorsPath
            groups.forEach { result.getOrPut(it) { linkedSetOf() }.add(relative) }
        }
        // Top-level and second-level names only, never traverse Android/data or Android/obb.
        layout.root
            .listFiles()
            .orEmpty()
            .take(MAX_FOLDERS)
            .takeWhile { withinBudget() }
            .filter { it.isDirectory && !it.name.startsWith('.') }
            .filter { layout.isAllowed(it) && !layout.hasLinkedAncestor(it) }
            .forEach { top ->
                add(top.name)
                top
                    .listFiles()
                    .orEmpty()
                    .take(MAX_FOLDERS)
                    .takeWhile { withinBudget() }
                    .filter { it.isDirectory && !it.name.startsWith('.') }
                    .filter { layout.isAllowed(it) && !layout.hasLinkedAncestor(it) }
                    .forEach {
                        add(top.name + "/" + it.name)
                    }
            }
        entries.distinctBy { Triple(it.relativePath, it.bucket, it.mime) }.takeWhile { withinBudget() }.forEach {
            val group =
                when {
                    it.mime?.startsWith("image/") == true -> "gallery"
                    it.mime?.startsWith("video/") == true -> "video"
                    it.mime?.startsWith("audio/") == true -> "audio"
                    layout.aliases.matchesType("pdf", File(it.name), it.mime) ||
                        layout.aliases.matchesType("doc", File(it.name), it.mime) -> "documents"
                    else -> null
                }
            add(it.relativePath.trimEnd('/'), it.bucket, setOfNotNull(group))
        }
        return result.mapValues { it.value.toList() }
    }

    companion object {
        private const val MAX_FOLDERS = 500
        private const val MAX_NANOS = 2_000_000_000L
    }
}
