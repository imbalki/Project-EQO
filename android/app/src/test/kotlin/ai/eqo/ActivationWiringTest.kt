package ai.eqo

import ai.eqo.adb.pairing.PrivilegedResult
import ai.eqo.adb.pairing.WirelessAdbActivation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TASK-008 (issue #13) acceptance criterion 1: "Fresh install shows 'activation required';
 * privileged entry points refuse with guidance until done."
 *
 * The behavior of the gate itself is tested in :adb-pairing; this guard pins the app-side
 * wiring and the user-visible next-step guidance, in the source-guard style of
 * `MainActivityTest` / `AllowBackupManifestTest`.
 */
class ActivationWiringTest {
    @Test
    fun activationRequiredCopyExplainsTheNextStepWithoutChangingTheGate() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val literal = Regex("<string name=\"activation_required\">([^<]+)</string>").find(strings)?.groupValues?.get(1)
        assertTrue(literal?.contains("Next: Set up EQO") == true)
        assertTrue(literal?.contains("unavailable") == true)
        assertEquals("Activation required", WirelessAdbActivation().activationRequiredMessage)
    }

    @Test
    fun freshInstallShowsActivationRequired() {
        val gate = WirelessAdbActivation()
        assertEquals("Activation required", gate.activationRequiredMessage)
    }

    @Test
    fun privilegedEntryPointRefusesWithGuidanceOnFreshInstall() {
        var ran = false
        val result = WirelessAdbActivation().runPrivileged("Wireless ADB") { ran = true }
        assertTrue(result is PrivilegedResult.Refused)
        assertTrue(!ran)
        val refused = result as PrivilegedResult.Refused
        assertTrue(refused.guidance.contains("Activation required"))
    }

    @Test
    fun mainActivityWiresTheActivationGate() {
        val activity = File("src/main/kotlin/ai/eqo/MainActivity.kt").readText()
        assertTrue(activity.contains("WirelessAdbActivation()"))
        assertTrue(activity.contains("runPrivileged("))
        assertTrue(activity.contains("R.id.activation_status"))
        assertTrue(activity.contains("R.id.privileged_action_button"))
    }

    @Test
    fun layoutShowsActivationStatusOnFreshInstall() {
        val layout = File("src/main/res/layout/main.xml").readText()
        assertTrue(layout.contains("android:id=\"@+id/activation_status\""))
        assertTrue(layout.contains("android:text=\"@string/activation_required\""))
        assertTrue(layout.contains("android:id=\"@+id/privileged_action_button\""))
    }
}
