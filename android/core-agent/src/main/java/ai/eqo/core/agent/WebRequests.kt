// Origin: EQO direct browser actions; pure phrase grammar, no I/O, no persistence, no logging.
package ai.eqo.core.agent

import java.util.Locale

/** A website or web search the person asked for. [browser] is null when no browser was named. */
data class WebRequest(
    /** True: open [text] as a site. False: search the web for [text]. */
    val open: Boolean,
    val text: String,
    val browser: String?,
)

/** Answers whether a spoken phrase names a browser app and which one ("Chrome", "the browser" -> its label). */
fun interface BrowserLookup {
    fun label(phrase: String): String?
}

/**
 * Recognises the plain phrasings of "open a website" and "search the web", with or without a named browser.
 *
 * Only the person's own first line is read. It returns null for anything it does not clearly understand, so the
 * request goes on to the model as before. Nothing here opens, plans or approves anything.
 */
object WebRequests {
    private const val OPEN_VERBS = "open|launch|go to|goto|visit|browse to|browse|navigate to|take me to|head to|load"
    private const val SEARCH_VERBS = "search for|search|look up|lookup|google|bing|find"
    private const val PREPS = "in|on|with|using|via|through"
    private const val MAX_LINE = 400
    private const val TARGET_GROUP = 3
    private const val QUOTES = "\"'‘’“”"
    private val WEB_VERBS = setOf("google", "bing")
    private val NOISE = Regex("[,\\s]*\\bplease\$", RegexOption.IGNORE_CASE)
    private val POLITE =
        Regex(
            "^(?:(?:please|pls|kindly|hey|ok|okay)[,\\s]+|(?:can|could|would|will) you (?:please |just )*|" +
                "i (?:want|need|would like|'d like) to |let'?s )+",
            RegexOption.IGNORE_CASE,
        )
    private val SEARCH_IN_BROWSER =
        Regex(
            "^(?:search|look up|lookup|find)\\s+(?:in|on|using|with|via)\\s+(.+?)\\s+for\\s+(.+)\$",
            RegexOption.IGNORE_CASE,
        )
    private val WEB_IN_BROWSER =
        Regex(
            "^(?:search|look up|lookup|google)\\s+(?:on\\s+)?(?:the\\s+)?(?:web|internet|net|online)\\s+" +
                "(?:in|on|using|with|via|through)\\s+(.+?)\\s+(?:for|about)\\s+(.+)\$",
            RegexOption.IGNORE_CASE,
        )
    private val BROWSER_THEN =
        Regex(
            "^(?:open|launch|start)\\s+(.+?)\\s+(?:and then|and|then)\\s+($SEARCH_VERBS|$OPEN_VERBS)\\s+(.+)\$",
            RegexOption.IGNORE_CASE,
        )
    private val LEADING_BROWSER =
        Regex("^(?:in|on|using|with|via)\\s+(.+?),?\\s+($SEARCH_VERBS|$OPEN_VERBS)\\s+(.+)\$", RegexOption.IGNORE_CASE)
    private val OPEN_SITE =
        Regex(
            "^(?:$OPEN_VERBS)\\s+(?:the\\s+)?(?:(?:website|web site|site|web ?page|page|url|link)\\s+)?" +
                "(\\S+)(?:\\s+(?:$PREPS)\\s+(.+))?\$",
            RegexOption.IGNORE_CASE,
        )
    private val WEB_SEARCH =
        Regex(
            "^(?:search|look up|lookup|google)\\s+(?:on\\s+)?(?:the\\s+)?(?:web|internet|net|online)\\s+" +
                "(?:for\\s+|about\\s+)?(.+)\$",
            RegexOption.IGNORE_CASE,
        )
    private val PLAIN_SEARCH = Regex("^($SEARCH_VERBS)\\s+(.+)\$", RegexOption.IGNORE_CASE)
    private val TRAILING_PREP = Regex("\\s(?:$PREPS)\\s", RegexOption.IGNORE_CASE)
    private val WEB_TAIL = Regex("\\s(?:on|over|via|using)\\s+the\\s+(?:web|internet|net)\$", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("\\s+")

    fun parse(
        line: String,
        lookup: BrowserLookup,
    ): WebRequest? {
        val text = clean(line.take(MAX_LINE))
        return listOf(
            { browserFirst(SEARCH_IN_BROWSER, 1 to 2, text, lookup) },
            { browserFirst(WEB_IN_BROWSER, 1 to 2, text, lookup) },
            { browserFirst(BROWSER_THEN, 1 to TARGET_GROUP, text, lookup) },
            { browserFirst(LEADING_BROWSER, 1 to TARGET_GROUP, text, lookup) },
            { openSite(text, lookup) },
            { webSearch(text, lookup) },
            { plainSearch(text, lookup) },
        ).firstNotNullOfOrNull { it() }
    }

    private fun clean(line: String): String =
        POLITE.replace(NOISE.replace(line.trim().replace(SPACES, " ").trimEnd('.', '!', '?', ',', ' '), ""), "").trim()

    // The browser comes first in the phrase: "search in Chrome for cats", "open Chrome and then search cats",
    // "in Chrome search cats", "search the web in Chrome for cats". Group numbers say where each part sits.
    private fun browserFirst(
        pattern: Regex,
        parts: Pair<Int, Int>,
        text: String,
        lookup: BrowserLookup,
    ): WebRequest? =
        pattern.matchEntire(text)?.let { match ->
            lookup.label(match.groupValues[parts.first])?.let { request(match.groupValues[parts.second], it) }
        }

    // "open wikipedia.org", "go to wikipedia.org in Chrome", "open wikipedia in Chrome" (a bare word is searched).
    private fun openSite(
        text: String,
        lookup: BrowserLookup,
    ): WebRequest? {
        val match = OPEN_SITE.matchEntire(text) ?: return null
        val token = match.groupValues[1].trim { it in QUOTES }
        val tail = match.groupValues[2]
        val browser = tail.takeIf { it.isNotEmpty() }?.let(lookup::label)
        val named = tail.isEmpty() || browser != null
        return if (named && (browser != null || WebTargets.looksLikeSite(token))) request(token, browser) else null
    }

    // "search the web for cats", "search the internet for cats in Chrome".
    private fun webSearch(
        text: String,
        lookup: BrowserLookup,
    ): WebRequest? =
        WEB_SEARCH.matchEntire(text)?.let { match ->
            val (query, browser) = splitBrowser(match.groupValues[1], lookup)
            request(query, browser)
        }

    // "search cats in Chrome", "google cats". A plain "search cats" is left to the model.
    private fun plainSearch(
        text: String,
        lookup: BrowserLookup,
    ): WebRequest? {
        val match = PLAIN_SEARCH.matchEntire(text) ?: return null
        val verb = match.groupValues[1].lowercase(Locale.ROOT)
        val webTail = WEB_TAIL.find(match.groupValues[2])
        val body = if (webTail == null) match.groupValues[2] else match.groupValues[2].substring(0, webTail.range.first)
        val (query, browser) = splitBrowser(body, lookup)
        val web = webTail != null || verb in WEB_VERBS
        return if (browser != null || web) request(query, browser) else null
    }

    // A browser phrase is only taken from the very end ("search flights in Mumbai on Chrome").
    private fun splitBrowser(
        query: String,
        lookup: BrowserLookup,
    ): Pair<String, String?> {
        val last = TRAILING_PREP.findAll(query).lastOrNull() ?: return query to null
        val head = query.substring(0, last.range.first).trim()
        val browser = lookup.label(query.substring(last.range.last + 1).trim())
        return if (browser != null && head.isNotEmpty()) head to browser else query to null
    }

    private fun request(
        target: String,
        browser: String?,
    ): WebRequest? {
        val text = target.trim().trim { it in QUOTES }.trim()
        val site = ' ' !in text && WebTargets.looksLikeSite(text)
        return if (text.isEmpty()) null else WebRequest(open = site, text = text, browser = browser)
    }
}

/**
 * Turns a recognised web request into an understood intent: the browser named, the person's chosen "the browser",
 * or the remembered browser. Pure; installed labels and the role memory are the only inputs.
 */
internal class WebIntents(
    private val installedApps: List<String>,
    // role key -> installed label the person chose to always use for that role (null when none or not installed).
    private val roleMemory: (String) -> String?,
) {
    // "open wikipedia.org in Chrome", "search cats in Brave", "search the web for cats": direct web steps.
    fun from(
        request: String,
        role: RoleDecision,
    ): TaskIntent? =
        WebRequests.parse(request.lines().first()) { browserLabel(it, role) }?.let { parsed ->
            val browser = parsed.browser ?: roleMemory(AppRoles.BROWSER.key)?.takeIf { it in installedApps }
            val where = browser?.let { " in $it" }.orEmpty()
            val site = (WebTargets.resolve(parsed.text) as? WebTargets.Target.Site)?.url
            if (parsed.open && site != null) {
                val goal = "Open ${WebTargets.display(site)}$where"
                TaskIntent(goal, browser, "open_url", mapOf("url" to site), confidence = 1.0)
            } else {
                val query = parsed.text.take(WebTargets.MAX_QUERY)
                val goal = "Search the web for ${query.take(MAX_GOAL_QUERY)}$where"
                TaskIntent(goal, browser, "search", mapOf("query" to query), confidence = 1.0)
            }
        }

    // The browser a spoken phrase names: an installed browser, the person's chosen "the browser", or a known
    // browser that is not installed (its name is kept so the usual missing-app flow can say so).
    private fun browserLabel(
        phrase: String,
        role: RoleDecision,
    ): String? {
        val named = AppRoles.withoutRoleWords(phrase)
        return if (named.isEmpty()) {
            (role as? RoleDecision.Use)?.label?.takeIf { AppRoles.namesRole(phrase) }
        } else {
            InstalledAppResolver.resolve(named, installedApps)?.takeIf { it.isBrowser() }
                ?: named.takeIf { it in RoleMembers.names(AppRoles.BROWSER) }?.let(::titleCase)
        }
    }

    private fun String.isBrowser() = AppRoles.membersOf(AppRoles.BROWSER, listOf(this)).isNotEmpty()

    private fun titleCase(text: String): String {
        val words = text.split(' ')
        return words.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }

    private companion object {
        const val MAX_GOAL_QUERY = 120
    }
}
