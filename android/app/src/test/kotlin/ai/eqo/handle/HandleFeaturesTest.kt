// Origin: EQO edge-handle task; Android preferences, UI entry and existing run-control regressions.
package ai.eqo.handle

import ai.eqo.R
import ai.eqo.accessibility.TakeoverDetector
import ai.eqo.accessibility.handle.EdgeHandleFeatures
import ai.eqo.accessibility.handle.HandlePreferences
import ai.eqo.accessibility.handle.HandleWindowGuard
import ai.eqo.accessibility.handle.PanelShortcutSpec
import ai.eqo.accessibility.handle.panelActionSpecs
import ai.eqo.accessibility.handle.panelHeight
import ai.eqo.accessibility.handle.panelWidthDp
import ai.eqo.core.agent.ActionLoop
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopState
import ai.eqo.core.agent.LoopStep
import ai.eqo.study.ApprovalOutcome
import ai.eqo.task.DictationFailure
import ai.eqo.task.EqoAutomationPort
import ai.eqo.task.HandleDictation
import ai.eqo.task.StudyActionExecutor
import ai.eqo.task.StudyApprovalGate
import ai.eqo.task.StudyApprovalSurface
import ai.eqo.task.StudyPermissionCheck
import ai.eqo.task.StudyTaskController
import ai.eqo.task.TaskActivity
import ai.eqo.task.TaskRunSession
import android.content.Context
import android.view.View
import android.view.WindowInsets
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
@Suppress("LargeClass") // Shared overlay fixture keeps run-control, focus and result lifecycle regressions together.
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
        EdgeHandleFeatures.dictationTranscript = null
        EdgeHandleFeatures.dictationInserted = false
        EdgeHandleFeatures.dictationStatus = 0
        EdgeHandleFeatures.dictationBusy = false
    }

    @Test
    fun `handle starts off and shortcut choices placement and hidden apps survive recreation`() {
        val preferences = HandlePreferences(context)
        assertFalse(preferences.enabled)
        val registry = createHandleRegistry(context)
        assertEquals(
            listOf("explain_screen", "ask_eqo", "pause", "stop", "open_eqo", "dictation", "recording", "screenshot"),
            registry.ordered().map { it.id },
        )
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
            assertEquals(
                listOf("explain_screen", "ask_eqo", "open_eqo", "dictation", "recording", "screenshot"),
                registry.visible(context).map { it.id },
            )
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
                panelAction(overlay, R.string.task_pause).performClick()
                assertFalse(HandleWindowGuard.shared.panelOpen)
                advanceTimeBy(9000)
                runCurrent()
                assertEquals(LoopState.PAUSED, controller.currentState())
                assertEquals(0, rootReads)
                overlayView(overlay)!!.performClick()
                panelAction(overlay, R.string.task_stop).performClick()
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

    /** Panel actions are icon buttons; UI tests find them by their spoken name. */
    private fun panelAction(
        overlay: ai.eqo.accessibility.handle.EdgeHandleOverlay,
        label: Int,
    ): android.view.View =
        descendants(overlayView(overlay)!!).first {
            it.contentDescription == context.getString(label)
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
        assertFalse(
            descendants(lifecycle.get().window.decorView).filterIsInstance<android.widget.TextView>().any {
                it.text == context.getString(R.string.task_practice_title)
            },
        )
        assertEquals(null, TaskRunSession.controller)
        lifecycle.pause().stop().destroy()
    }

    @Test
    fun `settings renders every registered entry without enabling the handle`() {
        val registry = createHandleRegistry(context)
        val originalOrder = registry.ordered().map { it.id }
        val movedOrder =
            originalOrder.toMutableList().apply {
                java.util.Collections.swap(this, indexOf("stop"), indexOf("open_eqo"))
            }
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
            val dictation = switches.first { it.text == context.getString(R.string.dictation_switch) }
            assertTrue(dictation.isChecked)
            dictation.isChecked = false
            assertFalse(createHandleRegistry(context).enabled("dictation"))
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
            assertEquals(
                movedOrder,
                registry.ordered().map { it.id },
            )
            descendants(activity.window.decorView)
                .filterIsInstance<android.widget.Button>()
                .first {
                    it.text == context.getString(R.string.handle_reset)
                }.performClick()
            assertEquals(
                originalOrder,
                registry.ordered().map { it.id },
            )
            assertTrue(registry.enabled("ask_eqo"))
            assertTrue(HandlePreferences(context).enabled)
            assertTrue(registry.enabled("dictation"))
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
            // Only an outside tap dismisses the panel (a tap inside it must not).
            val outside = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_OUTSIDE, 0f, 0f, 0)
            overlayView(overlay)!!.dispatchTouchEvent(outside)
            outside.recycle()
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
            // Only an outside tap dismisses the panel.
            val outside = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_OUTSIDE, 0f, 0f, 0)
            overlayView(overlay)!!.dispatchTouchEvent(outside)
            outside.recycle()
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
            panelAction(overlay, ai.eqo.platform.R.string.edge_handle_hide_app).performClick()
            assertTrue(HandlePreferences(context).isHidden("other.app"))
            assertEquals(null, overlayView(overlay))
        } finally {
            overlay.destroy()
        }
    }

    @Test
    @Suppress("LongMethod") // One lifecycle scenario verifies focus, start/stop/cancel, updates and opt-out cleanup.
    fun `direct handle mic keeps app focus and updates recording transcribing cancel and disabled states`() {
        val registry = createHandleRegistry(context)
        EdgeHandleFeatures.install(registry)
        HandlePreferences(context).enabled = true
        val tap = EdgeHandleFeatures.dictationTap
        val cancel = EdgeHandleFeatures.dictationCancel
        var taps = 0
        var cancels = 0
        EdgeHandleFeatures.dictationTap = { taps++ }
        EdgeHandleFeatures.dictationCancel = { cancels++ }
        registry.remove(EdgeHandleFeatures.DICTATION_ID)
        registry.register(
            BuiltInHandleShortcut(EdgeHandleFeatures.DICTATION_ID, R.string.dictation_switch, 0) {
                EdgeHandleFeatures.dictationTap(it)
            },
        )
        val overlay = createOverlay()
        try {
            overlay.setProbeAvailable(true)
            val main = shadowOf(android.os.Looper.getMainLooper())
            main.idle()
            val micLabel = ai.eqo.platform.R.string.edge_dictation_mic
            // Idle is a slim grip; dictation starts from the non-focusable shortcuts panel.
            assertFalse(descendants(overlayView(overlay)!!).any { it is android.widget.Button })
            overlayView(overlay)!!.performClick()
            panelAction(overlay, R.string.dictation_switch).performClick()

            val params = overlayView(overlay)!!.layoutParams as android.view.WindowManager.LayoutParams
            assertTrue(params.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
            assertEquals(1, taps)
            assertFalse(HandleWindowGuard.shared.panelOpen)
            EdgeHandleFeatures.dictationBusy = true
            EdgeHandleFeatures.dictationStatus = R.string.dictation_listening
            EdgeHandleFeatures.dictationChanged()
            main.idle()
            panelButton(overlay, micLabel).performClick()
            assertEquals(2, taps)
            panelButton(overlay, android.R.string.cancel).performClick()
            assertEquals(1, cancels)
            EdgeHandleFeatures.dictationStatus = R.string.dictation_transcribing
            EdgeHandleFeatures.dictationChanged()
            main.idle()
            assertTrue(
                descendants(overlayView(overlay)!!).filterIsInstance<android.widget.TextView>().any {
                    it.text == context.getString(R.string.dictation_transcribing)
                },
            )
            registry.setEnabled("dictation", false)
            main.idle()
            assertFalse(
                descendants(overlayView(overlay)!!).filterIsInstance<android.widget.Button>().any {
                    it.text == context.getString(micLabel)
                },
            )
        } finally {
            overlay.destroy()
            EdgeHandleFeatures.dictationTap = tap
            EdgeHandleFeatures.dictationCancel = cancel
            EdgeHandleFeatures.dictationBusy = false
            EdgeHandleFeatures.dictationStatus = 0
        }
    }

    @Test
    fun `dictation setup failures give specific Voice settings guidance and route to real settings`() {
        val messages =
            mapOf(
                DictationFailure.KEY to R.string.dictation_key,
                DictationFailure.MODEL to R.string.dictation_model,
                DictationFailure.CONSENT to R.string.dictation_consent,
                DictationFailure.PERMISSION to R.string.dictation_permission,
            )
        messages.forEach { (failure, resource) ->
            assertEquals(resource, HandleDictation.failureMessage(failure))
            assertTrue(context.getString(resource).contains("Voice settings"))
        }
        HandleDictation.openSettings(context)
        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(ai.eqo.onboarding.SetupHubActivity::class.java.name, intent.component!!.className)
        assertEquals(
            "This app did not accept the text. It is on your clipboard; paste it yourself.",
            context.getString(HandleDictation.failureMessage(DictationFailure.COPIED)),
        )
        assertEquals(
            "Dictation inserted. It is also on your clipboard.",
            context.getString(R.string.dictation_inserted),
        )
    }

    @Test
    fun `compact panel never focuses and collapses on idle outside and app switch with session hide`() {
        EdgeHandleFeatures.install(createHandleRegistry(context))
        HandlePreferences(context).enabled = true
        val overlay = createOverlay()
        val main = shadowOf(android.os.Looper.getMainLooper())
        try {
            overlay.setProbeAvailable(true)
            main.idle()
            overlayView(overlay)!!.performClick()
            val params = overlayView(overlay)!!.layoutParams as android.view.WindowManager.LayoutParams
            assertTrue(params.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
            assertTrue(params.width > 0 && params.height > 0)
            main.idleFor(java.time.Duration.ofSeconds(3))
            assertFalse(HandleWindowGuard.shared.panelOpen)
            overlayView(overlay)!!.performClick()
            val outside = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_OUTSIDE, 0f, 0f, 0)
            overlayView(overlay)!!.dispatchTouchEvent(outside)
            outside.recycle()
            assertFalse(HandleWindowGuard.shared.panelOpen)
            overlayView(overlay)!!.performClick()
            overlay.updateForeground("synthetic.app")
            main.idle()
            assertFalse(HandleWindowGuard.shared.panelOpen)
            overlayView(overlay)!!.performClick()
            panelAction(overlay, ai.eqo.platform.R.string.edge_handle_hide_now).performClick()
            main.idle()
            assertEquals(null, overlayView(overlay))
            assertFalse(HandlePreferences(context).isHidden("synthetic.app"))
            overlay.updateForeground("other.app")
            main.idle()
            assertTrue(overlayView(overlay) != null)
        } finally {
            overlay.destroy()
        }
    }

    @Test
    fun `failure card has selectable transcript copy dismiss and success disappears after two seconds`() {
        EdgeHandleFeatures.install(createHandleRegistry(context))
        HandlePreferences(context).enabled = true
        val overlay = createOverlay()
        val main = shadowOf(android.os.Looper.getMainLooper())
        try {
            overlay.setProbeAvailable(true)
            main.idle()
            EdgeHandleFeatures.dictationTranscript = "synthetic transcript"
            EdgeHandleFeatures.dictationStatus = R.string.dictation_copied
            EdgeHandleFeatures.dictationChanged()
            main.idle()
            val result =
                descendants(overlayView(overlay)!!)
                    .filterIsInstance<android.widget.TextView>()
                    .first { it.text.toString() == "synthetic transcript" }
            assertTrue(result.isTextSelectable)
            panelButton(overlay, ai.eqo.platform.R.string.edge_dictation_copy).performClick()
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            assertEquals("synthetic transcript", clipboard.primaryClip!!.getItemAt(0).text)
            panelButton(overlay, ai.eqo.platform.R.string.edge_dictation_dismiss).performClick()
            main.idle()
            assertEquals(null, EdgeHandleFeatures.dictationTranscript)
            assertFalse(descendants(overlayView(overlay)!!).any { it is android.widget.Button })
            EdgeHandleFeatures.dictationStatus = R.string.dictation_inserted
            EdgeHandleFeatures.dictationInserted = true
            EdgeHandleFeatures.dictationChanged()
            main.idle()
            assertTrue(
                descendants(overlayView(overlay)!!).filterIsInstance<android.widget.TextView>().any {
                    it.text == context.getString(ai.eqo.platform.R.string.edge_dictation_inserted)
                },
            )
            main.idleFor(java.time.Duration.ofSeconds(2))
            assertEquals(0, EdgeHandleFeatures.dictationStatus)
            assertFalse(descendants(overlayView(overlay)!!).any { it is android.widget.Button })
        } finally {
            overlay.destroy()
        }
    }

    @Test
    fun `previous success expiry cannot dismiss a newer failed transcript`() {
        EdgeHandleFeatures.install(createHandleRegistry(context))
        HandlePreferences(context).enabled = true
        val overlay = createOverlay()
        val main = shadowOf(android.os.Looper.getMainLooper())
        try {
            overlay.setProbeAvailable(true)
            main.idle()
            EdgeHandleFeatures.dictationInserted = true
            EdgeHandleFeatures.dictationStatus = R.string.dictation_inserted
            EdgeHandleFeatures.dictationChanged()
            main.idle()
            main.idleFor(java.time.Duration.ofSeconds(1))
            EdgeHandleFeatures.dictationInserted = false
            EdgeHandleFeatures.dictationTranscript = "new synthetic transcript"
            EdgeHandleFeatures.dictationStatus = R.string.dictation_copied
            EdgeHandleFeatures.dictationChanged()
            main.idleFor(java.time.Duration.ofSeconds(2))
            assertEquals("new synthetic transcript", EdgeHandleFeatures.dictationTranscript)
            assertTrue(panelButton(overlay, ai.eqo.platform.R.string.edge_dictation_copy).isShown)
        } finally {
            overlay.destroy()
        }
    }

    @Test
    fun `compact card puts fake shortcuts in two columns and outside tap only dismisses`() {
        var calls = 0
        val registry =
            ai.eqo.accessibility.handle
                .HandleShortcutRegistry<Context>(HandlePreferences(context))
        listOf(R.string.handle_ask, R.string.handle_open, R.string.explain_title, R.string.dictation_switch)
            .forEachIndexed { index, label ->
                registry.register(BuiltInHandleShortcut("fake_$index", label, 0) { calls++ })
            }
        EdgeHandleFeatures.install(registry)
        HandlePreferences(context).enabled = true
        val overlay = createOverlay()
        try {
            overlay.setProbeAvailable(true)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            overlayView(overlay)!!.performClick()
            val panel = overlayView(overlay)!!
            val params = panel.layoutParams as android.view.WindowManager.LayoutParams
            val density = context.resources.displayMetrics.density
            assertEquals((panelWidthDp(2) * density).toInt(), params.width)
            val scroll = (panel as android.view.ViewGroup).getChildAt(0) as android.widget.ScrollView
            val column = scroll.getChildAt(0)
            column.measure(
                View.MeasureSpec.makeMeasureSpec(params.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            val metrics = context.getSystemService(android.view.WindowManager::class.java).currentWindowMetrics
            val insets =
                metrics.windowInsets.getInsets(
                    WindowInsets.Type.ime() or WindowInsets.Type.systemBars(),
                )
            val available = metrics.bounds.height() - insets.top - insets.bottom
            assertEquals(panelHeight(column.measuredHeight, available), params.height)
            val cells = descendants(panel).filter { it.contentDescription != null }
            assertEquals(6, cells.size)
            assertTrue(cells.all { it.layoutParams.width == (48 * density).toInt() })
            assertTrue(cells.all { it.layoutParams.height == (48 * density).toInt() })
            assertTrue(
                cells.all {
                    val icon = (it as android.widget.FrameLayout).getChildAt(0) as android.widget.ImageView
                    icon.drawable != null && icon.layoutParams.width == (40 * density).toInt()
                },
            )
            val rows = cells.groupBy { it.parent }
            assertEquals(listOf(2, 2, 2), rows.values.map { it.size })
            val outside = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_OUTSIDE, 0f, 0f, 0)
            panel.dispatchTouchEvent(outside)
            outside.recycle()
            assertFalse(HandleWindowGuard.shared.panelOpen)
            assertEquals(0, calls)
            overlayView(overlay)!!.performClick()
            panelAction(overlay, R.string.handle_ask).performClick()
            assertEquals(1, calls)
            assertFalse(HandleWindowGuard.shared.panelOpen)
        } finally {
            overlay.destroy()
        }
    }

    @Test
    fun `tall compact panel scrolls inside its bounds and swipes never close it`() {
        val registry =
            ai.eqo.accessibility.handle
                .HandleShortcutRegistry<Context>(HandlePreferences(context))
        (1..40).forEach { index ->
            registry.register(BuiltInHandleShortcut("fake_$index", R.string.handle_ask, 0) {})
        }
        EdgeHandleFeatures.install(registry)
        HandlePreferences(context).enabled = true
        val overlay = createOverlay()
        try {
            overlay.setProbeAvailable(true)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            overlayView(overlay)!!.performClick()
            val panel = overlayView(overlay)!!
            val params = panel.layoutParams as android.view.WindowManager.LayoutParams
            val scroll = (panel as android.view.ViewGroup).getChildAt(0) as android.widget.ScrollView
            val column = scroll.getChildAt(0)
            column.measure(
                View.MeasureSpec.makeMeasureSpec(params.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            // The regression premise: the grid overflows the capped window and must scroll.
            assertTrue(params.height < column.measuredHeight)
            panel.measure(
                View.MeasureSpec.makeMeasureSpec(params.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(params.height, View.MeasureSpec.EXACTLY),
            )
            panel.layout(0, 0, params.width, params.height)
            steppedDrag(panel, 60f, params.height - 40f, 40f)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(HandleWindowGuard.shared.panelOpen)
            assertTrue(scroll.scrollY > 0)
        } finally {
            overlay.destroy()
        }
    }

    @Test
    fun `a vertical swipe inside the panel never fires the row under the finger`() {
        var calls = 0
        val registry =
            ai.eqo.accessibility.handle
                .HandleShortcutRegistry<Context>(HandlePreferences(context))
        listOf(R.string.handle_ask, R.string.handle_open).forEachIndexed { index, label ->
            registry.register(BuiltInHandleShortcut("fake_$index", label, 0) { calls++ })
        }
        EdgeHandleFeatures.install(registry)
        HandlePreferences(context).enabled = true
        val overlay = createOverlay()
        try {
            overlay.setProbeAvailable(true)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            overlayView(overlay)!!.performClick()
            val panel = overlayView(overlay)!!
            val params = panel.layoutParams as android.view.WindowManager.LayoutParams
            panel.measure(
                View.MeasureSpec.makeMeasureSpec(params.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(params.height, View.MeasureSpec.EXACTLY),
            )
            panel.layout(0, 0, params.width, params.height)
            // Beyond touch slop but fully inside the first icon cell ("Close").
            steppedDrag(panel, 30f, 20f, 50f)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(HandleWindowGuard.shared.panelOpen)
            assertEquals(0, calls)
            // A real tap on the same cell still runs the action.
            listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP).forEach { action ->
                val tap = android.view.MotionEvent.obtain(0, 0, action, 30f, 30f, 0)
                panel.dispatchTouchEvent(tap)
                tap.recycle()
            }
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertFalse(HandleWindowGuard.shared.panelOpen)
            assertEquals(0, calls)
        } finally {
            overlay.destroy()
        }
    }

    @Test
    fun `every registered shortcut reaches the panel with an icon and a spoken name`() {
        val registry = createHandleRegistry(context)
        val shortcuts = registry.ordered()
        assertTrue(shortcuts.all { it.iconRes != 0 })
        assertTrue(shortcuts.all { it.labelRes != 0 })
        val specs =
            panelActionSpecs(
                shortcuts.map { PanelShortcutSpec(it.id, it.iconRes, it.labelRes) },
                hideAppAvailable = true,
            )
        assertEquals(listOf("close", "hide_now") + shortcuts.map { it.id } + "hide_app", specs.map { it.key })
        assertTrue(specs.all { it.iconRes != 0 && it.labelRes != 0 })
        assertTrue(specs.all { it.viewId != android.view.View.NO_ID })
    }

    @Test
    fun `hover and touch and hold reveal the action name and never block the next tap`() {
        var calls = 0
        val registry =
            ai.eqo.accessibility.handle
                .HandleShortcutRegistry<Context>(HandlePreferences(context))
        registry.register(BuiltInHandleShortcut("fake_0", R.string.handle_ask, 0) { calls++ })
        EdgeHandleFeatures.install(registry)
        HandlePreferences(context).enabled = true
        val overlay = createOverlay()
        try {
            overlay.setProbeAvailable(true)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            overlayView(overlay)!!.performClick()
            val panel = overlayView(overlay)!!
            val params = panel.layoutParams as android.view.WindowManager.LayoutParams
            panel.measure(
                View.MeasureSpec.makeMeasureSpec(params.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(params.height, View.MeasureSpec.EXACTLY),
            )
            panel.layout(0, 0, params.width, params.height)
            val cell = descendants(panel).first { it.contentDescription == context.getString(R.string.handle_ask) }
            // A mouse or stylus hover shows the name chip; leaving hides it.
            hover(cell, android.view.MotionEvent.ACTION_HOVER_ENTER)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(context.getString(R.string.handle_ask), tooltipName(panel))
            hover(cell, android.view.MotionEvent.ACTION_HOVER_EXIT)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(null, tooltipName(panel))
            // Touch-and-hold shows the chip too; a cancelled release hides it without firing.
            val down = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, 30f, 90f, 0)
            panel.dispatchTouchEvent(down)
            down.recycle()
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
            assertEquals(context.getString(R.string.handle_ask), tooltipName(panel))
            assertEquals(0, calls)
            val cancel = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_CANCEL, 30f, 90f, 0)
            panel.dispatchTouchEvent(cancel)
            cancel.recycle()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(null, tooltipName(panel))
            // The next tap on the same cell still runs the action.
            listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP).forEach { action ->
                val tap = android.view.MotionEvent.obtain(0, 0, action, 30f, 90f, 0)
                panel.dispatchTouchEvent(tap)
                tap.recycle()
            }
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(1, calls)
            assertFalse(HandleWindowGuard.shared.panelOpen)
        } finally {
            overlay.destroy()
        }
    }

    @Test
    fun `hidden apps reuse stored choices and show again restores only the selected app`() {
        EdgeHandleFeatures.install(createHandleRegistry(context))
        val preferences = HandlePreferences(context)
        preferences.preferences.edit { putStringSet("hidden_apps", setOf("fake.alpha", "fake.beta")) }
        preferences.enabled = true
        val overlay = createOverlay()
        val lifecycle = Robolectric.buildActivity(EdgeHandleSettingsActivity::class.java).setup()
        try {
            overlay.updateForeground("fake.alpha")
            overlay.setProbeAvailable(true)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(null, overlayView(overlay))
            val buttons = descendants(lifecycle.get().window.decorView).filterIsInstance<android.widget.Button>()
            listOf("fake.alpha", "fake.beta").forEach { app ->
                assertTrue(buttons.any { it.text == context.getString(R.string.handle_unhide_app, app) })
            }
            buttons.first { it.text == context.getString(R.string.handle_unhide_app, "fake.alpha") }.performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(overlayView(overlay) != null)
            assertEquals(setOf("fake.beta"), HandlePreferences(context).hiddenApps())
            lifecycle.recreate()
            val remaining = descendants(lifecycle.get().window.decorView).filterIsInstance<android.widget.Button>()
            assertFalse(remaining.any { it.text == context.getString(R.string.handle_unhide_app, "fake.alpha") })
            assertTrue(remaining.any { it.text == context.getString(R.string.handle_unhide_app, "fake.beta") })
            assertTrue(preferences.enabled)
        } finally {
            lifecycle.pause().stop().destroy()
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

    private fun steppedDrag(
        view: android.view.View,
        x: Float,
        fromY: Float,
        toY: Float,
    ) {
        val steps = 4
        val points = (0..steps).map { fromY + (toY - fromY) * it / steps }
        points.forEachIndexed { index, y ->
            val action =
                when (index) {
                    0 -> android.view.MotionEvent.ACTION_DOWN
                    points.size - 1 -> android.view.MotionEvent.ACTION_UP
                    else -> android.view.MotionEvent.ACTION_MOVE
                }
            val event = android.view.MotionEvent.obtain(0, 0, action, x, y, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private fun hover(
        target: android.view.View,
        action: Int,
    ) {
        val event = android.view.MotionEvent.obtain(0, 0, action, 10f, 10f, 0)
        // dispatchHoverEvent is protected; UI tests reach the hover listener the way input does.
        val dispatch =
            android.view.View::class.java
                .getDeclaredMethod("dispatchHoverEvent", android.view.MotionEvent::class.java)
        dispatch.isAccessible = true
        dispatch.invoke(target, event)
        event.recycle()
    }

    private fun tooltipName(panel: android.view.View): String? =
        (panel as android.view.ViewGroup)
            .let { root -> (0 until root.childCount).map { root.getChildAt(it) } }
            .filterIsInstance<android.widget.TextView>()
            .firstOrNull { it.visibility == View.VISIBLE }
            ?.text
            ?.toString()

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
