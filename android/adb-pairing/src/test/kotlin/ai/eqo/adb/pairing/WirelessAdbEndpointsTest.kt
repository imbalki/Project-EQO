package ai.eqo.adb.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WirelessAdbEndpointsTest {
    @Test
    fun distinctPortsAreAcceptedAndNotConfused() {
        val endpoints = WirelessAdbEndpoints(pairingPort = 37_123, connectionPort = 42_137)
        assertFalse(endpoints.isPortConfusion)
    }

    @Test
    fun equalPortsAreFlaggedAsPortConfusion() {
        // adbd never exposes the same port for pairing and connecting: equality means one
        // of the two values was read from the wrong place on the phone.
        val endpoints = WirelessAdbEndpoints(pairingPort = 42_137, connectionPort = 42_137)
        assertTrue(endpoints.isPortConfusion)
    }

    @Test
    fun zeroPortIsRejected() {
        runCatching { WirelessAdbEndpoints(pairingPort = 0, connectionPort = 42_137) }
            .onSuccess { throw AssertionError("port 0 must be rejected") }
            .onFailure { assertTrue(it is IllegalArgumentException) }
    }

    @Test
    fun portAboveRangeIsRejected() {
        runCatching { WirelessAdbEndpoints(pairingPort = 65_536, connectionPort = 42_137) }
            .onSuccess { throw AssertionError("port 65536 must be rejected") }
            .onFailure { assertTrue(it is IllegalArgumentException) }
    }

    @Test
    fun bothPortsAreInRange() {
        assertEquals(1, WirelessAdbEndpoints.PORT_MIN)
        assertEquals(65_535, WirelessAdbEndpoints.PORT_MAX)
    }
}
