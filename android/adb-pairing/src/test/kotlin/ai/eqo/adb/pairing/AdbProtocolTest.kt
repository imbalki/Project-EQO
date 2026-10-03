package ai.eqo.adb.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Host-JVM wire-framing tests for the extracted AOSP adb protocol codec. */
class AdbProtocolTest {
    @Test
    fun writeThenReadRoundTrip() {
        val payload = "host::".toByteArray()
        val out = ByteArrayOutputStream()
        AdbProtocol.Message.write(out, AdbProtocol.A_CNXN, 0x0100_0001, 1024, payload)

        val read = AdbProtocol.Message.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(AdbProtocol.A_CNXN, read.command)
        assertEquals(0x0100_0001, read.arg0)
        assertEquals(1024, read.arg1)
        assertTrue(read.payload.contentEquals(payload))
    }

    @Test
    fun magicIsCommandBitwiseInverse() {
        val out = ByteArrayOutputStream()
        AdbProtocol.Message.write(out, AdbProtocol.A_STLS, 1, 0, ByteArray(0))
        val bytes = out.toByteArray()
        // magic is the last uint32 of the header, little-endian, = ~command
        val command = AdbProtocol.A_STLS
        val magic =
            (bytes[20].toInt() and 0xFF) or
                ((bytes[21].toInt() and 0xFF) shl 8) or
                ((bytes[22].toInt() and 0xFF) shl 16) or
                ((bytes[23].toInt() and 0xFF) shl 24)
        assertEquals(command.inv(), magic)
    }

    @Test
    fun badMagicIsRejected() {
        val out = ByteArrayOutputStream()
        AdbProtocol.Message.write(out, AdbProtocol.A_OKAY, 1, 2, ByteArray(0))
        val bytes = out.toByteArray()
        bytes[23] = (bytes[23].toInt() xor 0x01).toByte()
        val ex =
            assertThrows(IOException::class.java) {
                AdbProtocol.Message.read(ByteArrayInputStream(bytes))
            }
        assertTrue(ex.message.orEmpty().contains("magic"))
    }

    @Test
    fun openLocalAbstractEncodesDestination() {
        val msg = AdbProtocol.openLocalAbstract(1, "test_socket")
        assertEquals(AdbProtocol.A_OPEN, msg.command)
        assertEquals("localabstract:test_socket\u0000", String(msg.payload, Charsets.UTF_8))
    }
}
