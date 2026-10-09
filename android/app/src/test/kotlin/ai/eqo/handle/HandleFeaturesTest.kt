// Origin: EQO edge-handle task; Android preferences, UI entry and existing run-control regressions.
package ai.eqo.handle

import ai.eqo.R
import ai.eqo.accessibility.TakeoverDetector
import ai.eqo.accessibility.handle.EdgeHandleFeatures
import ai.eqo.accessibility.handle.HandlePreferences
import ai.eqo.accessibility.handle.HandleWindowGuard
import ai.eqo.core.agent.ActionLoop
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopState
import ai.eqo.core.agent.LoopStep
import ai.eqo.study.ApprovalOutcome
import ai.eqo.task.EqoAutomationPort
import ai.eqo.task.StudyActionExecutor
import ai.eqo.task.StudyApprovalGate
import ai.eqo.task.StudyApprovalSurface
import ai.eqo.task.StudyPermissionCheck
import ai.eqo.task.StudyTaskController
import ai.eqo.task.TaskActivity
import ai.eqo.task.TaskRunSession
import android.content.Context
import android.widget.EditText
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class HandleFeaturesTest {
    private lateinit var context: Context

    @Before
    fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        HandlePreferences(context).preferences.edit { clear() }
        HandleWindowGuard.shared.clear()
    }

    @After
    fun cleanup() {
        TaskRunSession.controller = null
        HandleWindowGuard.shared.clear()
    }

    @Test
    fun `handle starts off and shortcut choices placement and hidden apps survive recreation`() {
        val preferences = HandlePreferences(context)
        assertFalse(preferences.enabled)
        val registry = createHandleRegistry(context)
        assertEquals(listOf("ask_eqo", "pause", "stop", "open_eqo"), registry.ordered().map { it.id })
        assertTrue(registry.ordered().all { registry.enabled(it.id) })
        registry.setEnabled("ask_eqo", false)
        registry.move("open_eqo", -3)
        preferences.enabled = true
        preferences.rightEdge = false
        preferences.verticalFraction = 0.7f
        preferences.hide("test.app")
        val recreated = createHandleRegistry(context)
        val reloaded = HandlePreferences(context)
        assertEquals("open_eqo", recreated.ordered().first().id)
        assertFalse(recreated.enabled("ask_eqo"))
        assertTrue(reloaded.enabled)
        assertFalse(reloaded.rightEdge)
        assertEquals(0.7f, reloaded.verticalFraction)
        assertTrue(reloaded.isHidden("test.app"))
        reloaded.clearHiddenApps()
        assertFalse(preferences.isHidden("test.app"))
        recreated.reset()
        assertTrue(recreated.enabled("ask_eqo"))
    }

    @Test
    fun `pause and stop shortcuts disappear when idle and call the existing controller during a run`() =
        runTest {
            val detector = TakeoverDetector()
            val controller =
                StudyTaskController(
                    steps = (1..2).map { LoopStep("s$it", ExecutedAction("open_app", mapOf("app" to "test"))) },
                    permissionCheck = StudyPermissionCheck({ true }, { true }, { true }),
                    approvalGate = StudyApprovalGate(StudyApprovalSurface { ApprovalOutcome.Approved }, { 0L }),
                    executor = StudyActionExecutor(EqoAutomationPort({ null }, { _, _ -> false }, { true })),
                    observe = { "" },
                    config = ActionLoop.Config(interStepDelayMs = 8000),
                    takeoverDetector = detector,
                )
            val registry = createHandleRegistry(context)
            assertEquals(listOf("ask_eqo", "open_eqo"), registry.visible(context).map { it.id })
            TaskRunSession.controller = controller
            val run = async { controller.run() }
            runCurrent()
            assertEquals(LoopState.RUNNING, controller.currentState())
            registry.visible(context).first { it.id == "pause" }.run(context)
            advanceTimeBy(300)
            runCurrent()
            assertEquals(LoopState.PAUSED, controller.currentState())
            assertFalse(detector.isPaused)
            registry.visible(context).first { it.id == "stop" }.run(context)
            advanceUntilIdle()
            assertEquals("STOPPED", run.await().terminal)
        }

    @Test
    fun `Ask opens the request screen with focus and never plans or runs a request`() {
        val registry = createHandleRegistry(context)
        registry.ordered().first { it.id == "ask_eqo" }.run(context)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(TaskActivity::class.java.name, intent.component!!.className)
        assertTrue(intent.getBooleanExtra(TaskActivity.FOCUS_REQUEST, false))
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java, intent).setup()
        assertTrue(lifecycle.get().findViewById<EditText>(R.id.task_request).hasFocus())
        assertEquals(null, TaskRunSession.controller)
        lifecycle.pause().stop().destroy()
    }

    @Test
    fun `settings renders every registered entry without enabling the handle`() {
        EdgeHandleFeatures.install(createHandleRegistry(context))
        val lifecycle = Robolectric.buildActivity(EdgeHandleSettingsActivity::class.java).setup()
        assertEquals(context.getString(R.string.handle_settings_title), lifecycle.get().title)
        assertFalse(HandlePreferences(context).enabled)
        lifecycle.pause().stop().destroy()
    }

    @Test
    fun `overlay requires probe and opt in and panel supports outside and Back dismissal`() {
        EdgeHandleFeatures.install(createHandleRegistry(context))
        val manager = context.getSystemService(android.view.WindowManager::class.java)
        val overlay =
            ai.eqo.accessibility.handle
                .EdgeHandleOverlay(context, manager)
        try {
            overlay.setProbeAvailable(true)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(null, overlayView(overlay))
            HandlePreferences(context).enabled = true
            shadowOf(android.os.Looper.getMainLooper()).idle()
            val handle = overlayView(overlay)!!
            val params = handle.layoutParams as android.view.WindowManager.LayoutParams
            assertEquals(android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, params.type)
            handle.performClick()
            assertTrue(HandleWindowGuard.shared.panelOpen)
            overlayView(overlay)!!.performClick()
            assertFalse(HandleWindowGuard.shared.panelOpen)
            overlayView(overlay)!!.performClick()
            val panel = overlayView(overlay)!!
            assertTrue(panel.dispatchKeyEvent(android.view.KeyEvent(1, android.view.KeyEvent.KEYCODE_BACK)))
            assertFalse(HandleWindowGuard.shared.panelOpen)
            overlay.setProbeAvailable(false)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(null, overlayView(overlay))
            assertEquals(null, HandleWindowGuard.shared.bounds)
        } finally {
            overlay.destroy()
        }
    }

    private fun overlayView(overlay: ai.eqo.accessibility.handle.EdgeHandleOverlay): android.view.View? {
        val field = overlay.javaClass.getDeclaredField("view")
        field.isAccessible = true
        return field.get(overlay) as android.view.View?
    }
}
