// Origin: EQO local app-role guard; pure, no I/O, no persistence, no logging.
package ai.eqo.core.agent

/**
 * A generic role a request can name instead of an app (for example "the browser").
 *
 * [members] are the app names that fill the role, most common first. [generic] members only restate the role
 * and never count as naming a specific app. [editions] are label words that may follow a member name
 * ("Chrome Beta"); any other suffix ("Chrome Remote Desktop") is a different app.
 */
data class AppRole(
    val key: String,
    val noun: String,
    val words: Set<String>,
    val members: List<String>,
    val generic: Set<String> = emptySet(),
    val editions: Set<String> = emptySet(),
    val lookalikes: Set<String> = emptySet(),
)

/** What the role guard decided for one request. */
sealed interface RoleDecision {
    /** No role is named, a specific app is named, or no installed app fills the role: existing behaviour. */
    data object NoRole : RoleDecision

    /** Exactly one app is meant: the only installed member, a remembered answer or the person's answer. */
    data class Use(
        val role: AppRole,
        val label: String,
    ) : RoleDecision

    /** Several installed apps fit the role and the person has not chosen: ask, never guess. */
    data class Ask(
        val role: AppRole,
        val options: List<String>,
    ) : RoleDecision
}

/** Tokens of a request: lower-case words outside quotes, with typo-tolerant role words. */
internal object RoleWords {
    private const val MIN_TYPO_LENGTH = 6
    private const val LONG_TYPO_LENGTH = 7
    private const val PREFIX_LENGTH = 3
    val ARTICLES = setOf("the", "a", "an", "my", "your", "our", "web", "app")

    // Words that can sit right before a role word without being an app name ("open browser", "using browser").
    val VERBS = setOf("open", "launch", "start", "run", "use", "using", "close", "quit", "stop", "show", "via")
    private val QUOTED = Regex("\"[^\"]*\"|[\\u201C][^\\u201D]*[\\u201D]")

    fun of(text: String): List<String> =
        InstalledAppResolver
            .clean(text.replace(QUOTED, " "))
            .split(' ')
            .filter { it.isNotEmpty() }

    // "browsr" and "brower" are the role word; "browse", "browsed" and "browses" are not.
    fun isRoleWord(
        token: String,
        role: AppRole,
    ): Boolean {
        val allowedEdits = if (token.length >= LONG_TYPO_LENGTH) 2 else 1
        val typo =
            token.length >= MIN_TYPO_LENGTH &&
                token.take(PREFIX_LENGTH) == role.noun.take(PREFIX_LENGTH) &&
                InstalledAppResolver.editDistance(token, role.noun) <= allowedEdits
        return token in role.words || (typo && token !in role.lookalikes)
    }
}

/** Which installed labels fill a role: table order, "Chrome Beta" yes, "Chrome Remote Desktop" no. */
internal object RoleMembers {
    private const val MIN_BRAND_LENGTH = 3

    /** Installed labels that fill [role], in table order (then by label) so options are deterministic. */
    fun of(
        role: AppRole,
        labels: List<String>,
    ): List<String> =
        labels
            .mapNotNull { label -> indexOf(role, label)?.let { it to label } }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.second }

    // Each specific member's name and its first word ("opera mini" and "opera"), never the generic ones.
    fun names(role: AppRole): List<String> =
        role.members
            .map { InstalledAppResolver.clean(it) }
            .filter { it !in role.generic }
            .flatMap { name -> listOfNotNull(name, name.substringBefore(' ').takeIf { it.length >= MIN_BRAND_LENGTH }) }

    private fun indexOf(
        role: AppRole,
        label: String,
    ): Int? {
        val spoken = InstalledAppResolver.normalize(label)
        val match =
            role.members
                .filter { member -> fills(InstalledAppResolver.clean(member), spoken, role) }
                .maxByOrNull { it.length }
        return match?.let { role.members.indexOf(it) }
    }

    // "Chrome" and "Chrome Beta" fill the role; "Chrome Remote Desktop" is another app that shares the first word.
    private fun fills(
        member: String,
        label: String,
        role: AppRole,
    ): Boolean {
        val rest = label.removePrefix("$member ").split(' ')
        val extended =
            label.startsWith("$member ") && (rest.any { it in role.words } || rest.all { it in role.editions })
        return label == member || extended
    }
}

/** Small data-driven role table; add a role by adding an [AppRole] to [ALL]. */
object AppRoles {
    const val MAX_OPTIONS = 4
    private const val CLARIFICATION = "User clarification"
    private val OPEN_REQUEST = Regex("^(?:open|launch|start)\\s+(.+)\$", RegexOption.IGNORE_CASE)

    val BROWSER =
        AppRole(
            key = "browser",
            noun = "browser",
            words = setOf("browser", "browsers"),
            members =
                listOf(
                    "Chrome",
                    "Brave",
                    "Firefox",
                    "Opera",
                    "Opera Mini",
                    "Microsoft Edge",
                    "Edge",
                    "Samsung Internet",
                    "Vivaldi",
                    "DuckDuckGo",
                    "Kiwi",
                    "Tor",
                    "HeyTap Browser",
                    "Oppo Browser",
                    "Mi Browser",
                    "Web Browser",
                    "Browser",
                ),
            generic = setOf("web browser", "browser"),
            editions =
                setOf("beta", "dev", "canary", "nightly", "lite", "mini", "focus", "go", "private", "fast", "secure"),
            lookalikes = setOf("browse", "browsed", "browses"),
        )

    val ALL: List<AppRole> = listOf(BROWSER)

    fun byKey(key: String): AppRole? = ALL.find { it.key == key }

    /**
     * Decides for [request]. Only its first line is the person's own words; later "User clarification N: ..." lines
     * are answers. [remembered] maps a role key to the installed label the person chose to always use.
     */
    fun decide(
        request: String,
        installed: List<String>,
        remembered: (String) -> String?,
    ): RoleDecision {
        val labels = installed.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val lines = request.lines()
        val spoken = RoleWords.of(lines.first())
        val role = ALL.firstOrNull { role -> spoken.any { RoleWords.isRoleWord(it, role) } }
        return if (role == null || namesApp(spoken, role, labels)) {
            RoleDecision.NoRole
        } else {
            decideFor(role, lines.drop(1), labels, remembered)
        }
    }

    /** True when the whole request is "open the browser"-like: an open verb plus only articles and role words. */
    fun isBareOpen(request: String): Boolean {
        val target =
            OPEN_REQUEST
                .matchEntire(request.lines().first().trim())
                ?.groupValues
                ?.get(1)
                ?: return false
        return namesRole(target) && withoutRoleWords(target).isEmpty()
    }

    /** True when [text] uses a role word ("browser", "browsr") anywhere outside quotes. */
    fun namesRole(text: String): Boolean = RoleWords.of(text).any { t -> ALL.any { RoleWords.isRoleWord(t, it) } }

    /** [text] without articles and role words: "chrome browser" becomes "chrome", "the browser" becomes "". */
    fun withoutRoleWords(text: String): String =
        RoleWords
            .of(text)
            .filterNot { token -> token in RoleWords.ARTICLES || ALL.any { RoleWords.isRoleWord(token, it) } }
            .joinToString(" ")

    /** Installed labels that fill [role], in table order so options are deterministic. */
    fun membersOf(
        role: AppRole,
        labels: List<String>,
    ): List<String> = RoleMembers.of(role, labels)

    private fun decideFor(
        role: AppRole,
        clarifications: List<String>,
        labels: List<String>,
        remembered: (String) -> String?,
    ): RoleDecision {
        val members = RoleMembers.of(role, labels)
        val picked = answered(clarifications, labels) ?: remembered(role.key)?.takeIf { it in labels }
        return when {
            picked != null -> RoleDecision.Use(role, picked)
            members.isEmpty() -> RoleDecision.NoRole
            members.size == 1 -> RoleDecision.Use(role, members.single())
            else -> RoleDecision.Ask(role, members.take(MAX_OPTIONS))
        }
    }

    // The latest clarification that names exactly one installed app is the person's answer to the role question.
    private fun answered(
        clarifications: List<String>,
        labels: List<String>,
    ): String? =
        clarifications
            .lastOrNull { it.startsWith(CLARIFICATION) }
            ?.substringAfter(':', "")
            ?.let { InstalledAppResolver.resolve(it, labels) }

    // A specific app is named: a member's name or brand, an installed member label, or "<installed app> browser".
    private fun namesApp(
        spoken: List<String>,
        role: AppRole,
        labels: List<String>,
    ): Boolean {
        val text = " ${spoken.joinToString(" ")} "
        val installedNames = RoleMembers.of(role, labels).map { InstalledAppResolver.clean(it) }
        val phrases = RoleMembers.names(role) + installedNames.filter { it !in role.generic }
        val before = spoken.indices.filter { it > 0 && RoleWords.isRoleWord(spoken[it], role) }.map { spoken[it - 1] }
        return phrases.any { " $it " in text } || before.any { isInstalledApp(it, role, labels) }
    }

    private fun isInstalledApp(
        token: String,
        role: AppRole,
        labels: List<String>,
    ): Boolean {
        val filler = token in RoleWords.ARTICLES || token in RoleWords.VERBS || RoleWords.isRoleWord(token, role)
        return !filler && InstalledAppResolver.resolve(token, labels) != null
    }
}
