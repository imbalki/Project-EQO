/*
 * EQO (TASK-010, issue #15): informed-consent gate for the Chrome CDP capability.
 *
 * Acceptance criterion: "Consent shown before any preparation". Chrome control is a
 * high-privilege capability (it drives the user's real browser), so EQO presents an explicit
 * informed-consent screen and records the user's decision BEFORE any debug-flag preparation,
 * Chrome restart, or socket work happens. This is a distinct auth plane from pairing, helper
 * authorization and accessibility (task Notes): one passing proves nothing about the others.
 *
 * The model is deliberately order-preserving: consent must be SHOWN before it can be ACCEPTED,
 * and the setup orchestrator refuses to prepare until the state is ACCEPTED. Nothing is
 * persisted — process death returns to NOT_ASKED and fails closed.
 */
package ai.eqo.browser.cdp

enum class CdpConsentState {
    /** Fresh install / process start: the consent screen has not been presented. */
    NOT_ASKED,

    /** The consent screen is on screen; the user has not decided yet. */
    SHOWN,

    /** The user explicitly accepted CDP control. */
    ACCEPTED,

    /** The user declined CDP control; no preparation may run. */
    DECLINED,
}

/**
 * Informed-consent surface for the CDP capability. `show()` renders the screen (wired to the
 * app UI layer); `accept()` / `decline()` record the decision. `requireAccepted()` is what the
 * setup orchestrator calls before any preparation step.
 */
class CdpConsent {
    @Volatile
    var state: CdpConsentState = CdpConsentState.NOT_ASKED
        private set

    /** User-visible consent body. Device-asserted non-empty and shown before preparation. */
    val consentText: String =
        "EQO can control Chrome to open pages and fill forms on your behalf. " +
            "This uses the Chrome DevTools debugging socket. Only continue if you understand " +
            "EQO will read and type into pages you open. You can stop this at any time."

    /** Presents the consent screen. Idempotent while already SHOWN. */
    fun show() {
        if (state == CdpConsentState.NOT_ASKED) state = CdpConsentState.SHOWN
    }

    /** Records acceptance. Only valid from SHOWN — consent cannot be accepted before shown. */
    fun accept(): Boolean {
        if (state != CdpConsentState.SHOWN) return false
        state = CdpConsentState.ACCEPTED
        return true
    }

    /** Records a decline. Only valid from SHOWN. */
    fun decline(): Boolean {
        if (state != CdpConsentState.SHOWN) return false
        state = CdpConsentState.DECLINED
        return true
    }

    val isAccepted: Boolean get() = state == CdpConsentState.ACCEPTED

    /**
     * Gate used by the setup orchestrator before ANY preparation (AC1). Returns the acceptance,
     * or throws [CdpSetupError.ConsentRequired] when consent has not been shown AND accepted.
     */
    fun requireAccepted() {
        if (!isAccepted) throw CdpSetupError.ConsentRequired
    }
}
