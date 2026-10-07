package ai.eqo.adb.pairing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.cert.CertificateException

/** Real X.509 keys, fake monotonic clock. No device TLS success is claimed by these tests. */
class ConnectKeyEnrollmentTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val client by lazy { AdbCryptoKeyStore(File(tmp.root, "client")) }
    private val pairCert by lazy { AdbCryptoKeyStore(File(tmp.root, "pair")).loadOrCreate().certificate }
    private val connectCert by lazy { AdbCryptoKeyStore(File(tmp.root, "connect")).loadOrCreate().certificate }
    private var nowMs = 0L

    private fun session() =
        runBlocking {
            ConnectKeyEnrollment.afterPairing(HOST, PORT, { nowMs }) {
                PairingResult("client-public-key", "adb-guid", ServerKeyFingerprint.of(pairCert))
            }
        }

    private fun enroll(session: ConnectKeyEnrollment) {
        session.claim(HOST, PORT)
        val trust = PinnedServerTrustManager(session)
        trust.checkServerTrusted(arrayOf(connectCert), "RSA")
        trust.checkServerTrusted(arrayOf(connectCert), "RSA", null as java.net.Socket?)
        trust.checkServerTrusted(arrayOf(connectCert), "RSA", null as javax.net.ssl.SSLEngine?)
        trust.verify(arrayOf(connectCert)) // negotiated peer recheck
        session.commit(client.enrollment)
    }

    @Test
    fun failedSpakeExchangeProducesNoCapabilityOrEnrollment() {
        var token: ConnectKeyEnrollment? = null
        assertThrows(IOException::class.java) {
            runBlocking {
                token =
                    ConnectKeyEnrollment.afterPairing(HOST, PORT, { nowMs }) {
                        throw IOException("authenticated peer-info exchange failed")
                    }
            }
        }
        assertNull(token)
        assertNull(client.enrollment.current())
        assertThrows(ServerNotEnrolledException::class.java) {
            AdbTlsClient.connectWithStls(HOST, PORT, client, 1)
        }
    }

    @Test
    fun successPermitsConnectKeyNotPairingKeyAndOnlyAfterHandshake() {
        val token = session()
        assertNull(client.enrollment.current())
        token.claim(HOST, PORT)
        val trust = PinnedServerTrustManager(token)
        trust.verify(arrayOf(connectCert))
        assertNull("TLS callbacks alone must not persist a pin", client.enrollment.current())
        token.commit(client.enrollment)
        val stored = client.enrollment.current()!!
        assertEquals(ServerKeyFingerprint.of(connectCert), stored.key)
        assertNotEquals(ServerKeyFingerprint.of(pairCert), stored.key)
        assertEquals("adb-guid", stored.peerGuid)
    }

    @Test
    fun enrollmentIsSingleUseAndMismatchAfterwardsFailsClosed() {
        val token = session()
        enroll(token)
        assertThrows(ServerNotEnrolledException::class.java) { token.claim(HOST, PORT) }
        assertThrows(ServerNotEnrolledException::class.java) { token.commit(client.enrollment) }
        val pin = PinnedServerTrustManager(client.enrollment.current()!!.key)
        pin.verify(arrayOf(connectCert))
        assertThrows(CertificateException::class.java) { pin.verify(arrayOf(pairCert)) }
        assertTrue(pin.mismatchSeen)
        assertEquals(ServerKeyFingerprint.of(connectCert), client.enrollment.current()!!.key)
    }

    @Test
    fun pendingCapabilityDoesNotEnablePlainConnect() {
        session() // capability not passed: plain connect still refuses before keys/network
        assertThrows(ServerNotEnrolledException::class.java) {
            AdbTlsClient.connectWithStls(HOST, PORT, client, 1)
        }
        assertNull(client.enrollment.current())
        assertFalse(client.isPersisted())
    }

    @Test
    fun windowStartsAfterPairingAndExpiresAtBoundaryBeforeDial() {
        val token =
            runBlocking {
                ConnectKeyEnrollment.afterPairing(HOST, PORT, { nowMs }) {
                    nowMs = 100_000L // pairing duration does not consume the enrollment window
                    PairingResult("client-public-key", null, ServerKeyFingerprint.of(pairCert))
                }
            }
        nowMs += ConnectKeyEnrollment.WINDOW_MS
        assertThrows(ServerNotEnrolledException::class.java) {
            AdbTlsClient.connectWithStls(HOST, PORT, client, 1, token)
        }
        assertNull(client.enrollment.current())
        assertFalse(client.isPersisted())
    }

    @Test
    fun expiryDuringHandshakeIsRejectedAndNeverPersisted() {
        val token = session()
        token.claim(HOST, PORT)
        val trust = PinnedServerTrustManager(token)
        trust.verify(arrayOf(connectCert))
        nowMs = ConnectKeyEnrollment.WINDOW_MS
        assertThrows(CertificateException::class.java) { trust.verify(arrayOf(connectCert)) }
        assertTrue(trust.enrollmentRefused)
        assertThrows(ServerNotEnrolledException::class.java) { token.commit(client.enrollment) }
        assertNull(client.enrollment.current())
    }

    @Test
    fun expiryBeforeCommitAndBackwardClockFailClosed() {
        val token = session()
        token.claim(HOST, PORT)
        token.verify(arrayOf(connectCert))
        nowMs = ConnectKeyEnrollment.WINDOW_MS
        assertThrows(ServerNotEnrolledException::class.java) { token.commit(client.enrollment) }
        nowMs = 0
        val another = session()
        nowMs = -1
        assertThrows(ServerNotEnrolledException::class.java) { another.claim(HOST, PORT) }
        assertNull(client.enrollment.current())
    }

    @Test
    fun differentAddressOrPortConsumesOpportunity() {
        val wrongAddress = session()
        assertThrows(ServerNotEnrolledException::class.java) { wrongAddress.claim("::1", PORT) }
        assertThrows(ServerNotEnrolledException::class.java) { wrongAddress.claim(HOST, PORT) }
        val wrongPort = session()
        assertThrows(ServerNotEnrolledException::class.java) { wrongPort.claim(HOST, PORT + 1) }
        assertThrows(ServerNotEnrolledException::class.java) { wrongPort.claim(HOST, PORT) }
        assertNull(client.enrollment.current())
    }

    @Test
    fun secondAttemptAndUnclaimedVerificationAreRefused() {
        val unclaimed = session()
        assertThrows(CertificateException::class.java) { unclaimed.verify(arrayOf(connectCert)) }
        assertThrows(ServerNotEnrolledException::class.java) { unclaimed.claim(HOST, PORT) }
        val token = session()
        token.claim(HOST, PORT)
        assertThrows(ServerNotEnrolledException::class.java) { token.claim(HOST, PORT) }
        assertThrows(ServerNotEnrolledException::class.java) { token.commit(client.enrollment) }
        assertNull(client.enrollment.current())
    }

    @Test
    fun emptyChainAndChangedKeyDuringHandshakeAreRefused() {
        val empty = session()
        empty.claim(HOST, PORT)
        assertThrows(CertificateException::class.java) { empty.verify(emptyArray()) }
        assertThrows(ServerNotEnrolledException::class.java) { empty.commit(client.enrollment) }
        val missing = session()
        missing.claim(HOST, PORT)
        assertThrows(CertificateException::class.java) { missing.verify(null) }
        assertThrows(ServerNotEnrolledException::class.java) { missing.commit(client.enrollment) }
        val token = session()
        token.claim(HOST, PORT)
        val trust = PinnedServerTrustManager(token)
        trust.verify(arrayOf(connectCert))
        assertThrows(CertificateException::class.java) { trust.verify(arrayOf(pairCert)) }
        assertTrue(trust.mismatchSeen)
        assertThrows(ServerNotEnrolledException::class.java) { token.commit(client.enrollment) }
        assertNull(client.enrollment.current())
    }

    @Test
    fun expiryAfterClaimBeforeDialIsRejected() {
        val token = session()
        token.claim(HOST, PORT)
        nowMs = ConnectKeyEnrollment.WINDOW_MS - 1
        token.beforeDial()
        nowMs = ConnectKeyEnrollment.WINDOW_MS
        assertThrows(ServerNotEnrolledException::class.java) { token.beforeDial() }
        assertThrows(ServerNotEnrolledException::class.java) { token.commit(client.enrollment) }
        assertNull(client.enrollment.current())
        assertFalse(client.isPersisted())
    }

    @Test
    fun freshRepairCanReplaceAnExistingPin() {
        client.enrollment.enroll(ServerKeyFingerprint.of(pairCert), "old-guid")
        enroll(session())
        assertEquals(ServerKeyFingerprint.of(connectCert), client.enrollment.current()!!.key)
    }

    @Test
    fun productionEnrollmentIsOnlyInSessionAndRunnerUsesFreshPairing() {
        val root = File("src/main/kotlin/ai/eqo/adb/pairing")
        val pair = File(root, "AdbPairingClient.kt").readText()
        assertFalse(pair.contains(".enroll("))
        val runner = File(root, "WirelessAdbActivationRunner.kt").readText()
        assertTrue(runner.contains("ConnectKeyEnrollment.afterPairing(LOCALHOST, endpoints.connectionPort)"))
        assertTrue(runner.contains("pendingEnrollment = null // consume even on failed dial"))
        val writes =
            root
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { Regex("\\.enroll\\(").containsMatchIn(it.readText()) }
                .map { it.name }
                .toList()
        assertEquals(listOf("ConnectKeyEnrollment.kt"), writes)
    }

    companion object {
        private const val HOST = "127.0.0.1"
        private const val PORT = 37123
    }
}
