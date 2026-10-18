// Origin: EQO t_2f7fbd22, static public app-control hints; no screen or user data.
package ai.eqo.core.agent

/** Extend by package. Hints are planning guidance, not authority to bypass approval or screen guards. */
internal object AppControlHints {
    val byPackage: Map<String, String> =
        linkedMapOf(
            "com.google.android.keep" to
                "Google Keep: every new note must use CREATE_NOTE appName=Google Keep, body=<supplied text>, " +
                "optional title=<supplied title>. For title-only requests use that text as body. " +
                "Never TYPE_TEXT into editable_title or edit_note_text: Keep saves those notes empty. " +
                "Search only: OPEN_APP, WAIT, CLICK_TEXT text=id:toolbar, " +
                "TYPE_TEXT searchText=id:search_actionbar_query_text content=<query>, PRESS_ENTER once. " +
                "Never type into toolbar or repeat query-field clicks/typing after PRESS_ENTER.",
            "com.google.android.gm" to
                "Gmail: Compose; fields To, Subject, Compose email; Send sends real mail. " +
                "Prefer SEND_EMAIL for a self-contained email request.",
            "com.google.android.apps.messaging" to
                "Google Messages: Start chat,New conversation; To; Text message; Send. " +
                "Prefer SEND_SMS for a self-contained message request.",
            "com.whatsapp" to
                "WhatsApp: CLICK_TEXT text=id:com.whatsapp:id/menuitem_search opens list search; " +
                "TYPE_TEXT searchText=id:com.whatsapp:id/search_src_text filters results without Enter. " +
                "If either exact ID is missing, stop; never type a search into a Message composer. " +
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
            "com.zeptoconsumerapp" to SHOPPING_HINT,
            "com.grofers.customerapp" to SHOPPING_HINT,
            "in.swiggy.android" to SHOPPING_HINT,
            "com.bigbasket.mobileapp" to SHOPPING_HINT,
            "in.amazon.mShop.android.shopping" to SHOPPING_HINT,
            "com.flipkart.android" to SHOPPING_HINT,
        )

    private const val SHOPPING_HINT =
        "Shopping: use the cart-only skill. Search, first Add/Add to cart, increase requested units, open Cart. " +
            "Missing exact controls stop. Never pay, confirm an order or address, or enter payment details."

    fun prompt(): String =
        buildString {
            appendLine("Public app hints (versions/languages can differ; do not guess missing controls):")
            byPackage.forEach { (packageName, hint) -> appendLine("$packageName: $hint") }
            appendLine("CLICK_TEXT text accepts ordered comma-separated alternatives.")
            appendLine("All exact text/content-description/view-id suffix matches precede partial label matches.")
            appendLine("CLICK_TEXT text and TYPE_TEXT searchText accept id:<view-id suffix> references.")
            appendLine("App search: navigate to search first and use a search-only id: target, never a generic input.")
            appendLine("For make/add/write/create a note in Keep, use CREATE_NOTE, never an editor UI chain.")
            appendLine("Use the requested title/body, never invent content.")
            appendLine("For title-only Keep requests, use the supplied title as CREATE_NOTE body; invent nothing.")
            appendLine("Missing controls stop with Needs you; do not plan repeated taps or bypass approvals.")
        }
}
