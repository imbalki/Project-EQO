package ai.eqo.explain

import ai.eqo.R
import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.handle.EdgeHandleFeatures
import ai.eqo.accessibility.handle.HandlePreferences
import ai.eqo.handle.EdgeHandleSettingsActivity
import ai.eqo.onboarding.SetupHubActivity
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.Switch
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.ref.WeakReference
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 33])
class ExplainPolishTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun clean() {
        current()?.let { invoke(it, "close") }
        ExplainSettings.set(context, "notification", false)
        ExplainSettings.set(context, "allow_provider", false)
        HandlePreferences(context).preferences.edit().clear().commit()
        EdgeHandleFeatures.refreshEntries = ExplainNotificationService::refresh
    }

    @Test fun `panel sizes are capped and peek expires with fake clock`() {
        var clock = 0L
        val state = ExplainPanelState { clock }
        assertEquals(250, state.height(1000, 64))
        for (size in ExplainPanelState.Size.entries) {
            state.size = size
            assertTrue(state.height(1000, 64) <= 350)
        }
        state.size = ExplainPanelState.Size.SMALL
        assertEquals(64, state.height(1000, 64))
        assertEquals(217, ExplainPanelState.BACKGROUND_ALPHA)
        state.seeScreen()
        assertTrue(state.seeThrough)
        assertEquals(0.05f, state.alpha)
        clock = 4999L
        assertTrue(state.seeThrough)
        clock = 5000L
        assertFalse(state.seeThrough)
        assertEquals(1f, state.alpha)
    }

    @Test fun `open close open creates fresh attached overlay and peek restores touches`() {
        val service = Robolectric.buildService(EQOAccessibilityService::class.java).create()
        service.get().onServiceConnected()
        try {
            ExplainOverlay.open(service.get())
            val first = current()!!
            val sheet = field(first, "sheet") as ViewGroup
            val params = field(first, "params") as WindowManager.LayoutParams
            assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
            assertTrue(params.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            buttons(sheet).first { it.text == context.getString(R.string.explain_see_screen) }.performClick()
            assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
            assertFalse(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
            buttons(sheet).first { it.text == context.getString(R.string.explain_close) }.performClick()
            assertNull(current())
            ExplainOverlay.open(service.get())
            assertNotSame(first, current())
            assertEquals(true, field(current()!!, "attached"))
        } finally {
            current()?.let { invoke(it, "close") }
            service.destroy()
        }
    }

    @Test fun `entry sources use transient activity and distinct pending intents`() {
        for (source in listOf("edge_handle", "tile", "notification", "accessibility_button")) {
            val intent = ExplainEntryActivity.entryIntent(context, source)
            assertEquals(ExplainEntryActivity::class.java.name, intent.component?.className)
            assertEquals(source, intent.getStringExtra("entry_source"))
        }
        val notification = ExplainEntryActivity.pending(context)
        val tile = ExplainEntryActivity.pending(context, "tile")
        assertEquals("notification", shadowOf(notification).savedIntent.getStringExtra("entry_source"))
        assertEquals("tile", shadowOf(tile).savedIntent.getStringExtra("entry_source"))
    }

    @Test fun `notification repost restores missing entry without service and respects off`() {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.cancel(701)
        ExplainNotificationService.refresh(context)
        assertNull(shadowOf(manager).getNotification(701))
        ExplainSettings.set(context, "notification", true)
        ExplainNotificationService.refresh(context)
        assertNotNull(shadowOf(manager).getNotification(701))
        manager.cancel(701)
        ExplainNotificationService.refresh(context)
        assertNotNull(shadowOf(manager).getNotification(701))
    }

    @Test fun `service connect and window changes refresh but content changes do not`() {
        var refreshes = 0
        EdgeHandleFeatures.refreshEntries = { refreshes++ }
        val controller = Robolectric.buildService(EQOAccessibilityService::class.java).create()
        try {
            controller.get().onServiceConnected()
            assertEquals(1, refreshes)
            controller.get().onAccessibilityEvent(AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED))
            assertEquals(2, refreshes)
            controller.get().onAccessibilityEvent(AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED))
            assertEquals(2, refreshes)
        } finally { controller.destroy() }
    }

    @Test fun `hub switch persists opt in and background row opens battery settings`() {
        val lifecycle = Robolectric.buildActivity(SetupHubActivity::class.java).setup()
        try {
            val activity = lifecycle.get()
            val toggle = activity.findViewById<Switch>(R.id.hub_edge_handle)
            assertFalse(toggle.isChecked)
            toggle.isChecked = true
            assertTrue(HandlePreferences(context).enabled)
            toggle.isChecked = false
            assertFalse(HandlePreferences(context).enabled)
            activity.findViewById<Button>(R.id.hub_background).performClick()
            assertEquals(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                shadowOf(activity).nextStartedActivity.action)
        } finally { lifecycle.pause().stop().destroy() }
    }

    @Test fun `hidden app list supports individual and all unhide and cannot hide EQO`() {
        val prefs = HandlePreferences(context)
        prefs.hide(context.packageName)
        assertFalse(prefs.isHidden(context.packageName))
        assertFalse(context.packageName in prefs.hiddenApps())
        prefs.preferences.edit().putStringSet("hidden_apps", setOf(context.packageName, "test.one", "test.two")).commit()
        assertFalse(prefs.isHidden(context.packageName))
        val lifecycle = Robolectric.buildActivity(EdgeHandleSettingsActivity::class.java).setup()
        try {
            val activity = lifecycle.get()
            fun root() = activity.findViewById<ViewGroup>(android.R.id.content)
            buttons(root()).first { it.text == context.getString(R.string.handle_unhide_app, "test.one") }.performClick()
            assertFalse(prefs.isHidden("test.one"))
            assertTrue(prefs.isHidden("test.two"))
            buttons(root()).first { it.text == context.getString(R.string.handle_unhide_all) }.performClick()
            assertTrue(prefs.hiddenApps().isEmpty())
        } finally { lifecycle.pause().stop().destroy() }
    }

    private fun buttons(view: View): List<Button> =
        (if (view is Button) listOf(view) else emptyList()) +
            (if (view is ViewGroup) (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) } else emptyList())

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)

    private fun invoke(target: Any, name: String) {
        target.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(target)
    }

    private fun current(): Any? {
        val ref = ExplainOverlay::class.java.getDeclaredField("current").apply { isAccessible = true }.get(null)
        return (ref as? WeakReference<*>)?.get()
    }
}
