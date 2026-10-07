/*
 * EQO (TASK-015, issue #20): the two security gates of the shipped study flow.
 *
 * TASK-008 SF-1 (connect plane) and TASK-010 SF-1 (CDP socket check) both left an
 * unverified transport behind. TASK-080 closed SF-1 for wireless ADB: the connect plane
 * now pins the server key enrolled at pairing time and fails closed (no enrollment, or a
 * different key, means no connection), so that gate is open. The CDP endpoint check is
 * still name-only, so CDP stays switched off here, in one named place, and its screens
 * say so out loud (nothing is hidden and nothing is inferred).
 */
package ai.eqo.study

/**
 * Named switches for transports in the shipped study flow.
 *
 * The values are compile-time constants (not user settings): a study build cannot turn
 * a transport on by tapping. A transport may only be true while the safeguard named for
 * it exists; one that is false needs the pending work named for it first.
 */
object StudyFlowGate {
    /**
     * Wireless-ADB connect plane (`AdbTlsClient` / `connectWithStls`).
     *
     * TASK-080 closed TASK-008 SF-1: the connect plane no longer trusts any server
     * certificate. It dials loopback only, requires a server key enrolled at pairing time
     * ([WIRELESS_CONNECT_SAFEGUARD]) and refuses a different key or a missing enrollment
     * with a typed error and a re-pair prompt. The capability is used only to start the
     * privileged helper.
     */
    const val WIRELESS_CONNECT_PLANE_IN_STUDY_FLOW: Boolean = true

    /**
     * Chrome DevTools (CDP) relay and everything behind it.
     *
     * TASK-010 SF-1: the devtools endpoint check is name-only (no socket-owner
     * verification), and the relay has no pinned server. The informed-consent screen
     * (REQ-CDP-01 / REQ-PRIV-03) ships so the user can see what would be sent and
     * decline it; the consent answer is kept, the CDP machinery is never started.
     */
    const val CHROME_CDP_IN_STUDY_FLOW: Boolean = false

    /** What keeps [WIRELESS_CONNECT_PLANE_IN_STUDY_FLOW] safe to leave true. Remove it and the gate must close. */
    const val WIRELESS_CONNECT_SAFEGUARD: String =
        "post-pairing server-key pinning / enrollment, fail closed (TASK-008 SF-1, closed by TASK-080)"

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
