package ai.eqo.adb.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

/**
 * TASK-080 (closes TASK-008 SF-1): server enrollment and pinning, host-verified. The TLS
 * handshake itself needs the bundled Conscrypt (device-only); the decision logic that the
 * handshake calls ([PinnedServerTrustManager], the enrollment record, the fail-closed
 * pre-dial check) is exercised here with real X.509 certificates.
 */
class ServerPinTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(name: String) = AdbCryptoKeyStore(File(tmp.root, name))

    private fun serverCert(name: String): X509Certificate = store(name).loadOrCreate().certificate

    @Test
    fun enrollmentIsStoredAndReadBack() {
        val client = store("client")
        val server = ServerKeyFingerprint.of(serverCert("server-a"))
        assertNull("nothing enrolled before pairing", client.enrollment.current())

        client.enrollment.enroll(server, "adb-ABC123-xyz", nowMs = 1234L)

        val read = store("client").enrollment.current()
        assertNotNull(read)
        assertEquals(server, read!!.key)
        assertEquals("adb-ABC123-xyz", read.peerGuid)
        assertEquals(1234L, read.enrolledAtMs)
    }

    @Test
    fun matchingServerKeyIsAcceptedOnEveryTrustCallback() {
        val cert = serverCert("server-a")
        val pin = PinnedServerTrustManager(ServerKeyFingerprint.of(cert))
        pin.checkServerTrusted(arrayOf(cert), "RSA")
        pin.checkServerTrusted(arrayOf(cert), "RSA", null as java.net.Socket?)
        pin.checkServerTrusted(arrayOf(cert), "RSA", null as javax.net.ssl.SSLEngine?)
        assertFalse(pin.mismatchSeen)
    }

    @Test
    fun mismatchingServerKeyIsRejected() {
        val enrolled = serverCert("server-a")
        val impostor = serverCert("impostor")
        val pin = PinnedServerTrustManager(ServerKeyFingerprint.of(enrolled))
        assertThrows(CertificateException::class.java) { pin.checkServerTrusted(arrayOf(impostor), "RSA") }
        assertTrue(pin.mismatchSeen)
    }

    @Test
    fun emptyOrMissingChainIsRejected() {
        val pin = PinnedServerTrustManager(ServerKeyFingerprint.of(serverCert("server-a")))
        assertThrows(CertificateException::class.java) { pin.checkServerTrusted(emptyArray(), "RSA") }
        assertThrows(CertificateException::class.java) { pin.checkServerTrusted(null, "RSA") }
    }

    @Test
    fun pinnedManagerNeverAcceptsClientCertificates() {
        val cert = serverCert("server-a")
        val pin = PinnedServerTrustManager(ServerKeyFingerprint.of(cert))
        assertThrows(CertificateException::class.java) { pin.checkClientTrusted(arrayOf(cert), "RSA") }
    }

    @Test
    fun connectWithNoEnrollmentFailsClosedBeforeKeysOrNetwork() {
        val directory = File(tmp.root, "never-paired")
        assertThrows(ServerNotEnrolledException::class.java) {
            AdbTlsClient.connectWithStls("127.0.0.1", 1, AdbCryptoKeyStore(directory), 1)
        }
        assertFalse("no key material may be created for a refused connect", directory.exists())
    }

    @Test
    fun connectWithKeysButNoEnrollmentStillFailsClosed() {
        val keys = store("keys-only")
        keys.loadOrCreate()
        assertThrows(ServerNotEnrolledException::class.java) {
            AdbTlsClient.connectWithStls("127.0.0.1", 1, keys, 1)
        }
    }

    @Test
    fun cleared_enrollment_refuses_again() {
        val keys = store("client")
        keys.enrollment.enroll(ServerKeyFingerprint.of(serverCert("server-a")), null)
        keys.enrollment.clear()
        assertNull(keys.enrollment.current())
        assertThrows(ServerNotEnrolledException::class.java) {
            AdbTlsClient.connectWithStls("127.0.0.1", 1, keys, 1)
        }
    }

    @Test
    fun rePairReplacesTheEnrollment() {
        val keys = store("client")
        val first = ServerKeyFingerprint.of(serverCert("server-a"))
        val second = ServerKeyFingerprint.of(serverCert("server-b"))
        keys.enrollment.enroll(first, "adb-one")
        keys.enrollment.enroll(second, "adb-two")

        val read = keys.enrollment.current()!!
        assertEquals(second, read.key)
        assertEquals("adb-two", read.peerGuid)
        assertNotEquals(first, read.key)
        // The old server is no longer accepted by a pin built from the current enrollment.
        val oldCert = serverCert("server-a")
        assertThrows(CertificateException::class.java) {
            PinnedServerTrustManager(read.key).checkServerTrusted(arrayOf(oldCert), "RSA")
        }
    }

    @Test
    fun editedEnrollmentRecordIsRejected() {
        val keys = store("client")
        keys.enrollment.enroll(ServerKeyFingerprint.of(serverCert("server-a")), "adb-one")
        val file = File(File(tmp.root, "client"), ServerEnrollmentStore.FILE_NAME)
        val forged = ServerKeyFingerprint.of(serverCert("impostor")).encode()
        val edited = file.readLines().joinToString("\n") { if (it.startsWith("server=")) "server=$forged" else it }
        file.writeText(edited + "\n")
        assertNull("a record whose MAC no longer matches must not enroll anything", keys.enrollment.current())
    }

    @Test
    fun enrollmentCopiedFromAnotherClientKeyIsRejected() {
        val original = store("client")
        original.enrollment.enroll(ServerKeyFingerprint.of(serverCert("server-a")), null)
        val other = store("other-client")
        other.loadOrCreate()
        File(File(tmp.root, "client"), ServerEnrollmentStore.FILE_NAME)
            .copyTo(File(File(tmp.root, "other-client"), ServerEnrollmentStore.FILE_NAME))
        assertNull(other.enrollment.current())
    }

    @Test
    fun resettingTheKeyStoreDropsTheEnrollment() {
        val keys = store("client")
        keys.enrollment.enroll(ServerKeyFingerprint.of(serverCert("server-a")), null)
        keys.reset()
        assertNull(keys.enrollment.current())
    }

    @Test
    fun nothingSecretReachesStringsOrLogs() {
        val cert = serverCert("server-a")
        val fingerprint = ServerKeyFingerprint.of(cert)
        val encoded = fingerprint.encode()
        val enrolled = EnrolledServer(fingerprint, "adb-guid", 1L)
        listOf(
            fingerprint.toString(),
            enrolled.toString(),
            ServerKeyMismatchException().message.orEmpty(),
            ServerNotEnrolledException().message.orEmpty(),
            runCatching {
                PinnedServerTrustManager(fingerprint).checkServerTrusted(arrayOf(serverCert("impostor")), "RSA")
            }.exceptionOrNull()?.message.orEmpty(),
        ).forEach { text -> assertFalse("fingerprint leaked into: $text", text.contains(encoded)) }
    }

    @Test
    fun productionLogCallsNeverCarryKeysCodesOrFingerprints() {
        val sensitive =
            Regex("fingerprint|enroll|psk|digits|pairingcode|private|secret|\\.encode\\(", RegexOption.IGNORE_CASE)
        val offenders = mutableListOf<String>()
        mainSources().forEach { file ->
            file.readLines().filter { it.contains("Log.") && !it.trim().startsWith("//") }.forEach { line ->
                if (sensitive.containsMatchIn(line)) offenders += "${file.name}: ${line.trim()}"
            }
        }
        assertTrue("log lines with sensitive terms: $offenders", offenders.isEmpty())
    }

    @Test
    fun noTrustAllManagerRemainsOnTheConnectPlane() {
        val connect = File("src/main/kotlin/ai/eqo/adb/pairing/AdbTlsClient.kt").readText()
        assertFalse(connect.contains("TrustAll"))
        assertTrue(connect.contains("PinnedServerTrustManager"))
        // The only unchecked manager is the pairing-plane capture, authenticated by SPAKE2.
        // Its distinct PAIR key is never enrolled for CONNECT; bounded TOFU has its own checks.
        val acceptors =
            mainSources()
                .filter { Regex("checkServerTrusted\\([^)]*\\)\\s*=\\s*Unit").containsMatchIn(it.readText()) }
                .map { it.name }
        assertEquals(listOf("AdbPairingTls.kt"), acceptors)
        val pairingTls = File("src/main/kotlin/ai/eqo/adb/pairing/AdbPairingTls.kt").readText()
        assertFalse(pairingTls.contains("class TrustAllManager"))
    }

    private fun mainSources(): List<File> =
        File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
}
