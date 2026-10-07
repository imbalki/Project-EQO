/*
 * EQO (TASK-080, issue #20): the four plain-language states of the wireless link.
 *
 * The owner sees one of these, never a protocol term. Derived only from "is a server
 * enrolled" and the latest [ActivationReport]; the helper checks do not change it (a helper
 * failure is shown on its own check line, a pass on one plane proves nothing about another).
 */
package ai.eqo.adb.pairing

enum class WirelessLinkState {
    /** Never paired (or the last pair attempt failed and nothing was enrolled). */
    NOT_PAIRED,

    /** Paired and the pinned connection works right now. */
    PAIRED_AND_CONNECTED,

    /** Paired earlier, but the connection is not there right now (or was not tried yet). */
    CONNECTION_LOST,

    /** The paired device cannot be trusted or does not trust us: pairing must be repeated. */
    NEEDS_REPAIR,
}

object WirelessLink {
    fun stateOf(
        enrolled: Boolean,
        report: ActivationReport?,
    ): WirelessLinkState {
        val connectPassed = report?.outcomeOf(ActivationCheck.CONNECT) is CheckOutcome.Passed
        val failure = report?.firstFailure
        return when {
            failure is ActivationFailure.NeedsRepair || failure is ActivationFailure.PairingRevoked ->
                WirelessLinkState.NEEDS_REPAIR
            !enrolled -> WirelessLinkState.NOT_PAIRED
            connectPassed -> WirelessLinkState.PAIRED_AND_CONNECTED
            else -> WirelessLinkState.CONNECTION_LOST
        }
    }
}
