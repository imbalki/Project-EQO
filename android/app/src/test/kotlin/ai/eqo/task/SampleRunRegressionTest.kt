package ai.eqo.task

import ai.eqo.R
import ai.eqo.accessibility.TakeoverDetector
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopState
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.UserResumeConfirmation
import ai.eqo.study.StepProgressState
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Rect
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1080dp-h4000dp-mdpi")
class SampleRunRegressionTest {
    private val detector = TakeoverDetector.shared

    @After
    fun clearLatch() {
        detector.setControlTouchExclusion(null)
        detector.resume(UserResumeConfirmation.forExplicitUserConfirmation(1L))
    }

    @Test
    fun `new Run gesture clears takeover left by an earlier background tap`() {
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup().visible()
        val activity = lifecycle.get()
        shadowOf(Looper.getMainLooper()).idle()
        detector.onAgentActionStarted()
        touch(activity, activity.findViewById(R.id.task_state))
        detector.onAgentActionFinished()
        assertTrue(detector.isPaused)
        try {
            val start = activity.findViewById<Button>(R.id.task_start_button)
            touch(activity, start)
            assertTrue("Run DOWN must not clear the latch before confirmation", detector.isPaused)
            touch(activity, start, action = MotionEvent.ACTION_UP)
            // Android posts the click from ACTION_UP to the main looper.
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse("a new explicit Run gesture must clear the previous run's latch", detector.isPaused)
        } finally {
            val field = TaskActivity::class.java.getDeclaredField("controller").apply { isAccessible = true }
            (field.get(activity) as? StudyTaskController)?.stop()
            lifecycle.pause().stop().destroy()
        }
    }

    @Test
    fun `partially obscured Run gesture clears a stale takeover without bypassing typed planning`() {
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup().visible()
        val activity = lifecycle.get()
        shadowOf(Looper.getMainLooper()).idle()
        detector.onAgentActionStarted()
        touch(activity, activity.findViewById(R.id.task_state))
        detector.onAgentActionFinished()
        val start = activity.findViewById<Button>(R.id.task_start_button)
        try {
            assertTrue("typed Plan retains touch-point confirmation filtering", start.filterTouchesWhenObscured)
            touch(activity, start, flags = MotionEvent.FLAG_WINDOW_IS_OBSCURED)
            touch(activity, start, action = MotionEvent.ACTION_UP)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("an obscured Plan touch must not clear takeover", detector.isPaused)
            val flags = MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
            touch(activity, start, flags = flags)
            assertTrue("DOWN alone must not confirm", detector.isPaused)
            touch(activity, start, action = MotionEvent.ACTION_UP, flags = flags)
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse("an OEM overlay elsewhere must not block Run", detector.isPaused)
            val field = TaskActivity::class.java.getDeclaredField("controller").apply { isAccessible = true }
            assertEquals(null, field.get(activity))
            assertEquals(
                activity.getString(R.string.task_request_empty),
                activity.findViewById<android.widget.TextView>(R.id.task_state).text.toString(),
            )
        } finally {
            val field = TaskActivity::class.java.getDeclaredField("controller").apply { isAccessible = true }
            (field.get(activity) as? StudyTaskController)?.stop()
            lifecycle.pause().stop().destroy()
        }
    }

    @Test
    fun `all task control hitboxes including Run exclude touch without clearing takeover`() {
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup().visible()
        val activity = lifecycle.get()
        shadowOf(Looper.getMainLooper()).idle()
        activity.onWindowFocusChanged(true)
        detector.onAgentActionStarted()
        try {
            listOf(
                R.id.task_start_button,
                R.id.task_pause_button,
                R.id.task_stop_button,
                R.id.task_takeover_button,
                R.id.task_resume_button,
                R.id.task_setup_button,
            ).forEach { id ->
                val view = activity.findViewById<View>(id)
                val bounds = Rect()
                assertTrue(view.getGlobalVisibleRect(bounds))
                assertTrue(detector.isControlTouch(bounds.centerX(), bounds.centerY()))
                touch(activity, view)
                assertFalse("control DOWN must not latch: $id", detector.isPaused)
            }
            touch(activity, activity.findViewById(R.id.task_state))
            assertTrue("background DOWN still latches during a run", detector.isPaused)
            touch(activity, activity.findViewById(R.id.task_start_button))
            assertTrue("DOWN alone is not a new-run confirmation", detector.isPaused)
        } finally {
            detector.onAgentActionFinished()
            lifecycle.pause().stop().destroy()
        }
    }

    @Test
    fun `three step regression plan executes in order after real SMS approval and completes`() =
        runTest {
            val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup().visible()
            val activity = lifecycle.get()
            shadowOf(Looper.getMainLooper()).idle()
            val steps = regressionPlan()
            val surfaceMethod =
                TaskActivity::class.java.getDeclaredMethod("approvalSurface").apply { isAccessible = true }
            val surface = surfaceMethod.invoke(activity) as StudyApprovalSurface
            val executed = mutableListOf<String>()
            val intents = mutableListOf<Intent>()
            val opener = SmsDraftOpener(defaultSmsPackage = { "com.example.defaultsms" }) { intents += it }
            val port =
                object : StudyAutomationPort {
                    override fun observe(): String {
                        executed += "observe"
                        return "screen text captured"
                    }

                    override fun scroll(direction: String): Boolean {
                        assertEquals("down", direction)
                        executed += "scroll"
                        return true
                    }

                    override fun composeSmsDraft(
                        recipient: String,
                        body: String,
                    ): Boolean {
                        executed += "compose_sms"
                        return opener.open(recipient, body)
                    }

                    override fun tap(text: String) = error("unexpected tap")

                    override fun tapById(viewId: String) = error("unexpected tapById")

                    override fun typeText(text: String) = error("unexpected typeText")

                    override fun back() = error("unexpected back")

                    override fun home() = error("unexpected home")
                }
            val controller =
                StudyTaskController(
                    steps = steps,
                    permissionCheck = StudyPermissionCheck({ true }, { true }, { true }),
                    approvalGate = StudyApprovalGate(surface, { System.currentTimeMillis() }),
                    executor = StudyActionExecutor(port),
                    observe = { "screen text captured; messaging composer opened with the draft" },
                    config = SamplePractice.config,
                )
            val run = async { controller.run() }
            try {
                runCurrent()
                assertEquals(listOf("observe"), executed)
                // A real task-window background touch still pauses the live run.
                touch(activity, activity.findViewById(R.id.task_state))
                advanceTimeBy(100)
                runCurrent()
                assertEquals(LoopState.PAUSED, controller.currentState())
                assertTrue(detector.isPaused)
                assertTrue(controller.resume(UserResumeConfirmation.forExplicitUserConfirmation(2L)))
                advanceUntilIdle()
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(listOf("observe", "scroll"), executed)
                assertTrue(intents.isEmpty())
                approveSmsWithObscurationChecks(activity, intents)
                advanceUntilIdle()
                val receipt = run.await()
                assertEquals(listOf("observe", "scroll", "compose_sms"), executed)
                assertEquals("COMPLETED", receipt.terminal)
                assertEquals(steps.map { it.stepId }, receipt.steps.map { it.stepId })
                assertTrue(receipt.steps.dropLast(1).all { it.state == StepProgressState.DONE })
                assertEquals(StepProgressState.NEEDS_YOU, receipt.steps.last().state)
                assertEquals("NEEDS_YOU", RunStatusMapping.terminal(receipt))
                assertEquals(1, intents.size)
                assertEquals(Intent.ACTION_SENDTO, intents.single().action)
                assertEquals("smsto:", intents.single().dataString)
                assertEquals("com.example.defaultsms", intents.single().`package`)
                assertEquals("EQO regression draft", intents.single().getStringExtra("sms_body"))
                assertFalse(detector.isAgentActionInFlight())
            } finally {
                controller.stop()
                run.cancel()
                ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
                lifecycle.pause().stop().destroy()
            }
        }

    /** #67 removed the automatic sample; keep its regression plan as a test fixture. */
    private fun regressionPlan(): List<LoopStep> =
        listOf(
            LoopStep(
                "1-observe",
                ExecutedAction("observe", expectedPostconditions = listOf("screen text captured")),
            ),
            LoopStep("2-scroll", ExecutedAction("scroll", params = mapOf("direction" to "down"))),
            LoopStep(
                "3-compose-draft",
                ExecutedAction(
                    "compose_sms",
                    params = mapOf("to" to "", "body" to "EQO regression draft"),
                    expectedPostconditions = listOf("messaging composer opened with the draft"),
                ),
            ),
        )

    private fun kotlinx.coroutines.test.TestScope.approveSmsWithObscurationChecks(
        activity: TaskActivity,
        intents: List<Intent>,
    ) {
        val dialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
        assertTrue("SMS must show its actual approval dialog", dialog.isShowing)
        val approve = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(activity.getString(R.string.task_approval_approve), approve.text.toString())
        assertTrue("approval retains framework touch-point filtering", approve.filterTouchesWhenObscured)
        touch(activity, approve, dialog, flags = MotionEvent.FLAG_WINDOW_IS_OBSCURED)
        touch(activity, approve, dialog, action = MotionEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        assertTrue("touch-point obscuration must not approve", dialog.isShowing)
        assertTrue(intents.isEmpty())
        val partial = MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
        touch(activity, approve, dialog, flags = partial)
        assertFalse("Approve touch must not take over", detector.isPaused)
        touch(activity, approve, dialog, action = MotionEvent.ACTION_UP, flags = partial)
        // Drain the posted button click before awaiting the approval continuation.
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
    }

    private fun touch(
        activity: TaskActivity,
        view: View,
        dialog: AlertDialog? = null,
        action: Int = MotionEvent.ACTION_DOWN,
        flags: Int = 0,
    ) {
        val bounds = Rect()
        assertTrue(view.getGlobalVisibleRect(bounds))
        val properties = MotionEvent.PointerProperties().apply { id = 0 }
        val coordinates =
            MotionEvent.PointerCoords().apply {
                x = bounds.centerX().toFloat()
                y = bounds.centerY().toFloat()
            }
        val event =
            MotionEvent.obtain(
                0L,
                10L,
                action,
                1,
                arrayOf(properties),
                arrayOf(coordinates),
                0,
                0,
                1f,
                1f,
                0,
                0,
                android.view.InputDevice.SOURCE_TOUCHSCREEN,
                flags,
            )
        if (dialog == null) activity.dispatchTouchEvent(event) else dialog.window!!.callback.dispatchTouchEvent(event)
        event.recycle()
    }
}
