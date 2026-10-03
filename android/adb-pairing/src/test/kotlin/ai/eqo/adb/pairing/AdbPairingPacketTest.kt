package ai.eqo.adb.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException

/** Host-JVM framing tests for the extracted pairing packet (donor AdbPairingPacketTest, adapted). */
class AdbPairingPacketTest {
    @Test
    fun writeThenReadRoundTripPreservesTypeAndPayload() {
        val payload = ByteArray(64) { it.toByte() }
        val out = ByteArrayOutputStream()
        AdbPairingPacket.write(out, AdbPairingPacket.TYPE_SPAKE2_MSG, payload)

        val read = AdbPairingPacket.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(AdbPairingPacket.TYPE_SPAKE2_MSG, read.type)
        assertTrue(read.payload.contentEquals(payload))
    }

    @Test
    fun peerInfoRoundTripsAtMaxPayload() {
        val payload = ByteArray(AdbPairingPacket.MAX_PAYLOAD) { (it and 0x7F).toByte() }
        val out = ByteArrayOutputStream()
        AdbPairingPacket.write(out, AdbPairingPacket.TYPE_PEER_INFO, payload)

        val read = AdbPairingPacket.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(AdbPairingPacket.MAX_PAYLOAD, read.payload.size)
    }

    @Test
    fun readRejectsUnsupportedVersion() {
        val bytes = byteArrayOf(0x02, 0x00, 0x00, 0x00, 0x00, 0x00)
        val ex =
            assertThrows(IOException::class.java) {
                AdbPairingPacket.read(ByteArrayInputStream(bytes))
            }
        assertTrue(ex.message.orEmpty().contains("version"))
    }

    @Test
    fun readRejectsPayloadOverMax() {
        // version=1 type=0 payload=16385 (big-endian)
        val bytes = byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x40, 0x01.toByte())
        val ex =
            assertThrows(IOException::class.java) {
                AdbPairingPacket.read(ByteArrayInputStream(bytes))
            }
        assertTrue(ex.message.orEmpty().contains("payload length"))
    }

    @Test
    fun readRejectsUnknownType() {
        val bytes = byteArrayOf(0x01, 0x05, 0x00, 0x00, 0x00, 0x00)
        val ex =
            assertThrows(IOException::class.java) {
                AdbPairingPacket.read(ByteArrayInputStream(bytes))
            }
        assertTrue(ex.message.orEmpty().contains("type"))
    }

    @Test
    fun headerIsBigEndian() {
        val payload = ByteArray(0x0102) { 0x00 }
        val out = ByteArrayOutputStream()
        AdbPairingPacket.write(out, AdbPairingPacket.TYPE_SPAKE2_MSG, payload)
        val bytes = out.toByteArray()
        assertEquals(0x01.toByte(), bytes[0]) // version
        assertEquals(0x00.toByte(), bytes[1]) // type
        assertEquals(0x00.toByte(), bytes[2])
        assertEquals(0x00.toByte(), bytes[3])
        assertEquals(0x01.toByte(), bytes[4])
        assertEquals(0x02.toByte(), bytes[5])
    }

    @Test
    fun readTruncatedHeaderThrows() {
        val bytes = byteArrayOf(0x01, 0x00, 0x00)
        assertThrows(EOFException::class.java) {
            AdbPairingPacket.read(ByteArrayInputStream(bytes))
        }
    }
}
