package ai.eqo.browser.cdp

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The setup orchestrator's acceptance criteria:
 *  - AC1: consent shown before any preparation.
 *  - AC2: the endpoint is verified before any action (and per-function readiness gates the
 *         capability until its check passes).
 *  - AC4: cleanup leaves no open devtools port.
 *  - AC5: failures surface as typed setup errors.
 */
class ChromeCdpSetupTest {
    /** Fake [ChromeControl] recording calls and holding mutable device state. */
    private class FakeChromeControl(
        var running: Boolean = true,
        var portOpenAfterClose: Boolean = false,
    ) : ChromeControl {
        var prepareDebugFlagCalls = 0
        var coldRestartCalls = 0
        var closeDevtoolsPortCalls = 0
        var portOpen: Boolean = true

        override fun isChromeRunning(): Boolean = running

        override fun prepareDebugFlag() {
            prepareDebugFlagCalls++
        }

        override fun coldRestart() {
            coldRestartCalls++
        }

        override fun closeDevtoolsPort() {
            closeDevtoolsPortCalls++
            portOpen = portOpenAfterClose
        }

        override fun devtoolsPortOpen(): Boolean = portOpen
    }

    private fun http200(body: String): ByteArray = "HTTP/1.1 200 OK\r\n\r\n$body".toByteArray()

    private val versionBody =
        """{"Browser":"Chrome/120","Protocol-Version":"1.3"}"""

    private fun verifiedEndpoint() =
        DevtoolsEndpoint(
            socketProbe = DevtoolsSocketProbe(procNetUnixText = { "@chrome_devtools_remote" }),
            devtoolsGet = { http200(versionBody) },
        )

    private fun notBoundEndpoint() =
        DevtoolsEndpoint(
            socketProbe = DevtoolsSocketProbe(procNetUnixText = { "@other" }),
            devtoolsGet = { http200(versionBody) },
        )

    private fun acceptedConsent(): CdpConsent =
        CdpConsent().apply {
            show()
            accept()
        }

    @Test
    fun consentShownBeforeAnyPreparation() {
        val chrome = FakeChromeControl()
        val setup = ChromeCdpSetup(CdpConsent(), chrome, verifiedEndpoint())
        try {
            runBlocking { setup.prepare() }
            throw AssertionError("expected ConsentRequired")
        } catch (e: CdpSetupError.ConsentRequired) {
            // expected: consent was never accepted
        }
        // The consent screen is the first recorded step and no prep side effect happened.
        assertEquals(listOf(SetupStep.CONSENT_SHOWN), setup.stepLog())
        assertEquals(0, chrome.prepareDebugFlagCalls)
    }

    @Test
    fun prepareRunsTheDocumentedOrder() {
        val chrome = FakeChromeControl()
        val setup = ChromeCdpSetup(acceptedConsent(), chrome, verifiedEndpoint())
        val report = runBlocking { setup.prepare() }
        assertEquals(
            listOf(
                SetupStep.CONSENT_SHOWN,
                SetupStep.DEBUG_FLAG_PREPARED,
                SetupStep.CHROME_RESTARTED,
                SetupStep.ENDPOINT_VERIFIED,
            ),
            report.steps,
        )
        assertTrue(report.endpointVerified)
    }

    @Test
    fun readinessBlocksUntilEndpointVerifiedThenReady() {
        val setup = ChromeCdpSetup(acceptedConsent(), FakeChromeControl(), verifiedEndpoint())
        // Before prepare: capability is blocked (not shown to the user).
        assertTrue(setup.readiness(CdpFunction.NAVIGATE) is Readiness.Blocked)
        try {
            setup.requireReady(CdpFunction.FILL_FORM)
            throw AssertionError("expected NotReady")
        } catch (e: CdpSetupError.NotReady) {
            assertEquals("FILL_FORM", e.function)
        }
        runBlocking { setup.prepare() }
        assertEquals(Readiness.Ready, setup.readiness(CdpFunction.NAVIGATE))
        assertEquals(Readiness.Ready, setup.readiness(CdpFunction.FILL_FORM))
    }

    @Test
    fun prepareFailsTypedWhenSocketNotBound() {
        val setup = ChromeCdpSetup(acceptedConsent(), FakeChromeControl(), notBoundEndpoint())
        try {
            runBlocking { setup.prepare() }
            throw AssertionError("expected DevtoolsSocketMissing")
        } catch (e: CdpSetupError.DevtoolsSocketMissing) {
            assertEquals("devtools_socket_missing", e.code)
        }
        // Endpoint was not verified, so the capability stays blocked (verify-before-action).
        assertTrue(setup.readiness(CdpFunction.NAVIGATE) is Readiness.Blocked)
    }

    @Test
    fun prepareFailsTypedWhenChromeNotRunningAfterRestart() {
        val chrome = FakeChromeControl(running = false)
        val setup = ChromeCdpSetup(acceptedConsent(), chrome, verifiedEndpoint())
        try {
            runBlocking { setup.prepare() }
            throw AssertionError("expected ChromeNotRunning")
        } catch (e: CdpSetupError.ChromeNotRunning) {
            assertEquals("chrome_not_running", e.code)
        }
    }

    @Test
    fun teardownLeavesNoOpenPort() {
        val chrome = FakeChromeControl(portOpenAfterClose = false)
        val setup = ChromeCdpSetup(acceptedConsent(), chrome, verifiedEndpoint())
        runBlocking { setup.prepare() }
        val report = setup.teardown()
        assertEquals(1, chrome.closeDevtoolsPortCalls)
        assertFalse(chrome.devtoolsPortOpen())
        assertTrue(SetupStep.CLEANED_UP in report.steps)
    }

    @Test
    fun teardownFailsTypedWhenPortRemainsOpen() {
        val chrome = FakeChromeControl(portOpenAfterClose = true)
        val setup = ChromeCdpSetup(acceptedConsent(), chrome, verifiedEndpoint())
        runBlocking { setup.prepare() }
        try {
            setup.teardown()
            throw AssertionError("expected CleanupIncomplete")
        } catch (e: CdpSetupError.CleanupIncomplete) {
            assertEquals("cleanup_incomplete", e.code)
        }
    }
}
