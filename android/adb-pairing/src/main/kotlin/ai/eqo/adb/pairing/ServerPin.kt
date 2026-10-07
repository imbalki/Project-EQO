/*
 * EQO (TASK-080, issue #20): server pinning for the wireless-ADB connect plane.
 *
 * After fresh pairing, bounded TOFU enrolls adbd's CONNECT key (not the PAIR TLS key).
 * Every later connect refuses a different certificate key. No enrollment and no fresh
 * pairing-session capability means no connection (fail closed).
 *
 * Nothing in this file logs, and [ServerKeyFingerprint] never prints its value, so a
 * fingerprint cannot leak through a log line or an exception message.
 */
package ai.eqo.adb.pairing

import android.annotation.SuppressLint
import java.io.IOException
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager

/** SHA-256 over the DER SubjectPublicKeyInfo of a server certificate. Value is never printed. */
class ServerKeyFingerprint private constructor(
    private val digest: ByteArray,
) {
    init {
        require(digest.size == DIGEST_BYTES) { "fingerprint must be $DIGEST_BYTES bytes" }
    }

    /** Storage form (unpadded base64). Callers must not log it. */
    fun encode(): String = Base64.getEncoder().withoutPadding().encodeToString(digest)

    override fun equals(other: Any?): Boolean {
        if (other !is ServerKeyFingerprint) return false
        return MessageDigest.isEqual(digest, other.digest)
    }

    override fun hashCode(): Int = digest.contentHashCode()

    override fun toString(): String = "ServerKeyFingerprint(redacted)"

    companion object {
        private const val DIGEST_BYTES = 32

        fun of(certificate: X509Certificate): ServerKeyFingerprint =
            ServerKeyFingerprint(MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded))

        fun decode(encoded: String): ServerKeyFingerprint? =
            runCatching { ServerKeyFingerprint(Base64.getDecoder().decode(encoded.trim())) }.getOrNull()
    }
}

/** What pairing recorded about the adbd it paired with. */
data class EnrolledServer(
    val key: ServerKeyFingerprint,
    /** The adbd device guid exchanged inside the authenticated PAIR handshake, if any. */
    val peerGuid: String?,
    val enrolledAtMs: Long,
)

/** Typed refusals of the connect plane. Both mean the owner must pair again. */
sealed class ServerPinException(
    message: String,
) : IOException(message)

/** Nothing was enrolled for this client key: the connect plane refuses to dial. */
class ServerNotEnrolledException : ServerPinException("no enrolled server; pair again before connecting")

/** The server's certificate key is not the enrolled one. The channel was never used. */
class ServerKeyMismatchException : ServerPinException("this is not the device that was paired; pair again")

/**
 * Accepts exactly one server key. Replaces the former blanket trust-all manager on the
 * connect plane: any other certificate fails the handshake and is reported as a
 * mismatch through [mismatchSeen].
 */
@SuppressLint("CustomX509TrustManager") // pinned to one key; checkServerTrusted below validates it
internal class PinnedServerTrustManager private constructor(
    private val enrolled: ServerKeyFingerprint?,
    private val enrollment: ConnectKeyEnrollment?,
) : X509ExtendedTrustManager() {
    constructor(enrolled: ServerKeyFingerprint) : this(enrolled, null)

    constructor(enrollment: ConnectKeyEnrollment) : this(null, enrollment)

    @Volatile
    var mismatchSeen: Boolean = false
        private set

    @Volatile
    var enrollmentRefused: Boolean = false
        private set

    fun verify(chain: Array<out X509Certificate>?) {
        val session = enrollment
        if (session != null) {
            verifyEnrollment(session, chain)
            return
        }
        val leaf = chain?.firstOrNull()
        if (leaf == null || ServerKeyFingerprint.of(leaf) != enrolled) {
            mismatchSeen = true
            throw CertificateException("server key does not match the enrolled key")
        }
    }

    private fun verifyEnrollment(
        session: ConnectKeyEnrollment,
        chain: Array<out X509Certificate>?,
    ) {
        try {
            session.verify(chain)
        } catch (e: ServerNotEnrolledException) {
            enrollmentRefused = true
            throw CertificateException("connect enrollment expired", e)
        } catch (e: CertificateException) {
            mismatchSeen = true
            throw e
        }
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) = verify(chain)

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?,
    ) = verify(chain)

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?,
    ) = verify(chain)

    // We are the TLS client; a client-side check is never legitimate, so refuse.
    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ): Unit = throw CertificateException("client certificates are not accepted here")

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?,
    ): Unit = throw CertificateException("client certificates are not accepted here")

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?,
    ): Unit = throw CertificateException("client certificates are not accepted here")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
