// Origin: EQO-authored Android voice contract tests; no microphone or network used.
package ai.eqo.task

import ai.eqo.R
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
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
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class TaskVoiceInputTest {
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
