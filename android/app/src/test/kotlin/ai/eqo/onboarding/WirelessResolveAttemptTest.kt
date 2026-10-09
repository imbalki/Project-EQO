package ai.eqo.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WirelessResolveAttemptTest {
    @Test
    fun nsdSuccessDoesNotRunFallback() {
        var fallbacks = 0
        val results = mutableListOf<Int?>()
        val attempt = WirelessResolveAttempt({ fallbacks++ }, { results.add(it) })
        attempt.nsd(1)
        attempt.startFallback()
        attempt.nsd(null)
        assertEquals(0, fallbacks)
        assertEquals(listOf(1), results)
    }

    @Test
    fun nsdFailureRunsMdnsOnceAndIgnoresLateFrameworkCallbacks() {
        var fallbacks = 0
        val results = mutableListOf<Int?>()
        val attempt = WirelessResolveAttempt({ fallbacks++ }, { results.add(it) })
        attempt.nsd(null)
        attempt.startFallback()
        attempt.nsd(1)
        attempt.mdns(2)
        attempt.mdns(3)
        assertEquals(1, fallbacks)
        assertEquals(listOf(2), results)
    }

    @Test
    fun missingCallbackTimeoutAlsoFallsBackAndFailureCompletesQueue() {
        var fallbacks = 0
        val results = mutableListOf<Int?>()
        val attempt = WirelessResolveAttempt({ fallbacks++ }, { results.add(it) })
        attempt.startFallback()
        attempt.nsd(1)
        attempt.mdns(null)
        attempt.startFallback()
        assertEquals(1, fallbacks)
        assertEquals(1, results.size)
        assertNull(results.single())
    }
}
