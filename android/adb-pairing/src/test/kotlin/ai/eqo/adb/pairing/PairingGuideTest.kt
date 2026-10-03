package ai.eqo.adb.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingGuideTest {
    @Test
    fun guideCoversEveryScreenTheOwnerMustOpen() {
        val ids = PairingGuide.steps.map { it.id }
        assertEquals(
            listOf("developer-options", "wifi", "wireless-debugging", "pairing-code", "enter-code-and-ports", "verify"),
            ids,
        )
    }

    @Test
    fun everyStepHasInstruction() {
        for (step in PairingGuide.steps) {
            assertTrue("step ${step.id}", step.instruction.isNotBlank())
        }
    }

    @Test
    fun guideShowsAndroidOwnedNamesVerbatim() {
        val text = PairingGuide.steps.joinToString("\n") { it.instruction }
        assertTrue(text.contains(AndroidSettingsNames.DEVELOPER_OPTIONS))
        assertTrue(text.contains(AndroidSettingsNames.BUILD_NUMBER))
        assertTrue(text.contains(AndroidSettingsNames.NETWORK_AND_INTERNET))
        assertTrue(text.contains(AndroidSettingsNames.WIFI))
        assertTrue(text.contains(AndroidSettingsNames.WIRELESS_DEBUGGING))
        assertTrue(text.contains(AndroidSettingsNames.PAIR_DEVICE_WITH_PAIRING_CODE))
        assertTrue(text.contains(AndroidSettingsNames.PAIRING_CODE))
        assertTrue(text.contains(AndroidSettingsNames.IP_ADDRESS_AND_PORT))
    }

    @Test
    fun guideSpellsOutThatTheTwoPortsDiffer() {
        val pairingStep = PairingGuide.steps.first { it.id == "pairing-code" }
        assertTrue(pairingStep.instruction.contains("NOT the connection port"))
    }

    @Test
    fun guideListsTheFiveSeparatelyFailingChecks() {
        val verify = PairingGuide.steps.first { it.id == "verify" }
        for (name in listOf("pair", "connect", "helper start", "authorize", "binder health")) {
            assertTrue("verify step mentions $name", verify.instruction.contains(name))
        }
        assertTrue(verify.instruction.contains("proves nothing about the next one"))
    }
}
