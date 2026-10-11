// Origin: EQO missing-app preflight; shared local-only OPEN_APP resolution.
package ai.eqo.actions.impl

import ai.eqo.core.agent.AliasResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/** No network, activity launch, permission request or installed-app telemetry. */
class LaunchableAppResolver(
    private val launch: (String) -> Intent?,
    private val launcherApps: () -> List<Pair<String, String>>,
) {
    constructor(pm: PackageManager) : this(
        pm::getLaunchIntentForPackage,
        {
            pm
                .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        },
    )

    data class Candidate(
        val packageName: String,
        val label: String,
        val score: Int,
    )

    fun installedApps(): List<Pair<String, String>> =
        launcherApps()
            .distinctBy { it.first }
            .filter { launch(it.first) != null }

    fun candidates(appName: String): List<Candidate> {
        val alias = AliasResolver.appPackage(appName)
        return installedApps()
            .map { (pkg, label) ->
                val score =
                    if (pkg == alias || pkg.equals(appName.trim(), true)) {
                        AppNameMatcher.ALIAS_SCORE
                    } else {
                        AppNameMatcher.score(appName, label, pkg)
                    }
                Candidate(pkg, label, score)
            }.filter { it.score > 0 }
            .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.label }.thenBy { it.packageName })
    }

    fun clearWinner(candidates: List<Candidate>): Candidate? {
        val best = candidates.firstOrNull()
        val gap = (best?.score ?: 0) - (candidates.getOrNull(1)?.score ?: 0)
        return best?.takeIf { it.score >= AppNameMatcher.CLEAR_SCORE && gap >= AppNameMatcher.CLEAR_GAP }
    }

    // Only explicit package targets are accepted here; approved execution never fuzzy-redirects.
    fun resolvePackage(packageName: String): Intent? = launch(packageName)

    // Explicit fail-closed exits keep settings and empty-input handling independent.
    @Suppress("ReturnCount")
    fun resolve(appName: String): Intent? {
        val name = appName.trim()
        if (name.isEmpty()) return null
        if (name.equals("settings", true)) return Intent(Settings.ACTION_SETTINGS)
        return clearWinner(candidates(name))?.packageName?.let(launch)
    }
}
