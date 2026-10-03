package ai.eqo.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * TASK-009 acceptance criterion 1: exactly one accessibility service in the
 * merged manifest. Two layers of proof:
 *
 *  1. source guard - every android/**/src/main/AndroidManifest.xml in the repo
 *     together declares exactly one <service> with the
 *     android.accessibilityservice.AccessibilityService action. This is what
 *     fails the build if the future ClosePaw merge registers a second service
 *     (the donors each register one).
 *  2. the module's own manifest pins that single service to EQO's class.
 *
 * The AGP-merged manifest output is additionally captured in the TASK-009
 * evidence file (platform-a11y:processDebugManifest).
 */
class EqoSingleServiceManifestTest {
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

    private fun mainManifests(root: File): List<File> {
        val manifests = mutableListOf<File>()
        root
            .walkTopDown()
            .onEnter { dir -> dir.name != "build" }
            .filter { file ->
                file.isFile &&
                    file.name == "AndroidManifest.xml" &&
                    file.invariantSeparatorsPath.contains("/src/main/")
            }.forEach { manifests.add(it) }
        return manifests
    }

    private fun accessibilityServiceElements(manifest: File): List<Element> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
        val services = doc.getElementsByTagName("service")
        val found = mutableListOf<Element>()
        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            val actions = service.getElementsByTagName("action")
            for (j in 0 until actions.length) {
                val action = actions.item(j) as Element
                val name = action.getAttribute("android:name")
                if (name == "android.accessibilityservice.AccessibilityService") {
                    found.add(service)
                    break
                }
            }
        }
        return found
    }

    @Test
    fun exactlyOneAccessibilityServiceAcrossAllMainManifests() {
        val root = androidRoot()
        val manifests = mainManifests(root)
        assertTrue("expected at least one src/main manifest under $root", manifests.isNotEmpty())

        val services = manifests.flatMap { accessibilityServiceElements(it) }
        val names = services.map { it.getAttribute("android:name") }
        assertEquals(
            "TASK-009: OpenDroid and ClosePaw each register a service; the merged " +
                "manifest must have exactly one. Found in: $names",
            1,
            services.size,
        )
        assertEquals(
            "the single service must be the EQO service class",
            "ai.eqo.accessibility.EQOAccessibilityService",
            names.single(),
        )
    }

    @Test
    fun theSingleServiceBindsOnlyThroughTheSystemAccessibilityPermission() {
        val root = androidRoot()
        val manifests = mainManifests(root)
        val services = manifests.flatMap { accessibilityServiceElements(it) }
        val service = services.single()
        assertEquals(
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            service.getAttribute("android:permission"),
        )
        assertEquals("the service must not be exported", "false", service.getAttribute("android:exported"))
    }

    @Test
    fun theServiceConfigTakesOverRelevantEventsAndCanGesture() {
        val root = androidRoot()
        val config = File(root, "platform-a11y/src/main/res/xml/accessibility_service_config.xml")
        assertTrue("missing $config", config.isFile)
        val text = config.readText()
        listOf(
            "canPerformGestures=\"true\"",
            "canRetrieveWindowContent=\"true\"",
            "typeWindowStateChanged",
            "typeWindowContentChanged",
            "typeTouchInteractionStart",
        ).forEach { needle ->
            assertTrue("accessibility_service_config.xml must contain $needle", text.contains(needle))
        }
    }
}
