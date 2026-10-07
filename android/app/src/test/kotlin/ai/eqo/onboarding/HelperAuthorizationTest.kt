package ai.eqo.onboarding

import ai.eqo.adb.pairing.ActivationCheck
import ai.eqo.adb.pairing.ActivationSequence
import ai.eqo.adb.pairing.ActivationStepRunner
import ai.eqo.adb.pairing.AdbPairingCode
import ai.eqo.adb.pairing.CheckOutcome
import ai.eqo.adb.pairing.StepSignal
import ai.eqo.adb.pairing.StepSignalException
import ai.eqo.adb.pairing.WirelessAdbEndpoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperAuthorizationTest {
    private class Port(
        private val answer: Boolean?,
        var granted: Boolean = false,
    ) : HelperPermissionPort {
        var requested = false
        var closed = false
        var retained = false

        override fun isGranted() = granted

        override fun request(result: (Boolean) -> Unit) {
            requested = true
            answer?.let {
                granted = it
                result(it)
            }
        }

        override fun close(authorized: Boolean) {
            closed = true
            retained = authorized
            if (!authorized) granted = false
        }
    }

    @Test
    fun grantRequiresAnAnswerAndConfirmedPermission() {
        val port = Port(true)
        HelperAuthorization(port).await()
        assertTrue(port.requested)
        assertTrue(port.closed)
        assertTrue(port.granted)
        assertTrue(port.retained)
    }

    @Test
    fun denyIsTypedAndFailsClosed() {
        val port = Port(false)
        val error = assertThrows(StepSignalException::class.java) { HelperAuthorization(port).await() }
        assertEquals(StepSignal.HELPER_NOT_AUTHORIZED, error.signal)
        assertTrue(error.detail.contains("denied"))
        assertFalse(port.granted)
        assertTrue(port.closed)
    }

    @Test
    fun timeoutIsTypedAndClosesThePrompt() {
        val port = Port(null)
        val error = assertThrows(StepSignalException::class.java) { HelperAuthorization(port, 1L).await() }
        assertEquals(StepSignal.HELPER_NOT_AUTHORIZED, error.signal)
        assertTrue(error.detail.contains("timed out"))
        assertFalse(port.granted)
        assertTrue(port.closed)
    }

    @Test
    fun anAlreadyAuthorizedSessionDoesNotRequestAgain() {
        val port = Port(null, granted = true)
        HelperAuthorization(port).await()
        assertFalse(port.requested)
        assertTrue(port.closed)
        assertTrue(port.retained)
    }

    @Test
    fun interruptionFailsClosedAndPreservesInterrupt() {
        val port = Port(null)
        Thread.currentThread().interrupt()
        try {
            val error = assertThrows(StepSignalException::class.java) { HelperAuthorization(port).await() }
            assertEquals(StepSignal.HELPER_NOT_AUTHORIZED, error.signal)
            assertTrue(Thread.currentThread().isInterrupted)
            assertTrue(port.closed)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun binderHealthRunsOnlyAfterGrantNotDenyOrTimeout() {
        listOf(true, false, null).forEach { answer ->
            var healthRan = false
            val authorization = HelperAuthorization(Port(answer), 1L)
            val runner =
                object : ActivationStepRunner {
                    override fun pair(
                        endpoints: WirelessAdbEndpoints,
                        code: AdbPairingCode,
                    ) = Unit

                    override fun connect(endpoints: WirelessAdbEndpoints) = Unit

                    override fun startHelper() = Unit

                    override fun authorizeHelper() = authorization.await()

                    override fun checkBinder() {
                        healthRan = true
                    }
                }
            val report = ActivationSequence(runner).reconnect(WirelessAdbEndpoints.forReconnect(40_000), true)
            assertEquals(answer == true, healthRan)
            if (answer == true) {
                assertTrue(report.allPassed)
            } else {
                assertEquals(ActivationCheck.AUTHORIZE, report.firstFailure?.check)
                assertTrue(report.outcomeOf(ActivationCheck.BINDER_HEALTH) is CheckOutcome.NotRun)
            }
        }
    }
}
