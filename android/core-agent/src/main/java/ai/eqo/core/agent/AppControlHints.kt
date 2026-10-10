// Origin: EQO t_2f7fbd22, static public app-control hints; no screen or user data.
package ai.eqo.core.agent

/** Extend by package. Hints are planning guidance, not authority to bypass approval or screen guards. */
internal object AppControlHints {
    val byPackage: Map<String, String> =
        linkedMapOf(
            "com.google.android.keep" to
                "Google Keep: new note CLICK_TEXT text=Take a note,New text note; " +
                "TYPE_TEXT searchText=Title for the title, searchText=Note for the body; " +
                "PRESS_BACK to autosave. Do not invent a Save or New note button.",
            "com.google.android.gm" to
                "Gmail: Compose; fields To, Subject, Compose email; Send sends real mail. " +
                "Prefer SEND_EMAIL for a self-contained email request.",
            "com.google.android.apps.messaging" to
                "Google Messages: Start chat,New conversation; To; Text message; Send. " +
                "Prefer SEND_SMS for a self-contained message request.",
            "com.whatsapp" to
                "WhatsApp: Search content-description opens search; Search field. " +
                "A reopened app may be inside a chat, not the chat list; never guess a recipient. " +
                "Prefer SEND_WHATSAPP or WHATSAPP_CALL for self-contained requests.",
            "com.android.chrome" to
                "Chrome: Search or type web address,url_bar for the address bar; " +
                "TYPE_TEXT then PRESS_ENTER navigates. Prefer OPEN_URL for a known URL.",
            "com.google.android.calendar" to
                "Calendar: Create,Event; Title; Save commits the event. " +
                "Prefer CREATE_CALENDAR_EVENT for a self-contained event request.",
            "com.google.android.contacts" to
                "Contacts: Search contacts; Create contact; First name,Last name,Phone; Save.",
            "com.google.android.calculator" to
                "Calculator: Clear; digits 0 to 9; Add, Subtract, Multiply, Divide, Point, Equals. " +
                "Tap one key per step; CALCULATE gives an answer without driving this app.",
            "com.google.android.deskclock" to
                "Clock: Alarm tab; Add alarm content-description; choose time; OK confirms.",
        )

    fun prompt(): String =
        buildString {
            appendLine("Public app hints (versions/languages can differ; do not guess missing controls):")
            byPackage.forEach { (packageName, hint) -> appendLine("$packageName: $hint") }
            appendLine("CLICK_TEXT text accepts ordered comma-separated alternatives.")
            appendLine("All exact text/content-description/view-id suffix matches precede partial label matches.")
            appendLine("For a note in a named app: OPEN_APP, WAIT 3000, CLICK_TEXT entry alternatives,")
            appendLine("TYPE_TEXT Title, TYPE_TEXT Note, PRESS_BACK.")
            appendLine("Use the requested title/body, never invent content.")
            appendLine("For title-only Keep requests, omit body typing rather than inventing a body.")
            appendLine("Missing controls stop with Needs you; do not plan repeated taps or bypass approvals.")
        }
}
