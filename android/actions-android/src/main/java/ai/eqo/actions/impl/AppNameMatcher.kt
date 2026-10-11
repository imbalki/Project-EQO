// Origin: EQO ranked local app-name matching; no request persistence or telemetry.
package ai.eqo.actions.impl

import ai.eqo.core.agent.InstalledAppResolver

/** Scores are ordered bands, not probabilities. Weak matches always need a human choice. */
@Suppress("MagicNumber") // Explicit score bands and bounded edit distance are the resolution policy.
object AppNameMatcher {
    const val EXACT_SCORE = 100
    const val CLEAR_SCORE = 70
    const val CLEAR_GAP = 15
    const val ALIAS_SCORE = 120

    // One normalization shared with stage-one intent understanding; remembered-app keys depend on it.
    fun normalize(name: String): String = InstalledAppResolver.normalize(name)

    fun score(
        name: String,
        label: String,
        pkg: String,
    ): Int {
        val query = normalize(name)
        val targets = listOf(normalize(label), clean(label), normalize(pkg))
        return if (query.isBlank()) 0 else targets.maxOf { compare(query, it) }
    }

    private fun clean(name: String): String = InstalledAppResolver.clean(name)

    private fun compare(
        query: String,
        target: String,
    ): Int =
        when {
            target == query -> EXACT_SCORE
            target.startsWith(query) -> 90
            target.split(' ').any { it == query } -> 80
            query.length >= 3 && withinTypoDistance(query, target) -> 70
            query.split(' ').intersect(target.split(' ').toSet()).isNotEmpty() -> 40
            else -> 0
        }

    private fun withinTypoDistance(
        query: String,
        target: String,
    ): Boolean = InstalledAppResolver.editDistance(query, target) <= InstalledAppResolver.MAX_EDIT_DISTANCE
}
