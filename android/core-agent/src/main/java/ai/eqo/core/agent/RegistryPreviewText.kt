package ai.eqo.core.agent

/** Display-only catalog wording. These strings must never be used as execution parameters. */
internal object RegistryPreviewText {
    private val slot = Regex("\\{([a-zA-Z_]+)\\}")
    private const val SEND_WARNING = "; EQO attempts to press Send; delivery is not verified"
    private const val TAP_WARNING = " in the current app (may submit or send)"

    private val system =
        mapOf(
            "OPEN_APP" to "open {appName}",
            "CREATE_NOTE" to "save a note with text {body}",
            "TAKE_SCREENSHOT" to "save a screenshot of the current app (protected screens are refused)",
            "LOCK_SCREEN" to "lock the phone screen",
            "SET_BRIGHTNESS" to "set screen brightness to {level} percent",
            "SET_VOLUME" to "set device volume to {level} percent",
            "RESTART_DEVICE" to "restart the device",
            "SET_WALLPAPER" to "set the wallpaper using image {imageUrl}",
            "RECORD_SCREEN" to "record the screen for {duration} seconds",
            "INSTALL_APP" to "open the Play Store to install {appName}",
            "GET_SYSTEM_INFO" to "read battery, storage and memory information",
            "SET_RINGER_MODE" to "set the phone's ringing mode to {mode}",
            "CLOSE_APP" to "close the current app",
            "ANALYZE_SCREENSHOT" to "analyze a screenshot of the current screen, asking {question}",
            "CLEAR_CLIPBOARD" to "clear the clipboard",
            "COPY_TO_CLIPBOARD" to "copy {text} to the clipboard",
            "GET_CLIPBOARD" to "read the clipboard",
            "OPEN_BROWSER" to "open the default web browser",
            "OPEN_URL" to "open website {url}",
            "ENABLE_PRIVATE_MODE" to "open a private browsing window in Chrome",
            "CLEAR_BROWSER_DATA" to "open browser settings to clear browsing history, cache and cookies",
        )
    private val toggles =
        mapOf(
            "TOGGLE_FLASHLIGHT" to "the flashlight",
            "TOGGLE_WIFI" to "Wi-Fi",
            "TOGGLE_BLUETOOTH" to "Bluetooth",
            "TOGGLE_MOBILE_DATA" to "mobile data",
            "TOGGLE_HOTSPOT" to "the personal hotspot",
            "TOGGLE_DND" to "Do Not Disturb",
        )
    private val communication =
        mapOf(
            "MAKE_CALL" to "place a phone call to {contact} (this rings a real person)",
            "WHATSAPP_CALL" to
                "place a WhatsApp voice call to {contact} (this rings a real person; no automatic retry)",
            "SEND_WHATSAPP" to "send a WhatsApp message to {contact} saying {message}$SEND_WARNING",
            "SEND_WHATSAPP_GROUP" to
                "open WhatsApp for group {groupName}; you find the group, enter {message} and press Send",
            "SEND_TELEGRAM" to "send a Telegram message to {contact} saying {message}$SEND_WARNING",
            // The executors attempt Send despite the catalog's older draft-only descriptions.
            "SEND_SMS" to "send a text message to {contact} saying {message}$SEND_WARNING",
            "SEND_EMAIL" to "send an email to {to}, with subject {subject} and body {body}$SEND_WARNING",
            "OPEN_TELEGRAM" to "open Telegram",
            "SHARE_CONTACT" to "share the saved phone number of {contact} with {to} using {via}$SEND_WARNING",
            "SHARE_LOCATION" to
                "share your current or last-known location as a Google Maps link with {to} using {via}$SEND_WARNING",
            "MAKE_VIDEO_CALL" to "open a calling app for a video call to {contact} (may ring a real person)",
            "READ_MESSAGES" to "read recent messages",
            "READ_EMAILS" to "read recent emails",
        )
    private val productivity =
        mapOf(
            "SET_ALARM" to "set an alarm for {time}",
            "SET_REMINDER" to "set a reminder for {datetime} saying {text}",
            "SET_TIMER" to "start a countdown timer for {duration}",
            "CREATE_CALENDAR_EVENT" to "create a calendar event titled {title} on {date}",
            "ADD_NOTE" to "create a note with text {content}",
            "LIST_CALENDAR_TODAY" to "list today's calendar events",
            "LIST_CALENDAR_WEEK" to "list this week's calendar events",
            "CREATE_TASK" to "create a to-do item titled {title}",
            "READ_NOTES" to "read saved notes",
            "READ_AND_REMEMBER_SCREEN" to "read the current screen and save important information",
            "RECALL_MEMORY" to "search saved notes and memories for {query}",
            "QUERY_KNOWLEDGE_GRAPH" to "search learned preferences, facts and routines",
            "UPDATE_PREFERENCE" to "save the preference named {key} with value {value}",
            "SAVE_SENSITIVE_INFO" to "save an encrypted confidential record named {key} with protected text {secret}",
        )
    private val information =
        mapOf(
            "WEB_SEARCH" to "search the web for {query}",
            "GET_WEATHER" to "check the weather",
            "GET_NEWS" to "read the latest news",
            "CALCULATE" to "calculate {expression}",
            "TRANSLATE" to "translate {text} into language {to}",
            "DEFINE_WORD" to "look up the meaning of {word}",
            "CONVERT_UNITS" to "convert {value} from unit {from} to unit {to}",
            "CURRENCY_CONVERT" to "convert {amount} from currency {from} to currency {to}",
            "CHECK_STOCK" to "check the stock price for {symbol}",
            "SUMMARIZE_URL" to "summarize the page at {url}",
            "FACT_CHECK" to "check whether this claim is true: {claim}",
            "GET_DIRECTIONS" to "get directions to {to}",
            "CHECK_TRAFFIC" to "check traffic on route {route}",
            "CHECK_FLIGHT" to "check the status of flight {flightNumber}",
            "TRACK_DELIVERY" to "track the package with tracking number {trackingNumber}",
        )
    private val media =
        mapOf(
            "PLAY_MUSIC" to "play music matching {query}",
            "PAUSE_MUSIC" to "pause the music",
            "NEXT_TRACK" to "skip to the next track",
            "PREV_TRACK" to "go to the previous track",
            "SET_VOLUME_MUSIC" to "set music volume to {level} percent",
            "PLAY_YOUTUBE" to "play a YouTube video matching {query}",
            "TAKE_PHOTO" to "take a photo",
            "RECORD_VIDEO" to "record a video",
            "TAKE_PHOTO_BACKGROUND" to "take a photo silently in the background",
        )
    private val purchases =
        mapOf(
            "BOOK_UBER" to "request an Uber ride to {destination}",
            "BOOK_OLA" to "request an Ola ride to {destination}",
            "ORDER_FOOD" to "order food: {items}",
            "ORDER_GROCERY" to "order groceries: {items}",
            "SEARCH_AMAZON" to "search Amazon for {query}",
            "SEARCH_FLIPKART" to "search Flipkart for {query}",
            "ADD_TO_CART" to "add {product} to a shopping cart",
            "SMART_HOME" to "control smart home device {device}, requesting {action}",
            "TOGGLE_LIGHT" to "change the smart light in room {room}",
            "SET_THERMOSTAT" to "set the thermostat temperature to {temperature}",
            "LOCK_DOOR" to "lock the smart door at {location}",
            "PAY_UPI" to "make a UPI payment of {amount} to {to}",
            "CHECK_BALANCE" to "check the account balance",
            "SPLIT_BILL" to "split a bill of {totalAmount} among {people}",
        )
    private val automation =
        mapOf(
            "RUN_MACRO" to "run the saved action sequence named {macroName}",
            "CREATE_MACRO" to "create a saved action sequence named {name} with steps {steps}",
            "SCHEDULE_MACRO" to "schedule the saved action sequence {macroName} using schedule {cronExpression}",
            "DELETE_MACRO" to "delete the saved action sequence named {macroName}",
            "LIST_MACROS" to "list saved action sequences",
            "RUN_ROUTINE" to "run an automated routine",
            "GET_MORNING_BRIEFING" to "prepare a morning briefing of meetings, notifications and tasks",
            "DETECT_ROUTINES" to "look for recurring habits and app usage patterns",
            "APPROVE_ROUTINE" to "approve a detected routine and activate its automation",
            "CHAT" to "respond with {response} without taking a device action",
            "READ_NOTIFICATIONS" to "read saved notifications",
            "AUTO_REPLY_TOGGLE" to "set automatic message replies to {state}",
            "DISMISS_NOTIFICATION" to "remove saved notification history (not the live notification)",
        )
    private val files =
        mapOf(
            "LIST_FILES" to "list file names in a folder (read-only)",
            "FIND_FILES" to "find file names containing {query} (read-only; does not read file contents)",
            "READ_FILE" to "read the contents of file {filePath}",
            "WRITE_FILE" to "write {content} to file {filePath}",
            "DELETE_FILE" to "delete file {filePath}",
            "CREATE_DIRECTORY" to "create folder {path}",
            "COPY_FILE" to "copy file {sourcePath} to {destPath}",
            "MOVE_FILE" to "move file {sourcePath} to {destPath}",
            "ZIP_FILES" to "compress {sourcePath} into archive {zipFilePath}",
            "UNZIP_FILE" to "extract archive {zipFilePath} into folder {destDirPath}",
            "LIST_INSTALLED_APPS" to "list installed apps",
        )
    private val interaction =
        mapOf(
            "CLICK_TEXT" to "tap {text}$TAP_WARNING",
            "CLICK_ID" to "tap the control with screen identifier {viewId}$TAP_WARNING",
            "TYPE_TEXT" to "type {content} into {searchText} in the current app",
            "TYPE_ID" to "type {content} into the field with screen identifier {viewId} in the current app",
            "SCROLL" to "scroll in direction {direction} in the current app",
            "GET_SCREEN_TEXT" to "read the visible text in the current app",
            "CLICK_COORDINATES" to "tap at horizontal position {x}, vertical position {y}$TAP_WARNING",
            "PRESS_BACK" to "press Back in the current app (may save changes automatically)",
            "PRESS_ENTER" to "press Enter$TAP_WARNING",
            "WAIT" to "pause for {durationMs} milliseconds",
        )
    private val social =
        mapOf(
            "SOCIAL_SHOW_PERFORMANCE" to "show social media reach, engagement and follower counts",
            "SOCIAL_CREATE_POST" to "draft a social media post about {topic}",
            "SOCIAL_SCHEDULE_POST" to "schedule drafted post {postId} for future publication",
            "SOCIAL_CHECK_INBOX" to "check social media mentions and unread interactions",
            "SOCIAL_DRAFT_REPLIES" to "draft replies to unanswered social media comments",
            "SOCIAL_SHOW_TOP_CONTENT" to "show the best-performing social media posts",
            "SOCIAL_CREATE_REPORT" to "create a weekly social media report",
            "SOCIAL_CREATE_CAMPAIGN" to "plan a multi-platform campaign with scheduled post drafts",
        )
    private val templates =
        system + communication + productivity + information + media + purchases +
            automation + files + interaction + social

    // Only these explicit labels may introduce additional catalog parameters; never derive labels from raw keys.
    private val labels =
        mapOf(
            "appName" to "app to use",
            "app" to "app to use",
            "title" to "title to use",
            "state" to "desired setting",
            "type" to "volume channel",
            "level" to "percentage level",
            "imageUrl" to "image address or path",
            "duration" to "duration to use",
            "mode" to "travel mode",
            "question" to "question to analyze",
            "text" to "text to use",
            "url" to "website address",
            "browser" to "browser to use",
            "contact" to "person or chat to open",
            "video" to "video calling enabled",
            "message" to "message text",
            "draftOnly" to "leave sending to you",
            "attachment" to "files to attach",
            "groupName" to "recipient group",
            "to" to "recipient or destination",
            "subject" to "email subject",
            "body" to "full text",
            "cc" to "copied email recipients",
            "via" to "sharing service",
            "count" to "number of items to read",
            "folder" to "folder to use",
            "filter" to "selection criteria",
            "time" to "time of day",
            "label" to "display name",
            "repeat" to "repetition schedule",
            "datetime" to "reminder date and time",
            "date" to "event date",
            "location" to "place to use",
            "attendees" to "people to invite",
            "content" to "text to use",
            "tags" to "note topics",
            "dueDate" to "due date",
            "priority" to "importance level",
            "list" to "task collection",
            "query" to "search terms",
            "topic" to "subject of interest",
            "save_as" to "destination for saved information",
            "category" to "information category",
            "tier" to "memory storage layer",
            "key" to "record name",
            "value" to "setting to use",
            "secret" to "protected text",
            "days" to "number of days",
            "sources" to "news providers",
            "expression" to "calculation",
            "from" to "starting point or source language",
            "word" to "word to define",
            "amount" to "sum of money",
            "symbol" to "stock ticker",
            "claim" to "statement to check",
            "destination" to "drop-off place",
            "pickup" to "pickup place",
            "rideType" to "ride option",
            "route" to "route to check",
            "flightNumber" to "flight number",
            "trackingNumber" to "tracking number",
            "courier" to "delivery service",
            "camera" to "camera to use",
            "items" to "items to order",
            "address" to "delivery address",
            "product" to "product to add",
            "device" to "smart home device",
            "action" to "requested operation",
            "room" to "room to control",
            "on" to "light enabled",
            "brightness" to "light intensity",
            "color" to "light colour",
            "temperature" to "target temperature",
            "note" to "payment memo",
            "totalAmount" to "bill total",
            "people" to "people sharing the bill",
            "description" to "bill details",
            "macroName" to "saved sequence name",
            "name" to "name to use",
            "steps" to "sequence instructions",
            "cronExpression" to "recurring schedule",
            "routineName" to "routine name",
            "routineId" to "routine identifier",
            "section" to "briefing section",
            "speak" to "read aloud",
            "lookbackDays" to "days of history to analyze",
            "filePath" to "file to use",
            "path" to "folder path",
            "sourcePath" to "source file or folder",
            "destPath" to "destination path",
            "zipFilePath" to "archive path",
            "destDirPath" to "extraction folder",
            "viewId" to "screen identifier",
            "searchText" to "field label",
            "direction" to "scroll direction",
            "x" to "horizontal position",
            "y" to "vertical position",
            "durationMs" to "pause length in milliseconds",
            "response" to "reply text",
            "notificationId" to "saved notification identifier",
            "platform" to "social media service",
            "tone" to "writing style",
            "postId" to "drafted post identifier",
            "hours" to "hours until publication",
            "objective" to "campaign goal",
        )

    fun describe(
        name: String,
        params: Map<String, String>,
    ): String {
        require(name != "ASK_USER") { "Questions must be handled before showing a task preview" }
        val definition = requireNotNull(ActionSchema.getAction(name)) { "Unsupported preview action" }
        require(params.keys.all { key -> definition.params.any { it.name == key } }) { "Unsupported preview parameter" }
        val template = templateFor(name, params)
        val used = slot.findAll(template).map { it.groupValues[1] }.toSet() + consumedControls(name)
        val sentence =
            slot.replace(template) { match ->
                val key = match.groupValues[1]
                val default =
                    definition.params
                        .firstOrNull { it.name == key }
                        ?.defaultValue
                        ?.toString()
                quote(params[key] ?: default ?: "not specified")
            }
        val details =
            definition.params.mapNotNull { param ->
                params[param.name]?.takeUnless { param.name in used }?.let { value ->
                    "${requireNotNull(labels[param.name]) { "Missing English preview label" }}: ${quote(value)}"
                }
            }
        return sentence + if (details.isEmpty()) "" else details.joinToString("; ", prefix = "; ")
    }

    private fun templateFor(
        name: String,
        params: Map<String, String>,
    ): String =
        when {
            name in toggles -> toggleTemplate(requireNotNull(toggles[name]), params["state"] ?: "toggle")
            name == "SEND_WHATSAPP" && params["draftOnly"] == "true" ->
                "draft a WhatsApp message to {contact} saying {message}; you press Send"
            name == "WHATSAPP_CALL" && params["video"] == "true" ->
                "place a WhatsApp video call to {contact} (this rings a real person; no automatic retry)"
            else -> requireNotNull(templates[name]) { "Missing catalog preview wording" }
        }

    private fun consumedControls(name: String): Set<String> =
        when {
            name in toggles -> setOf("state")
            name == "SEND_WHATSAPP" -> setOf("draftOnly")
            name == "WHATSAPP_CALL" -> setOf("video")
            else -> emptySet()
        }

    private fun toggleTemplate(
        target: String,
        state: String,
    ): String =
        when (state) {
            "on" -> "turn $target on"
            "off" -> "turn $target off"
            "toggle" -> "switch $target to the opposite setting"
            else -> "change $target to {state}"
        }

    private fun quote(value: String): String {
        val escaped = TaskDisplayText.escape(PlanOutputReferences.describe(value)).replace("\"", "\\\"")
        return "\"$escaped\""
    }
}
