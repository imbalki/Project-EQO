// Origin: EQO-authored regressions: a voice transcript plans like a Plan tap and never bypasses a guard.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.accessibility.TakeoverDetector
import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.ActionLoop
import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopState
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.UserResumeConfirmation
import ai.eqo.data.models.PlanStatus
import android.Manifest
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowSpeechRecognizer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceAutoPlanActivityTest {
    private val detector = TakeoverDetector.shared
    private val lifecycle = Robolectric.buildActivity(TaskActivity::class.java)

    @After
    fun cleanUp() {
        TaskRunSession.controller = null
        TaskRunSession.pending = null
        TaskRunSession.readAloud = false
        TaskRunSession.status = PlanStatus.PENDING
        TaskRunSession.receipt = null
        TaskRunSession.retry = TaskPlanRetry()
        TaskRunSession.requestDraft = ""
        TaskRunSession.progress.clear()
        PlanApprovalSettings.setRequired(lifecycle.get(), true)
        detector.onAgentActionFinished()
        detector.resume(UserResumeConfirmation.forExplicitUserConfirmation(1L))
        lifecycle.pause().stop().destroy()
    }

    private fun screen(): TaskActivity = lifecycle.setup().get()

    private fun TaskActivity.stateLine() = findViewById<TextView>(R.id.task_state).text.toString()

    private fun TaskActivity.typeRequest(text: String) = findViewById<EditText>(R.id.task_request).setText(text)

    private fun TaskActivity.showTestPlan(plan: ApprovedTaskPlan) {
        val show =
            TaskActivity::class.java.getDeclaredMethod(
                "showPlan",
                ApprovedTaskPlan::class.java,
                Map::class.java,
                List::class.java,
                Boolean::class.javaPrimitiveType,
            )
        show.isAccessible = true
        show.invoke(this, plan, emptyMap<String, String>(), emptyList<String>(), false)
    }

    @Test
    fun voiceHandOffStartsPlanningLikeAPlanTapAndOnlyOnce() {
        val activity = screen()
        activity.typeRequest("open notes")
        assertTrue(activity.planFromVoice())
        assertEquals(activity.getString(R.string.task_planning), activity.stateLine())
        assertFalse(activity.findViewById<Button>(R.id.task_start_button).isEnabled)
        assertFalse("a second transcript must not start a second plan", activity.planFromVoice())
    }

    @Test
    fun typedPlanTapIsUnchanged() {
        val activity = screen()
        activity.typeRequest("open notes")
        activity.findViewById<Button>(R.id.task_start_button).performClick()
        assertEquals(activity.getString(R.string.task_planning), activity.stateLine())
        assertFalse(activity.findViewById<Button>(R.id.task_start_button).isEnabled)
    }

    @Test
    fun emptyRequestDoesNotPlan() {
        val activity = screen()
        assertFalse(activity.planFromVoice())
        assertEquals(activity.getString(R.string.task_request_empty), activity.stateLine())
    }

    @Test
    fun aRunThatIsActiveOrPendingBlocksPlanning() {
        val activity = screen()
        activity.typeRequest("open notes")
        val before = activity.stateLine()
        TaskRunSession.pending = ApprovedTaskPlan(listOf(LoopStep("1", ExecutedAction("observe"))))
        assertFalse(activity.planFromVoice())
        assertEquals(before, activity.stateLine())
        assertTrue(activity.findViewById<Button>(R.id.task_start_button).isEnabled)
    }

    @Test
    fun aLatchedTakeoverBlocksPlanningAndIsNeverClearedByVoice() {
        val activity = screen()
        activity.typeRequest("open notes")
        detector.onAgentActionStarted()
        detector.onTouch(TakeoverDetector.TouchSource.USER, 0L)
        assertTrue(detector.isPaused)
        val before = activity.stateLine()
        assertFalse(activity.planFromVoice())
        assertTrue("only a Plan tap or the Resume dialog may clear a takeover", detector.isPaused)
        assertEquals(before, activity.stateLine())
    }

    @Test
    fun anOpenPlanApprovalDialogBlocksPlanning() {
        val activity = screen()
        activity.typeRequest("open notes")
        val sms = ExecutedAction("compose_sms", mapOf("to" to "", "body" to "hello"))
        val plan = ApprovedTaskPlan(listOf(LoopStep("1", sms)))
        activity.showTestPlan(plan)
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        assertFalse(activity.planFromVoice())
        dialog.dismiss()
        assertTrue(activity.planFromVoice())
    }

    @Test
    fun voicePlanRequiresApprovalEvenWhenTypedPlanApprovalIsDisabled() {
        val activity = screen()
        PlanApprovalSettings.setRequired(activity, false)
        activity.typeRequest("open notes")
        assertTrue(activity.planFromVoice())
        val plan = ApprovedTaskPlan(listOf(LoopStep("1", ExecutedAction("OPEN_APP", mapOf("appName" to "notes")))))
        activity.showTestPlan(plan)
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        assertEquals(null, TaskRunSession.pending)
        assertEquals(null, TaskRunSession.controller)
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(null, TaskRunSession.pending)
    }

    @Test
    fun typedPlanStillHonorsTheApprovalPreference() {
        val activity = screen()
        PlanApprovalSettings.setRequired(activity, false)
        activity.typeRequest("open notes")
        activity.findViewById<Button>(R.id.task_start_button).performClick()
        val plan = ApprovedTaskPlan(listOf(LoopStep("1", ExecutedAction("OPEN_APP", mapOf("appName" to "notes")))))
        activity.showTestPlan(plan)
        assertEquals(plan, TaskRunSession.pending)
        assertFalse(TaskRunSession.readAloud)
    }

    @Test
    fun runningAndPausedControllerBlockVoicePlanning() =
        runTest {
            val activity = screen()
            activity.typeRequest("new request")
            val step = LoopStep("1", ExecutedAction("OPEN_APP", mapOf("appName" to "notes")))
            val plan = ApprovedTaskPlan(listOf(step, step.copy(stepId = "2")))
            val settle = CompletableDeferred<Unit>()
            val config = ActionLoop.Config()
            val executor =
                StudyActionExecutor(EqoAutomationPort({ null }, { _, _ -> false }), plan) { _, _ ->
                    settle.await()
                    ActionResult.Success()
                }
            val controller =
                StudyTaskController(
                    plan.steps(),
                    StudyPermissionCheck({ true }, { true }, { false }),
                    StudyApprovalGate(StudyApprovalSurface { error("Already approved") }, { 0L }, plan),
                    executor,
                    { "" },
                    config = config,
                    takeoverDetector = TakeoverDetector(),
                )
            TaskRunSession.controller = controller
            val running = async { controller.run() }
            runCurrent()
            assertEquals(LoopState.RUNNING, controller.currentState())
            assertFalse(activity.planFromVoice())
            assertTrue(controller.pause())
            settle.complete(Unit)
            advanceTimeBy(config.tickMs)
            runCurrent()
            assertEquals(LoopState.PAUSED, controller.currentState())
            assertFalse(activity.planFromVoice())
            controller.stop()
            running.await()
        }

    @Test
    fun spokenWordsFromThePhoneRecognizerStartAPlanThatStillNeedsApproval() {
        val activity = screen()
        VoiceSettings(activity).engine = VoiceEngine.PHONE
        val service =
            ServiceInfo().apply {
                packageName = "test.speech"
                name = "test.speech.RecognitionService"
            }
        shadowOf(activity.packageManager).addResolveInfoForIntent(
            Intent(RecognitionService.SERVICE_INTERFACE),
            ResolveInfo().apply { serviceInfo = service },
        )
        shadowOf(activity.application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        activity.findViewById<Button>(R.id.task_voice_button).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer()).triggerOnResults(
            Bundle().apply {
                putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("open notes"))
            },
        )
        assertEquals("open notes", activity.findViewById<EditText>(R.id.task_request).text.toString())
        assertEquals(activity.getString(R.string.task_planning), activity.stateLine())
        assertEquals(
            activity.getString(R.string.task_planning),
            activity.findViewById<TextView>(R.id.task_voice_state).text.toString(),
        )
        // Planning only prepares a plan: nothing is running, so no run was ever pending.
        assertEquals(null, TaskRunSession.pending)
        assertNotEquals(activity.getString(R.string.task_idle), activity.stateLine())
        val plan = ApprovedTaskPlan(listOf(LoopStep("1", ExecutedAction("OPEN_APP", mapOf("appName" to "notes")))))
        activity.showTestPlan(plan)
        assertEquals(null, TaskRunSession.pending)
        ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(plan, TaskRunSession.pending)
        assertTrue("the approved voice-started run must retain spoken readout", TaskRunSession.readAloud)
    }
}
