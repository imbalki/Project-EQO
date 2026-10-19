package ai.eqo.explain

/** Small, display-only Markdown conversion. Returns a String, never HTML or clickable spans. */
internal object ExplainAnswerText {
    private const val CODE_CONTENT_GROUP = 3
    private val fence = Regex("^ {0,3}(`{3,}|~{3,})(.*)$")
    private val heading = Regex("^( {0,3})#{1,6}\\s+(.+?)(?:\\s+#+)?\\s*$")
    private val bullet = Regex("^(\\s*)[-+*]\\s+")
    private val numbered = Regex("^(\\s*\\d+)[.)]\\s+")
    private val quote = Regex("^ {0,3}(?:>\\s?)+")
    private val divider = Regex("^ {0,3}(?:([-*_])\\s*){3,}$")
    private val setext = Regex("^ {0,3}(?:=+|-+)\\s*$")
    private val literalTokens = Regex("\\\\([\\\\`*_\\{\\}\\[\\]()#+.!>~-])|(`+)(.+?)\\2")
    private val link = Regex("!?\\[([^\\]\\n]+)]\\((?:[^()\\n]|\\([^()\\n]*\\))*\\)")
    private val emphasis =
        listOf(
            Regex("\\*\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*\\*"),
            Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*"),
            Regex("(?<!\\w)__(?=\\S)(.+?)(?<=\\S)__(?!\\w)"),
            Regex("(?<!\\*)\\*(?=\\S)(.+?)(?<=\\S)\\*(?!\\*)"),
            Regex("(?<!\\w)_(?=\\S)(.+?)(?<=\\S)_(?!\\w)"),
            Regex("~~(?=\\S)(.+?)(?<=\\S)~~"),
        )

    fun render(markdown: String): String {
        var fenceMarker = ""
        val lines = mutableListOf<String>()
        for (line in markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val marker = fence.find(line)
            if (fenceMarker.isNotEmpty()) {
                if (closesFence(marker, fenceMarker)) {
                    fenceMarker = ""
                } else {
                    lines.add(line)
                }
            } else if (marker != null) {
                fenceMarker = marker.groupValues[1]
            } else {
                addProse(lines, line)
            }
        }
        return lines.joinToString("\n")
    }

    private fun closesFence(
        marker: MatchResult?,
        current: String,
    ): Boolean {
        if (marker == null) return false
        val delimiter = marker.groupValues[1]
        return delimiter.startsWith(current) && marker.groupValues[2].isBlank()
    }

    private fun addProse(
        lines: MutableList<String>,
        line: String,
    ) {
        val unquoted = quote.replace(line, "")
        val underline = lines.lastOrNull()?.isNotBlank() == true && setext.matches(unquoted)
        if (!underline && !divider.matches(unquoted)) {
            val titled = heading.replace(unquoted, "$1$2")
            val listed = numbered.replace(bullet.replace(titled, "$1• "), "$1. ")
            lines.add(inline(listed))
        }
    }

    private fun inline(text: String): String {
        // Protect literal escapes/code while removing surrounding emphasis and link syntax.
        var prefix = "\uE000"
        while (prefix in text) prefix += '\uE000'
        val literals = mutableListOf<String>()
        var result =
            literalTokens.replace(text) { match ->
                literals.add(match.groups[1]?.value ?: match.groupValues[CODE_CONTENT_GROUP])
                "$prefix${literals.lastIndex}\uE001"
            }
        result = link.replace(result, "$1")
        for (pattern in emphasis) result = pattern.replace(result, "$1")
        return Regex("${Regex.escape(prefix)}(\\d+)\uE001").replace(result) { match ->
            literals[match.groupValues[1].toInt()]
        }
    }
}
