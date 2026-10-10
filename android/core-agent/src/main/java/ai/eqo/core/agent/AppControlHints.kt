// Origin: EQO t_2f7fbd22, static public app-control hints; no screen or user data.
package ai.eqo.core.agent

/** Extend by package. Hints are planning guidance, not authority to bypass approval or screen guards. */
internal object AppControlHints {
    val byPackage: Map<String, String> =
        linkedMapOf(
            "com.google.android.keep" to
                "Google Keep: OPEN_APP appName=Google Keep, WAIT durationMs=3000; " +
                "CLICK_TEXT text=Create a note (content-description, speed_dial_create_close_button); " +
                "CLICK_TEXT text=id:new_note_button (unlabelled speed-dial text-note item); " +
                "TYPE_TEXT searchText=id:editable_title for the title, " +
                "searchText=id:edit_note_text for the supplied body; PRESS_BACK to autosave. " +
                "There is no Take a note bar. Never type into toolbar (Search Keep).",
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
            appendLine("CLICK_TEXT text and TYPE_TEXT searchText accept id:<view-id suffix> references.")
            appendLine("For add/write/create a note in Keep, follow the exact Keep flow above.")
            appendLine("If Create a note is missing, stop with Needs you; Keep is not on its home screen.")
            appendLine("Use the requested title/body, never invent content.")
            appendLine("For title-only Keep requests, omit body typing rather than inventing a body.")
            appendLine("Missing controls stop with Needs you; do not plan repeated taps or bypass approvals.")
        }
}
