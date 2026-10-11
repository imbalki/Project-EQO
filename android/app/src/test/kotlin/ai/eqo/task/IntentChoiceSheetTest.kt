// Origin: EQO intent v1; actual Android clarification controls with synthetic data and no provider.
package ai.eqo.task

import ai.eqo.core.agent.TaskIntent
import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class IntentChoiceSheetTest {
    private val intent =
        TaskIntent(
            "Open a tool",
            confidence = 0.3,
            question = "Calculator or Notes?",
            options = listOf("Calculator", "Notes"),
        )

    @Test fun choicesAreExplicitAnswersNotApprovals() =
        runTest {
            val lifecycle = Robolectric.buildActivity(Activity::class.java).setup()
            var protected = false
            val sheet = IntentChoiceSheet(lifecycle.get()) { protected = true }
            val answer = async(start = CoroutineStart.UNDISPATCHED) { sheet.ask(intent) }
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(protected)
            assertFalse(answer.isCompleted)
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals(2, dialog.listView.adapter.count)
            shadowOf(dialog).clickOnItem(1)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Notes", answer.await())
            assertNull(TaskRunSession.pending)
            assertNull(TaskRunSession.controller)
            lifecycle.pause().stop().destroy()
        }

    @Test fun freeTextRequiresAnExplicitNonblankAnswerAndIsNotSaved() =
        runTest {
            val lifecycle = Robolectric.buildActivity(Activity::class.java).setup()
            val sheet = IntentChoiceSheet(lifecycle.get()) {}
            val answer = async(start = CoroutineStart.UNDISPATCHED) { sheet.ask(intent) }
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertFalse(answer.isCompleted)
            val input = edits(dialog.window!!.decorView).single()
            assertFalse(input.isSaveEnabled)
            input.setText("Notes called synthetic test")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Notes called synthetic test", answer.await())
            lifecycle.pause().stop().destroy()
        }

    @Test fun backgroundStopAndCancelCannotAnswerOrApprove() =
        runTest {
            val lifecycle = Robolectric.buildActivity(Activity::class.java).setup()
            val sheet = IntentChoiceSheet(lifecycle.get()) {}
            val cancelled = async(start = CoroutineStart.UNDISPATCHED) { sheet.ask(intent) }
            shadowOf(Looper.getMainLooper()).idle()
            sheet.cancel()
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(cancelled.await())
            val userCancel = async(start = CoroutineStart.UNDISPATCHED) { sheet.ask(intent) }
            shadowOf(Looper.getMainLooper()).idle()
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(userCancel.await())
            assertNull(TaskRunSession.pending)
            lifecycle.pause().stop().destroy()
        }

    private fun edits(view: View): List<EditText> =
        when (view) {
            is EditText -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { edits(view.getChildAt(it)) }
            else -> emptyList()
        }
}
