// Origin: EQO Files v2, validated run-time attachment search contract.
package ai.eqo.core.agent

import java.time.LocalDate

/** A search is approved, not an invented path. Relative dates must be expanded by the planner. */
data class AttachmentSearch(
    val words: List<String>,
    val type: String?,
    val folder: String?,
    val firstDay: LocalDate?,
    val lastDay: LocalDate?,
    val latest: Boolean,
) {
    companion object {
        private val types = setOf("image", "screenshot", "pdf", "doc", "video", "audio")
        private val dayPattern = Regex("\\d{4}-\\d{2}-\\d{2}")

        fun isSearch(reference: String): Boolean = reference.startsWith("find:", ignoreCase = true)

        @Suppress("CyclomaticComplexMethod") // Each filter has a distinct validation rule.
        fun parse(reference: String): AttachmentSearch {
            require(isSearch(reference)) { "attachment search must start with find:" }
            val body = reference.substringAfter(':').trim()
            require(body.isNotEmpty() && body.length <= 200) { "attachment search must be short and non-empty" }
            require(!body.contains("://") && body.none { it.isISOControl() }) { "invalid attachment search" }
            val filters = linkedMapOf<String, String>()
            val words = mutableListOf<String>()
            var latest = false
            body.split(',').forEach { part ->
                val token = part.trim()
                require(token.isNotEmpty()) { "empty attachment search filter" }
                when {
                    token.equals("latest", ignoreCase = true) -> {
                        require(!latest) { "duplicate latest filter" }
                        latest = true
                    }
                    '=' in token -> {
                        val key = token.substringBefore('=').trim().lowercase()
                        val value = token.substringAfter('=').trim()
                        require(key in setOf("name", "type", "folder", "date")) { "unknown attachment search filter" }
                        require(value.isNotEmpty() && filters.put(key, value) == null) { "empty or duplicate filter" }
                    }
                    else -> words += token.split(Regex("\\s+"))
                }
            }
            filters["name"]?.let { words += it.split(Regex("\\s+")) }
            require(words.none { '/' in it || '\\' in it || it == ".." }) { "search name must not be a path" }
            val type = filters["type"]?.lowercase()
            require(type == null || type in types) { "unknown attachment file type" }
            val folder = filters["folder"]
            require(folder == null || folder.split('/', '\\').none { it == ".." }) { "unsafe search folder" }
            val dates = filters["date"]?.split("..")
            require(dates == null || dates.size in 1..2) { "invalid attachment date range" }
            val parsed = dates?.map {
                require(dayPattern.matches(it)) { "attachment date must be YYYY-MM-DD" }
                LocalDate.parse(it)
            }
            val first = parsed?.first()
            val last = parsed?.last()
            require(first == null || !first.isAfter(last)) { "attachment date range is reversed" }
            return AttachmentSearch(words, type, folder, first, last, latest)
        }
    }
}
