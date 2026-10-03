package ai.eqo.browser.cdp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance criterion (AC2): "After restart, the endpoint is verified before any action".
 * These cover the verification building blocks — the token-aware DevTools socket match (extracted
 * from the donor), the strict DevTools HTTP/JSON parsing, and the combined endpoint check.
 */
class DevtoolsSetupTest {
    private fun http200(body: String): ByteArray = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n$body".toByteArray()

    private val versionBody =
        """{"Browser":"Chrome/120","Protocol-Version":"1.3","webSocketDebuggerUrl":"ws://x/y"}"""

    // --- DevtoolsSocketProbe token matching -------------------------------------------

    @Test
    fun containsAbstractSocketMatchesExactName() {
        val proc = "00000000: 00000003 00 00 0001 01 1234 @chrome_devtools_remote"
        assertTrue(DevtoolsSocketProbe.containsAbstractSocket(proc, "chrome_devtools_remote"))
    }

    @Test
    fun containsAbstractSocketMatchesPidSuffix() {
        val proc = "00000000: 00000003 00 00 0001 01 1234 @chrome_devtools_remote_5678"
        assertTrue(DevtoolsSocketProbe.containsAbstractSocket(proc, "chrome_devtools_remote"))
    }

    @Test
    fun containsAbstractSocketRejectsUnrelatedSuffix() {
        val proc = "00000000: 00 00 0001 01 1 @chrome_devtools_remote_unrelated_thing"
        assertFalse(DevtoolsSocketProbe.containsAbstractSocket(proc, "chrome_devtools_remote"))
    }

    @Test
    fun probeReportsBoundNotBoundUnknown() {
        val bound =
            DevtoolsSocketProbe(
                procNetUnixText = { "x @chrome_devtools_remote" },
            ).probe()
        assertEquals(DevtoolsSocketProbe.Result.Bound, bound)

        val notBound = DevtoolsSocketProbe(procNetUnixText = { "x @other" }).probe()
        assertEquals(DevtoolsSocketProbe.Result.NotBound, notBound)

        val unknown =
            DevtoolsSocketProbe(
                procNetUnixText = { null },
                shellFallbackText = { null },
            ).probe()
        assertEquals(DevtoolsSocketProbe.Result.Unknown, unknown)
    }

    // --- DevtoolsHttpProtocol parsing -------------------------------------------------

    @Test
    fun parseHttpBodyReturnsBodyOn200() {
        val body = DevtoolsHttpProtocol.parseHttpBody(http200("{\"a\":1}"))
        assertEquals("{\"a\":1}", body)
    }

    @Test(expected = CdpSetupError.MalformedResponse::class)
    fun parseHttpBodyRejectsNon200() {
        DevtoolsHttpProtocol.parseHttpBody("HTTP/1.1 404 Not Found\r\n\r\n{}".toByteArray())
    }

    @Test
    fun parseVersionReadsFields() {
        val v = DevtoolsHttpProtocol.parseVersion(versionBody)
        assertEquals("Chrome/120", v.browser)
        assertEquals("1.3", v.protocolVersion)
    }

    @Test(expected = CdpSetupError.MalformedResponse::class)
    fun parseVersionRejectsMissingField() {
        DevtoolsHttpProtocol.parseVersion("""{"Browser":"Chrome/120"}""")
    }

    // --- DevtoolsEndpoint.verify (AC2) -----------------------------------------------

    @Test
    fun verifyIsVerifiedWhenSocketBoundAndEndpointAnswers() {
        val endpoint =
            DevtoolsEndpoint(
                socketProbe = DevtoolsSocketProbe(procNetUnixText = { "@chrome_devtools_remote" }),
                devtoolsGet = { http200(versionBody) },
            )
        val result = endpoint.verify()
        assertTrue(result is EndpointVerification.Verified)
    }

    @Test
    fun verifyIsNotBoundWhenSocketAbsent() {
        val endpoint =
            DevtoolsEndpoint(
                socketProbe = DevtoolsSocketProbe(procNetUnixText = { "@other" }),
                devtoolsGet = { http200(versionBody) },
            )
        assertEquals(EndpointVerification.NotBound, endpoint.verify())
    }

    @Test
    fun verifyIsUnreachableWhenEndpointDoesNotAnswer() {
        val endpoint =
            DevtoolsEndpoint(
                socketProbe = DevtoolsSocketProbe(procNetUnixText = { "@chrome_devtools_remote" }),
                devtoolsGet = { throw java.io.IOException("connection refused") },
            )
        assertEquals(EndpointVerification.Unreachable, endpoint.verify())
    }

    @Test
    fun verifyIsUnknownWhenSocketCannotBeRead() {
        val endpoint =
            DevtoolsEndpoint(
                socketProbe = DevtoolsSocketProbe(procNetUnixText = { null }, shellFallbackText = { null }),
                devtoolsGet = { http200(versionBody) },
            )
        assertEquals(EndpointVerification.Unknown, endpoint.verify())
    }
}
