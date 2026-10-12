// Origin: EQO direct browser actions; no network, no installed-app telemetry, no logging.
package ai.eqo.actions.impl

import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.WebTargets
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri

/** Which browser a web step is aimed at. */
internal sealed interface BrowserChoice {
    /** None named: Android's own default for web links. */
    data object Default : BrowserChoice

    /** One installed browser package, set on the intent so no other app can take the page. */
    data class Package(
        val name: String,
    ) : BrowserChoice

    /** A browser was named but cannot be used; nothing is opened and the plain reason is reported. */
    data class Unavailable(
        val reason: String,
    ) : BrowserChoice
}

/**
 * Opens a web address or a web search directly in a chosen browser, never by tapping an address bar.
 *
 * An approved plan's pinned `packageName` is authoritative and is never fuzzy-redirected. Without a pin the browser
 * label is resolved on the phone with the same launcher inventory as OPEN_APP.
 */
internal object BrowserTarget {
    private const val CHROME = "com.android.chrome"
    private const val UNAVAILABLE = "That browser is not installed or is not clearly one app. Nothing was opened."
    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

    fun choose(
        params: Map<String, String>,
        resolver: LaunchableAppResolver,
    ): BrowserChoice {
        val pinned = params["packageName"]?.takeIf { it.isNotBlank() }
        val named = params["browser"]?.trim().orEmpty()
        return when {
            pinned != null -> installed(pinned, resolver, "The approved browser is no longer installed.")
            named.isEmpty() -> BrowserChoice.Default
            named.equals("chrome", true) || named.equals("google chrome", true) -> BrowserChoice.Package(CHROME)
            PACKAGE.matches(named) -> installed(named, resolver, UNAVAILABLE)
            else -> byLabel(named, resolver)
        }
    }

    /** An ACTION_VIEW intent for [url], limited to [choice]'s package when one is named. */
    fun intent(
        url: String,
        choice: BrowserChoice,
    ): Intent =
        Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (choice is BrowserChoice.Package) setPackage(choice.name)
        }

    /** Opens the page through [launcher]; a named browser that cannot show it is a plain failure, not a fallback. */
    fun open(
        launcher: GatedIntentLauncher,
        url: String,
        choice: BrowserChoice,
    ): String? =
        try {
            launcher.open(intent(url, choice))
            null
        } catch (_: ActivityNotFoundException) {
            if (choice is BrowserChoice.Package) {
                "That browser could not open the page."
            } else {
                "No app can open web pages."
            }
        }

    /**
     * WEB_SEARCH in a named browser: the search page opens directly in that browser's package. Null when no browser
     * was named, so the caller keeps the system default.
     */
    fun search(
        launcher: GatedIntentLauncher,
        params: Map<String, String>,
        resolver: LaunchableAppResolver,
        online: Boolean,
    ): ActionResult? {
        val browser = choose(params, resolver)
        return when {
            browser is BrowserChoice.Default -> null
            browser is BrowserChoice.Unavailable -> ActionResult.Failure(browser.reason)
            !online -> ActionResult.Failure("No internet connection is available; no search was opened.")
            else ->
                open(launcher, WebTargets.searchUrl(params.getValue("query")), browser)
                    ?.let { ActionResult.Failure(it) }
                    ?: ActionResult.UserActionRequired(
                        "Search opened. Review the results there; EQO has not retrieved or verified them.",
                    )
        }
    }

    /** A web address or a search phrase, normalized by the same rules the planner uses. */
    fun urlFor(raw: String): String? =
        when (val target = WebTargets.resolve(raw)) {
            is WebTargets.Target.Site -> target.url
            is WebTargets.Target.Search -> WebTargets.searchUrl(target.query)
            WebTargets.Target.Refused -> null
        }

    private fun installed(
        packageName: String,
        resolver: LaunchableAppResolver,
        reason: String,
    ): BrowserChoice =
        if (resolver.resolvePackage(packageName) != null) {
            BrowserChoice.Package(packageName)
        } else {
            BrowserChoice.Unavailable(reason)
        }

    private fun byLabel(
        name: String,
        resolver: LaunchableAppResolver,
    ): BrowserChoice =
        resolver.clearWinner(resolver.candidates(name))?.let { BrowserChoice.Package(it.packageName) }
            ?: BrowserChoice.Unavailable(UNAVAILABLE)
}
