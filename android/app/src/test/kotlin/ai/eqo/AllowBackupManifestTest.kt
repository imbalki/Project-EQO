// TASK-006 security pass F3 (issue #11): the app must not back up its
// secret-bearing files.
package ai.eqo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * F3 of the TASK-006 security pass: the app stores provider API keys in
 * app-private SharedPreferences/DataStore (plus the legacy plaintext
 * `opendroid_prefs` file during the one-time import), so Auto Backup must be
 * off — the merged manifest at targetSdk 36 defaults to backup enabled.
 *
 * Source guard in the style of the manifest tests already on the board
 * (`SmsPermissionsManifestTest`, `EqoSingleServiceManifestTest`): a build fails
 * the moment `android:allowBackup` is missing or flipped to `true`.
 */
class AllowBackupManifestTest {
    @Test
    fun `application manifest declares allowBackup false`() {
        val manifest = appManifest()
        assertTrue("missing manifest: $manifest", manifest.isFile)

        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
        val applications = document.getElementsByTagName("application")
        assertEquals("expected exactly one <application>", 1, applications.length)
        val application = applications.item(0) as Element

        assertEquals(
            "android:allowBackup must be \"false\" in ${manifest.invariantSeparatorsPath}",
            "false",
            application.getAttribute("android:allowBackup"),
        )
    }

    @Test
    fun `application manifest declares the backup exclusion rules`() {
        val manifest = appManifest()
        assertTrue("missing manifest: $manifest", manifest.isFile)

        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
        val applications = document.getElementsByTagName("application")
        assertEquals("expected exactly one <application>", 1, applications.length)
        val application = applications.item(0) as Element

        // F3 companions added for lint [DataExtractionRules] at minSdk 30 /
        // targetSdk 36: the pre-12 and 12+ backup paths must both point at the
        // exclude-everything rules, so no secret-bearing file is ever extracted.
        assertEquals(
            "android:fullBackupContent must point at the exclusion rules in ${manifest.invariantSeparatorsPath}",
            "@xml/backup_rules",
            application.getAttribute("android:fullBackupContent"),
        )
        assertEquals(
            "android:dataExtractionRules must point at the exclusion rules in ${manifest.invariantSeparatorsPath}",
            "@xml/data_extraction_rules",
            application.getAttribute("android:dataExtractionRules"),
        )
    }

    private fun appManifest(): File = File(androidRoot(), "app/src/main/AndroidManifest.xml")

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
}
