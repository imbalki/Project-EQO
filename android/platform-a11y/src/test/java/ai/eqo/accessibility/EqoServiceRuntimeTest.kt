package ai.eqo.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EqoServiceRuntimeTest {
    @Test
    fun constructsWithoutAndroidApplicationOrHiltAndRefusesUnboundActions() {
        val runtime =
            EqoServiceRuntime(
                rootProvider = { error("unbound actions must not read a window") },
                serviceState = { EqoAutomation.ServiceState.ACCESSIBILITY_DISABLED },
                isSecureWindow = { error("unbound actions must not read a window") },
                takeover = TakeoverDetector(),
            )
        assertEquals(A11yError.AccessibilityDisabled, (runtime.automation.tap("test") as A11yResult.Failure).error)
        assertSame(runtime.automation, runtime.automation)
    }

    @Test
    fun takeoverRefusesActionsWithoutReadingTheWindow() {
        val detector = TakeoverDetector()
        detector.onAgentActionStarted()
        detector.onTouch(TakeoverDetector.TouchSource.USER, nowMs = 500L)
        detector.onAgentActionFinished()
        val runtime =
            EqoServiceRuntime(
                rootProvider = { error("paused runtime must not read a window") },
                serviceState = { EqoAutomation.ServiceState.AVAILABLE },
                isSecureWindow = { true },
                takeover = detector,
            )
        assertEquals(A11yError.TakeoverDetected, (runtime.automation.observe() as A11yResult.Failure).error)
        assertTrue(detector.isPaused)
    }

    @Test
    fun productionRuntimeSharesTheStudyTakeoverLatch() {
        val runtime =
            EqoServiceRuntime(
                rootProvider = { null },
                serviceState = { EqoAutomation.ServiceState.ACCESSIBILITY_DISABLED },
                isSecureWindow = { true },
            )
        assertSame(TakeoverDetector.shared, runtime.takeover)
    }

    @Test
    fun productionServiceCannotReachAnUnsupportedDonorGraph() {
        var root = File(System.getProperty("user.dir")).absoluteFile
        while (!File(root, "settings.gradle.kts").isFile) {
            root = root.parentFile ?: error("android root missing")
        }
        val source =
            File(root, "platform-a11y/src/main/java/ai/eqo/accessibility/EQOAccessibilityService.kt").readText()
        listOf(
            "@AndroidEntryPoint",
            "@Inject",
            "agentLoop",
            "settingsRepository",
            "habitRoutineEngine",
            "serviceBridge",
            "notificationTapTarget",
            "EntryPointAccessors",
        ).forEach { assertFalse("Unsupported service seam: $it", source.contains(it)) }
        assertTrue(source.contains("private val nodeTraversal = AccessibilityNodeTraversal()"))
        assertTrue(source.contains("EqoServiceRuntime("))
        val appMain = File(root, "app/src/main")
        val application = appMain.walkTopDown().first { it.name == "EqoApplication.kt" }.readText()
        assertFalse(application.contains("@HiltAndroidApp"))
        val manifest = File(appMain, "AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:name=\".EqoApplication\""))
        val port = File(appMain, "kotlin/ai/eqo/task/EqoAutomationPort.kt").readText()
        assertFalse("Study back/home must not bypass takeover", port.contains("service.performGlobalAction("))
        assertTrue(port.contains("service.gatedActions.pressBack()"))
        assertTrue(port.contains("service.gatedActions.pressHome()"))
        val merged =
            File(root, "app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml")
        // Standalone platform tests use the declared manifest; the full gate also checks the merged one.
        if (merged.isFile) {
            val mergedText = merged.readText()
            assertTrue(mergedText.contains("android:name=\"ai.eqo.EqoApplication\""))
            assertTrue(mergedText.contains("android:name=\"ai.eqo.accessibility.EQOAccessibilityService\""))
        }
        // If a future app entry point is added, this explicit-graph contract must be revisited.
        assertFalse(
            appMain
                .walkTopDown()
                .filter { it.extension == "kt" }
                .any { it.readText().contains("@AndroidEntryPoint") },
        )
    }
}
