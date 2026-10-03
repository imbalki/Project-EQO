package ai.eqo.adb.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-JVM proof that the extracted SPAKE2-25519 handshake agrees with itself: both
 * roles derive the same session key from the same pairing code, and a different code
 * derives a different key (the protocol-level "wrong code" case).
 */
class Spake25519RoundTripTest {
    private val clientName = "adb pair client\u0000".toByteArray()
    private val serverName = "adb pair server\u0000".toByteArray()

    @Test
    fun bothRolesDeriveTheSameKeyFromTheSameCode() {
        val alice = Spake25519(Spake25519.Role.ALICE, clientName, serverName)
        val bob = Spake25519(Spake25519.Role.BOB, serverName, clientName)

        val aliceMsg = alice.generateMessage(PASSWORD)
        val bobMsg = bob.generateMessage(PASSWORD)

        val aliceKey = alice.processMessage(bobMsg)
        val bobKey = bob.processMessage(aliceMsg)

        assertEquals(64, aliceKey.size)
        assertTrue(aliceKey.contentEquals(bobKey))
    }

    @Test
    fun aDifferentCodeDerivesADifferentKey() {
        val alice = Spake25519(Spake25519.Role.ALICE, clientName, serverName)
        val bob = Spake25519(Spake25519.Role.BOB, serverName, clientName)

        val aliceMsg = alice.generateMessage(PASSWORD)
        val bobMsg = bob.generateMessage("999999".toByteArray())

        val aliceKey = alice.processMessage(bobMsg)
        val bobKey = bob.processMessage(aliceMsg)

        assertFalse(aliceKey.contentEquals(bobKey))
    }

    @Test
    fun messagesAreThirtyTwoBytes() {
        val alice = Spake25519(Spake25519.Role.ALICE, clientName, serverName)
        assertEquals(32, alice.generateMessage(PASSWORD).size)
    }

    @Test
    fun processMessageRequiresGenerateFirst() {
        val alice = Spake25519(Spake25519.Role.ALICE, clientName, serverName)
        val thrown = runCatching { alice.processMessage(ByteArray(32)) }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
    }

    companion object {
        private val PASSWORD = "123456".toByteArray()
    }
}
