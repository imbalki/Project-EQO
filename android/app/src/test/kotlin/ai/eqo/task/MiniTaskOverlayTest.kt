package ai.eqo.task

import ai.eqo.R
import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.handle.HandleEntry
import ai.eqo.accessibility.handle.HandlePreferences
import ai.eqo.accessibility.handle.HandleWindowGuard
import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.data.models.PlanStatus
import ai.eqo.handle.HandleReturn
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class MiniTaskOverlayTest {
    private val dispatcher = StandardTestDispatcher()
    private val host = Robolectric.buildService(EQOAccessibilityService::class.java).create()
    private val service get() = host.get()
    private val plan = ApprovedTaskPlan(listOf(LoopStep("wait", ExecutedAction("WAIT"))))
    private var overlay: MiniTaskOverlay? = null
    private var started = 0

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        service.javaClass.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(service)
        TaskRunSession.pending = null
        TaskRunSession.controller = null
        TaskRunSession.receipt = null
        TaskRunSession.progress.clear()
    }

    @After fun clean() {
        overlay?.close()
        TaskRunSession.pending = null
        TaskRunSession.controller = null
        TaskRunSession.receipt = null
        HandleWindowGuard.shared.compactBounds = null
        host.destroy()
        Dispatchers.resetMain()
    }

    @Test fun `in place panel previews and requires protected explicit approval`() = runTest {
        val panel = open()
        val params = field(panel, "params") as WindowManager.LayoutParams
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, params.type)
        assertEquals((service.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.height() * 0.3f)
            .toInt(), params.height)
        draft(panel).setText("synthetic request")
        button(panel, R.string.mini_plan).performClick()
        runCurrent()
        assertEquals(0, started)
        val approve = button(panel, R.string.task_approval_approve)
        assertTrue(approve.isEnabled)
        assertTrue(approve.filterTouchesWhenObscured)
        approve.performClick()
        runCurrent()
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        advanceTimeBy(400)
        runCurrent()
        assertEquals(1, started)
        panel.close()
        assertNull(HandleWindowGuard.shared.compactBounds)
    }

    @Test fun `edit cancel and close invalidate late plans and approvals`() = runTest {
        val waiting = CompletableDeferred<ApprovedTaskPlan>()
        val panel = open { waiting.await() }
        draft(panel).setText("first")
        button(panel, R.string.mini_plan).performClick()
        runCurrent()
        draft(panel).setText("edited")
        waiting.complete(plan)
        runCurrent()
        assertFalse(button(panel, R.string.task_approval_approve).isEnabled)
        assertEquals(0, started)
        panel.close()
        assertEquals(0, started)
    }

    @Test fun `Stop before delayed service start cancels approval`() = runTest {
        val panel = open()
        draft(panel).setText("request")
        button(panel, R.string.mini_plan).performClick()
        runCurrent()
        button(panel, R.string.task_approval_approve).performClick()
        runCurrent()
        button(panel, R.string.task_stop).performClick()
        advanceTimeBy(400)
        runCurrent()
        assertEquals(0, started)
    }

    @Test fun `collapsed panel retains live status and drag changes window position`() = runTest {
        val panel = open()
        TaskRunSession.status = PlanStatus.PAUSED
        val drag = button(panel, R.string.mini_drag)
        drag.performClick()
        val params = field(panel, "params") as WindowManager.LayoutParams
        assertEquals((service.resources.displayMetrics.density * 96).toInt(), params.height)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 200f, 0)
        val move = MotionEvent.obtain(0, 10, MotionEvent.ACTION_MOVE, 10f, 100f, 0)
        drag.dispatchTouchEvent(down)
        drag.dispatchTouchEvent(move)
        assertTrue(params.y > 0)
        down.recycle()
        move.recycle()
        assertTrue(button(panel, R.string.handle_open).isShown)
    }

    @Test fun `missing access cannot approve and Full EQO has previous app navigation`() = runTest {
        val panel = open { throw MiniAccessNeeded() }
        draft(panel).setText("request")
        button(panel, R.string.mini_plan).performClick()
        runCurrent()
        assertFalse(button(panel, R.string.task_approval_approve).isEnabled)
        button(panel, R.string.handle_open).performClick()
        val intent = shadowOf(service).nextStartedActivity
        assertEquals(ai.eqo.MainActivity::class.java.name, intent.component!!.className)
        assertEquals("test.previous", intent.getStringExtra("ai.eqo.handle.PREVIOUS_APP"))
    }

    @Test fun `handle reattaches immediately after a panel closes without app switch`() {
        val prefs = HandlePreferences(service)
        prefs.enabled = true
        shadowOf(Looper.getMainLooper()).idle()
        val edge = field(service, "edgeHandle")!!
        val before = field(edge, "view") as View
        service.redrawHandle()
        val after = field(edge, "view") as View
        assertNotSame(before, after)
        assertTrue(after.isAttachedToWindow)
        assertTrue(org.robolectric.shadows.ShadowLog.getLogsForTag("EqoExplain").any { it.msg == "handle: redrawn" })
        prefs.enabled = false
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ZERO)
    }

    @Test fun `Back navigation uses captured package and own package has no return`() {
        val intent = HandleReturn.intent(service, ai.eqo.MainActivity::class.java, HandleEntry("test.previous"))
        val activity = Robolectric.buildActivity(ai.eqo.MainActivity::class.java, intent).setup()
        try {
            val back = descendants(activity.get().window.decorView).filterIsInstance<Button>()
                .first { it.text.toString().startsWith("Back to ") }
            assertTrue(back.performClick())
        } finally {
            activity.pause().stop().destroy()
        }
        assertNull(HandleReturn.intent(service, ai.eqo.MainActivity::class.java, HandleEntry(service.packageName))
            .getStringExtra("ai.eqo.handle.PREVIOUS_APP"))
    }

    @Test fun `Pause and Stop reach a real controller while progress stays visible`() = runTest {
        val panel = open()
        val detector = ai.eqo.accessibility.TakeoverDetector()
        val executor = StudyActionExecutor(
            EqoAutomationPort({ null }, { _, _ -> false }),
            registryExecute = { _, _ ->
                kotlinx.coroutines.delay(500)
                ai.eqo.actions.base.ActionResult.Success()
            },
        )
        val controller = StudyTaskController(
            steps = plan.steps(),
            permissionCheck = StudyPermissionCheck({ true }, { true }, { true }),
            approvalGate = StudyApprovalGate(StudyApprovalSurface { ai.eqo.study.ApprovalOutcome.Approved }, { 0L }),
            executor = executor,
            observe = { "" },
            takeoverDetector = detector,
        )
        TaskRunSession.controller = controller
        val run = async { controller.run() }
        runCurrent()
        button(panel, R.string.task_pause).performClick()
        assertTrue(controller.isActionInFlight())
        button(panel, R.string.task_stop).performClick()
        advanceTimeBy(600)
        runCurrent()
        assertEquals("STOPPED", run.await().terminal)
        assertFalse(detector.isPaused)
        assertTrue((field(panel, "root") as View).isAttachedToWindow)
    }

    @Test fun `transient microphone activity requests only access and finishes without starting work`() {
        val lifecycle = Robolectric.buildActivity(MiniVoicePermissionActivity::class.java).create()
        val request = shadowOf(lifecycle.get()).lastRequestedPermission
        assertEquals(listOf(android.Manifest.permission.RECORD_AUDIO), request.requestedPermissions.toList())
        lifecycle.get().onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(-1))
        assertTrue(lifecycle.get().isFinishing)
        assertNull(TaskRunSession.pending)
        lifecycle.destroy()
    }

    private fun open(prepare: suspend (String) -> ApprovedTaskPlan = { plan }): MiniTaskOverlay =
        MiniTaskOverlay(service, HandleEntry("test.previous"), prepare) { started++ }
            .also { overlay = it; it.open() }

    private fun draft(panel: MiniTaskOverlay): EditText = (field(panel, "root") as View).findViewById(R.id.task_request)

    private fun button(panel: MiniTaskOverlay, resource: Int): Button =
        descendants(field(panel, "root") as View).filterIsInstance<Button>().first {
            it.text == service.getString(resource)
        }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
}
