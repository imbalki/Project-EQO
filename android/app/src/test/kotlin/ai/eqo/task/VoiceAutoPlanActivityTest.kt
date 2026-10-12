// Origin: EQO-authored regressions: a voice transcript plans like a Plan tap and never bypasses a guard.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.accessibility.TakeoverDetector
import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.UserResumeConfirmation
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
class VoiceAutoPlanActivityTest {
    private val detector = TakeoverDetector.shared
    private val lifecycle = Robolectric.buildActivity(TaskActivity::class.java)

    @After
    fun cleanUp() {
        TaskRunSession.pending = null
        detector.onAgentActionFinished()
        detector.resume(UserResumeConfirmation.forExplicitUserConfirmation(1L))
        lifecycle.pause().stop().destroy()
    }

    private fun screen(): TaskActivity = lifecycle.setup().get()

    private fun TaskActivity.stateLine() = findViewById<TextView>(R.id.task_state).text.toString()

    private fun TaskActivity.typeRequest(text: String) = findViewById<EditText>(R.id.task_request).setText(text)

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
        val show =
            TaskActivity::class.java.getDeclaredMethod(
                "showPlan",
                ApprovedTaskPlan::class.java,
                Map::class.java,
                List::class.java,
                Boolean::class.javaPrimitiveType,
            )
        show.isAccessible = true
        show.invoke(activity, plan, emptyMap<String, String>(), emptyList<String>(), false)
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        assertFalse(activity.planFromVoice())
        dialog.dismiss()
        assertTrue(activity.planFromVoice())
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
            activity.getString(R.string.voice_heard_planning),
            activity.findViewById<TextView>(R.id.task_voice_state).text.toString(),
        )
        // Planning only prepares a plan: nothing is running, so no run was ever pending.
        assertEquals(null, TaskRunSession.pending)
        assertNotEquals(activity.getString(R.string.task_idle), activity.stateLine())
    }
}
