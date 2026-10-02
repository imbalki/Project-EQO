// TASK-014 (issue #19): guard tests for SMS compose-only and permission narrowing.
package ai.eqo.core.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic guards for the SMS compose-only rule (PRD REQ-SMS-04, UF-13):
 * EQO may open a pre-filled draft but must never send directly.
 */
class SmsComposePolicyTest {
    @Test
    fun `direct send is disabled in phase one`() {
        assertFalse(SmsComposePolicy.DIRECT_SEND_ALLOWED)
    }

    @Test
    fun `compose requires both recipient and content`() {
        assertTrue(SmsComposePolicy.canCompose("+1 555 0100", "hi"))
        assertFalse(SmsComposePolicy.canCompose(null, "hi"))
        assertFalse(SmsComposePolicy.canCompose("", "hi"))
        assertFalse(SmsComposePolicy.canCompose("   ", "hi"))
        assertFalse(SmsComposePolicy.canCompose("+1 555 0100", null))
        assertFalse(SmsComposePolicy.canCompose("+1 555 0100", ""))
        assertFalse(SmsComposePolicy.canCompose("+1 555 0100", "   "))
        assertFalse(SmsComposePolicy.canCompose(null, null))
    }

    @Test
    fun `send_sms stays auto-approvable but is described as compose-only`() {
        // The action itself remains available — it just resolves to a compose
        // intent, never to a direct send, so it needs no neverAutoApprove gate.
        assertTrue(ActionSchema.isValid("SEND_SMS"))
        val definition = ActionSchema.getAction("SEND_SMS")!!
        assertFalse(definition.neverAutoApprove)
        val description = definition.description
        val describeDraft =
            "SEND_SMS description must state the user taps Send" to
                description.contains("user to review and send")
        assertTrue(describeDraft.first, describeDraft.second)
        assertFalse("SEND_SMS description must promise a draft, not a send", description.startsWith("Sends an SMS"))
    }
}
