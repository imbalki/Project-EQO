/*
 * EQO (TASK-010, issue #15): device seam for Chrome lifecycle + debug-flag preparation.
 *
 * The CDP spike's setup flow is: informed consent -> debug-flag preparation -> Chrome cold
 * restart -> verified debugging socket -> per-function readiness. The first, third and the
 * cleanup all touch the real device (write /data/local/tmp/chrome-command-line, force-stop +
 * relaunch Chrome, confirm no devtools port remains). Those operations live behind this seam
 * so the setup orchestrator is host-testable with a fake while production supplies the real
 * shell-uid (Shizuku) or reflection-backed implementation.
 *
 * Failures surface as typed [CdpSetupError]s (AC5) — never a generic error. This seam does NOT
 * wire any concrete privileged transport; see the loopback-only disposition in the TASK-010
 * evidence and the SF-1 security carry-over (no production caller on the trust-all plane).
 */
package ai.eqo.browser.cdp

/**
 * Device-side Chrome operations the setup orchestrator depends on. Implementations throw a
 * typed [CdpSetupError] when a step cannot be completed so the failure is actionable.
 */
interface ChromeControl {
    /** Whether a Chrome process is currently running. */
    fun isChromeRunning(): Boolean

    /**
     * Debug-flag preparation: idempotently write Chrome's command-line file so Chrome binds the
     * `chrome_devtools_remote` socket once the user flips the chrome://flags unlock and restarts
     * Chrome. Throws [CdpSetupError.DebugFlagPreparation] on failure.
     */
    fun prepareDebugFlag()

    /**
     * Cold-restart Chrome (force-stop then relaunch) so it re-reads the command-line file and
     * rebinds its DevTools socket. Throws [CdpSetupError.ChromeRestartFailed] on failure.
     */
    fun coldRestart()

    /**
     * Cleanup: tear down any DevTools relay/forwarding and stop exposing the DevTools endpoint.
     * Leaves no open devtools port (AC4). Throws [CdpSetupError.CleanupIncomplete] on failure.
     */
    fun closeDevtoolsPort()

    /**
     * Whether a DevTools endpoint / port is still open. Used by cleanup (AC4) to confirm teardown
     * left no open devtools port. True = still open.
     */
    fun devtoolsPortOpen(): Boolean
}
