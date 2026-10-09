package ai.eqo.explain

import ai.eqo.accessibility.EQOAccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 33])
@Suppress("DEPRECATION") // AccessibilityNodeInfo.obtain matches the supported API 30 tree contract.
class ExplainAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun cleanPreferences() {
        context
            .getSharedPreferences("explain_screen", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test fun `all screen sharing settings start off`() {
        assertFalse(ExplainSettings.allowed(context))
        assertFalse(ExplainSettings.autoRead(context))
        assertFalse(ExplainSettings.notification(context))
        ExplainSettings.set(context, "allow_provider", true)
        assertTrue(ExplainSettings.allowed(context))
        ExplainSettings.set(context, "allow_provider", false)
        assertFalse(ExplainSettings.allowed(context))
    }

    @Test fun `image capability requires exact model and explicit image modality`() {
        val json = """{"data":[{"id":"test/vision","architecture":{"input_modalities":["text","image"]}},
            {"id":"test/text","architecture":{"input_modalities":["text"]}}]}"""
        assertTrue(AndroidExplainModel.imageCapability(json, "test/vision"))
        assertFalse(AndroidExplainModel.imageCapability(json, "test/text"))
        assertFalse(AndroidExplainModel.imageCapability(json, "test/missing"))
        assertFalse(AndroidExplainModel.imageCapability("not JSON", "test/vision"))
    }

    @Test fun `Android source refuses EQO and notification shade roots`() {
        val service = Robolectric.buildService(EQOAccessibilityService::class.java).get()
        for (app in listOf(context.packageName, "com.android.systemui")) {
            val source = AndroidExplainSource(service, { node(app, "private window") }, { error("Must not capture") })
            assertTrue(runCatching { source.read() }.isFailure)
        }
    }

    @Test fun `Android source skips password labels and hidden text`() {
        val service = Robolectric.buildService(EQOAccessibilityService::class.java).get()
        val password = AndroidExplainSource(service, { node("test.app", "synthetic password", password = true) })
        assertEquals("", password.read().text)
        assertTrue(password.read().hasPassword)
        val hidden = AndroidExplainSource(service, { node("test.app", "hidden", visible = false) })
        assertEquals("", hidden.read().text)
    }

    @Test fun `capture discarded when foreground changes and later attempt does not replace snapshot`() =
        runTest {
            val service = Robolectric.buildService(EQOAccessibilityService::class.java).get()
            var app = "test.app"
            var captures = 0
            val source =
                AndroidExplainSource(service, { node(app, "screen") }, {
                    captures++
                    app = "different.app"
                    "synthetic-image"
                })
            source.read()
            assertNull(source.screenshot())
            assertNull(source.screenshot())
            assertEquals(1, captures)
        }

    @Test fun `capture is memory only and works for matching root`() =
        runTest {
            val service = Robolectric.buildService(EQOAccessibilityService::class.java).get()
            val source = AndroidExplainSource(service, { node("test.app", "screen") }, { "synthetic-image" })
            source.read()
            assertEquals("synthetic-image", source.screenshot())
            source.close()
            assertNull(source.screenshot())
            assertTrue(runCatching { source.read() }.isFailure)
        }

    @Test fun `notification action targets only transient entry not main UI`() {
        ExplainSettings.set(context, "notification", true)
        val controller = Robolectric.buildService(ExplainNotificationService::class.java).create()
        try {
            controller.get().onStartCommand(Intent(), 0, 1)
            val manager = context.getSystemService(NotificationManager::class.java)
            val notification = shadowOf(manager).getNotification(701)
            val target = shadowOf(notification.actions.single().actionIntent).savedIntent
            assertEquals(ExplainEntryActivity::class.java.name, target.component?.className)
            assertEquals(context.packageName, target.component?.packageName)
            assertTrue(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        } finally {
            controller.destroy()
        }
    }

    private fun node(
        app: String,
        label: String,
        password: Boolean = false,
        visible: Boolean = true,
    ): AccessibilityNodeInfo =
        AccessibilityNodeInfo.obtain().apply {
            packageName = app
            text = label
            isPassword = password
            isVisibleToUser = visible
        }
}
