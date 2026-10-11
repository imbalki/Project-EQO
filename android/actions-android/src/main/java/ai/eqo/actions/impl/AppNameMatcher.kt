// Origin: EQO ranked local app-name matching; no request persistence or telemetry.
package ai.eqo.actions.impl

import java.util.Locale

/** Scores are ordered bands, not probabilities. Weak matches always need a human choice. */
@Suppress("MagicNumber") // Explicit score bands and bounded edit distance are the resolution policy.
object AppNameMatcher {
    const val CLEAR_SCORE = 70
    const val CLEAR_GAP = 15
    const val ALIAS_SCORE = 120

    fun normalize(name: String): String =
        clean(name)
            .removePrefix("google ")
            .removeSuffix(" app")
            .removeSuffix(" notes")
            .trim()

    fun score(
        name: String,
        label: String,
        pkg: String,
    ): Int {
        val query = normalize(name)
        val targets = listOf(normalize(label), clean(label), normalize(pkg))
        return if (query.isBlank()) 0 else targets.maxOf { compare(query, it) }
    }

    private fun clean(name: String): String = name.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun compare(
        query: String,
        target: String,
    ): Int =
        when {
            target == query -> 100
            target.startsWith(query) -> 90
            target.split(' ').any { it == query } -> 80
            query.length >= 3 && distance(query, target) <= 2 -> 70
            query.split(' ').intersect(target.split(' ').toSet()).isNotEmpty() -> 40
            else -> 0
        }

    private fun distance(
        a: String,
        b: String,
    ): Int {
        if (kotlin.math.abs(a.length - b.length) > 2) return 3
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
}
