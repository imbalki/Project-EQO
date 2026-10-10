// Origin: EQO Files v2, foreground chooser/disclosure lifecycle tests without real sends.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.actions.impl.AttachmentChoice
import ai.eqo.actions.impl.AttachmentDecision
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.provider.DocumentsContract
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
import org.robolectric.Shadows.shadowOf
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
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()
            assertTrue(result.await())
        }

    @Test fun confirmationHasSendCancelAndDifferentWithDateAndSize() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val ui = TaskAttachmentSelection(activity(), ::protectConfirmationDialog)
            ui.foreground()
            val result = async { ui.confirm(files.take(1)) }
            runCurrent()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val message = dialog.findViewById<TextView>(android.R.id.message).text.toString()
            assertTrue(message.contains("bill-a.pdf"))
            assertTrue(message.contains("1970-01-01"))
            assertTrue(message.contains("42 bytes"))
            assertEquals("Send", dialog.getButton(AlertDialog.BUTTON_POSITIVE).text)
            assertEquals("Cancel", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text)
            assertEquals("Choose a different file", dialog.getButton(AlertDialog.BUTTON_NEUTRAL).text)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            runCurrent()
            assertEquals(AttachmentDecision.DIFFERENT, result.await())
        }

    @Test fun pickerUsesSingleDocumentAndBestGuessFolderAndCancellationReturnsNoFile() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val activity = activity()
            val picker = TaskDocumentPicker(activity)
            val result = async { picker.pick("DCIM/Screenshots") }
            runCurrent()
            val started = shadowOf(activity).nextStartedActivityForResult
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.intent.action)
            assertTrue(started.intent.categories.contains(Intent.CATEGORY_OPENABLE))
            assertFalse(started.intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, true))
            assertTrue(started.intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
            val initial = started.intent.getParcelableExtra<Uri>(DocumentsContract.EXTRA_INITIAL_URI)!!
            assertEquals("primary:DCIM/Screenshots", DocumentsContract.getDocumentId(initial))
            picker.onResult(started.requestCode, Activity.RESULT_CANCELED, null)
            assertFalse(result.isCompleted)
            picker.foreground()
            runCurrent()
            assertNull(result.await())
        }

    @Test fun pickerStopAndLateResultCannotAuthorizeAnything() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val picker = TaskDocumentPicker(activity())
            val result = async { picker.pick("Download") }
            runCurrent()
            result.cancel()
            runCurrent()
            val lateData = Intent().setData(Uri.parse("content://fake/a"))
            picker.onResult(TaskDocumentPicker.REQUEST, Activity.RESULT_OK, lateData)
            picker.foreground()
            assertTrue(result.isCancelled)
        }

    @Test fun pickerResultReadsMetadataAndReleasesTheOnePersistedGrant() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val activity = activity()
            org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(
                "synthetic.documents",
                FakeDocument(),
            )
            val picker = TaskDocumentPicker(activity)
            val result = async { picker.pick("Download") }
            runCurrent()
            val uri = Uri.parse("content://synthetic.documents/document/one")
            val data =
                Intent().setData(uri).addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                )
            picker.onResult(TaskDocumentPicker.REQUEST, Activity.RESULT_OK, data)
            assertFalse(result.isCompleted)
            picker.foreground()
            runCurrent()
            val chosen = requireNotNull(result.await())
            assertEquals(AttachmentChoice("synthetic.pdf", 1_000, 42), chosen.choice)
            assertEquals("application/pdf", chosen.mime)
            assertTrue(activity.contentResolver.persistedUriPermissions.any { it.uri == uri })
            chosen.close()
            assertFalse(activity.contentResolver.persistedUriPermissions.any { it.uri == uri })
        }

    @Test fun recoveryReleasesReadOnlyDocumentGrantsButPreservesWorkspaceTrees() {
        val activity = activity()
        val resolver = activity.contentResolver
        val document = Uri.parse("content://synthetic.documents/document/stale")
        val tree = Uri.parse("content://synthetic.documents/tree/workspace")
        resolver.takePersistableUriPermission(document, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        TaskDocumentPicker.recoverReadGrants(activity)
        assertFalse(resolver.persistedUriPermissions.any { it.uri == document })
        assertTrue(resolver.persistedUriPermissions.any { it.uri == tree })
        resolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private class FakeDocument : android.content.ContentProvider() {
        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): android.database.MatrixCursor =
            android.database.MatrixCursor(projection!!).apply {
                addRow(arrayOf<Any>("synthetic.pdf", 42L, 1_000L))
            }

        override fun getType(uri: Uri): String = "application/pdf"

        override fun insert(
            uri: Uri,
            values: android.content.ContentValues?,
        ): Uri? = null

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        override fun update(
            uri: Uri,
            values: android.content.ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
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
