// Origin: imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536,
//   path: app/src/main/kotlin/ai/closepaw/browser/cdp/wireless/AdbPairingTls.kt
// TASK-008 (issue #13): extracted into :adb-pairing for the guided on-phone pairing flow.
// Changes vs donor: package renamed to ai.eqo.adb.pairing; default peer label rebranded to
// EQO. Provenance record: android/Phase-One/evidence/task-008-wireless-adb-pairing.md.
package ai.eqo.adb.pairing

import android.annotation.SuppressLint
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

internal object AdbPairingTls {
    private const val EPHEMERAL_ALIAS = "adb-pair-ephemeral"

    fun connect(
        host: String,
        port: Int,
        timeoutMs: Int,
    ): SSLSocket {
        val address = LoopbackAdbHost.requireAddress(host)
        WirelessAdbProviders.ensure()
        val ephemeral = generateEphemeralMaterial()
        val sslContext =
            SSLContext.getInstance("TLSv1.3", "Conscrypt").apply {
                init(
                    arrayOf<KeyManager>(SingleCertKeyManager(ephemeral.first, ephemeral.second)),
                    arrayOf<TrustManager>(TrustAllManager()),
                    SecureRandom(),
                )
            }
        val plain = Socket()
        plain.connect(InetSocketAddress(address, port), timeoutMs)
        plain.soTimeout = timeoutMs
        val sslSocket = sslContext.socketFactory.createSocket(plain, host, port, true) as SSLSocket
        sslSocket.useClientMode = true
        sslSocket.enabledProtocols = arrayOf("TLSv1.3")
        sslSocket.soTimeout = timeoutMs
        sslSocket.startHandshake()
        return sslSocket
    }

    private fun generateEphemeralMaterial(): Pair<KeyPair, X509Certificate> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 60_000L)
        val notAfter = Date(now + 24L * 60L * 60L * 1000L)
        val serial =
            BigInteger(64, SecureRandom()).abs().let {
                if (it.signum() == 0) BigInteger.ONE else it
            }
        val name = X500Principal("CN=Adb, O=Android, C=US")
        val builder = JcaX509v3CertificateBuilder(name, serial, notBefore, notAfter, name, keyPair.public)
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment),
        )
        val signer =
            JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.private)
        val holder = builder.build(signer)
        val cert =
            JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder)
        return keyPair to cert
    }

    @Synchronized
    private fun ensureBouncyCastle() {
        WirelessAdbProviders.ensure()
    }

    private class SingleCertKeyManager(
        private val keyPair: KeyPair,
        private val cert: X509Certificate,
    ) : X509ExtendedKeyManager() {
        private val chain = arrayOf(cert)

        override fun getClientAliases(
            keyType: String?,
            issuers: Array<out Principal>?,
        ): Array<String> = arrayOf(EPHEMERAL_ALIAS)

        override fun chooseClientAlias(
            keyType: Array<out String>?,
            issuers: Array<out Principal>?,
            socket: java.net.Socket?,
        ): String = EPHEMERAL_ALIAS

        override fun getServerAliases(
            keyType: String?,
            issuers: Array<out Principal>?,
        ): Array<String> = arrayOf(EPHEMERAL_ALIAS)

        override fun chooseServerAlias(
            keyType: String?,
            issuers: Array<out Principal>?,
            socket: java.net.Socket?,
        ): String = EPHEMERAL_ALIAS

        override fun getCertificateChain(alias: String?): Array<X509Certificate> = chain

        override fun getPrivateKey(alias: String?): PrivateKey = keyPair.private
    }

    // Like AOSP pairing_connection.cpp: TLS accepts the certificate, then runPair
    // authenticates the peer using the PSK + TLS exporter, SPAKE2 and AES-GCM peer info.
    // Numeric-loopback-only dialing prevents use against LAN/public peers; it does not
    // prove a local listener's identity. SECURITY PASS MUST DECIDE the remaining risk.
    @SuppressLint("CustomX509TrustManager")
    private class TrustAllManager : X509TrustManager {
        @SuppressLint("TrustAllX509TrustManager")
        override fun checkClientTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
        ) = Unit

        @SuppressLint("TrustAllX509TrustManager")
        override fun checkServerTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
        ) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
