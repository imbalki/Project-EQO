// Origin: EQO TASK-069 (#20), just-in-time Android permission UI regressions.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.actions.impl.ActionPermission
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
import java.util.concurrent.atomic.AtomicReference

/**
 * Real Android dialogs, fake grants. Dispatchers.Main is set to Unconfined and each
 * request is started with an unconfined launch: it runs on the Robolectric main
 * thread exactly like production, suspends at the dialog or the Android permission
 * request, and resumes INLINE the moment the test settles it. There is no worker
 * thread and no test scheduler that could leave a callback un-advanced — if a resume
 * is ever broken the bounded [awaitState] fails fast instead of hanging the suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class TaskPermissionRequesterTest {
    @Test
    @Config(sdk = [33])
    fun preciseLocationRequestsCoarseAndFineTogetherBeforeRun(): Unit =
        runBlocking {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val requester = TaskPermissionRequester(activity)
            val answer =
                CoroutineScope(Dispatchers.Unconfined).async {
                    requester.request(
                        ActionPermission.Runtime(Manifest.permission.ACCESS_FINE_LOCATION, "Allow location"),
                    )
                }
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertArrayEquals(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                shadowOf(activity).lastRequestedPermission.requestedPermissions,
            )
            requester.cancelWaiting()
            assertEquals(false, answer.await())
        }

    @Test fun locationPlanRequestsAccessBeforeApprovalAndStopRejectsLateAllow(): Unit =
        runBlocking {
            val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
            val activity = lifecycle.get()
            val step =
                ai.eqo.core.agent.LoopStep(
                    stepId = "location",
                    action =
                        ai.eqo.core.agent.ExecutedAction(
                            "SHARE_LOCATION",
                            mapOf("to" to "+15550199", "via" to "sms"),
                        ),
                )
            val preparation = CoroutineScope(Dispatchers.Unconfined).async { activity.prepareAndShowPlan(listOf(step)) }
            val preview = activity.findViewById<TextView>(R.id.task_preview).text.toString()
            assertTrue(preview.contains("Allow location"))
            assertNull(TaskRunSession.pending)
            assertNull(TaskRunSession.controller)
            val instructions = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals("Allow now", instructions.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
            instructions.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            val request = shadowOf(activity).lastRequestedPermission
            assertArrayEquals(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), request.requestedPermissions)
            activity.findViewById<android.widget.Button>(R.id.task_stop_button).performClick()
            preparation.await()
            shadowOf(activity.application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
            activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(0))
            assertNull(TaskRunSession.pending)
            assertNull(TaskRunSession.controller)
            assertTrue(
                activity
                    .findViewById<TextView>(R.id.task_state)
                    .text
                    .toString()
                    .contains("No run started"),
            )
            activity.prepareAndShowPlan(listOf(step))
            val approval = ShadowAlertDialog.getLatestAlertDialog()
            org.junit.Assert.assertNotSame(instructions, approval)
            assertTrue(approval.isShowing)
            assertTrue(
                activity
                    .findViewById<TextView>(R.id.task_preview)
                    .text
                    .toString()
                    .contains("Allow location"),
            )
            assertEquals(request.requestCode, shadowOf(activity).lastRequestedPermission.requestCode)
            assertNull(TaskRunSession.pending)
            assertNull(TaskRunSession.controller)
            lifecycle.pause().stop().destroy()
        }

    @Test fun preflightRequiresAllowNowAndActiveRunNeverLaunchesPermissionUi() =
        runBlocking {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            var running = false
            val requester = TaskPermissionRequester(activity, canRequest = { !running })
            val permission = ActionPermission.Runtime(Manifest.permission.ACCESS_FINE_LOCATION, "Allow location.")
            val result = CoroutineScope(Dispatchers.Unconfined).async { requester.request(permission) }
            assertNull(shadowOf(activity).lastRequestedPermission)
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals("Allow now", dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            val code = shadowOf(activity).lastRequestedPermission.requestCode
            shadowOf(activity.application).grantPermissions(permission.name)
            requester.onRequestPermissionsResult(code)
            assertTrue(result.await())
            running = true
            assertTrue(requester.request(permission))
            shadowOf(activity.application).denyPermissions(permission.name)
            org.junit.Assert.assertFalse(requester.request(permission))
            assertEquals(code, shadowOf(activity).lastRequestedPermission.requestCode)
            org.junit.Assert.assertFalse(requester.isWaiting())
        }

    @Test fun staleSettingsInstructionsCannotLaunchOrCancelTheNextRequest() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val requester = TaskPermissionRequester(activity)
        val first =
            startRequest(
                requester,
                ActionPermission.SpecialAccess(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    true,
                    "Turn on All files access.",
                    { false },
                ),
            )
        val oldDialog = ShadowAlertDialog.getLatestAlertDialog()
        requester.cancelWaiting()
        awaitSettled(first, "cancelled Settings instructions")
        val second =
            startRequest(
                requester,
                ActionPermission.Runtime(Manifest.permission.READ_CONTACTS, "Find a recipient."),
            )
        assertEquals("android.content.pm.action.REQUEST_PERMISSIONS", shadowOf(activity).nextStartedActivity.action)
        oldDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        oldDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertNull(shadowOf(activity).nextStartedActivity)
        assertNull(second.outcome.get())
        assertTrue(requester.isWaiting())
        requester.cancelWaiting()
        awaitSettled(second, "cancelled second request")
    }

    @Test fun stopSettlesPermissionWaitAndOldCallbackCannotSettleTheNextRequest() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val requester = TaskPermissionRequester(activity)
        val permission = ActionPermission.Runtime(Manifest.permission.READ_CONTACTS, "Find a recipient.")
        val first = startRequest(requester, permission)
        val oldCode = shadowOf(activity).lastRequestedPermission.requestCode
        assertTrue(requester.isWaiting())
        requester.cancelWaiting()
        awaitSettled(first, "Stop cancelling permission")
        assertEquals(false, first.outcome.get())
        val second = startRequest(requester, permission)
        val newCode = shadowOf(activity).lastRequestedPermission.requestCode
        org.junit.Assert.assertFalse(requester.onRequestPermissionsResult(oldCode))
        assertNull(second.outcome.get())
        shadowOf(activity.application).grantPermissions(Manifest.permission.READ_CONTACTS)
        assertTrue(requester.onRequestPermissionsResult(newCode))
        awaitSettled(second, "new permission callback")
        assertEquals(true, second.outcome.get())
    }

    @Test fun requestWaitsUpTo120SecondsInVirtualTime() =
        kotlinx.coroutines.test.runTest {
            Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val requester = TaskPermissionRequester(activity)
            val result =
                async {
                    requester.request(
                        ActionPermission.Runtime(Manifest.permission.ACCESS_FINE_LOCATION, "Share location."),
                    )
                }
            runCurrent()
            advanceTimeBy(119_999)
            assertTrue(requester.isWaiting())
            org.junit.Assert.assertFalse(result.isCompleted)
            advanceUntilIdle()
            assertEquals(false, result.await())
            org.junit.Assert.assertFalse(requester.isWaiting())
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class InFlight(
        val job: Job,
        val outcome: AtomicReference<Boolean?>,
    )

    private fun startRequest(
        requester: TaskPermissionRequester,
        permission: ActionPermission,
    ): InFlight {
        val outcome = AtomicReference<Boolean?>(null)
        val job = CoroutineScope(Dispatchers.Unconfined).launch { outcome.set(requester.request(permission)) }
        if (permission is ActionPermission.Runtime && requester.isWaiting()) {
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        return InFlight(job, outcome)
    }

    private fun awaitState(
        description: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + REQUEST_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(POLL_STEP_MS)
        }
        throw AssertionError("timed out waiting for: $description")
    }

    private fun awaitSettled(
        inFlight: InFlight,
        description: String,
    ) {
        awaitState(description) { inFlight.job.isCompleted }
    }

    @Test
    fun `runtime request shows Android dialog only when needed and continues after grant`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val requester = TaskPermissionRequester(activity)
        assertNull(shadowOf(activity).lastRequestedPermission)
        val permission = ActionPermission.Runtime(Manifest.permission.READ_CONTACTS, "Find a recipient.")

        val inFlight = startRequest(requester, permission)

        val request = shadowOf(activity).lastRequestedPermission
        assertArrayEquals(arrayOf(Manifest.permission.READ_CONTACTS), request.requestedPermissions)
        assertNull("request must wait for the user", inFlight.outcome.get())
        shadowOf(activity.application).grantPermissions(Manifest.permission.READ_CONTACTS)
        assertTrue(requester.onRequestPermissionsResult(request.requestCode))
        awaitSettled(inFlight, "the granted request")
        assertEquals(true, inFlight.outcome.get())

        assertTrue(runBlocking { requester.request(permission) })
    }

    @Test
    fun `decline and activity destruction fail without silently proceeding`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val requester = TaskPermissionRequester(activity)
        val permission = ActionPermission.Runtime(Manifest.permission.CALL_PHONE, "Place this call.")

        val denied = startRequest(requester, permission)
        val first = shadowOf(activity).lastRequestedPermission
        requester.onRequestPermissionsResult(TaskPermissionRequester.REQUEST_CODE)
        awaitSettled(denied, "the declined request")
        assertEquals(false, denied.outcome.get())

        val destroyed = startRequest(requester, permission)
        awaitState("the second Android permission request") {
            shadowOf(activity).lastRequestedPermission != null && shadowOf(activity).lastRequestedPermission !== first
        }
        requester.close()
        awaitSettled(destroyed, "the request cancelled by task destruction")
        assertEquals(false, destroyed.outcome.get())
    }

    @Test
    fun `special access opens exact package settings after instructions and rechecks on return`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val requester = TaskPermissionRequester(activity)
        var allowed = false
        val permission =
            ActionPermission.SpecialAccess(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                true,
                "Allow EQO to display over other apps on the next screen, then return.",
                { allowed },
            )

        val inFlight = startRequest(requester, permission)

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue("Continue must be visible to the owner", dialog.getButton(AlertDialog.BUTTON_POSITIVE).isShown)
        assertTrue("Cancel must be visible to the owner", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isShown)
        assertNull("nothing opens before the owner continues", shadowOf(activity).nextStartedActivity)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intent.action)
        assertEquals("package:${activity.packageName}", intent.dataString)

        requester.onPause()
        allowed = true
        requester.onResume()
        awaitSettled(inFlight, "the request resumed after settings")
        assertEquals(true, inFlight.outcome.get())
    }

    @Test
    fun `task lifecycle settles an in-flight runtime request on destruction`() {
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
        val activity = lifecycle.get()
        val field = TaskActivity::class.java.getDeclaredField("actionPermissions\$delegate")
        field.isAccessible = true
        val requester = (field.get(activity) as Lazy<*>).value as TaskPermissionRequester
        assertNull("opening the task screen requests no grant", shadowOf(activity).lastRequestedPermission)
        val inFlight =
            startRequest(requester, ActionPermission.Runtime(Manifest.permission.CALL_PHONE, "Place this call."))

        val waitingText = activity.findViewById<TextView>(R.id.task_state).text.toString()
        assertTrue(waitingText.contains("Tap Allow"))
        assertTrue(waitingText.contains("Phone"))

        lifecycle.pause().stop().destroy()

        awaitSettled(inFlight, "the request cancelled by the actual task lifecycle")
        assertEquals(false, inFlight.outcome.get())
    }

    @Test
    fun `task lifecycle rechecks special access when returning from settings`() {
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
        val activity = lifecycle.get()
        val field = TaskActivity::class.java.getDeclaredField("actionPermissions\$delegate")
        field.isAccessible = true
        val requester = (field.get(activity) as Lazy<*>).value as TaskPermissionRequester
        var allowed = false
        val inFlight =
            startRequest(
                requester,
                ActionPermission.SpecialAccess(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    true,
                    "Allow overlay access.",
                    { allowed },
                ),
            )
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, shadowOf(activity).nextStartedActivity.action)

        lifecycle.pause().stop()
        allowed = true
        lifecycle.start().resume()

        awaitSettled(inFlight, "the request rechecked by the actual task lifecycle")
        assertEquals(true, inFlight.outcome.get())
        lifecycle.pause().stop().destroy()
    }

    companion object {
        private const val POLL_STEP_MS = 25L
        private const val REQUEST_TIMEOUT_MS = 5_000L
    }
}
