// Origin: imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536,
//   path: app/src/main/kotlin/ai/closepaw/browser/cdp/wireless/AdbTlsClient.kt
// TASK-008 (issue #13): extracted into :adb-pairing for the guided on-phone pairing flow.
// Changes vs donor: package renamed to ai.eqo.adb.pairing; default peer label rebranded to
// EQO. TASK-080 (issue #20): the trust-all manager is removed; the server key enrolled at
// pairing time is pinned and a missing enrollment fails closed. Provenance record:
// android/Phase-One/evidence/task-008-wireless-adb-pairing.md.
package ai.eqo.adb.pairing

import ai.eqo.adb.pairing.AdbProtocol.A_STLS
import android.util.Log
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509ExtendedKeyManager

/**
 * Plain TCP -> A_STLS exchange -> TLS 1.3 wrap to the wireless ADB TLS port.
 *
 * Per AOSP `daemon/adb_wifi.cpp` the adbd state machine on the TLS port is:
 *   - Daemon accepts TCP, starts plain read thread.
 *   - Client sends A_CNXN  -> daemon `handle_new_connection` -> `send_tls_request` (A_STLS).
 *   - Client reads A_STLS, sends its own A_STLS.
 *   - Daemon `adbd_auth_tls_handshake` performs TLS handshake.
 *   - On success daemon `adbd_wifi_secure_connect` calls `send_connect(t)` which writes A_CNXN
 *     to us through TLS. The client MUST wait for that A_CNXN before sending any further
 *     adb wire packets — sending another A_CNXN here makes adbd run `handle_new_connection`
 *     again, which calls `handle_offline()` (firing the disconnect callback) and emits a
 *     stray A_STLS over the encrypted channel.
 *
 * adbd authenticates US via our client cert against `/data/misc/adb/adb_keys`. TASK-080
 * (closes TASK-008 SF-1): WE authenticate the SERVER by pinning. The key enrolled at pairing
 * time via bounded CONNECT-plane enrollment is the only server key accepted. Without a
 * fresh pairing capability or enrollment this refuses to dial; a different key fails the
 * handshake ([ServerKeyMismatchException]). The loopback-only host guard stays in front.
 */
internal object AdbTlsClient {
    /** Bidirectional byte-stream channel over the post-handshake mTLS connection. */
    interface TlsChannel : Closeable {
        val inputStream: InputStream
        val outputStream: OutputStream

        /**
         * Reset the underlying socket's SO_TIMEOUT after the handshake completes. 0 = infinite.
         * The handshake uses a short bounded timeout; long-lived streams (CDP WebSocket relay)
         * must clear it so idle gaps between frames don't tear down the socket.
         */
        fun setIdleReadTimeoutMs(ms: Int)
    }

    fun connectWithStls(
        host: String,
        port: Int,
        keyStore: AdbCryptoKeyStore,
        handshakeTimeoutMs: Int,
        enrollment: ConnectKeyEnrollment? = null,
    ): TlsChannel {
        val address = LoopbackAdbHost.requireAddress(host)
        // Only a fresh pairing capability may bypass the existing-pin pre-dial requirement.
        val pin =
            if (enrollment != null) {
                enrollment.claim(host, port)
                PinnedServerTrustManager(enrollment)
            } else {
                val enrolled = keyStore.enrollment.current() ?: throw ServerNotEnrolledException()
                PinnedServerTrustManager(enrolled.key)
            }
        WirelessAdbProviders.ensure()
        val material = keyStore.loadOrCreate()

        val plain = Socket()
        var connected = false
        try {
            plain.tcpNoDelay = true
            val target = InetSocketAddress(address, port)
            enrollment?.beforeDial()
            plain.connect(target, handshakeTimeoutMs)
            plain.soTimeout = handshakeTimeoutMs
            negotiateStls(plain)
            val channel = pinnedHandshake(plain, target, material, pin, handshakeTimeoutMs)
            try {
                enrollment?.commit(keyStore.enrollment)
            } catch (e: IOException) {
                channel.close()
                throw e
            }
            connected = true
            return channel
        } finally {
            if (!connected) runCatching { plain.close() }
        }
    }

    private fun negotiateStls(plain: Socket) {
        // Step 1: pre-TLS A_CNXN -> A_STLS handshake (plaintext). Banner advertises only the
        // features we actually implement on the wire. Notably we do NOT advertise `delayed_ack`:
        // with delayed_ack negotiated, every A_OKAY must carry a 4-byte `acked_bytes` payload
        // (see AOSP packages/modules/adb/sockets.cpp `local_socket_ack`), and our minimal client
        // sends bare A_OKAYs. Mismatched delayed-ack state would silently no-op the ack on the
        // daemon side. Listing common features keeps the banner shape adbd expects.
        AdbProtocol.Message.write(
            plain.getOutputStream(),
            AdbProtocol.A_CNXN,
            AdbProtocol.A_VERSION_SKIP_CHECKSUM,
            AdbProtocol.A_MAX_PAYLOAD,
            "host::features=shell_v2,cmd,stat_v2,fixed_push_mkdir,apex,abb_exec,sendrecv_v2 ".toByteArray(Charsets.UTF_8),
        )
        plain.getOutputStream().flush()

        val first = AdbProtocol.Message.read(plain.getInputStream())
        Log.i(
            TAG,
            "wireless adb greeting cmd=0x${"%08x".format(
                first.command,
            )} arg0=0x${"%08x".format(first.arg0)} arg1=${first.arg1} payloadLen=${first.payload.size}",
        )
        if (first.command != A_STLS) {
            throw java.io.IOException(
                "unexpected greeting from wireless adb: cmd=0x${"%08x".format(first.command)}",
            )
        }
        AdbProtocol.Message.write(
            plain.getOutputStream(),
            A_STLS,
            A_STLS_VERSION,
            0,
            ByteArray(0),
        )
        plain.getOutputStream().flush()
    }

    private fun pinnedHandshake(
        plain: Socket,
        target: InetSocketAddress,
        material: AdbCryptoKeyStore.Material,
        pin: PinnedServerTrustManager,
        handshakeTimeoutMs: Int,
    ): TlsChannel {
        // Step 2: mTLS handshake. Mirrors libadb-android's SslUtils - provider-qualified to the
        // bundled Conscrypt registered by [WirelessAdbProviders] (the platform's hidden Conscrypt
        // can't export keying material on some vendor builds). Only the enrolled server key passes.
        val context = SSLContext.getInstance("TLSv1.3", "Conscrypt")
        context.init(
            arrayOf(SingleCertKeyManager(material.keyPair.private, material.certificate)),
            arrayOf<javax.net.ssl.TrustManager>(pin),
            SecureRandom(),
        )
        val factory: SSLSocketFactory = context.socketFactory
        val tls = factory.createSocket(plain, target.hostString, target.port, true) as SSLSocket
        tls.useClientMode = true
        tls.enabledProtocols = arrayOf("TLSv1.3")
        tls.soTimeout = handshakeTimeoutMs
        try {
            tls.startHandshake()
        } catch (e: SSLException) {
            throw when {
                pin.enrollmentRefused -> ServerNotEnrolledException()
                pin.mismatchSeen -> ServerKeyMismatchException()
                else -> e
            }
        }
        // Defence in depth: re-check the negotiated peer key before the channel is handed out.
        requirePinned(pin, tls)
        Log.i(TAG, "TLS session established with the paired device")
        return SocketChannel(tls)
    }

    private fun requirePinned(
        pin: PinnedServerTrustManager,
        tls: SSLSocket,
    ) {
        val peer =
            tls.session.peerCertificates
                .filterIsInstance<X509Certificate>()
                .toTypedArray()
        try {
            pin.verify(peer)
        } catch (e: java.security.cert.CertificateException) {
            val refusal = if (pin.enrollmentRefused) ServerNotEnrolledException() else ServerKeyMismatchException()
            throw refusal.apply { initCause(e) }
        }
    }

    internal class SocketChannel(
        private val socket: Socket,
    ) : TlsChannel {
        override val inputStream: InputStream get() = socket.getInputStream()
        override val outputStream: OutputStream get() = socket.getOutputStream()

        override fun setIdleReadTimeoutMs(ms: Int) {
            socket.soTimeout = ms
        }

        override fun close() {
            runCatching { socket.close() }
        }
    }

    private const val A_STLS_VERSION = 0x01000000

    private class SingleCertKeyManager(
        private val privateKey: PrivateKey,
        private val certificate: X509Certificate,
    ) : X509ExtendedKeyManager() {
        private val chain = arrayOf(certificate)
        private val aliases = arrayOf(ALIAS)

        override fun getClientAliases(
            keyType: String?,
            issuers: Array<out Principal>?,
        ): Array<String> = aliases

        override fun chooseClientAlias(
            keyType: Array<out String>?,
            issuers: Array<out Principal>?,
            socket: Socket?,
        ): String = ALIAS

        override fun getServerAliases(
            keyType: String?,
            issuers: Array<out Principal>?,
        ): Array<String> = aliases

        override fun chooseServerAlias(
            keyType: String?,
            issuers: Array<out Principal>?,
            socket: Socket?,
        ): String = ALIAS

        override fun chooseEngineClientAlias(
            keyType: Array<out String>?,
            issuers: Array<out Principal>?,
            engine: SSLEngine?,
        ): String = ALIAS

        override fun chooseEngineServerAlias(
            keyType: String?,
            issuers: Array<out Principal>?,
            engine: SSLEngine?,
        ): String = ALIAS

        override fun getCertificateChain(alias: String?): Array<X509Certificate> = chain

        override fun getPrivateKey(alias: String?): PrivateKey = privateKey

        companion object {
            private const val ALIAS = "adb"
        }
    }

    private const val TAG = "AdbTlsClient"
}
