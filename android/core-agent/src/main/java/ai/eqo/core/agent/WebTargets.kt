// Origin: EQO direct browser actions; pure, no I/O, no persistence, no logging.
package ai.eqo.core.agent

import java.net.URLEncoder
import java.util.Locale

/**
 * Turns what a person typed ("wikipedia.org", "www.x.com", "x.com/path", "cats") into a safe web target.
 *
 * Only http and https are ever produced. A script, file, intent or other scheme, an address with embedded
 * credentials and control characters are refused, never "repaired". A bare word is a search, not a host.
 */
object WebTargets {
    /** What a typed address or phrase means. */
    sealed interface Target {
        /** A normalized absolute http(s) address. */
        data class Site(
            val url: String,
        ) : Target

        /** Words, not an address: search for them. */
        data class Search(
            val query: String,
        ) : Target

        /** An unsafe or empty address: nothing is opened. */
        data object Refused : Target
    }

    const val SEARCH_BASE = "https://www.google.com/search"
    const val MAX_QUERY = 500
    private const val MAX_URL = 2000
    private const val MAX_PORT = 65535
    private const val MAX_DISPLAY = 80
    private const val IPV4_LABELS = 4
    private const val MAX_OCTET = 255
    private const val FORBIDDEN = "\"<>\\`{}|^"
    private val EXPLICIT = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://(.*)\$")
    private val ANY_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private val HOST_PORT = Regex("^[A-Za-z0-9.-]+:[0-9]+([/?#].*)?\$")

    // Free text "wikipedia.org" is only called a site when its last label is a well-known top-level domain,
    // so file names such as notes.txt or script.py are never taken for web pages.
    private val COMMON_TLDS =
        (
            "com org net edu gov mil int info biz io co ai app dev me tv xyz online site store shop tech blog " +
                "news wiki page cloud space live eu asia in uk us ca au de fr es it nl se no fi dk pl ru ua jp " +
                "cn kr hk sg my id ph th vn tw nz za ae sa tr br mx ar cl pe ch at be ie pt gr cz ro hu il pk " +
                "bd lk np"
        ).split(' ').toSet()

    private class Parsed(
        val scheme: String,
        val host: String,
        val port: String,
        val tail: String,
        val explicit: Boolean,
    ) {
        fun url() = "$scheme://$host$port$tail"
    }

    /** The address as an absolute http(s) URL, or null when it is not a safe, well-formed web address. */
    fun siteUrl(raw: String): String? = parse(raw)?.url()

    /** True for text that is clearly a web address in running speech: a scheme, "www." or a known domain ending. */
    fun looksLikeSite(raw: String): Boolean {
        val parsed = parse(raw.trim()) ?: return false
        val tld = parsed.host.substringAfterLast('.').lowercase(Locale.ROOT)
        val named = parsed.host.startsWith("www.", true) || tld in COMMON_TLDS
        return parsed.explicit || ('.' in parsed.host && named && !isNumericHost(parsed.host))
    }

    /** A typed address becomes a site, a bare word or phrase becomes a search, anything unsafe is refused. */
    fun resolve(raw: String): Target {
        val text = raw.trim()
        val url = siteUrl(text)
        return when {
            url != null -> Target.Site(url)
            text.isEmpty() || unsafe(text) -> Target.Refused
            else -> Target.Search(text.take(MAX_QUERY))
        }
    }

    fun searchUrl(query: String): String = "$SEARCH_BASE?q=" + URLEncoder.encode(query.trim().take(MAX_QUERY), "UTF-8")

    /** Short plain text for the plan preview: no scheme, no trailing slash. */
    fun display(url: String): String =
        url
            .substringAfter("://")
            .removeSuffix("/")
            .take(MAX_DISPLAY)

    private fun unsafe(text: String): Boolean {
        val oneWord = text.none { it.isWhitespace() }
        val scheme = ANY_SCHEME.containsMatchIn(text) && !HOST_PORT.matches(text)
        return text.any { it.isISOControl() } || (oneWord && (scheme || '@' in text))
    }

    // A single parse used by every public function, so "what is a site" has one definition.
    private fun parse(raw: String): Parsed? {
        val text = raw.trim()
        val explicit = EXPLICIT.matchEntire(text)
        val scheme = explicit?.groupValues?.get(1)?.lowercase(Locale.ROOT) ?: "https"
        val foreign = explicit == null && ANY_SCHEME.containsMatchIn(text) && !HOST_PORT.matches(text)
        val plain =
            text.isNotEmpty() &&
                text.length <= MAX_URL &&
                text.none { it.isWhitespace() || it.isISOControl() || it in FORBIDDEN }
        return if (plain && !foreign && scheme in setOf("http", "https")) {
            authority(scheme, explicit?.groupValues?.get(2) ?: text, explicit != null)
        } else {
            null
        }
    }

    private fun authority(
        scheme: String,
        rest: String,
        explicit: Boolean,
    ): Parsed? {
        val authority = rest.takeWhile { it !in "/?#" }
        val host = authority.substringBeforeLast(':')
        val port = if (':' in authority) authority.substringAfterLast(':') else ""
        val portOk = port.isEmpty() || port.toIntOrNull()?.let { it in 1..MAX_PORT } == true
        val ok = '@' !in authority && '[' !in authority && portOk && validHost(host)
        val shownPort = if (port.isEmpty()) "" else ":$port"
        return if (ok) Parsed(scheme, host, shownPort, rest.substring(authority.length), explicit) else null
    }

    private fun validHost(host: String): Boolean {
        val labels = host.split('.')
        val wellFormed =
            labels.all { label ->
                label.isNotEmpty() &&
                    !label.startsWith('-') &&
                    !label.endsWith('-') &&
                    label.all { it.isLetterOrDigit() || it == '-' }
            }
        val named = '.' in host || host.equals("localhost", true)
        val octets = labels.size == IPV4_LABELS && labels.all { it.toIntOrNull()?.let { n -> n <= MAX_OCTET } == true }
        return wellFormed && named && (!isNumericHost(host) || octets)
    }

    private fun isNumericHost(host: String): Boolean = host.split('.').all { label -> label.all { it.isDigit() } }
}
