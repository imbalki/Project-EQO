// Origin: EQO-authored Android voice contract tests; no microphone or network used.
package ai.eqo.task

import ai.eqo.R
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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
import org.robolectric.shadows.ShadowSpeechRecognizer
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class TaskVoiceInputTest {
    private fun installRecognizer(activity: Activity) {
        val service =
            ServiceInfo().apply {
                packageName = "test.speech"
                name = "test.speech.RecognitionService"
            }
        shadowOf(activity.packageManager).addResolveInfoForIntent(
            Intent(RecognitionService.SERVICE_INTERFACE),
            ResolveInfo().apply { serviceInfo = service },
        )
    }

    @Test
    fun micShowsRationaleBeforePermissionAndDenialKeepsTypingWorking() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.setContentView(R.layout.task_screen)
        installRecognizer(activity)
        val voice = TaskVoiceInput(activity)
        assertNull(shadowOf(activity).lastRequestedPermission)
        activity.findViewById<Button>(R.id.task_voice_button).performClick()
        assertNull(shadowOf(activity).lastRequestedPermission)
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(TaskVoiceInput.REQUEST_CODE, shadowOf(activity).lastRequestedPermission.requestCode)
        voice.onPermissionResult(TaskVoiceInput.REQUEST_CODE)
        assertEquals(activity.getString(R.string.voice_not_allowed), activity.findViewById<TextView>(R.id.task_voice_state).text)
        assertTrue(activity.findViewById<EditText>(R.id.task_request).isEnabled)
        voice.close()
        controller.pause().stop().destroy()
    }

    @Test
    fun speechResultFillsEditableBoxWithoutClickingTaskButtonAndCleansUp() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.setContentView(R.layout.task_screen)
        installRecognizer(activity)
        shadowOf(activity.application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        var submitted = false
        activity.findViewById<Button>(R.id.task_start_button).setOnClickListener { submitted = true }
        val voice = TaskVoiceInput(activity)
        activity.findViewById<Button>(R.id.task_voice_button).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val speech = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        speech.triggerOnResults(
            Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("Open notes")) },
        )
        assertEquals("Open notes", activity.findViewById<EditText>(R.id.task_request).text.toString())
        assertFalse(submitted)
        assertTrue(speech.isDestroyed)
        activity.findViewById<EditText>(R.id.task_request).setText("Edited request")
        speech.triggerOnResults(
            Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("late text")) },
        )
        assertEquals("Edited request", activity.findViewById<EditText>(R.id.task_request).text.toString())
        voice.close()
        controller.pause().stop().destroy()
    }

    @Test
    fun intentUsesDeviceLanguageAndDoesNotRequestAudioOutput() {
        val intent = TaskVoiceInput.recognitionIntent()
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertEquals(Locale.getDefault().toLanguageTag(), intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals(
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL),
        )
        assertFalse(intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true))
        assertFalse(intent.hasExtra("android.speech.extra.GET_AUDIO"))
        assertFalse(intent.hasExtra("android.speech.extra.GET_AUDIO_FORMAT"))
    }

    @Test
    fun speechErrorsHavePlainStates() {
        assertEquals(VoiceInputState.NOT_ALLOWED, TaskVoiceInput.errorState(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertEquals(VoiceInputState.NOT_CAUGHT, TaskVoiceInput.errorState(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(VoiceInputState.NOT_CAUGHT, TaskVoiceInput.errorState(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals(VoiceInputState.ERROR, TaskVoiceInput.errorState(SpeechRecognizer.ERROR_NETWORK))
        assertEquals(VoiceInputState.ERROR, TaskVoiceInput.errorState(SpeechRecognizer.ERROR_AUDIO))
    }

    @Test
    fun unavailableRecognizerDisablesMicWithoutStartupPermissionRequest() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.setContentView(R.layout.task_screen)
        val voice = TaskVoiceInput(activity)
        assertNull(shadowOf(activity).lastRequestedPermission)
        assertEquals(PackageManager.PERMISSION_DENIED, activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        assertFalse(activity.findViewById<Button>(R.id.task_voice_button).isEnabled)
        assertEquals(activity.getString(R.string.voice_unavailable), activity.findViewById<TextView>(R.id.task_voice_state).text)
        assertTrue(activity.findViewById<EditText>(R.id.task_request).isEnabled)
        voice.close()
        controller.pause().stop().destroy()
    }
}
