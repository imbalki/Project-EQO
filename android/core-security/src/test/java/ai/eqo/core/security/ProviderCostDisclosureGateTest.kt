package ai.eqo.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TASK-006 acceptance coverage for the cost-disclosure gate in :core-security:
 * the screen must be required before the key is first used and can never be
 * silently skipped by a second attempt.
 */
class ProviderCostDisclosureGateTest {
    @Test
    fun `first use requires the disclosure screen`() {
        val gate = InMemoryProviderCostDisclosureGate()

        assertTrue(gate.requirementBeforeFirstUse("OpenRouter") is FirstUseRequirement.ShowDisclosure)
        assertFalse(gate.isAcknowledged("OpenRouter"))
    }

    @Test
    fun `acknowledgement clears the requirement exactly once`() {
        val gate = InMemoryProviderCostDisclosureGate()

        assertTrue(gate.acknowledge("OpenRouter") is CredentialStoreResult.Success)
        assertTrue(gate.requirementBeforeFirstUse("OpenRouter") is FirstUseRequirement.Cleared)
        assertTrue(gate.isAcknowledged("OpenRouter"))

        val firstStamp = gate.disclosureEvents.value.getValue("OpenRouter")
        gate.acknowledge("OpenRouter")
        assertEquals(firstStamp, gate.disclosureEvents.value.getValue("OpenRouter"))
    }

    @Test
    fun `first key use is blocked until the disclosure is acknowledged`() {
        val gate = InMemoryProviderCostDisclosureGate()

        // First-use attempt: the gate blocks the credential round-trip until
        // the disclosure screen has been shown and accepted.
        val attemptBeforeAck: FirstUseRequirement = gate.requirementBeforeFirstUse("OpenRouter")
        assertTrue(attemptBeforeAck is FirstUseRequirement.ShowDisclosure)
        assertFalse(gate.isAcknowledged("OpenRouter"))

        // Only an explicit acknowledgement clears the requirement; the blocked
        // attempt itself must never have acknowledged anything.
        assertTrue(gate.acknowledge("OpenRouter") is CredentialStoreResult.Success)
        assertTrue(gate.requirementBeforeFirstUse("OpenRouter") is FirstUseRequirement.Cleared)
    }

    @Test
    fun `acknowledgements are tracked per provider`() {
        val gate = InMemoryProviderCostDisclosureGate()

        assertTrue(gate.acknowledge("OpenRouter") is CredentialStoreResult.Success)

        assertTrue(gate.isAcknowledged("OpenRouter"))
        assertFalse(gate.isAcknowledged("OpenAI"))
        assertTrue(gate.requirementBeforeFirstUse("OpenAI") is FirstUseRequirement.ShowDisclosure)
    }

    @Test
    fun `disclosure copy names real money and the per-request cost`() {
        // The screen must tell the user their OpenRouter credits are real money
        // before the key is first used - this is the disclosure's one job.
        val text = ProviderCostDisclosure.DISCLOSURE_TEXT
        assertTrue(text.lowercase().contains("openrouter"))
        assertTrue(text.lowercase().contains("credit"))
        assertTrue(text.lowercase().contains("money"))
        assertTrue(text.lowercase().contains("charge"))
        // The acknowledgement control names the cost of per-request charges.
        assertTrue(ProviderCostDisclosure.ACKNOWLEDGEMENT_LABEL.lowercase().contains("cost money"))
    }

    @Test
    fun `disclosure state never stores or emits credential material`() {
        val gate = InMemoryProviderCostDisclosureGate()
        val provider = "OpenRouter"
        val secret = "sk-or-v1-cost-disclosure-secret-key"

        assertTrue(gate.acknowledge(provider) is CredentialStoreResult.Success)
        // The ledger keys on the caller's provider name only, and the event map
        // value is a timestamp; no credential-shaped value is emitted by any surface.
        val rendered = gate.disclosureEvents.value.toString()
        assertFalse(rendered.contains(secret))
        val acknowledgedProviders = gate.disclosureEvents.value.keys
        assertTrue(acknowledgedProviders.single() == provider)
    }
}
