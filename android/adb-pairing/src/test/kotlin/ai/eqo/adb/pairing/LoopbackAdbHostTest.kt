package ai.eqo.adb.pairing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LoopbackAdbHostTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val remoteHosts = listOf("203.0.113.10", "198.51.100.1", "8.8.8.8", "2001:4860:4860::8888")

    @Test
    fun acceptsEntireIpv4LoopbackRange() {
        listOf("127.0.0.1", "127.0.0.0", "127.42.3.4", "127.255.255.255").forEach {
            assertTrue(LoopbackAdbHost.requireAddress(it).isLoopbackAddress)
        }
    }

    @Test
    fun acceptsIpv6Loopback() {
        listOf("::1", "0:0:0:0:0:0:0:1").forEach {
            assertTrue(LoopbackAdbHost.requireAddress(it).isLoopbackAddress)
        }
    }

    @Test
    fun rejectsLanPublicAndWildcardAddresses() {
        (remoteHosts + listOf("0.0.0.0", "::", "128.0.0.1", "126.255.255.255")).forEach {
            assertThrows(IllegalArgumentException::class.java) { LoopbackAdbHost.requireAddress(it) }
        }
    }

    @Test
    fun rejectsDnsAmbiguousAndMalformedHosts() {
        val invalidHosts =
            listOf("localhost", "example.com", "127.1", "2130706433", "127.0.0.256", "127.00.0.1", " ::1", "::1%lo")
        invalidHosts.forEach {
            assertThrows(IllegalArgumentException::class.java) { LoopbackAdbHost.requireAddress(it) }
        }
    }

    @Test
    fun connectionClientRejectsRemoteBeforeKeysOrNetwork() {
        val directory = File(tmp.root, "connect-keys")
        remoteHosts.forEach {
            assertThrows(IllegalArgumentException::class.java) {
                AdbTlsClient.connectWithStls(it, 1, AdbCryptoKeyStore(directory), 1)
            }
        }
        assertFalse(directory.exists())
    }

    @Test
    fun pairingClientRejectsRemoteBeforeKeysOrNetwork() {
        val directory = File(tmp.root, "pair-keys")
        val client = AdbPairingClient(AdbCryptoKeyStore(directory))
        remoteHosts.forEach {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { client.pair(it, 1, "123456".toByteArray(), 1) }
            }
        }
        assertFalse(directory.exists())
    }

    @Test
    fun pairingTlsRejectsRemoteBeforeProviderOrNetwork() {
        remoteHosts.forEach {
            assertThrows(IllegalArgumentException::class.java) { AdbPairingTls.connect(it, 1, 1) }
        }
    }
}
