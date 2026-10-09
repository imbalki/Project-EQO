// Origin: EQO files-attachments, shared reading of the optional `attachment` parameter.
package ai.eqo.core.agent

/**
 * The optional `attachment` parameter of SEND_EMAIL, SEND_WHATSAPP and SEND_SMS.
 *
 * One value holds one or more references separated by `|` or a new line. A reference is either a
 * file path or the word `last_screenshot`. The planner validator, the plan preview and the Android
 * executors all read it through here so the owner approves exactly what is later attached.
 */
object AttachmentSpec {
    const val PARAM = "attachment"
    const val LAST_SCREENSHOT = "last_screenshot"
    const val MAX_ATTACHMENTS = 10

    private const val MAX_REFERENCE = 1024
    private val separator = Regex("[|\\n]")

    /** Actions that accept [PARAM]. */
    val ACTIONS: Set<String> = setOf("SEND_EMAIL", "SEND_WHATSAPP", "SEND_SMS")

    /** Trimmed, non-blank references in the order given. */
    fun parse(raw: String?): List<String> =
        raw
            .orEmpty()
            .split(separator)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun isLastScreenshot(reference: String): Boolean = reference.trim().equals(LAST_SCREENSHOT, ignoreCase = true)

    /** Problems that make the value unusable. Empty when the value is acceptable (or absent). */
    fun errors(raw: String?): List<String> {
        val references = parse(raw)
        return buildList {
            if (references.size > MAX_ATTACHMENTS) add("at most $MAX_ATTACHMENTS attachments")
            if (references.distinct().size != references.size) add("duplicate attachment")
            references.forEach { reference ->
                when {
                    reference.length > MAX_REFERENCE -> add("attachment path too long")
                    isLastScreenshot(reference) -> Unit
                    reference.contains("://") -> add("attachment must be a file path, not a link")
                    reference.split('/', '\\').any { it == ".." } -> add("attachment path must not contain '..'")
                    reference.any { it.isISOControl() } -> add("attachment path has control characters")
                }
            }
        }.distinct()
    }

    /** What the owner sees in the plan preview for one reference: the file name, never the content. */
    fun displayName(reference: String): String =
        if (isLastScreenshot(reference)) {
            "your latest EQO screenshot"
        } else {
            val name = reference.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
            "\"" + TaskDisplayText.escape(name) + "\""
        }

    fun displayNames(raw: String?): List<String> = parse(raw).map(::displayName)
}
