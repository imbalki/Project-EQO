package ai.eqo.adb.pairing

import java.security.cert.CertificateException
import java.security.cert.X509Certificate

/**
 * One in-memory CONNECT-key enrollment opportunity, created only after fresh SPAKE2 success.
 * AOSP's pairing service and adbd use different keys. This is bounded TOFU, not cryptographic
 * authentication of the connect certificate by SPAKE2. Never saved or used by plain reconnect.
 */
internal class ConnectKeyEnrollment private constructor(
    private val address: java.net.InetAddress,
    private val port: Int,
    private val peerGuid: String?,
    private val startedAtMs: Long,
    private val clockMs: () -> Long,
) {
    private var claimed = false
    private var finished = false
    private var candidate: ServerKeyFingerprint? = null

    /** Consume before dialing: even a failed attempt requires another user-initiated pairing. */
    @Synchronized
    fun claim(
        host: String,
        connectionPort: Int,
    ) {
        if (claimed || finished) throw ServerNotEnrolledException()
        claimed = true
        if (LoopbackAdbHost.requireAddress(host) != address || connectionPort != port) {
            finished = true
            throw ServerNotEnrolledException()
        }
        requireWindow()
    }

    /** Recheck after provider/key setup, immediately before opening the TCP connection. */
    @Synchronized
    fun beforeDial() {
        requireWindow()
        if (!claimed || finished) throw ServerNotEnrolledException()
    }

    /** TLS callbacks and post-handshake recheck must all see the same nonempty leaf key. */
    @Synchronized
    fun verify(chain: Array<out X509Certificate>?) {
        requireWindow()
        val leaf = chain?.firstOrNull()
        if (!claimed || finished || leaf == null) {
            finished = true
            throw CertificateException("no active connect certificate")
        }
        val key = ServerKeyFingerprint.of(leaf)
        val first = candidate
        if (first != null && first != key) {
            finished = true
            throw CertificateException("connect key changed during enrollment")
        }
        candidate = key
    }

    /** Persist only after TLS succeeds and the negotiated certificate is rechecked. */
    @Synchronized
    fun commit(store: ServerEnrollmentStore) {
        requireWindow()
        if (!claimed || finished) throw ServerNotEnrolledException()
        finished = true
        val key = candidate ?: throw ServerNotEnrolledException()
        store.enroll(key, peerGuid)
    }

    private fun requireWindow() {
        val elapsed = clockMs() - startedAtMs
        if (elapsed < 0 || elapsed >= WINDOW_MS) {
            finished = true
            throw ServerNotEnrolledException()
        }
    }

    companion object {
        internal const val WINDOW_MS = 30_000L
        private const val NANOS_PER_MS = 1_000_000L

        /** The callback must finish SPAKE2 + authenticated peer-info exchange, or no token exists. */
        suspend fun afterPairing(
            host: String,
            connectionPort: Int,
            clockMs: () -> Long = { System.nanoTime() / NANOS_PER_MS },
            pair: suspend () -> PairingResult,
        ): ConnectKeyEnrollment {
            val address = LoopbackAdbHost.requireAddress(host)
            require(connectionPort in WirelessAdbEndpoints.PORT_MIN..WirelessAdbEndpoints.PORT_MAX) {
                "invalid connection port"
            }
            val result = pair()
            return ConnectKeyEnrollment(address, connectionPort, result.peerGuid, clockMs(), clockMs)
        }
    }
}
