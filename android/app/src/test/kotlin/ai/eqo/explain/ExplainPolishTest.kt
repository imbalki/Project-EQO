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
        HandlePreferences(context)
            .preferences
            .edit()
            .clear()
            .commit()
        EdgeHandleFeatures.refreshEntries = ExplainNotificationService::refresh
        EdgeHandleFeatures.explain = ExplainEntryActivity::launch
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
        invoke(service.get(), "onServiceConnected")
        try {
            ExplainOverlay.open(service.get())
            val first = current()!!
            val sheet = field(first, "sheet") as ViewGroup
            val params = field(first, "params") as WindowManager.LayoutParams
            val manager = service.get().getSystemService(WindowManager::class.java)
            assertTrue(params.height <= (manager.currentWindowMetrics.bounds.height() * 0.35f).toInt())
            val content = field(first, "content") as View
            val background = content.background as android.graphics.drawable.GradientDrawable
            assertEquals(217, (background.color!!.defaultColor ushr 24))
            val resize = buttons(sheet).first { it.text == context.getString(R.string.explain_resize) }
            resize.performClick()
            assertEquals(ExplainPanelState.Size.LARGE, (field(first, "panel") as ExplainPanelState).size)
            resize.performClick()
            assertEquals(ExplainPanelState.Size.SMALL, (field(first, "panel") as ExplainPanelState).size)
            resize.performClick()
            assertEquals(ExplainPanelState.Size.MEDIUM, (field(first, "panel") as ExplainPanelState).size)
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
        ExplainSettings.set(context, "notification", false)
        ExplainNotificationService.update(context)
        assertNull(shadowOf(manager).getNotification(701))
        ExplainNotificationService.refresh(context)
        assertNull(shadowOf(manager).getNotification(701))
    }

    @Test fun `service connect and window changes refresh but content changes do not`() {
        var refreshes = 0
        EdgeHandleFeatures.refreshEntries = { refreshes++ }
        val controller = Robolectric.buildService(EQOAccessibilityService::class.java).create()
        try {
            invoke(controller.get(), "onServiceConnected")
            assertEquals(1, refreshes)
            controller.get().onAccessibilityEvent(AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED))
            assertEquals(2, refreshes)
            controller.get().onAccessibilityEvent(AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED))
            assertEquals(2, refreshes)
        } finally {
            controller.destroy()
        }
    }

    @Test fun `accessibility button routes explicit Explain entry source`() {
        var source: String? = null
        EdgeHandleFeatures.explain = { _, entry -> source = entry }
        val controller = Robolectric.buildService(EQOAccessibilityService::class.java).create()
        try {
            invoke(controller.get(), "onServiceConnected")
            val callback =
                field(controller.get(), "explainButton") as
                    android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback
            callback.onClicked(controller.get().accessibilityButtonController)
            assertEquals("accessibility_button", source)
        } finally {
            controller.destroy()
        }
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
            assertEquals(
                android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                shadowOf(activity).nextStartedActivity.action,
            )
        } finally {
            lifecycle.pause().stop().destroy()
        }
    }

    @Test fun `hub opt in draws handle immediately and EQO panel cannot hide itself`() {
        val service = Robolectric.buildService(EQOAccessibilityService::class.java).create()
        invoke(service.get(), "onServiceConnected")
        val lifecycle = Robolectric.buildActivity(SetupHubActivity::class.java).setup()
        try {
            val edge = field(service.get(), "edgeHandle")!!
            val main = shadowOf(Looper.getMainLooper())
            main.idle()
            assertNull(field(edge, "view"))
            lifecycle.get().findViewById<Switch>(R.id.hub_edge_handle).isChecked = true
            main.idle()
            val handle = field(edge, "view") as View
            assertTrue(handle.isAttachedToWindow)
            assertTrue(HandlePreferences(context).hintShown)
            handle.performClick()
            val panel = field(edge, "view") as ViewGroup
            assertFalse(
                buttons(panel).any {
                    it.text == context.getString(ai.eqo.platform.R.string.edge_handle_hide_app)
                },
            )
            lifecycle.get().findViewById<Switch>(R.id.hub_edge_handle).isChecked = false
            main.idle()
            assertNull(field(edge, "view"))
        } finally {
            lifecycle.pause().stop().destroy()
            service.destroy()
        }
    }

    @Test fun `Explain shortcut is first for fresh registry and routes edge source`() {
        val registry = ai.eqo.handle.createHandleRegistry(context)
        val shortcut = registry.ordered().first()
        assertEquals("explain_screen", shortcut.id)
        assertTrue(registry.enabled(shortcut.id))
        shortcut.run(context)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(ExplainEntryActivity::class.java.name, intent.component?.className)
        assertEquals("edge_handle", intent.getStringExtra("entry_source"))
    }

    @Test fun `entry without accessibility logs no service and never opens panel`() {
        val lifecycle =
            Robolectric
                .buildActivity(
                    ExplainEntryActivity::class.java,
                    ExplainEntryActivity.entryIntent(context, "edge_handle"),
                ).create()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertNull(current())
        assertTrue(
            org.robolectric.shadows.ShadowLog.getLogsForTag("EqoExplain").any {
                it.msg == "no-service source=edge_handle"
            },
        )
        lifecycle.destroy()
    }

    @Test fun `hidden app list supports individual and all unhide and cannot hide EQO`() {
        val prefs = HandlePreferences(context)
        prefs.hide(context.packageName)
        assertFalse(prefs.isHidden(context.packageName))
        assertFalse(context.packageName in prefs.hiddenApps())
        prefs.preferences
            .edit()
            .putStringSet("hidden_apps", setOf(context.packageName, "test.one", "test.two"))
            .commit()
        assertFalse(prefs.isHidden(context.packageName))
        val lifecycle = Robolectric.buildActivity(EdgeHandleSettingsActivity::class.java).setup()
        try {
            val activity = lifecycle.get()

            fun root() = activity.findViewById<ViewGroup>(android.R.id.content)
            assertTrue(texts(root()).any { it.text == context.getString(R.string.handle_hidden_count, 3) })
            buttons(root())
                .first { it.text == context.getString(R.string.handle_unhide_app, "test.one") }
                .performClick()
            assertFalse(prefs.isHidden("test.one"))
            assertTrue(texts(root()).any { it.text == context.getString(R.string.handle_hidden_count, 2) })
            assertTrue(prefs.isHidden("test.two"))
            buttons(root()).first { it.text == context.getString(R.string.handle_unhide_all) }.performClick()
            assertTrue(prefs.hiddenApps().isEmpty())
            assertTrue(texts(root()).any { it.text == context.getString(R.string.handle_hidden_count, 0) })
        } finally {
            lifecycle.pause().stop().destroy()
        }
    }

    private fun texts(view: View): List<android.widget.TextView> =
        (if (view is android.widget.TextView) listOf(view) else emptyList()) +
            (if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList())

    private fun buttons(view: View): List<Button> =
        (if (view is Button) listOf(view) else emptyList()) +
            (if (view is ViewGroup) (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) } else emptyList())

    private fun field(
        target: Any,
        name: String,
    ): Any? =
        target.javaClass
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .get(target)

    private fun invoke(
        target: Any,
        name: String,
    ) {
        target.javaClass
            .getDeclaredMethod(name)
            .apply { isAccessible = true }
            .invoke(target)
    }

    private fun current(): Any? {
        val ref =
            ExplainOverlay::class.java
                .getDeclaredField("current")
                .apply { isAccessible = true }
                .get(null)
        return (ref as? WeakReference<*>)?.get()
    }
}
