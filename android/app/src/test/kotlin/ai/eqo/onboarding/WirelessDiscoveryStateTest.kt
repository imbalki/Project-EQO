package ai.eqo.onboarding

import ai.eqo.adb.pairing.ActivationStepRunner
import ai.eqo.adb.pairing.AdbPairingCode
import ai.eqo.adb.pairing.PairingInput
import ai.eqo.adb.pairing.WirelessAdbEndpoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WirelessDiscoveryStateTest {
    private class FakeRunner(
        private val afterPair: () -> Unit = {},
    ) : ActivationStepRunner {
        val calls = mutableListOf<String>()

        override fun pair(
            endpoints: WirelessAdbEndpoints,
            code: AdbPairingCode,
        ) {
            calls.add("pair")
            afterPair()
        }

        override fun connect(endpoints: WirelessAdbEndpoints) {
            calls.add("connect")
        }

        override fun startHelper(): Unit = error("Notification must not start helper")

        override fun authorizeHelper(): Unit = error("Notification must not approve consent")

        override fun checkBinder(): Unit = error("Notification must not bypass activity")
    }

    private val address = "192.0.2.1"
    private val network = "test-wifi"

    private fun state() = WirelessDiscoveryState().apply { networkChanged(network, setOf(address)) }

    private fun found(
        state: WirelessDiscoveryState,
        type: String,
        port: Int,
    ) = state.found(type, type, address, port, network)

    @Test
    fun bothLocalServicesRemoveNeedForManualPorts() {
        val state = state()
        assertTrue(found(state, WirelessDiscoveryState.PAIRING, 30_001))
        assertTrue(found(state, WirelessDiscoveryState.CONNECT, 30_002))
        assertEquals(30_001, state.endpoints()?.pairingPort)
        assertEquals(30_002, state.endpoints()?.connectionPort)
        state.timeout()
        assertFalse(state.manualFallback)
    }

    @Test
    fun timeoutOffersManualFallbackAndLateDiscoveryRecovers() {
        val state = state()
        assertFalse(state.manualFallback)
        state.timeout()
        assertTrue(state.manualFallback)
        found(state, WirelessDiscoveryState.PAIRING, 30_001)
        assertTrue(state.manualFallback)
        found(state, WirelessDiscoveryState.CONNECT, 30_002)
        assertFalse(state.manualFallback)
    }

    @Test
    fun rejectsOtherNetworkAndOtherDevicesEvenOnSameSubnet() {
        val state = state()
        assertFalse(state.found("peer", WirelessDiscoveryState.PAIRING, "192.0.2.2", 30_001, network))
        assertFalse(state.found("stale", WirelessDiscoveryState.PAIRING, address, 30_001, "other-wifi"))
        assertFalse(state.found("unknown", WirelessDiscoveryState.PAIRING, null, 30_001, network))
        assertNull(state.pairingPort)
    }

    @Test
    fun wifiLossAndNetworkChangeInvalidateAllEndpoints() {
        val state = state()
        found(state, WirelessDiscoveryState.PAIRING, 30_001)
        found(state, WirelessDiscoveryState.CONNECT, 30_002)
        state.networkChanged("other-wifi", setOf("192.0.2.3"))
        assertNull(state.endpoints())
        state.networkChanged(null, emptySet())
        assertFalse(state.found("late", WirelessDiscoveryState.PAIRING, address, 30_001, null))
        assertNull(state.networkId)
    }

    @Test
    fun serviceLossAndAmbiguityFailClosed() {
        val state = state()
        found(state, WirelessDiscoveryState.PAIRING, 30_001)
        found(state, WirelessDiscoveryState.CONNECT, 30_002)
        state.found("ambiguous", WirelessDiscoveryState.PAIRING, address, 30_003, network)
        assertNull(state.endpoints())
        state.lost("ambiguous")
        assertEquals(30_001, state.pairingPort)
        state.lost(WirelessDiscoveryState.PAIRING)
        assertNull(state.endpoints())
    }

    @Test
    fun invalidTypePortAndSwappedPortsCannotFormEndpoints() {
        val state = state()
        assertFalse(found(state, "_unrelated._tcp.", 30_001))
        assertFalse(found(state, WirelessDiscoveryState.PAIRING, 0))
        assertFalse(found(state, WirelessDiscoveryState.CONNECT, 65_536))
        found(state, WirelessDiscoveryState.PAIRING, 30_001)
        found(state, WirelessDiscoveryState.CONNECT, 30_001)
        assertNull(state.endpoints())
    }

    @Test
    fun notificationReplyRequiresSixAsciiDigitsAndPreservesLeadingZeros() {
        assertEquals("001234", notificationPairingCode(" 001234 ")?.digits)
        listOf(null, "", "12345", "1234567", "123 456", "１２３４５６", "abcdef").forEach {
            assertNull(notificationPairingCode(it))
        }
    }

    @Test
    fun notificationPairsAndConnectsWithSameRunnerButNeverAuthorizesHelper() {
        val runner = FakeRunner()
        val report = pairNotificationReply(runner, input()) { true }
        assertFalse(report.allPassed)
        assertEquals(2, report.records.size)
        assertEquals(listOf("pair", "connect"), runner.calls)
    }

    @Test
    fun notificationRejectsWifiLossBeforePairAndBeforeConnect() {
        val noWifi = FakeRunner()
        assertThrows(IllegalStateException::class.java) { pairNotificationReply(noWifi, input()) { false } }
        assertTrue(noWifi.calls.isEmpty())
        var wifi = true
        val changedWifi = FakeRunner { wifi = false }
        assertThrows(IllegalStateException::class.java) { pairNotificationReply(changedWifi, input()) { wifi } }
        assertEquals(listOf("pair"), changedWifi.calls)
    }

    @Test
    fun serviceTypeWithoutTrailingDotAndAddressRotationAreHandled() {
        val state = state()
        assertTrue(found(state, WirelessDiscoveryState.PAIRING.trimEnd('.'), 30_001))
        assertEquals(30_001, state.pairingPort)
        val revision = state.revision
        state.networkChanged(network, setOf("192.0.2.3"))
        assertTrue(state.revision > revision)
        assertNull(state.pairingPort)
    }

    private fun input() = PairingInput(WirelessAdbEndpoints(30_001, 30_002), notificationPairingCode("001234")!!)
}
