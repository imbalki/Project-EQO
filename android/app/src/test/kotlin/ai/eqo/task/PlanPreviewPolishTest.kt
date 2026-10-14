package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import android.app.AlertDialog
import android.os.Looper
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@OptIn(ExperimentalCoroutinesApi::class)
class PlanPreviewPolishTest {
    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        TaskRunSession.pending = null
        TaskRunSession.controller = null
    }

    @After fun tearDown() {
        TaskRunSession.pending = null
        TaskRunSession.controller = null
        Dispatchers.resetMain()
    }

    @Test fun plannerQuestionPrecedesPreviewResolutionPermissionsAndApproval(): Unit =
        runBlocking {
            val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
            val activity = lifecycle.get()
            val steps =
                listOf(
                    LoopStep("open", ExecutedAction("OPEN_APP", mapOf("appName" to "Uninstalled notes"))),
                    LoopStep("call", ExecutedAction("MAKE_CALL", mapOf("contact" to "Synthetic person"))),
                    LoopStep("ask", ExecutedAction("ASK_USER", mapOf("question" to "What should the note say?"))),
                )
            val pending = CoroutineScope(Dispatchers.Unconfined).async { activity.prepareAndShowPlan(steps) }
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals("What should the note say?", shadowOf(dialog).title.toString())
            assertEquals("Use this answer", dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
            assertTrue(activity.findViewById<TextView>(R.id.task_preview).text.isEmpty())
            assertNull(shadowOf(activity).lastRequestedPermission)
            assertNull(TaskRunSession.pending)
            assertNull(TaskRunSession.controller)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("Cancellation must settle before awaiting it", pending.isCompleted)
            pending.await()
            assertNull(TaskRunSession.pending)
            lifecycle.pause().stop().destroy()
        }

    @Test fun permissionPreviewContainsOnlyPermissionsForTheActualSteps() {
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
        val activity = lifecycle.get()
        val preview = TaskActivity::class.java.getDeclaredMethod("permissionPreview", List::class.java)
        preview.isAccessible = true
        val note = listOf(LoopStep("note", ExecutedAction("CREATE_NOTE", mapOf("body" to "Synthetic note"))))
        val question = listOf(LoopStep("ask", ExecutedAction("ASK_USER", mapOf("question" to "Which note?"))))
        assertEquals("", preview.invoke(activity, note))
        assertEquals("", preview.invoke(activity, question))
        val location = listOf(LoopStep("place", ExecutedAction("SHARE_LOCATION", mapOf("to" to "Sam", "via" to "sms"))))
        val access = preview.invoke(activity, location) as String
        assertTrue(access.contains("location", ignoreCase = true))
        assertFalse(access.contains("phone setting", ignoreCase = true))
        assertFalse(access.contains("camera", ignoreCase = true))
        lifecycle.pause().stop().destroy()
    }

    @Test fun understoodLineUsesFinalPlanNotAnUnfulfilledPaymentGoal() {
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
        val activity = lifecycle.get()
        TaskActivity::class.java.getDeclaredField("purchasePreview").apply {
            isAccessible = true
            setBoolean(activity, true)
        }
        val understood = TaskActivity::class.java.getDeclaredMethod("understoodFor", List::class.java)
        understood.isAccessible = true
        val steps = listOf(LoopStep("open", ExecutedAction("OPEN_APP", mapOf("appName" to "Zepto"))))
        val line = understood.invoke(activity, steps) as String
        assertTrue(line.contains("EQO will not pay; it will open \"Zepto\" only"))
        assertFalse(line.contains("pay with UPI"))
        lifecycle.pause().stop().destroy()
    }
}
