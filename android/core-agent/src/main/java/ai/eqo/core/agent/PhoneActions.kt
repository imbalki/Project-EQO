// Origin: EQO stage-one awareness of the registry's own phone actions; pure, no I/O, no logging.
package ai.eqo.core.agent

/**
 * The phone's own settings and hardware actions (flashlight, volume, Wi-Fi, alarms ...) that already exist in the
 * action registry. Stage one is told about them as data so such a request is never mistaken for "open an app" and
 * never asks "via which app?". Nothing here plans, approves or runs anything.
 */
object PhoneActions {
    /** One enabled registry action as stage one sees it: its name and a short description. Data, not instruction. */
    data class Hint(
        val name: String,
        val description: String,
    )

    /** Intent param naming the registry action a request maps to. Read by stage two as a plain hint. */
    const val PARAM = "registry_action"
    private const val MAX_DESCRIPTION = 70
    private const val MAX_WORDS = 12
    private val timeQuestions =
        setOf("what time is it", "what s the time", "what is the time", "what is the current time", "tell me the time")

    // Stage one already has dedicated skills for these, and the low-level screen controls are never a user goal.
    private val NOT_PHONE_ACTIONS =
        setOf(
            "OPEN_APP",
            "OPEN_URL",
            "OPEN_BROWSER",
            "WEB_SEARCH",
            "CREATE_NOTE",
            "CALCULATE",
            "SEND_SMS",
            "SEND_WHATSAPP",
            "SEND_TELEGRAM",
            "SEND_EMAIL",
            "MAKE_CALL",
            "WHATSAPP_CALL",
            "CLICK_TEXT",
            "CLICK_ID",
            "CLICK_COORDINATES",
            "TYPE_TEXT",
            "TYPE_ID",
            "SCROLL",
            "PRESS_BACK",
            "PRESS_ENTER",
            "WAIT",
            "GET_SCREEN_TEXT",
            "ASK_USER",
            "CHAT",
        )

    // Plain nouns that name a phone setting. Registry data stays the source of the action names and descriptions.
    private val NOUNS =
        listOf(
            "TOGGLE_FLASHLIGHT" to listOf("flashlight", "torch", "torchlight", "flash light"),
            "TOGGLE_WIFI" to listOf("wifi", "wi fi"),
            "TOGGLE_BLUETOOTH" to listOf("bluetooth"),
            "TOGGLE_HOTSPOT" to listOf("hotspot"),
            "TOGGLE_MOBILE_DATA" to listOf("mobile data", "cellular data"),
            "TOGGLE_DND" to listOf("do not disturb", "dnd"),
            "SET_RINGER_MODE" to listOf("silent mode", "vibrate mode", "ringer"),
            "SET_BRIGHTNESS" to listOf("brightness"),
            "SET_VOLUME" to listOf("volume"),
            "SET_ALARM" to listOf("alarm"),
            "SET_TIMER" to listOf("timer"),
        )

    // A command, not a question: "turn on the flashlight", "volume up", "set an alarm", "wifi off", "brightness 50".
    private val COMMAND_WORDS =
        (
            "turn switch set put enable disable toggle on off up down increase decrease raise lower mute unmute " +
                "max min maximum minimum start stop make change open activate deactivate louder quieter brighter " +
                "dim cancel"
        ).split(' ').toSet()

    // Words of another skill: those requests keep their own slots and go through the usual understanding.
    private val OTHER_SKILLS =
        (
            "message send text sms whatsapp telegram email mail call note notes remind reminder calculate " +
                "search google then and"
        ).split(' ').toSet()

    /** Enabled phone actions, by name, with a one-line description. Empty when nothing is enabled. */
    fun hints(enabled: Set<String>): List<Hint> =
        enabled
            .filter { it !in NOT_PHONE_ACTIONS }
            .sorted()
            .mapNotNull { name ->
                ActionSchema.getAction(name)?.let { Hint(it.name, oneLine(it.description)) }
            }

    /**
     * The one enabled phone action a short, plain command names ("turn on the flashlight"), or null. Compound
     * requests and requests that belong to another skill are never matched here; they go to the model, which
     * still sees [hints].
     */
    fun match(
        request: String,
        enabled: Set<String>,
    ): String? {
        if ("GET_CURRENT_TIME" in enabled && InstalledAppResolver.clean(request) in timeQuestions) {
            return "GET_CURRENT_TIME"
        }
        val words = InstalledAppResolver.clean(request.lines().first()).split(' ').filter { it.isNotEmpty() }
        val simple =
            words.isNotEmpty() &&
                words.size <= MAX_WORDS &&
                words.none { it in OTHER_SKILLS } &&
                '"' !in request &&
                (words.any { it in COMMAND_WORDS } || words.any { word -> word.any(Char::isDigit) })
        val text = " ${words.joinToString(" ")} "
        return NOUNS
            .takeIf { simple }
            ?.filter { (name, _) -> name in enabled }
            ?.filter { (_, nouns) -> nouns.any { " $it " in text } }
            ?.map { it.first }
            ?.singleOrNull()
    }

    private fun oneLine(description: String): String =
        description
            .replace(Regex("\\s+"), " ")
            .trim()
            .substringBefore(". ")
            .take(MAX_DESCRIPTION)
}
