package ai.eqo.onboarding

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class DebugPairReceiverTest {
    @Test
    fun validLabRequestUsesNonExportedPairingServiceAndConsumesExtras() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = labIntent()
        DebugPairReceiver().onReceive(context, intent)
        val started = shadowOf(context as android.app.Application).nextStartedService
        assertEquals(WirelessPairingService::class.java.name, started.component?.className)
        assertEquals(WirelessPairingService.SUBMIT, started.action)
        assertEquals("001234 30001 30002", started.getStringExtra(WirelessPairingService.CODE))
        assertFalse(intent.hasExtra("code"))
        assertFalse(intent.hasExtra("pairing_port"))
        assertFalse(intent.hasExtra("connection_port"))
    }

    @Test
    fun invalidCodeOrPortsAndOtherActionsCannotStartService() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (intent in listOf(
            labIntent().putExtra("code", "bad"),
            labIntent().putExtra("pairing_port", 1),
            labIntent().setAction("other"),
        )) {
            DebugPairReceiver().onReceive(context, intent)
            assertNull(shadowOf(context as android.app.Application).nextStartedService)
            assertFalse(intent.hasExtra("code"))
        }
    }

    @Test
    fun receiverIsPermissionProtectedAndAbsentFromReleaseSourceSets() {
        val source = File("src").takeIf { it.isDirectory } ?: File("app/src")
        val debug = File(source, "debug/AndroidManifest.xml")
        val document =
            DocumentBuilderFactory
                .newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(debug)
        val receiver = document.getElementsByTagName("receiver").item(0)
        assertEquals("android.permission.DUMP", receiver.attributes.getNamedItemNS(ANDROID, "permission").nodeValue)
        assertEquals("true", receiver.attributes.getNamedItemNS(ANDROID, "exported").nodeValue)
        for (set in listOf("main", "release")) {
            val root = File(source, set)
            assertFalse(root.walkTopDown().filter { it.isFile }.any { it.name == "DebugPairReceiver.kt" })
            val manifest = File(root, "AndroidManifest.xml")
            if (manifest.exists()) {
                assertFalse(manifest.readText().contains(DebugPairReceiver.ACTION))
                assertFalse(manifest.readText().contains("DebugPairReceiver"))
            }
        }
        val kotlin = File(source, "debug/kotlin/ai/eqo/onboarding/DebugPairReceiver.kt").readText()
        assertTrue(kotlin.contains("!BuildConfig.DEBUG"))
    }

    private fun labIntent(): Intent =
        Intent(DebugPairReceiver.ACTION)
            .putExtra("code", "001234")
            .putExtra("pairing_port", 30_001)
            .putExtra("connection_port", 30_002)

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
