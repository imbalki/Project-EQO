package ai.eqo.adb.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

/**
 * Host-JVM tests for the device-side runner: helper checks are separate and individually
 * failing, and transport failures map to typed signals instead of passing silently. The
 * pair/connect checks are exercised against a refused loopback port (nothing listens
 * there), which is exactly the stale-or-swapped-port case.
 */
class WirelessAdbActivationRunnerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val endpoints = WirelessAdbEndpoints(pairingPort = 1, connectionPort = 2)
    private val code = requireNotNull(code("123456"))

    private class FakeHooks(
        private val failStart: Boolean = false,
        private val failAuthorize: Boolean = false,
        private val failBinder: Boolean = false,
    ) : HelperHooks {
        override fun startHelper() {
            if (failStart) throw IOException("helper did not start")
        }

        override fun authorizeHelper() {
            if (failAuthorize) throw StepSignalException(StepSignal.HELPER_NOT_AUTHORIZED, "denied")
        }

        override fun checkBinder() {
            if (failBinder) error("binder dead")
        }
    }

    private fun runner(hooks: HelperHooks = FakeHooks()): WirelessAdbActivationRunner =
        WirelessAdbActivationRunner(
            keyStore = AdbCryptoKeyStore(File(tmp.root, "adb-keys")),
            helper = hooks,
            handshakeTimeoutMs = 2_000,
        )

    @Test
    fun helperChecksAreSeparatelyFailing() {
        val startFail = runner(FakeHooks(failStart = true))
        val startSignal = runCatching { startFail.startHelper() }.exceptionOrNull() as StepSignalException
        assertEquals(StepSignal.HELPER_NOT_STARTED, startSignal.signal)

        val authFail = runner(FakeHooks(failAuthorize = true))
        val authSignal = runCatching { authFail.authorizeHelper() }.exceptionOrNull() as StepSignalException
        assertEquals(StepSignal.HELPER_NOT_AUTHORIZED, authSignal.signal)

        val binderFail = runner(FakeHooks(failBinder = true))
        val binderSignal = runCatching { binderFail.checkBinder() }.exceptionOrNull() as StepSignalException
        assertEquals(StepSignal.BINDER_DEAD, binderSignal.signal)
    }

    @Test
    fun helperChecksPassIndependently() {
        val ok = runner()
        ok.startHelper()
        ok.authorizeHelper()
        ok.checkBinder()
    }

    @Test
    fun pairFailureSurfacesAsTypedSignalNotSuccess() {
        // Host-JVM note: the pairing TLS context needs the bundled Conscrypt (native,
        // device-only), so on the host the pair attempt fails before any socket is
        // dialled. What must hold everywhere: it fails VISIBLY as a typed signal.
        val signal = runCatching { runner().pair(endpoints, code) }.exceptionOrNull() as StepSignalException
        assertTrue(signal.detail.isNotBlank())
    }

    @Test
    fun refusedConnectionPortIsReportedAsPortRefusedNotSuccess() {
        val signal = runCatching { runner().connect(endpoints) }.exceptionOrNull() as StepSignalException
        assertEquals(StepSignal.PORT_REFUSED, signal.signal)
    }

    @Test
    fun transportMappingRefusedPortIsPortRefused() {
        val mapped = runner().mapTransport(java.net.ConnectException("refused"), StepSignal.PAIRING_CODE_REJECTED)
        assertEquals(StepSignal.PORT_REFUSED, mapped.signal)
    }

    @Test
    fun transportMappingTimeoutIsPortRefused() {
        val mapped = runner().mapTransport(SocketTimeoutException("timeout"), StepSignal.PORT_REFUSED)
        assertEquals(StepSignal.PORT_REFUSED, mapped.signal)
    }

    @Test
    fun transportMappingTlsFailureKeepsTheCallersDefault() {
        val mapped = runner().mapTransport(SSLHandshakeException("bad psk"), StepSignal.PAIRING_CODE_REJECTED)
        assertEquals(StepSignal.PAIRING_CODE_REJECTED, mapped.signal)
    }

    @Test
    fun transportMappingAuthMessageIsAuthRejected() {
        val mapped = runner().mapTransport(IOException("unexpected A_AUTH on TLS port"), StepSignal.PORT_REFUSED)
        assertEquals(StepSignal.AUTH_REJECTED, mapped.signal)
    }

    @Test
    fun transportMappingPreservesTheOriginalExceptionAsCause() {
        val cause = ConnectException("refused")
        assertEquals(cause, runner().mapTransport(cause, StepSignal.PORT_REFUSED).cause)
    }

    private fun code(raw: String): AdbPairingCode? {
        val parsed = AdbPairingCode.parse(raw)
        return (parsed as? AdbPairingCode.ParseResult.Ok)?.code
    }
}
