package ai.eqo.adb.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance criterion: "Wrong code, port confusion, revoke, reboot and Wi-Fi change are
 * each tested and recover with guidance, not silently."
 */
class FailureClassifierTest {
    @Test
    fun wrongCodeSignalMapsToWrongCodeWithGuidance() {
        val failure = FailureClassifier.classify(ActivationCheck.PAIR, StepSignal.PAIRING_CODE_REJECTED)
        assertTrue(failure is ActivationFailure.WrongCode)
        assertTrue(failure.guidance.contains(AndroidSettingsNames.PAIRING_CODE))
        assertTrue(failure.guidance.contains(AndroidSettingsNames.PAIR_DEVICE_WITH_PAIRING_CODE))
        assertTrue(failure.guidance.contains(AndroidSettingsNames.WIRELESS_DEBUGGING))
    }

    @Test
    fun malformedCodeSignalMapsToMalformedCodeWithGuidance() {
        val failure = FailureClassifier.classify(ActivationCheck.PAIR, StepSignal.MALFORMED_CODE, detail = "12")
        assertTrue(failure is ActivationFailure.MalformedCode)
        assertEquals("12", (failure as ActivationFailure.MalformedCode).raw)
        assertTrue(failure.guidance.contains(AndroidSettingsNames.PAIRING_CODE))
    }

    @Test
    fun refusedPortMapsToPortConfusionWithBothSurfacesNamed() {
        val endpoints = WirelessAdbEndpoints(pairingPort = 37_123, connectionPort = 42_137)
        val failure = FailureClassifier.classify(ActivationCheck.PAIR, StepSignal.PORT_REFUSED, endpoints)
        assertTrue(failure is ActivationFailure.PortConfusion)
        assertTrue(failure.guidance.contains(AndroidSettingsNames.PAIR_DEVICE_WITH_PAIRING_CODE))
        assertTrue(failure.guidance.contains(AndroidSettingsNames.PAIRING_CODE))
        assertTrue(failure.guidance.contains(AndroidSettingsNames.IP_ADDRESS_AND_PORT))
        assertTrue(failure.guidance.contains(AndroidSettingsNames.WIRELESS_DEBUGGING))
    }

    @Test
    fun rejectedAuthMapsToPairingRevokedWithRevokeName() {
        val failure = FailureClassifier.classify(ActivationCheck.CONNECT, StepSignal.AUTH_REJECTED)
        assertTrue(failure is ActivationFailure.PairingRevoked)
        assertEquals(ActivationCheck.CONNECT, failure.check)
        assertTrue(failure.guidance.contains(AndroidSettingsNames.REVOKE_USB_DEBUGGING_AUTHORIZATIONS))
        assertTrue(failure.guidance.contains(AndroidSettingsNames.WIRELESS_DEBUGGING))
    }

    @Test
    fun wirelessDebuggingOffMapsToDeviceRebooted() {
        val failure = FailureClassifier.classify(ActivationCheck.CONNECT, StepSignal.WIRELESS_DEBUGGING_OFF)
        assertTrue(failure is ActivationFailure.DeviceRebooted)
        assertTrue(failure.guidance.contains(AndroidSettingsNames.DEVELOPER_OPTIONS))
        assertTrue(failure.guidance.contains(AndroidSettingsNames.WIRELESS_DEBUGGING))
        assertTrue(failure.guidance.contains(AndroidSettingsNames.IP_ADDRESS_AND_PORT))
    }

    @Test
    fun networkChangedMapsToWifiChanged() {
        val failure = FailureClassifier.classify(ActivationCheck.CONNECT, StepSignal.NETWORK_CHANGED)
        assertTrue(failure is ActivationFailure.WifiChanged)
        assertTrue(failure.guidance.contains(AndroidSettingsNames.WIFI))
        assertTrue(failure.guidance.contains(AndroidSettingsNames.WIRELESS_DEBUGGING))
    }

    @Test
    fun helperSignalsMapToStepFailedWithDetail() {
        val helperSignals =
            listOf(StepSignal.HELPER_NOT_STARTED, StepSignal.HELPER_NOT_AUTHORIZED, StepSignal.BINDER_DEAD)
        for (signal in helperSignals) {
            val failure = FailureClassifier.classify(ActivationCheck.BINDER_HEALTH, signal, detail = signal.name)
            assertTrue(failure is ActivationFailure.StepFailed)
            assertEquals(signal.name, (failure as ActivationFailure.StepFailed).detail)
            assertTrue(failure.guidance.contains(AndroidSettingsNames.WIRELESS_DEBUGGING))
        }
    }

    @Test
    fun everySignalProducesNonEmptyGuidance() {
        for (signal in StepSignal.entries) {
            for (check in ActivationCheck.entries) {
                val failure = FailureClassifier.classify(check, signal)
                assertTrue("guidance for $signal/$check", failure.guidance.isNotBlank())
                // MalformedCode and PortConfusion are pair-plane failures by definition.
                val expectedCheck =
                    when (signal) {
                        StepSignal.MALFORMED_CODE, StepSignal.PORT_REFUSED -> ActivationCheck.PAIR
                        else -> check
                    }
                assertEquals(expectedCheck, failure.check)
            }
        }
    }
}
