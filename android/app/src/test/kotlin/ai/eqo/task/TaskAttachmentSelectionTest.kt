// Origin: EQO Files v2, foreground chooser/disclosure lifecycle tests without real sends.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.actions.impl.AttachmentChoice
import android.app.Activity
import android.app.AlertDialog
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class TaskAttachmentSelectionTest {
    private val files = listOf(AttachmentChoice("bill-a.pdf", 1_000L, 42), AttachmentChoice("bill-b.pdf", 2_000L, 43))

    @After fun cleanup() {
        Dispatchers.resetMain()
    }

    private fun activity(): Activity =
        Robolectric.buildActivity(Activity::class.java).setup().get().also {
            it.setContentView(R.layout.task_screen)
        }

    @Test fun chooserShowsNameDateSizeAndReturnsOnlyTheHumanChoice() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val ui = TaskAttachmentSelection(activity(), ::protectConfirmationDialog)
            ui.foreground()
            val result = async { ui.choose("bill", files) }
            runCurrent()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(
                dialog.listView.adapter
                    .getItem(0)
                    .toString()
                    .contains("bill-a.pdf"),
            )
            assertTrue(
                dialog.listView.adapter
                    .getItem(0)
                    .toString()
                    .contains("42 bytes"),
            )
            assertFalse(result.isCompleted)
            dialog.listView.performItemClick(android.view.View(dialog.context), 1, 1L)
            runCurrent()
            assertEquals(1, result.await())
        }

    @Test fun disclosureUpdatesRunStatusBeforeContinue() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val activity = activity()
            val ui = TaskAttachmentSelection(activity, ::protectConfirmationDialog)
            ui.foreground()
            val result = async { ui.showResolved(files.take(1)) }
            runCurrent()
            assertEquals(
                "Ready to attach: bill-a.pdf",
                activity.findViewById<TextView>(R.id.task_control_feedback).text,
            )
            assertFalse(result.isCompleted)
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            runCurrent()
            assertTrue(result.await())
        }

    @Test fun backgroundAndCancellationNeverAuthorizeASend() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val ui = TaskAttachmentSelection(activity(), ::protectConfirmationDialog)
            assertNull(ui.choose("bill", files))
            assertFalse(ui.showResolved(files))
            ui.foreground()
            val result = async { ui.choose("bill", files) }
            runCurrent()
            ui.background()
            runCurrent()
            assertNull(result.await())
            assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing)
        }

    @Test fun timeoutDismissesChooserAndReturnsNoChoice() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val ui = TaskAttachmentSelection(activity(), ::protectConfirmationDialog)
            ui.foreground()
            val result = async { ui.choose("bill", files) }
            runCurrent()
            advanceTimeBy(60_001)
            runCurrent()
            assertNull(result.await())
            assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing)
        }
}
