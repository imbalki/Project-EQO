package ai.eqo.onboarding

import ai.eqo.adb.pairing.ActivationStepRunner
import ai.eqo.adb.pairing.AdbPairingCode
import ai.eqo.adb.pairing.WirelessAdbEndpoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WirelessPairingReplyTest {
    @Test
    fun strictCodeAndSpaceOrCommaPortsPreserveLeadingZeroes() {
        for (text in listOf("001234", "001234 30001", "001234,30001,30002", " 001234, 30001 30002 ")) {
            val reply = WirelessPairingReply.parse(text)!!
            assertEquals("001234", reply.code.digits)
            assertNotNull(reply.request(30_001, 30_002))
        }
    }

    @Test
    fun malformedInputIsRejectedBeforeAnyPairing() {
        val invalid =
            listOf(
                "",
                "12345",
                "1234567",
                "１２３４５６",
                "001234\t30001",
                "001234\n30001",
                "001234,",
                "001234 -30001",
                "001234 +30001",
                "001234 1023",
                "001234 65536",
                "001234 123456789",
                "001234 30001 30002 30003",
                "001234 abc",
                "001234 1",
                "001234 01024",
            )
        invalid.dropLast(1).forEach { assertNull(it, WirelessPairingReply.parse(it)) }
        assertNotNull(WirelessPairingReply.parse("001234 1024 65535"))
    }

    @Test
    fun explicitPortsOverrideDiscoveryAndEqualPortsAreRefused() {
        val reply = WirelessPairingReply.parse("001234 30001 30002")!!
        val request = reply.request(40_001, 40_002)!!
        assertEquals(30_001, request.input.endpoints.pairingPort)
        assertEquals(30_002, request.input.endpoints.connectionPort)
        assertNull(WirelessPairingReply.parse("001234 30001 30001")!!.request(null, null))
        assertNull(WirelessPairingReply.parse("001234")!!.request(null, null))
    }

    @Test
    fun twoFieldsPairWithoutGuessingOrConnectingAndNeverAuthorize() {
        val request = WirelessPairingReply.parse("001234 30001")!!.request(null, null)!!
        assertFalse(request.connect)
        val runner = FakeRunner()
        val report = pairNotificationReply(runner, request.input, request.connect) { true }
        assertEquals(listOf("pair"), runner.calls)
        assertFalse(report.allPassed)
        assertEquals(1, report.records.size)
    }

    @Test
    fun threeFieldsUseSamePairAndPinnedConnectOnlyPath() {
        val request = WirelessPairingReply.parse("001234 30001 30002")!!.request(null, null)!!
        val runner = FakeRunner()
        pairNotificationReply(runner, request.input, request.connect) { true }
        assertEquals(listOf("pair", "connect"), runner.calls)
    }

    private class FakeRunner : ActivationStepRunner {
        val calls = mutableListOf<String>()

        override fun pair(
            endpoints: WirelessAdbEndpoints,
            code: AdbPairingCode,
        ) {
            calls.add("pair")
        }

        override fun connect(endpoints: WirelessAdbEndpoints) {
            calls.add("connect")
        }

        override fun startHelper(): Unit = error("must not start helper")

        override fun authorizeHelper(): Unit = error("must not authorize")

        override fun checkBinder(): Unit = error("must not activate")
    }
}
