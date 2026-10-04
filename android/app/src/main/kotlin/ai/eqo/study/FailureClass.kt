/*
 * EQO (TASK-015, issue #20): recovery guidance per failure class.
 *
 * PRD §7.10 (REQ-REC-01..10) lists the fault classes and their required explicit
 * states; USER-FLOWS.md §13 (UF-R1..UF-R8) fixes the microcopy and the resume rule.
 * One class = one named state + one repair action + one resume rule; nothing collapses
 * into a generic error, and no class auto-resumes or auto-retries (REQ-TASK-07).
 */
package ai.eqo.study

/** The closed set of failure classes the study build knows how to recover from. */
enum class FailureClass(
    /** The state name shown to the user (PRD §7.10 wording). */
    val stateName: String,
    /** PRD requirement ID. */
    val requirementId: String,
    /** USER-FLOWS.md recovery row (UF-Rn / flow). */
    val userFlow: String,
    /** The repair guidance shown to the user (UF-12 microcopy; never empty). */
    val repair: String,
    /** What may happen after the repair (UF-12 resume rule; never empty). */
    val resumeRule: String,
) {
    MODEL_UNAUTHORIZED(
        stateName = "ModelError: Unauthorized",
        requirementId = "REQ-REC-01",
        userFlow = "UF-R1 / UF-11",
        repair = "The provider rejected the API key. Re-enter the key on the model setup screen.",
        resumeRule =
            "Task stays Interrupted until you resume; on resume EQO re-verifies the target, " +
                "content and current screen, then asks for a fresh approval for any sensitive action.",
    ),
    MODEL_RATE_LIMITED(
        stateName = "ModelError: RateLimited",
        requirementId = "REQ-REC-02",
        userFlow = "UF-R1 / UF-11",
        repair = "The provider is rate-limiting requests. A countdown is shown; wait for it, or stop the task.",
        resumeRule = "No automatic retry loop. Waiting or resuming is your call; nothing is retried without you.",
    ),
    MODEL_CREDIT(
        stateName = "ModelError: Credit",
        requirementId = "REQ-REC-03",
        userFlow = "UF-R1 / UF-11",
        repair =
            "The provider account has insufficient credit. Open the provider's billing page to add credit; " +
                "your key stays stored either way.",
        resumeRule = "Resume when credit is available; the task re-verifies before it continues.",
    ),
    MODEL_INCOMPATIBLE(
        stateName = "ModelError: IncompatibleModel",
        requirementId = "REQ-REC-04",
        userFlow = "UF-R1 / UF-11",
        repair = "The selected model cannot run this task. Pick another model; EQO re-checks its capabilities.",
        resumeRule = "Resume after the model picker; capabilities are re-checked, not assumed.",
    ),
    MODEL_NETWORK(
        stateName = "ModelError: Network",
        requirementId = "REQ-REC-05",
        userFlow = "UF-R1 / UF-11",
        repair = "The network request failed. Check the connection and tap Retry — retrying is always your action.",
        resumeRule = "Retry only when you initiate it; nothing retries on its own.",
    ),
    ADB_DISCONNECTED(
        stateName = "AdbDisconnected",
        requirementId = "REQ-REC-06",
        userFlow = "UF-R2",
        repair =
            "Your phone's wireless debugging connection dropped. Reconnect with the pairing steps — " +
                "Android may show a new pairing code.",
        resumeRule = "Fresh resume plus renewed approval for any pending sensitive step.",
    ),
    ADB_REVOKED(
        stateName = "AdbRevoked",
        requirementId = "REQ-REC-06",
        userFlow = "UF-R2",
        repair =
            "Android stopped trusting EQO's debugging key (usually after revoking debugging " +
                "authorizations). Pair again from the wireless debugging screen.",
        resumeRule = "Fresh resume plus renewed approval for any pending sensitive step.",
    ),
    ADB_AFTER_REBOOT(
        stateName = "AdbAfterReboot",
        requirementId = "REQ-REC-06",
        userFlow = "UF-R2",
        repair =
            "Ports and pairing state do not survive a reboot. Re-open wireless debugging and read the " +
                "new IP address and port.",
        resumeRule = "Fresh resume plus renewed approval for any pending sensitive step.",
    ),
    BINDER_DEAD(
        stateName = "BinderDead",
        requirementId = "REQ-REC-07",
        userFlow = "UF-R3",
        repair = "The helper stopped responding. EQO will restart its checks.",
        resumeRule = "Task is Interrupted; helper start, authorization and probes run again only when you resume.",
    ),
    HELPER_REVOKED(
        stateName = "HelperRevoked",
        requirementId = "REQ-REC-07",
        userFlow = "UF-R7",
        repair = "You turned off the helper authorization. EQO paused. Turn it back on when you want to continue.",
        resumeRule = "Renewed approval for sensitive steps after you resume.",
    ),
    HELPER_NEEDS_RESTART(
        stateName = "HelperNeedsRestart",
        requirementId = "REQ-REC-07",
        userFlow = "UF-R8",
        repair = "EQO was updated, so the helper needs a quick restart.",
        resumeRule = "Restart is user-initiated; nothing restarts behind your back.",
    ),
    A11Y_LOST(
        stateName = "A11yLost",
        requirementId = "REQ-REC-08",
        userFlow = "UF-R4",
        repair =
            "The accessibility service was turned off. Open Settings > Accessibility > EQO and turn it " +
                "back on (Android 13+ may require 'Allow restricted settings' first).",
        resumeRule = "No automatic resume. You re-enable the service and then explicitly resume.",
    ),
    DEVTOOLS_UNAVAILABLE(
        stateName = "DevToolsUnavailable",
        requirementId = "REQ-REC-09",
        userFlow = "UF-R5",
        repair = "Chrome's debugging endpoint went away (Chrome may have restarted). EQO will re-check.",
        resumeRule =
            "Consent is not re-required (scope unchanged); browser functions are re-probed and only " +
                "resume after your confirmation.",
    ),
    VIRTUAL_DISPLAY_FAILED(
        stateName = "VirtualDisplayFailed",
        requirementId = "REQ-REC-10",
        userFlow = "UF-R6",
        repair = "Background mode is unavailable here. EQO proposes running the app in the foreground instead.",
        resumeRule = "Approved foreground fallback or cancel — never a silent background continuation.",
    ),
    ;

    companion object {
        /** Every failure class the study build can name (closed set; a new fault must be added here). */
        fun all(): List<FailureClass> = entries

        /**
         * Maps a typed failure surface name (as reported by the modules) to its class.
         * Returns null for an unknown name so the caller must handle it explicitly —
         * an unknown fault never gets generic copy by default.
         */
        fun fromStateName(stateName: String): FailureClass? = entries.firstOrNull { it.stateName == stateName }

        /**
         * Maps the model layer's typed error codes (core-llm `LLMError.code`) onto the
         * recovery classes of PRD §7.10 REQ-REC-01..05. Unknown codes return null so the
         * caller must decide instead of getting silent generic copy.
         */
        fun forLlmErrorCode(code: String): FailureClass? =
            when (code) {
                "AUTH_MISSING", "AUTH_INVALID" -> MODEL_UNAUTHORIZED
                "RATE_LIMITED" -> MODEL_RATE_LIMITED
                "QUOTA_EXHAUSTED" -> MODEL_CREDIT
                "MODEL_UNAVAILABLE", "REQUEST_INVALID" -> MODEL_INCOMPATIBLE
                "NETWORK" -> MODEL_NETWORK
                else -> null
            }
    }
}
