package ai.eqo.adb.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance criterion: "Fresh install shows 'activation required'; privileged entry
 * points refuse with guidance until done."
 */
class ActivationGateTest {
    @Test
    fun freshInstallRequiresActivation() {
        val gate = WirelessAdbActivation()
        assertEquals(ActivationStatus.ACTIVATION_REQUIRED, gate.status)
    }

    @Test
    fun freshInstallShowsActivationRequiredMessage() {
        val gate = WirelessAdbActivation()
        assertEquals("Activation required", gate.activationRequiredMessage)
    }

    @Test
    fun privilegedEntryPointRefusesWhileActivationIsPending() {
        val gate = WirelessAdbActivation()
        var ran = false
        val result = gate.runPrivileged("wireless adb") { ran = true }
        assertTrue(result is PrivilegedResult.Refused)
        assertFalse(ran)
    }

    @Test
    fun refusalCarriesGuidanceAndNamesAndroidSettings() {
        val gate = WirelessAdbActivation()
        val result = gate.runPrivileged("wireless adb") { }
        val refused = result as PrivilegedResult.Refused
        assertEquals("wireless adb", refused.feature)
        assertTrue(refused.guidance.isNotEmpty())
        assertTrue(refused.guidance.contains("Activation required"))
        assertTrue(refused.guidance.contains(AndroidSettingsNames.DEVELOPER_OPTIONS))
        assertTrue(refused.guidance.contains(AndroidSettingsNames.WIRELESS_DEBUGGING))
        assertTrue(refused.guidance.contains(AndroidSettingsNames.PAIR_DEVICE_WITH_PAIRING_CODE))
    }

    @Test
    fun privilegedEntryPointRunsOnlyAfterActivation() {
        val gate = WirelessAdbActivation()
        gate.markActive()
        assertEquals(ActivationStatus.ACTIVE, gate.status)
        var ran = false
        val result =
            gate.runPrivileged("wireless adb") {
                ran = true
                "ok"
            }
        assertTrue(result is PrivilegedResult.Allowed)
        assertTrue(ran)
        assertEquals("ok", (result as PrivilegedResult.Allowed).value)
    }
}
