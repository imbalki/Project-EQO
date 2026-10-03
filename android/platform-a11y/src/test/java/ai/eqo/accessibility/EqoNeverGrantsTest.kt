package ai.eqo.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TASK-009 scope line: "Guide the Android 13+ restricted-settings repair through
 * Android's own settings screens (user grants, EQO never grants)."
 *
 * Two guarantees:
 *  1. the guide walks the user through Android's own Settings screens and its
 *     intents only OPEN those screens;
 *  2. no source file in :platform-a11y writes secure settings - in particular
 *     nothing writes `enabled_accessibility_services`, so EQO can never grant
 *     (or revoke) its own accessibility permission.
 */
class EqoNeverGrantsTest {
    private fun androidRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile && File(dir, "app").isDirectory) {
                return dir
            }
            dir = dir.parentFile
                ?: break
        }
        error("could not locate the android/ root from ${System.getProperty("user.dir")}")
    }

    @Test
    fun noSourceInPlatformA11yWritesSecureSettings() {
        val moduleDir = File(androidRoot(), "platform-a11y/src/main")
        val offenders = mutableListOf<String>()
        moduleDir
            .walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .forEach { file ->
                val text = file.readText()
                listOf(
                    "Settings.Secure.putString",
                    "Settings.Secure.putStringForUser",
                    "putGlobalSetting",
                    "enabled_accessibility_services",
                ).forEach { needle ->
                    if (text.contains(needle)) {
                        offenders.add("${file.name}: contains '$needle'")
                    }
                }
            }
        assertEquals(
            "TASK-009: EQO never grants the accessibility permission - found secure-settings writes",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun theRestrictedSettingsGuideWalksAndroidOwnSettingsScreens() {
        val steps = AccessibilitySetupGuide.stepsForApi33Plus()
        val screens = steps.map { it.screen }
        assertTrue(screens.all { it.startsWith("Settings > ") })
        assertTrue(screens.any { it.startsWith("Settings > Apps > EQO") })
        assertTrue(screens.any { it.startsWith("Settings > Accessibility") })
        val joined = steps.joinToString(" ") { it.instruction }
        assertTrue("the 13+ restricted-settings repair must be covered", joined.contains("Allow restricted settings"))
        assertTrue("the user grants, EQO never does", joined.contains("EQO never enables this for you"))
    }

    @Test
    fun theShorterFlowCoversAndroid11And12() {
        val steps = AccessibilitySetupGuide.stepsForApi30To32()
        assertEquals(2, steps.size)
        assertTrue(steps.all { it.screen.startsWith("Settings > ") })
    }

    @Test
    fun oemRefusalIsReportedPerDeviceNotGeneralized() {
        val joined = AccessibilitySetupGuide.stepsForApi33Plus().joinToString(" ") { it.instruction }
        assertTrue(joined.contains("report the device model"))
        assertTrue(joined.contains("do not generalize"))
    }
}
