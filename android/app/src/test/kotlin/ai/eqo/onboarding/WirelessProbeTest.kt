// TASK-080 (issue #20): the wireless row reports its own plain-language state.
package ai.eqo.onboarding

import ai.eqo.adb.pairing.ActivationSequence
import ai.eqo.adb.pairing.ActivationStepRunner
import ai.eqo.adb.pairing.AdbPairingCode
import ai.eqo.adb.pairing.StepSignal
import ai.eqo.adb.pairing.StepSignalException
import ai.eqo.adb.pairing.WirelessAdbActivation
import ai.eqo.adb.pairing.WirelessAdbEndpoints
import ai.eqo.study.CapabilityId
import ai.eqo.study.CapabilityState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WirelessProbeTest {
    private class Runner(
        private val connectSignal: StepSignal? = null,
    ) : ActivationStepRunner {
        override fun pair(
            endpoints: WirelessAdbEndpoints,
            code: AdbPairingCode,
        ) = Unit

        override fun connect(endpoints: WirelessAdbEndpoints) {
            connectSignal?.let { throw StepSignalException(it, "synthetic") }
        }

        override fun startHelper() = Unit

        override fun authorizeHelper() = Unit

        override fun checkBinder() = Unit
    }

    @After
    fun reset() {
        StudySetup.wirelessReport = null
    }

    @Test
    fun beforeAnyAttemptTheRowIsNotStartedAndSaysNotPaired() {
        StudySetup.wirelessReport = null
        val status = StudySetup.probeWirelessAdb()
        assertEquals(CapabilityState.NOT_STARTED, status.state)
        assertEquals(CapabilityId.WIRELESS_ADB.probeName, status.probeName)
        assertEquals("not paired", status.detail)
    }

    @Test
    fun aKeyMismatchShowsNeedsRepairWithGuidanceNotReady() {
        StudySetup.wirelessReport =
            ActivationSequence(Runner(StepSignal.SERVER_KEY_MISMATCH), WirelessAdbActivation())
                .reconnect(WirelessAdbEndpoints.forReconnect(40_000), enrolled = true)
        val status = StudySetup.probeWirelessAdb()
        assertEquals(CapabilityState.FAILED, status.state)
        assertEquals("needs re-pair", status.detail)
        assertTrue(status.guidance.contains("Pair again"))
    }
}
