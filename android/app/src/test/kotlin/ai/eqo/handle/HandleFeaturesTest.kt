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
        assertEquals(listOf("explain_screen", "ask_eqo", "pause", "stop", "open_eqo"), registry.ordered().map { it.id })
        assertTrue(registry.ordered().all { registry.enabled(it.id) })
        registry.setEnabled("ask_eqo", false)
        registry.move("open_eqo", -4)
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
            assertEquals(listOf("explain_screen", "ask_eqo", "open_eqo"), registry.visible(context).map { it.id })
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
    @Suppress("LongMethod") // One integrated real-panel/due-step/Pause/Stop scheduling regression.
    fun `opening real panel during pacing preserves controls past due step without automation`() =
        runTest {
            val detector = TakeoverDetector()
            var rootReads = 0
            val automation =
                ai.eqo.accessibility.EqoAutomation(
                    {
                        rootReads++
                        null
                    },
                    { ai.eqo.accessibility.EqoAutomation.ServiceState.AVAILABLE },
                    detector,
                    ownPackage = context.packageName,
                )
            val port = EqoAutomationPort({ automation }, { _, _ -> false }, { true })
            val controller =
                StudyTaskController(
                    steps =
                        listOf(
                            LoopStep("open", ExecutedAction("open_app", mapOf("app" to "test"))),
                            LoopStep("click", ExecutedAction("click_text", mapOf("text" to "Send"))),
                        ),
                    permissionCheck = StudyPermissionCheck({ true }, { true }, { true }),
                    approvalGate = StudyApprovalGate(StudyApprovalSurface { ApprovalOutcome.Approved }, { 0L }),
                    executor = StudyActionExecutor(port),
                    observe = { "" },
                    config = ActionLoop.Config(interStepDelayMs = 8000),
                    takeoverDetector = detector,
                )
            TaskRunSession.controller = controller
            EdgeHandleFeatures.install(createHandleRegistry(context), ::prepareHandlePanel)
            HandlePreferences(context).enabled = true
            val overlay =
                ai.eqo.accessibility.handle.EdgeHandleOverlay(
                    context,
                    context.getSystemService(android.view.WindowManager::class.java),
                )
            try {
                overlay.setProbeAvailable(true)
                shadowOf(android.os.Looper.getMainLooper()).idle()
                val run = async { controller.run() }
                runCurrent()
                overlayView(overlay)!!.performClick()
                assertFalse(HandleWindowGuard.shared.panelOpen)
                advanceTimeBy(100)
                runCurrent()
                shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
                assertTrue(HandleWindowGuard.shared.panelOpen)
                advanceTimeBy(9000)
                runCurrent()
                assertFalse(run.isCompleted)
                assertEquals(LoopState.PAUSED, controller.currentState())
                assertFalse(detector.isPaused)
                assertEquals(0, rootReads)
                assertEquals(null, port.lastFailure)
                assertFalse(automation.tap("Pause").isSuccess)
                assertFalse(automation.tap("Send").isSuccess)
                assertEquals(0, rootReads)
                panelButton(overlay, R.string.task_pause).performClick()
                assertFalse(HandleWindowGuard.shared.panelOpen)
                advanceTimeBy(9000)
                runCurrent()
                assertEquals(LoopState.PAUSED, controller.currentState())
                assertEquals(0, rootReads)
                overlayView(overlay)!!.performClick()
                panelButton(overlay, R.string.task_stop).performClick()
                advanceUntilIdle()
                assertEquals("STOPPED", run.await().terminal)
                assertEquals(0, rootReads)
                assertFalse(detector.isPaused)
            } finally {
                overlay.destroy()
            }
        }

    @Test
    fun `panel waits for in flight action to settle and dismissal never resumes`() =
        runTest {
            val detector = TakeoverDetector()
            val feedback = mutableListOf<ai.eqo.task.TaskControlFeedback>()
            var applied = false
            val executor =
                StudyActionExecutor(
                    EqoAutomationPort({ null }, { _, _ -> false }),
                    registryExecute = { _, _ ->
                        kotlinx.coroutines.delay(500)
                        assertFalse(HandleWindowGuard.shared.panelOpen)
                        applied = true
                        ai.eqo.actions.base.ActionResult
                            .Success()
                    },
                )
            val controller =
                StudyTaskController(
                    steps = listOf(LoopStep("work", ExecutedAction("WAIT"))),
                    permissionCheck = StudyPermissionCheck({ true }, { true }, { true }),
                    approvalGate = StudyApprovalGate(StudyApprovalSurface { ApprovalOutcome.Approved }, { 0L }),
                    executor = executor,
                    observe = { "" },
                    onControlFeedback = feedback::add,
                    takeoverDetector = detector,
                )
            TaskRunSession.controller = controller
            EdgeHandleFeatures.install(createHandleRegistry(context), ::prepareHandlePanel)
            HandlePreferences(context).enabled = true
            val overlay = createOverlay()
            try {
                overlay.setProbeAvailable(true)
                shadowOf(android.os.Looper.getMainLooper()).idle()
                val run = async { controller.run() }
                runCurrent()
                assertTrue(controller.isActionInFlight())
                overlayView(overlay)!!.performClick()
                shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
                assertFalse(HandleWindowGuard.shared.panelOpen)
                assertEquals(1, feedback.count { it == ai.eqo.task.TaskControlFeedback.PAUSE_REQUESTED })
                advanceTimeBy(600)
                runCurrent()
                assertTrue(applied)
                assertFalse(controller.isActionInFlight())
                shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
                assertTrue(HandleWindowGuard.shared.panelOpen)
                overlayView(overlay)!!.performClick()
                advanceTimeBy(9000)
                runCurrent()
                assertEquals(LoopState.PAUSED, controller.currentState())
                assertFalse(run.isCompleted)
                assertFalse(detector.isPaused)
                assertTrue(controller.stop())
                advanceUntilIdle()
                assertEquals("STOPPED", run.await().terminal)
            } finally {
                overlay.destroy()
            }
        }

    private fun createOverlay(): ai.eqo.accessibility.handle.EdgeHandleOverlay =
        ai.eqo.accessibility.handle.EdgeHandleOverlay(
            context,
            context.getSystemService(android.view.WindowManager::class.java),
        )

    private fun panelButton(
        overlay: ai.eqo.accessibility.handle.EdgeHandleOverlay,
        label: Int,
    ): android.widget.Button =
        descendants(overlayView(overlay)!!).filterIsInstance<android.widget.Button>().first {
            it.text == context.getString(label)
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
        assertTrue(lifecycle.get().findViewById<android.widget.Button>(R.id.task_voice_button).isShown)
        assertFalse(descendants(lifecycle.get().window.decorView).filterIsInstance<android.widget.TextView>().any {
            it.text == context.getString(R.string.task_practice_title)
        })
        assertEquals(null, TaskRunSession.controller)
        lifecycle.pause().stop().destroy()
    }

    @Test
    fun `settings renders every registered entry without enabling the handle`() {
        val registry = createHandleRegistry(context)
        EdgeHandleFeatures.install(registry)
        val lifecycle = Robolectric.buildActivity(EdgeHandleSettingsActivity::class.java).setup()
        try {
            val activity = lifecycle.get()
            assertEquals(context.getString(R.string.handle_settings_title), activity.title)
            assertFalse(HandlePreferences(context).enabled)
            val switches = descendants(activity.window.decorView).filterIsInstance<android.widget.Switch>()
            val labels = listOf(R.string.handle_enable) + registry.ordered().map { it.labelRes }
            assertEquals(labels.map(context::getString), switches.map { it.text.toString() })
            switches.first().isChecked = true
            switches.first { it.text == context.getString(R.string.handle_ask) }.isChecked = false
            assertTrue(HandlePreferences(context).enabled)
            assertFalse(registry.enabled("ask_eqo"))
            val move =
                descendants(activity.window.decorView).filterIsInstance<android.widget.Button>().first {
                    it.contentDescription ==
                        context.getString(
                            R.string.handle_move_up_named,
                            context.getString(R.string.handle_open),
                        )
                }
            move.performClick()
            assertEquals(listOf("explain_screen", "ask_eqo", "pause", "open_eqo", "stop"),
                registry.ordered().map { it.id })
            descendants(activity.window.decorView)
                .filterIsInstance<android.widget.Button>()
                .first {
                    it.text == context.getString(R.string.handle_reset)
                }.performClick()
            assertEquals(listOf("explain_screen", "ask_eqo", "pause", "stop", "open_eqo"),
                registry.ordered().map { it.id })
            assertTrue(registry.enabled("ask_eqo"))
            assertTrue(HandlePreferences(context).enabled)
        } finally {
            lifecycle.pause().stop().destroy()
        }
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

    @Test
    fun `pending panel opening is cancelled on foreground change and disable`() {
        var ready = false
        EdgeHandleFeatures.install(createHandleRegistry(context)) { ready }
        HandlePreferences(context).enabled = true
        val overlay =
            ai.eqo.accessibility.handle.EdgeHandleOverlay(
                context,
                context.getSystemService(android.view.WindowManager::class.java),
            )
        try {
            overlay.setProbeAvailable(true)
            val main = shadowOf(android.os.Looper.getMainLooper())
            main.idle()
            overlayView(overlay)!!.performClick()
            assertFalse(HandleWindowGuard.shared.panelOpen)
            overlay.updateForeground("other.app")
            ready = true
            main.idleFor(java.time.Duration.ofMillis(100))
            assertFalse(HandleWindowGuard.shared.panelOpen)
            ready = false
            overlayView(overlay)!!.performClick()
            HandlePreferences(context).enabled = false
            main.idle()
            ready = true
            main.idleFor(java.time.Duration.ofMillis(100))
            assertEquals(null, overlayView(overlay))
            assertFalse(HandleWindowGuard.shared.panelOpen)
        } finally {
            overlay.destroy()
        }
    }

    @Test
    fun `drag across the screen switches edge rather than opening the panel and inward swipe opens it`() {
        EdgeHandleFeatures.install(createHandleRegistry(context))
        HandlePreferences(context).enabled = true
        val manager = context.getSystemService(android.view.WindowManager::class.java)
        val overlay =
            ai.eqo.accessibility.handle
                .EdgeHandleOverlay(context, manager)
        try {
            overlay.setProbeAvailable(true)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            val handle = overlayView(overlay)!!
            val width =
                manager.currentWindowMetrics.bounds
                    .width()
                    .toFloat()
            drag(handle, width - 10, 10f, 200f)
            assertFalse(HandlePreferences(context).rightEdge)
            assertFalse(HandleWindowGuard.shared.panelOpen)
            drag(handle, 10f, width - 10, 200f)
            assertTrue(HandlePreferences(context).rightEdge)
            assertFalse(HandleWindowGuard.shared.panelOpen)
            drag(handle, width - 10, width - 110, 200f)
            assertTrue(HandleWindowGuard.shared.panelOpen)
            overlayView(overlay)!!.performClick()
            val returned = overlayView(overlay)!!
            val oldPosition = HandlePreferences(context).verticalFraction
            drag(returned, width - 10, width - 10, 200f, 300f)
            assertTrue(HandlePreferences(context).verticalFraction > oldPosition)
            overlay.configurationChanged()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(overlayView(overlay) != null)
            overlay.updateForeground("test.app")
            HandlePreferences(context).hide("test.app")
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(null, overlayView(overlay))
            overlay.updateForeground("other.app")
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(overlayView(overlay) != null)
            overlayView(overlay)!!.performLongClick()
            val panel = overlayView(overlay) as android.view.ViewGroup
            val scroll = panel.getChildAt(0) as android.view.ViewGroup
            val column = scroll.getChildAt(0) as android.view.ViewGroup
            column.getChildAt(column.childCount - 2).performClick()
            assertTrue(HandlePreferences(context).isHidden("other.app"))
            assertEquals(null, overlayView(overlay))
        } finally {
            overlay.destroy()
        }
    }

    private fun drag(
        view: android.view.View,
        fromX: Float,
        toX: Float,
        fromY: Float,
        toY: Float = fromY,
    ) {
        listOf(
            Triple(android.view.MotionEvent.ACTION_DOWN, fromX, fromY),
            Triple(android.view.MotionEvent.ACTION_MOVE, toX, toY),
            Triple(android.view.MotionEvent.ACTION_UP, toX, toY),
        ).forEach { (action, x, y) ->
            val event = android.view.MotionEvent.obtain(0, 0, action, x, y, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private fun overlayView(overlay: ai.eqo.accessibility.handle.EdgeHandleOverlay): android.view.View? {
        val field = overlay.javaClass.getDeclaredField("view")
        field.isAccessible = true
        return field.get(overlay) as android.view.View?
    }

    private fun descendants(view: android.view.View): List<android.view.View> =
        buildList {
            add(view)
            if (view is android.view.ViewGroup) {
                for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
            }
        }
}
