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

    // Explicit fail-closed exits keep alias and ambiguous-name checks independent.
    @Suppress("ReturnCount")
    fun resolve(appName: String): Intent? {
        val name = appName.trim()
        if (name.isEmpty()) return null
        if (name.equals("settings", true)) return Intent(Settings.ACTION_SETTINGS)
        // Known aliases must never resolve to a similarly named unrelated app.
        AliasResolver.appPackage(name)?.let { return launch(it) }
        launch(name)?.let { return it }
        val apps = launcherApps()
        val exact = apps.filter { (pkg, label) -> pkg.equals(name, true) || label.equals(name, true) }
        val matches =
            exact.ifEmpty {
                apps.filter { (pkg, label) -> label.contains(name, true) || pkg.contains(name, true) }
            }
        // Ambiguous names are not proof that the requested app is installed.
        val packages = matches.map { it.first }.distinct()
        return packages.singleOrNull()?.let(launch)
    }
}
