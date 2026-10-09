// Origin: EQO-authored voice v2 regressions; fake recording/provider, no microphone or network.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.llm.providers.AudioUnsupportedException
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Looper
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class VoiceInputV2Test {
    private val dispatcher = StandardTestDispatcher()
    private val controller = Robolectric.buildActivity(Activity::class.java)
    private lateinit var activity: Activity
    private lateinit var settings: VoiceSettings
    private lateinit var clip: FakeRecording
    private lateinit var voice: TaskVoiceInput
    private var submitted = false

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        activity = controller.setup().get()
        activity.setContentView(R.layout.task_screen)
        settings = VoiceSettings(activity)
        activity.getSharedPreferences("eqo_voice_settings", Activity.MODE_PRIVATE).edit { clear() }
        shadowOf(activity.application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        activity.findViewById<Button>(R.id.task_start_button).setOnClickListener { submitted = true }
    }

    @After
    fun teardown() {
        if (::voice.isInitialized) voice.close()
        controller.pause().stop().destroy()
        Dispatchers.resetMain()
    }

    private fun create(transcribe: suspend (File, String) -> String = { _, _ -> "spoken words" }) {
        voice = TaskVoiceInput(
            activity,
            recordingFactory = { cache, _ -> FakeRecording(cache).also { clip = it } },
            ioDispatcher = dispatcher,
            transcribe = transcribe,
        )
    }

    private fun tap() = activity.findViewById<Button>(R.id.task_voice_button).performClick()

    private fun ai(consent: Boolean = true) {
        settings.engine = VoiceEngine.OPENROUTER
        settings.consent = consent
    }

    @Test
    fun phoneDefaultEngineCanSwitchAndConsentPrecedesRecording() {
        assertEquals(VoiceEngine.PHONE, settings.engine)
        assertFalse(settings.consent)
        ai(false)
        create()
        tap()
        assertFalse(::clip.isInitialized)
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertFalse(settings.consent)
        tap()
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(settings.consent)
        assertTrue(clip.started)
        assertEquals(activity.getString(R.string.voice_stop), activity.findViewById<Button>(R.id.task_voice_button).text)
    }

    @Test
    fun successDeletesAudioAndAppendsOnlyToDraft() = runTest(dispatcher) {
        ai()
        settings.language = "hi-IN"
        activity.findViewById<EditText>(R.id.task_request).setText("typed")
        create { file, language ->
            assertTrue(file.exists())
            assertEquals("hi-IN", language)
            "spoken words"
        }
        tap()
        tap()
        advanceUntilIdle()
        assertFalse(clip.file.exists())
        assertEquals("typed spoken words", activity.findViewById<EditText>(R.id.task_request).text.toString())
        assertFalse(submitted)
    }

    @Test
    fun failureDeletesAudioAndPreservesDraft() = runTest(dispatcher) {
        ai()
        activity.findViewById<EditText>(R.id.task_request).setText("keep me")
        create { _, _ -> error("synthetic failure") }
        tap()
        tap()
        advanceUntilIdle()
        assertFalse(clip.file.exists())
        assertEquals("keep me", activity.findViewById<EditText>(R.id.task_request).text.toString())
        assertEquals(activity.getString(R.string.voice_ai_error), activity.findViewById<TextView>(R.id.task_voice_state).text)
        assertFalse(submitted)
    }

    @Test
    fun unsupportedModelOffersPhoneEngineAndDeletesAudio() = runTest(dispatcher) {
        ai()
        create { _, _ -> throw AudioUnsupportedException() }
        tap()
        tap()
        advanceUntilIdle()
        assertFalse(clip.file.exists())
        assertEquals(
            activity.getString(R.string.voice_ai_unsupported),
            activity.findViewById<TextView>(R.id.task_voice_state).text,
        )
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(VoiceEngine.PHONE, settings.engine)
    }

    @Test
    fun cancellationDuringUploadDeletesFileAndDoesNotFillDraft() = runTest(dispatcher) {
        ai()
        var called = false
        create { _, _ -> called = true; awaitCancellation() }
        tap()
        tap()
        advanceUntilIdle()
        assertTrue(called)
        tap()
        advanceUntilIdle()
        assertFalse(clip.file.exists())
        assertEquals("", activity.findViewById<EditText>(R.id.task_request).text.toString())
        assertFalse(submitted)
    }

    @Test
    fun leavingWhileRecordingDeletesFileWithoutUpload() {
        ai()
        create { _, _ -> error("must not upload") }
        tap()
        voice.pause()
        assertFalse(clip.file.exists())
    }

    @Test
    fun captureLimitFinishesRecording() = runTest(dispatcher) {
        ai()
        create()
        tap()
        clip.onLimit()
        shadowOf(Looper.getMainLooper()).idle()
        advanceUntilIdle()
        assertFalse(clip.file.exists())
        assertEquals("spoken words", activity.findViewById<EditText>(R.id.task_request).text.toString())
    }

    @Test
    fun wavHeaderIs16KhzMonoAndCapIs60Seconds() {
        val header = ByteBuffer.wrap(VoiceAudioRecorder.wavHeader(320)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, header.getShort(22).toInt())
        assertEquals(16000, header.getInt(24))
        assertEquals(16, header.getShort(34).toInt())
        assertEquals(320, header.getInt(40))
        assertEquals(1920000, VoiceAudioRecorder.MAX_BYTES)
    }

    private class FakeRecording(cache: File) : VoiceRecording {
        override val file = File.createTempFile("fake-voice-", ".wav", cache)
        var started = false
        lateinit var onLimit: () -> Unit
        override fun start(onLimit: () -> Unit) {
            started = true
            this.onLimit = onLimit
            file.writeBytes(ByteArray(60))
        }
        override suspend fun finish(): File = file
        override fun cancel() { file.delete() }
    }
}
