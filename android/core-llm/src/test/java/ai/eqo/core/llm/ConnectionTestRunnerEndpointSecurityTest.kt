// TASK-006 security pass F4 (issue #11): the connection-test endpoint must be
// https, with http accepted only for loopback hosts.
package ai.eqo.core.llm

import ai.eqo.core.llm.error.LLMError
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/**
 * F4 of the TASK-006 security pass: [ConnectionTestRunner] posts the user's key
 * as `Authorization: Bearer` to a caller-supplied endpoint, so the transport
 * must be https. `http` is accepted only for a loopback host
 * (localhost / 127.0.0.1 / ::1) — that is what the in-process MockWebServer
 * tests need. A non-https non-loopback endpoint is rejected with a typed
 * [ConnectionTestState.Failed] (no free-text field can echo the endpoint) and
 * no request is built or sent: the mock sees 0 requests.
 */
class ConnectionTestRunnerEndpointSecurityTest {
    private val plainServer = MockWebServer().also { it.start(InetAddress.getByName("127.0.0.1"), 0) }

    private val httpsCertificate =
        HeldCertificate
            .Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .build()

    private val httpsServer =
        MockWebServer().also { server ->
            server.useHttps(
                HandshakeCertificates
                    .Builder()
                    .heldCertificate(httpsCertificate)
                    .build()
                    .sslSocketFactory(),
            )
            server.start(InetAddress.getByName("127.0.0.1"), 0)
        }

    /** Never asserted on directly; only its absence from rendered state is. */
    private val apiKey = "sk-or-...f401"

    @After
    fun tearDown() {
        plainServer.close()
        httpsServer.close()
    }

    @Test
    fun `an https endpoint is probed`() {
        httpsServer.enqueue(successResponse())

        val state = ConnectionTestRunner(clientTrustingTestCertificate()).run(httpsEndpoint(), apiKey, MODEL)

        assertTrue("expected Connected but was $state", state is ConnectionTestState.Connected)
        assertEquals(1, httpsServer.requestCount)
    }

    @Test
    fun `a cleartext non-loopback endpoint is rejected before any request is sent`() {
        plainServer.enqueue(successResponse())
        // A client routed through the mock as an HTTP proxy: if the probe were
        // sent, the mock would see it.
        val client = proxiedClient()

        val state = ConnectionTestRunner(client).run("http://example.com/v1", apiKey, MODEL)

        assertTrue("expected Failed but was $state", state is ConnectionTestState.Failed)
        state as ConnectionTestState.Failed
        assertEquals(LLMError.RequestInvalid, state.error)
        assertEquals(0, plainServer.requestCount)
        assertFalse(state.toString().contains("example.com"))
        assertFalse(state.toString().contains(apiKey))
    }

    @Test
    fun `http is allowed on every loopback host spelling`() {
        val runner = ConnectionTestRunner(proxiedClient())
        val loopbackEndpoints =
            listOf(
                "http://localhost:9/v1",
                "http://127.0.0.1:9/v1",
                "http://[::1]:9/v1",
            )

        loopbackEndpoints.forEach { endpoint ->
            plainServer.enqueue(successResponse())
            val state = runner.run(endpoint, apiKey, MODEL)
            assertTrue("expected Connected for $endpoint but was $state", state is ConnectionTestState.Connected)
        }
        assertEquals(loopbackEndpoints.size, plainServer.requestCount)
    }

    /**
     * Routes plain-http probes through the mock as an HTTP proxy, so the mock
     * observes every request that is actually sent without needing a listener
     * at the target host.
     */
    private fun proxiedClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .proxy(plainServer.proxyAddress)
            .build()

    private fun clientTrustingTestCertificate(): OkHttpClient {
        val clientCertificates =
            HandshakeCertificates
                .Builder()
                .addTrustedCertificate(httpsCertificate.certificate)
                .build()
        return OkHttpClient
            .Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .build()
    }

    private fun successResponse(): MockResponse =
        MockResponse
            .Builder()
            .code(200)
            .setHeader("Content-Type", "application/json")
            .body("""{"choices":[{"message":{"content":"pong"}}],"usage":{"total_tokens":3}}""")
            .build()

    /**
     * The mock's host reverse-resolves to a machine-specific name on some
     * machines, so the URL is pinned to `127.0.0.1` — a subject alternative
     * name of the held certificate — instead of whatever the mock reports.
     */
    private fun httpsEndpoint(): String =
        httpsServer
            .url("/v1")
            .newBuilder()
            .host("127.0.0.1")
            .build()
            .toString()

    private companion object {
        const val MODEL = "openrouter/auto"
    }
}
