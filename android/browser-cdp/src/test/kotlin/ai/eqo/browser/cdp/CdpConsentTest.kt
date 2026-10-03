package ai.eqo.browser.cdp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance criterion (AC1): "Consent shown before any preparation". The consent model must
 * present the screen before a decision is possible, and the setup gate must refuse preparation
 * until consent is accepted.
 */
class CdpConsentTest {
    @Test
    fun freshStateIsNotAskedAndTextIsPresent() {
        val consent = CdpConsent()
        assertEquals(CdpConsentState.NOT_ASKED, consent.state)
        assertTrue(consent.consentText.isNotEmpty())
    }

    @Test
    fun cannotAcceptBeforeShown() {
        val consent = CdpConsent()
        assertFalse(consent.accept())
        assertEquals(CdpConsentState.NOT_ASKED, consent.state)
    }

    @Test
    fun shownThenAcceptedReachesAccepted() {
        val consent = CdpConsent()
        consent.show()
        assertEquals(CdpConsentState.SHOWN, consent.state)
        assertTrue(consent.accept())
        assertTrue(consent.isAccepted)
    }

    @Test
    fun declineBlocksPreparation() {
        val consent = CdpConsent()
        consent.show()
        assertTrue(consent.decline())
        assertFalse(consent.isAccepted)
    }

    @Test(expected = CdpSetupError.ConsentRequired::class)
    fun requireAcceptedThrowsUntilAccepted() {
        val consent = CdpConsent()
        consent.show()
        consent.requireAccepted()
    }

    @Test
    fun requireAcceptedPassesOnceAccepted() {
        val consent = CdpConsent()
        consent.show()
        consent.accept()
        consent.requireAccepted() // no throw
    }
}
