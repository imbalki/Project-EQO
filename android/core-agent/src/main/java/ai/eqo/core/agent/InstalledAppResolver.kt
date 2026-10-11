// Origin: EQO local installed-app name resolution; pure, no I/O, no persistence, no logging.
package ai.eqo.core.agent

import java.util.Locale

/**
 * Maps a typed or model-returned app name to exactly one installed label.
 *
 * `null` always means "ask the person": the name is unknown, names a generic role (browser, mail) that several
 * installed apps may fill, or fits two or more installed labels about equally. Order is exact label, normalized
 * label (case, punctuation, a leading "google ", a trailing " app"/" notes"), unique prefix, unique whole word,
 * then a unique label within two edits. A tier with two labels never falls through to a weaker tier.
 */
object InstalledAppResolver {
    const val MAX_EDIT_DISTANCE = 2
    private const val MIN_APPROXIMATE_LENGTH = 3
    private const val PUBLISHER = "google "
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    // Known names of apps whose launcher label differs; each maps to a label's normalized form.
    private val ALIASES =
        mapOf(
            "google mail" to "gmail",
            "google chrome" to "chrome",
            "google keep" to "keep",
            "google keep notes" to "keep",
            "keep notes" to "keep",
        )

    // A generic role never matches loosely: several installed apps may fill it, so only an exact label counts.
    private val ROLE_WORDS =
        setOf(
            "browser",
            "internet",
            "web",
            "mail",
            "email",
            "message",
            "messages",
            "sms",
            "text",
            "music",
            "notes",
            "note",
            "camera",
            "photos",
            "gallery",
            "maps",
            "contacts",
            "phone",
            "dialer",
            "calendar",
            "clock",
            "files",
            "video",
        )

    fun clean(name: String): String = name.lowercase(Locale.ROOT).replace(NON_WORD, " ").trim()

    fun normalize(name: String): String =
        clean(name)
            .removePrefix(PUBLISHER)
            .removeSuffix(" app")
            .removeSuffix(" notes")
            .trim()

    /** Levenshtein distance, reported as more than [MAX_EDIT_DISTANCE] when the lengths already differ that much. */
    fun editDistance(
        a: String,
        b: String,
    ): Int {
        if (kotlin.math.abs(a.length - b.length) > MAX_EDIT_DISTANCE) return MAX_EDIT_DISTANCE + 1
        var previous = IntArray(b.length + 1) { it }
        a.forEachIndexed { i, left ->
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            b.forEachIndexed { j, right ->
                current[j + 1] =
                    minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (left == right) 0 else 1)
            }
            previous = current
        }
        return previous.last()
    }

    /** The one installed label [name] clearly refers to, or null when it is unknown or ambiguous. */
    fun resolve(
        name: String,
        installed: List<String>,
    ): String? {
        val labels = installed.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val spoken = clean(name)
        if (spoken.isEmpty() || labels.isEmpty()) return null
        val alias = ALIASES[spoken]
        val query = alias ?: normalize(name)
        val tiers =
            when {
                // A publisher claim ("Google Foo") is never stripped: only a literal label or a known alias counts.
                alias == null && spoken.startsWith(PUBLISHER) -> listOf(literal(name))
                alias != null || query in ROLE_WORDS -> listOf(literal(name), normalizedExact(query))
                else -> listOf(literal(name), normalizedExact(query), prefix(query), word(query), nearby(query))
            }
        return tiers
            .firstNotNullOfOrNull { tier -> labels.filter(tier).takeIf { it.isNotEmpty() } }
            ?.singleOrNull()
    }

    private fun compact(text: String): String = text.replace(" ", "")

    private fun literal(name: String): (String) -> Boolean {
        val spoken = compact(clean(name))
        return { label -> label.equals(name.trim(), true) || compact(clean(label)) == spoken }
    }

    private fun normalizedExact(query: String): (String) -> Boolean {
        val spoken = compact(query)
        return { label -> compact(normalize(label)) == spoken }
    }

    private fun prefix(query: String): (String) -> Boolean =
        { label -> query.length >= MIN_APPROXIMATE_LENGTH && normalize(label).startsWith(query) }

    private fun word(query: String): (String) -> Boolean =
        { label ->
            query.length >= MIN_APPROXIMATE_LENGTH &&
                query in normalize(label).split(' ') + clean(label).split(' ')
        }

    private fun nearby(query: String): (String) -> Boolean =
        { label ->
            query.length >= MIN_APPROXIMATE_LENGTH &&
                editDistance(query, normalize(label)) <= MAX_EDIT_DISTANCE
        }
}
