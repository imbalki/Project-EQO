// Origin: EQO-authored fake capture/storage and real compact-overlay regressions; no device capture.
package ai.eqo.handle

import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.handle.EdgeHandleFeatures
import ai.eqo.accessibility.handle.EdgeHandleOverlay
import ai.eqo.accessibility.handle.HandlePreferences
import ai.eqo.platform.R
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowToast
import java.io.File

@RunWith(RobolectricTestRunner::class)
class HandleScreenshotTest {
    @After
    fun cleanup() {
        HandleScreenshot.dismiss()
        EdgeHandleFeatures.screenshotHidden = false
        EdgeHandleFeatures.screenshotStatus = 0
        EdgeHandleFeatures.screenshotChanged = {}
        EdgeHandleFeatures.screenshotShare = HandleScreenshot::share
        EdgeHandleFeatures.screenshotDismiss = HandleScreenshot::dismiss
    }

    @Test
    fun `missing service reports screenshot refusal even when another card is present`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        EdgeHandleFeatures.recordingMessage = "Synthetic older recording result"
        HandleScreenshot.tap(context)
        assertEquals(
            context.getString(R.string.edge_screenshot_refused),
            ShadowToast.getTextOfLatestToast(),
        )
        assertTrue(
            ShadowLog.getLogsForTag("EqoRun").any {
                it.msg.contains("screenshot result=refused")
            },
        )
        EdgeHandleFeatures.recordingMessage = ""
    }

    @Test
    fun `refusal never invokes capture or publication`() =
        runTest {
            val result =
                captureHandleScreenshot<String>(
                    gate = { false },
                    capture = { error("must not capture") },
                    publish = { error("must not publish") },
                    discard = { error("nothing to discard") },
                )
            assertNull(result)
        }

    @Test
    fun `failed capture never publishes`() =
        runTest {
            assertNull(
                captureHandleScreenshot<String>(
                    gate = { true },
                    capture = { false },
                    publish = { error("must not publish") },
                    discard = {},
                ),
            )
        }

    @Test
    fun `screen change during capture drops image before publication`() =
        runTest {
            var allowed = true
            assertNull(
                captureHandleScreenshot<String>(
                    gate = { allowed },
                    capture = {
                        allowed = false
                        true
                    },
                    publish = { error("must not publish") },
                    discard = {},
                ),
            )
        }

    @Test
    fun `screen change during publication rolls back public image`() =
        runTest {
            var allowed = true
            val removed = mutableListOf<String>()
            assertNull(
                captureHandleScreenshot(
                    gate = { allowed },
                    capture = { true },
                    publish = {
                        allowed = false
                        "synthetic-image"
                    },
                    discard = { removed += it },
                ),
            )
            assertEquals(listOf("synthetic-image"), removed)
        }

    @Test
    fun `successful local save never shares automatically`() =
        runTest {
            assertEquals(
                "synthetic-image",
                captureHandleScreenshot(
                    gate = { true },
                    capture = { true },
                    publish = { "synthetic-image" },
                    discard = { error("must keep local save") },
                ),
            )
        }

    @Test
    fun `screenshot defaults on and settings choice persists`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        HandlePreferences(context).preferences.edit { clear() }
        val registry = createHandleRegistry(context)
        assertTrue(registry.enabled(EdgeHandleFeatures.SCREENSHOT_ID))
        registry.setEnabled(EdgeHandleFeatures.SCREENSHOT_ID, false)
        assertFalse(createHandleRegistry(context).enabled(EdgeHandleFeatures.SCREENSHOT_ID))
        registry.reset()
    }

    @Test
    fun `password metadata and exhausted tree budget refuse capture`() {
        val node = AccessibilityNodeInfo.obtain()
        try {
            assertFalse(HandleScreenshot.hasPassword(node, intArrayOf(10)))
            node.isPassword = true
            assertTrue(HandleScreenshot.hasPassword(node, intArrayOf(10)))
            node.isPassword = false
            assertTrue(HandleScreenshot.hasPassword(node, intArrayOf(0)))
            assertTrue(HandleScreenshot.hasPassword(node, intArrayOf(10), Int.MAX_VALUE))
        } finally {
            node.recycle()
        }
    }

    @Test
    fun `gate exception after publication still deletes image`() =
        runTest {
            var checks = 0
            var removed = false
            val result =
                runCatching {
                    captureHandleScreenshot(
                        gate = { if (++checks == 3) error("synthetic stale node") else true },
                        capture = { true },
                        publish = { "synthetic-image" },
                        discard = { removed = true },
                    )
                }
            assertTrue(result.isFailure)
            assertTrue(removed)
        }

    @Test
    fun `MediaStore writes public picture pending then commits and rollback deletes failed save`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val target = File.createTempFile("synthetic-image-", ".png", context.cacheDir)
            val source = File.createTempFile("synthetic-source-", ".png", context.cacheDir)
            val provider = FakeImages(target)
            val info = ProviderInfo().apply { authority = "media" }
            provider.attachInfo(context, info)
            ShadowContentResolver.registerProviderInternal("media", provider)
            try {
                source.writeBytes(byteArrayOf(1, 2, 3))
                assertEquals(provider.image, HandleScreenshot.publish(context, source))
                assertEquals("Pictures/EQO", provider.inserted?.getAsString(MediaStore.Images.Media.RELATIVE_PATH))
                assertEquals("image/png", provider.inserted?.getAsString(MediaStore.Images.Media.MIME_TYPE))
                assertEquals(1, provider.inserted?.getAsInteger(MediaStore.Images.Media.IS_PENDING))
                assertEquals(0, provider.updated?.getAsInteger(MediaStore.Images.Media.IS_PENDING))
                assertTrue(target.readBytes().contentEquals(source.readBytes()))
                assertEquals(0, provider.deletes)
                provider.acceptUpdate = false
                assertTrue(runCatching { HandleScreenshot.publish(context, source) }.isFailure)
                assertEquals(1, provider.deletes)
            } finally {
                source.delete()
                target.delete()
            }
        }

    private class FakeImages(
        private val target: File,
    ) : ContentProvider() {
        val image: Uri = Uri.parse("content://media/external/images/media/123")
        var inserted: ContentValues? = null
        var updated: ContentValues? = null
        var deletes = 0
        var acceptUpdate = true

        override fun onCreate() = true

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri {
            inserted = ContentValues(values)
            return image
        }

        override fun openFile(
            uri: Uri,
            mode: String,
        ): ParcelFileDescriptor =
            ParcelFileDescriptor.open(
                target,
                ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE,
            )

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            updated = ContentValues(values)
            return if (acceptUpdate) 1 else 0
        }

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            deletes++
            return 1
        }

        override fun getType(uri: Uri): String = "image/png"

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): android.database.Cursor? = null
    }

    @Test
    fun `compact saved card exposes explicit share and done and capture hides overlay`() {
        val service = Robolectric.buildService(EQOAccessibilityService::class.java).create().get()
        val preferences = HandlePreferences(service)
        preferences.preferences.edit { clear() }
        preferences.enabled = true
        preferences.hintShown = true
        val overlay = EdgeHandleOverlay(service, service.getSystemService(WindowManager::class.java))
        var shares = 0
        var dismisses = 0
        EdgeHandleFeatures.screenshotShare = { shares++ }
        EdgeHandleFeatures.screenshotDismiss = { dismisses++ }
        try {
            overlay.setProbeAvailable(true)
            EdgeHandleFeatures.screenshotStatus = R.string.edge_screenshot_saved
            EdgeHandleFeatures.screenshotShareAvailable = true
            EdgeHandleFeatures.screenshotChanged()
            shadowOf(Looper.getMainLooper()).idle()
            val root = view(overlay)!!
            assertTrue(
                descendants(root).filterIsInstance<TextView>().any { it.text == "Screenshot saved · Pictures/EQO" },
            )
            descendants(root).filterIsInstance<Button>().first { it.text == "Share" }.performClick()
            assertEquals(1, shares)
            descendants(root).filterIsInstance<Button>().first { it.text == "Done" }.performClick()
            assertEquals(1, dismisses)
            EdgeHandleFeatures.screenshotHidden = true
            EdgeHandleFeatures.screenshotChanged()
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(view(overlay))
        } finally {
            overlay.destroy()
            preferences.preferences.edit { clear() }
        }
    }

    private fun view(overlay: EdgeHandleOverlay): View? =
        overlay.javaClass
            .getDeclaredField("view")
            .apply { isAccessible = true }
            .get(overlay) as? View

    private fun descendants(root: View): List<View> =
        listOf(root) +
            if (root is ViewGroup) {
                (0 until root.childCount).flatMap { descendants(root.getChildAt(it)) }
            } else {
                emptyList()
            }
}
