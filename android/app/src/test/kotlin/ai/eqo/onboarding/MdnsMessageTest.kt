package ai.eqo.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetAddress

class MdnsMessageTest {
    private val instance = "fixture._adb-tls-pairing._tcp.local."
    private val own = InetAddress.getByName("192.0.2.1")

    @Test
    fun validSrvAndCompressedASelectOnlyOwnPhone() {
        val records = MdnsMessage.parse(response(own.address))
        assertEquals(2, records.size)
        assertEquals(own to 30_001, MdnsMessage.ownEndpoint(records, instance, setOf(own)))
        assertNull(MdnsMessage.ownEndpoint(records, "other.$instance", setOf(own)))
    }

    @Test
    fun aaaaWorksWithoutTextualScopeMatching() {
        val local = InetAddress.getByName("2001:db8::1")
        val records = MdnsMessage.parse(response(local.address))
        assertEquals(local to 30_001, MdnsMessage.ownEndpoint(records, instance, setOf(local)))
    }

    @Test
    fun everyTruncatedPrefixFailsWithoutPartialRecords() {
        val valid = response(own.address)
        for (size in valid.indices) assertTrue("prefix $size", MdnsMessage.parse(valid.copyOf(size)).isEmpty())
    }

    @Test
    fun compressionLoopAndOutOfBoundsPointersFailClosed() {
        val bytes = response(own.address)
        bytes[12] = 0xc0.toByte()
        bytes[13] = 12
        assertTrue(MdnsMessage.parse(bytes).isEmpty())
        bytes[13] = 0xff.toByte()
        assertTrue(MdnsMessage.parse(bytes).isEmpty())
    }

    @Test
    fun oversizedPacketsAndCountsAreRejected() {
        assertTrue(MdnsMessage.parse(ByteArray(MdnsMessage.MAX_PACKET + 1)).isEmpty())
        val bytes = response(own.address)
        bytes[6] = 1
        assertTrue(MdnsMessage.parse(bytes).isEmpty())
    }

    @Test
    fun wrongHostGoodbyeTruncatedFlagAndInvalidPortsCannotResolve() {
        val records = MdnsMessage.parse(response(InetAddress.getByName("192.0.2.2").address))
        assertNull(MdnsMessage.ownEndpoint(records, instance, setOf(own)))
        val local = MdnsMessage.parse(response(own.address))
        assertNull(MdnsMessage.ownEndpoint(local.map { it.copy(ttl = 0) }, instance, setOf(own)))
        val invalid = local.map { if (it.type == 33) it.copy(port = 1023) else it }
        assertNull(MdnsMessage.ownEndpoint(invalid, instance, setOf(own)))
        val truncated = response(own.address)
        truncated[2] = 0x82.toByte()
        assertTrue(MdnsMessage.parse(truncated).isEmpty())
    }

    @Test
    fun mixedLocalAndRemoteAddressesAreNotAccepted() {
        val local = MdnsMessage.parse(response(own.address))
        val remote = local.last().copy(address = InetAddress.getByName("192.0.2.2"))
        assertNull(MdnsMessage.ownEndpoint(local + remote, instance, setOf(own)))
    }

    @Test
    fun queryHasBoundedQuestionsAndMulticastClass() {
        val query = MdnsMessage.query(listOf(instance to 33))
        assertEquals(1, query[5].toInt())
        assertEquals(listOf(0, 33, 0, 1), query.takeLast(4).map { it.toInt() and 255 })
    }

    private fun response(address: ByteArray): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        out.writeShort(0)
        out.writeShort(0x8400)
        out.writeShort(0)
        out.writeShort(2)
        out.writeShort(0)
        out.writeShort(0)
        name(out, instance)
        out.writeShort(33)
        out.writeShort(0x8001)
        out.writeInt(120)
        out.writeShort(6 + 12) // "phone.local." encoded length
        out.writeShort(0)
        out.writeShort(0)
        out.writeShort(30_001)
        val hostOffset = bytes.size()
        name(out, "phone.local.")
        out.writeShort(0xc000 or hostOffset)
        out.writeShort(if (address.size == 4) 1 else 28)
        out.writeShort(0x8001)
        out.writeInt(120)
        out.writeShort(address.size)
        out.write(address)
        return bytes.toByteArray()
    }

    private fun name(
        out: DataOutputStream,
        value: String,
    ) {
        value.trimEnd('.').split('.').forEach {
            out.writeByte(it.length)
            out.writeBytes(it)
        }
        out.writeByte(0)
    }
}
