package ai.eqo.core.agent

/**
 * TASK-014 (issue #19): SMS is compose-only in EQO.
 *
 * EQO never calls [android.telephony.SmsManager] directly. Sending a text
 * message is an irreversible, externally-visible action, so EQO may only:
 *  1. open the messaging app with the recipient pre-filled (compose first), and
 *  2. hand the final send to the user in the messaging app UI.
 *
 * The policy is a single source of truth so tests can assert every SMS path
 * goes through the compose intent instead of a silent `sendTextMessage` call.
 */
object SmsComposePolicy {
    /**
     * True when direct SMS sending is allowed. Phase One ships compose-only
     * (PRD REQ-SMS-04 / USER-FLOWS UF-13): always false. Flipping this later
     * requires an owner decision and an explicit send gate — it is not reachable
     * from configuration.
     */
    const val DIRECT_SEND_ALLOWED: Boolean = false

    /**
     * Compose-first checklist: EQO may open a draft for the user, but the
     * message must never leave the device without the user pressing Send in
     * the messaging app. Both recipient and content must exist before the
     * compose screen opens (compose is prepared, not "sent").
     */
    fun canCompose(
        recipient: String?,
        content: String?,
    ): Boolean = !recipient.isNullOrBlank() && !content.isNullOrBlank()
}
