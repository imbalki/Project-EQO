package ai.eqo.task

import ai.eqo.R
import ai.eqo.accessibility.TakeoverDetector
import ai.eqo.core.agent.UserResumeConfirmation
import android.graphics.Rect
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
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
import org.robolectric.shadows.ShadowAlertDialog

// Size the simulated display, not just its decor: ViewRootImpl clips global visible
// bounds to the window frame. A manually enlarged decor on the default small display
// leaves the lower task controls and rightmost dialog button outside that frame.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1080dp-h4000dp-mdpi")
class TaskControlPresentationTest {
    @After
    fun clearTestTouchState() {
        TakeoverDetector.shared.setControlTouchExclusion(null)
        TakeoverDetector.shared.resume(UserResumeConfirmation.forExplicitUserConfirmation(1L))
    }

    @Test
    fun `idle controls stay tappable and explain rather than silently doing nothing`() {
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
        val activity = lifecycle.get()
        val feedback = activity.findViewById<TextView>(R.id.task_control_feedback)
        listOf(R.id.task_pause_button, R.id.task_stop_button, R.id.task_takeover_button).forEach { id ->
            val button = activity.findViewById<Button>(id)
            assertTrue(button.isEnabled)
            button.performClick()
            assertEquals("Nothing is running", feedback.text.toString())
        }
        activity.findViewById<Button>(R.id.task_resume_button).performClick()
        assertEquals("Not paused", feedback.text.toString())
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        lifecycle.pause().stop().destroy()
    }

    @Test
    @Suppress("LongMethod") // Exercise positive/neutral hitboxes and background takeover in one real dialog lifecycle.
    fun `confirmation dialog registers only buttons and background touch still takes over`() {
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup().visible()
        val activity = lifecycle.get()
        val dialog =
            android.app.AlertDialog
                .Builder(activity)
                .setMessage("Approve this practice step")
                .setPositiveButton("Approve", null)
                .setNegativeButton("Reject", null)
                .setNeutralButton("Choose a different file", null)
                .show()
        val prepare =
            TaskActivity::class.java.getDeclaredMethod(
                "prepareTaskDialog",
                android.app.AlertDialog::class.java,
                Boolean::class.javaPrimitiveType,
            )
        prepare.isAccessible = true
        prepare.invoke(activity, dialog, false)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val bounds = Rect()
        val detector = TakeoverDetector.shared
        detector.onAgentActionStarted()
        try {
            val controls = listOf(android.app.AlertDialog.BUTTON_POSITIVE, android.app.AlertDialog.BUTTON_NEUTRAL)
            for (control in controls) {
                val button = dialog.getButton(control)
                assertTrue(button.getGlobalVisibleRect(bounds))
                assertTrue(detector.isControlTouch(bounds.centerX(), bounds.centerY()))
                val touch =
                    MotionEvent.obtain(
                        0L,
                        0L,
                        MotionEvent.ACTION_DOWN,
                        bounds.centerX().toFloat(),
                        bounds.centerY().toFloat(),
                        0,
                    )
                dialog.window!!.callback.dispatchTouchEvent(touch)
                touch.recycle()
            }
            assertFalse("approval must not cause takeover", detector.isPaused)
            val message = dialog.findViewById<TextView>(android.R.id.message)
            assertTrue(message.getGlobalVisibleRect(bounds))
            val background =
                MotionEvent.obtain(
                    0L,
                    1L,
                    MotionEvent.ACTION_DOWN,
                    bounds.centerX().toFloat(),
                    bounds.centerY().toFloat(),
                    0,
                )
            dialog.window!!.callback.dispatchTouchEvent(background)
            background.recycle()
            assertTrue(detector.isPaused)
        } finally {
            detector.onAgentActionFinished()
            dialog.dismiss()
            lifecycle.pause().stop().destroy()
        }
    }

    @Test
    fun `task window background touch uses shared accessibility latch but control touch does not`() {
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup().visible()
        val activity = lifecycle.get()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val detector = TakeoverDetector.shared
        detector.onAgentActionStarted()
        try {
            val button = activity.findViewById<Button>(R.id.task_pause_button)
            val bounds = Rect()
            assertTrue(button.getGlobalVisibleRect(bounds))
            val buttonTouch =
                MotionEvent.obtain(
                    0L,
                    0L,
                    MotionEvent.ACTION_DOWN,
                    bounds.centerX().toFloat(),
                    bounds.centerY().toFloat(),
                    0,
                )
            activity.dispatchTouchEvent(buttonTouch)
            buttonTouch.recycle()
            assertFalse(detector.isPaused)
            val text = activity.findViewById<TextView>(R.id.task_state)
            assertTrue(text.getGlobalVisibleRect(bounds))
            val backgroundTouch =
                MotionEvent.obtain(
                    0L,
                    1L,
                    MotionEvent.ACTION_DOWN,
                    bounds.centerX().toFloat(),
                    bounds.centerY().toFloat(),
                    0,
                )
            activity.dispatchTouchEvent(backgroundTouch)
            backgroundTouch.recycle()
            assertTrue(detector.isPaused)
        } finally {
            detector.onAgentActionFinished()
            lifecycle.pause().stop().destroy()
        }
    }
}
