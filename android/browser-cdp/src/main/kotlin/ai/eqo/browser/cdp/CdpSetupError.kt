/*
 * EQO (TASK-010, issue #15): typed setup errors for the Chrome DevTools (CDP) spike.
 *
 * Acceptance criterion: "Failures surface as typed setup errors". The setup orchestrator and
 * the DevTools endpoint parser MUST never return a generic error — each failure maps to one
 * actionable, distinct case so the caller (agent tool layer / UI) can compose accurate
 * guidance. `MalformedResponse` is shared with DevtoolsHttpProtocol (extracted), which raises
 * it on any malformed /json/version or /json/list payload.
 *
 * This class supersedes the donor's DevtoolsSetupError (imoonkey/closepaw) for EQO: the codes
 * are EQO-owned and cover the CDP spike's own setup phases (consent, debug-flag prep, Chrome
 * cold restart, verified devtools socket, cleanup) rather than the Shizuku bridge cases.
 */
package ai.eqo.browser.cdp

sealed class CdpSetupError(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** Setup attempted before the informed-consent screen was acknowledged (AC1). */
    object ConsentRequired : CdpSetupError(
        code = "consent_required",
        message =
            "Chrome CDP control needs explicit informed consent before any preparation. " +
                "Show the consent screen and wait for the user to accept.",
    )

    /** The chrome://flags unlock / command-line file could not be prepared (debug flag prep). */
    class DebugFlagPreparation(
        detail: String,
    ) : CdpSetupError(
            code = "debug_flag_preparation_failed",
            message = "Chrome's remote-debugging flag could not be prepared: $detail",
        )

    /** Chrome cold restart could not be completed, so the socket cannot be rebound. */
    class ChromeRestartFailed(
        detail: String,
    ) : CdpSetupError(
            code = "chrome_restart_failed",
            message =
                "Chrome cold restart did not complete, so the DevTools socket was not rebound: " +
                    detail,
        )

    /** Chrome is not running after the restart step. */
    object ChromeNotRunning : CdpSetupError(
        code = "chrome_not_running",
        message = "Chrome is not running. Open Chrome and load any page, then try again.",
    )

    /**
     * The verified-debugging-socket check failed: after Chrome's cold restart the
     * `chrome_devtools_remote` endpoint is not bound (or could not be verified). Chrome must
     * create its own socket — port forwarding does not create it (task Scope).
     */
    object DevtoolsSocketMissing : CdpSetupError(
        code = "devtools_socket_missing",
        message =
            "Chrome is running but its DevTools socket (chrome_devtools_remote) is not " +
                "bound after restart. Enable the command-line flag, cold-restart Chrome, and " +
                "re-verify the endpoint before any action. Forwarding does not create the socket.",
    )

    /** An action ran before the endpoint was verified post-restart (AC2). */
    object EndpointNotVerified : CdpSetupError(
        code = "endpoint_not_verified",
        message =
            "Refusing to act before the DevTools endpoint is verified after Chrome's " +
                "cold restart. Run the setup sequence to its verified-socket step first.",
    )

    /** The DevTools HTTP endpoint returned a malformed response. */
    class MalformedResponse(
        detail: String,
        cause: Throwable? = null,
    ) : CdpSetupError(
            code = "malformed_response",
            message = "Chrome DevTools returned a malformed HTTP/WebSocket response: $detail",
            cause = cause,
        )

    /** Cleanup left an open devtools port (AC4). */
    class CleanupIncomplete(
        detail: String,
    ) : CdpSetupError(
            code = "cleanup_incomplete",
            message = "Cleanup did not close the DevTools endpoint cleanly: $detail",
        )

    /** A per-function readiness check did not pass, so the capability is not shown. */
    class NotReady(
        val function: String,
        detail: String,
    ) : CdpSetupError(
            code = "not_ready",
            message = "'$function' is not ready to be shown to the user yet: $detail",
        )
}
