/*
 * EQO (TASK-010, issue #15): the CDP setup orchestrator.
 *
 * Drives the spike's documented preparation in a fixed, fail-closed order and gates every
 * action on a real readiness check:
 *
 *   1. Informed consent is shown and must be accepted BEFORE any preparation (AC1).
 *   2. Debug-flag preparation (Chrome's remote-debugging command-line unlock).
 *   3. Chrome cold restart (so Chrome rebinds its own DevTools socket).
 *   4. The debugging endpoint is VERIFIED (socket bound + /json/version answers) before any
 *      action runs (AC2). Chrome must create its own socket; forwarding does not create it.
 *   5. Per-function readiness: a capability is only shown to the user after its own readiness
 *      check passes (task Notes).
 *
 * Cleanup (teardown) leaves no open devtools port (AC4). Every failure surfaces as a typed
 * [CdpSetupError] (AC5), never a generic error. The ordered [SetupStep] log makes the
 * consent-before-prep and verify-before-action orderings directly assertable in tests.
 */
package ai.eqo.browser.cdp

/** Ordered phases the setup sequence can record. */
enum class SetupStep {
    CONSENT_SHOWN,
    DEBUG_FLAG_PREPARED,
    CHROME_RESTARTED,
    ENDPOINT_VERIFIED,
    CLEANED_UP,
}

/** The CDP capabilities that are gated on their own readiness check. */
enum class CdpFunction {
    NAVIGATE,
    FILL_FORM,
}

/** Per-function readiness: a capability is shown only when [Ready]. */
sealed class Readiness {
    object Ready : Readiness()

    data class Blocked(
        val reason: String,
    ) : Readiness()
}

/** Outcome of a setup or teardown run. */
data class SetupReport(
    val steps: List<SetupStep>,
    val endpoint: EndpointVerification?,
) {
    val endpointVerified: Boolean get() = endpoint is EndpointVerification.Verified
}

class ChromeCdpSetup(
    private val consent: CdpConsent,
    private val chrome: ChromeControl,
    private val endpoint: DevtoolsEndpoint,
) {
    private val steps = mutableListOf<SetupStep>()

    @Volatile
    private var lastEndpoint: EndpointVerification? = null

    /** Ordered record of phases completed so far (for tests and evidence). */
    fun stepLog(): List<SetupStep> = steps.toList()

    /**
     * Runs the preparation sequence up to and including the verified-endpoint step. Throws a
     * typed [CdpSetupError] if any phase fails; the returned [SetupReport] is only produced when
     * the endpoint is verified. Consent is enforced first (AC1), so no preparation side effect
     * occurs without it.
     */
    suspend fun prepare(): SetupReport {
        // Phase 1 (AC1): present consent and refuse to prepare until it is accepted. show() is
        // recorded as the FIRST step so the ordering "consent shown before any preparation" is
        // visible in the step log.
        consent.show()
        record(SetupStep.CONSENT_SHOWN)
        consent.requireAccepted()

        // Phase 2: debug-flag preparation.
        chrome.prepareDebugFlag()
        record(SetupStep.DEBUG_FLAG_PREPARED)

        // Phase 3: Chrome cold restart to rebind its own DevTools socket.
        chrome.coldRestart()
        record(SetupStep.CHROME_RESTARTED)

        // Phase 4 (AC2): verify the endpoint before any action.
        if (!chrome.isChromeRunning()) throw CdpSetupError.ChromeNotRunning
        val verification = endpoint.verify()
        lastEndpoint = verification
        if (verification !is EndpointVerification.Verified) {
            throw when (verification) {
                EndpointVerification.Unreachable ->
                    CdpSetupError.MalformedResponse(
                        "DevTools endpoint did not answer /json/version after Chrome's cold restart",
                    )
                EndpointVerification.NotBound -> CdpSetupError.DevtoolsSocketMissing
                EndpointVerification.Unknown -> CdpSetupError.DevtoolsSocketMissing
                is EndpointVerification.Verified -> CdpSetupError.DevtoolsSocketMissing
            }
        }
        record(SetupStep.ENDPOINT_VERIFIED)
        return SetupReport(steps.toList(), verification)
    }

    /**
     * Per-function readiness (task Notes: a capability is shown to the user only after its own
     * readiness check passes). NAVIGATE and FILL_FORM both require the endpoint to be verified.
     */
    fun readiness(function: CdpFunction): Readiness {
        if (lastEndpoint !is EndpointVerification.Verified) {
            return Readiness.Blocked(
                "the DevTools endpoint has not been verified after Chrome's cold restart",
            )
        }
        return Readiness.Ready
    }

    /** Throws [CdpSetupError.NotReady] unless [function]'s readiness check has passed. */
    fun requireReady(function: CdpFunction) {
        val readiness = readiness(function)
        if (readiness is Readiness.Blocked) {
            throw CdpSetupError.NotReady(function.name, readiness.reason)
        }
    }

    /**
     * Cleanup (AC4): close the DevTools port and confirm none is left open. Throws
     * [CdpSetupError.CleanupIncomplete] if a port remains open after teardown.
     */
    fun teardown(): SetupReport {
        chrome.closeDevtoolsPort()
        if (chrome.devtoolsPortOpen()) {
            throw CdpSetupError.CleanupIncomplete("a devtools port is still open after teardown")
        }
        record(SetupStep.CLEANED_UP)
        lastEndpoint = null
        return SetupReport(steps.toList(), null)
    }

    private fun record(step: SetupStep) {
        if (step !in steps) steps.add(step)
    }
}
