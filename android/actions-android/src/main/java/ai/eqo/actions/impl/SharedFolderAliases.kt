// Origin: EQO Files v2, single data-driven alias table for shared-storage/OEM layouts.
package ai.eqo.actions.impl

import android.content.Context
import org.json.JSONObject
import java.io.File

internal class SharedFolderAliases(
    val folders: Map<String, List<String>>,
    private val extensions: Map<String, List<String>>,
    private val mimePrefixes: Map<String, List<String>>,
) {
    fun groups(path: String): Set<String> {
        val normalized = normalize(path)
        val leaf = normalized.substringAfterLast('/')
        return folders
            .filterValues { names ->
                names.any { normalize(it) == normalized || normalize(it).substringAfterLast('/') == leaf }
            }.keys
    }

    @Suppress("ReturnCount") // Exact path, alias group and missing-alias outcomes stay explicit.
    fun folder(
        root: File,
        reference: String,
        learned: Map<String, List<String>> = emptyMap(),
    ): File? {
        val exact = File(root, reference)
        if (exact.exists()) return exact
        val first = reference.substringBefore('/')
        val group =
            folders.keys.firstOrNull { normalize(it) == normalize(first) }
                ?: groups(first).firstOrNull() ?: return null
        val known = folders[group].orEmpty()
        val discovered = learned[group].orEmpty()
        val candidates = if (group == "screenshot") (discovered + known).distinct() else (known + discovered).distinct()
        val target = candidates.firstOrNull { File(root, it).isDirectory } ?: candidates.firstOrNull() ?: return null
        val suffix = reference.substringAfter('/', "")
        return if (suffix.isEmpty()) File(root, target) else File(File(root, target), suffix)
    }

    fun matchesType(
        type: String,
        file: File,
        mime: String?,
    ): Boolean =
        if (!mime.isNullOrBlank() && mime != "application/octet-stream") {
            mimePrefixes[type].orEmpty().any { mime.startsWith(it, ignoreCase = true) }
        } else {
            extensions[type].orEmpty().any { it.equals(file.extension, ignoreCase = true) }
        }

    /** Search every existing alias, not just the first: modern and legacy media can coexist. */
    fun searchFolders(
        root: File,
        reference: String,
        learned: Map<String, List<String>>,
    ): List<File> {
        if ('/' in reference || '\\' in reference || File(reference).isAbsolute) {
            return listOf(folder(root, reference, learned) ?: File(root, reference))
        }
        val group =
            folders.keys.firstOrNull { normalize(it) == normalize(reference) }
                ?: groups(reference).firstOrNull()
                ?: return listOf(File(root, reference))
        val candidates = (folders[group].orEmpty() + learned[group].orEmpty()).distinct().map { File(root, it) }
        return candidates.filter { it.isDirectory }.ifEmpty { candidates.take(1) }
    }

    companion object {
        val EMPTY = SharedFolderAliases(emptyMap(), emptyMap(), emptyMap())
        private const val ASSET = "shared-storage-aliases.json"

        fun load(context: Context): SharedFolderAliases {
            val json =
                context.assets
                    .open(ASSET)
                    .bufferedReader()
                    .use { JSONObject(it.readText()) }
            return SharedFolderAliases(
                table(json.getJSONObject("folders")),
                table(json.getJSONObject("extensions")),
                table(json.getJSONObject("mimePrefixes")),
            )
        }

        private fun table(json: JSONObject): Map<String, List<String>> =
            json.keys().asSequence().associateWith { key ->
                val values = json.getJSONArray(key)
                (0 until values.length()).map { values.getString(it) }
            }

        private fun normalize(value: String): String = value.lowercase().trim('/').replace(Regex("[ _-]"), "")
    }
}
