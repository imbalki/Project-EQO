// Origin: imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536,
//   path: app/src/main/kotlin/ai/closepaw/browser/cdp/ChromeCdpTarget.kt
// TASK-010 (issue #15): extracted into :browser-cdp. Changes vs donor: package renamed to
// ai.eqo.browser.cdp; PageTarget now lives in DevtoolsHttpProtocol.kt (this module drops the
// shizuku/ sub-package where the donor defined it).
package ai.eqo.browser.cdp

object ChromeCdpTarget {
    private val INTERNAL_PREFIXES =
        listOf(
            "chrome://",
            "chrome-untrusted://",
            "devtools://",
            "chrome-extension://",
            "about:",
        )

    fun isRealPage(
        type: String,
        url: String,
    ): Boolean = type == "page" && INTERNAL_PREFIXES.none { url.startsWith(it) }

    fun firstRealPage(targets: List<PageTarget>): PageTarget? = targets.firstOrNull { isRealPage(it.type, it.url) }
}
