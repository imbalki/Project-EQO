/*
 * EQO (TASK-015, issue #20): the two security gates of the shipped study flow.
 *
 * TASK-008 SF-1 (connect plane) and TASK-010 SF-1 (CDP socket check) both leave an
 * unverified transport behind: the wireless-ADB connect plane accepts any server
 * certificate, and the CDP endpoint check is name-only. Neither may get a production
 * caller until socket-owner verification / server-key pinning exists, so the study
 * flow ships with BOTH switched off here, in one named place, and every capability
 * screen says so out loud (nothing is hidden and nothing is inferred).
 */
package ai.eqo.study

/**
 * Named switches for work that is deliberately NOT reachable from the shipped study flow.
 *
 * The values are compile-time constants (not user settings): a study build cannot turn
 * an unverified transport on by tapping. Flipping one of these is a code change that
 * must land together with the pinning/socket-owner work named in [reasonFor].
 */
object StudyFlowGate {
    /**
     * Wireless-ADB connect plane (`AdbTlsClient` / `connectWithStls`).
     *
     * TASK-008 SF-1: the connect-plane TLS accepts any server certificate (AOSP parity,
     * loopback-only). A production caller may only appear after post-pairing server-key
     * pinning/enrollment exists. The study flow therefore never dispatches the CONNECT
     * check; the pairing-side guidance (what the owner taps in Android's own settings)
     * still ships, and the capability row reports [CapabilityState.GATED].
     */
    const val WIRELESS_CONNECT_PLANE_IN_STUDY_FLOW: Boolean = false

    /**
     * Chrome DevTools (CDP) relay and everything behind it.
     *
     * TASK-010 SF-1: the devtools endpoint check is name-only (no socket-owner
     * verification), and the relay has no pinned server. The informed-consent screen
     * (REQ-CDP-01 / REQ-PRIV-03) ships so the user can see what would be sent and
     * decline it; the consent answer is kept, the CDP machinery is never started.
     */
    const val CHROME_CDP_IN_STUDY_FLOW: Boolean = false

    /** What must land before [WIRELESS_CONNECT_PLANE_IN_STUDY_FLOW] may become true. */
    const val WIRELESS_CONNECT_PENDING_WORK: String =
        "post-pairing server-key pinning / enrollment (TASK-008 SF-1)"

    /** What must land before [CHROME_CDP_IN_STUDY_FLOW] may become true. */
    const val CHROME_CDP_PENDING_WORK: String =
        "socket-owner verification plus a pinned devtools server (TASK-010 SF-1)"

    /**
     * The one place that answers "may this flow dispatch an unverified transport?".
     * Callers must not read the constants directly so that a grep for
     * `StudyFlowGate.` finds every caller.
     */
    fun permits(transport: StudyTransport): Boolean =
        when (transport) {
            StudyTransport.WIRELESS_CONNECT_PLANE -> WIRELESS_CONNECT_PLANE_IN_STUDY_FLOW
            StudyTransport.CHROME_CDP -> CHROME_CDP_IN_STUDY_FLOW
        }

    /** The unverified transports this gate owns. */
    enum class StudyTransport {
        WIRELESS_CONNECT_PLANE,
        CHROME_CDP,
    }
}
