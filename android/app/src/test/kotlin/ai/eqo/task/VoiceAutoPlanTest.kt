// Origin: EQO-authored voice-to-plan hand-off regressions; fake recording/provider, no microphone or network.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.llm.providers.AudioKeyMissingException
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import androidx.core.content.edit
import kotlinx.coroutines.CompletableDeferred
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
import org.robolectric.shadows.ShadowSpeechRecognizer
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class VoiceAutoPlanTest {
    private val dispatcher = StandardTestDispatcher()
    private val controller = Robolectric.buildActivity(Activity::class.java)
    private lateinit var activity: Activity
    private lateinit var settings: VoiceSettings
    private lateinit var clip: FakeRecording
    private lateinit var voice: TaskVoiceInput
    private val planned = mutableListOf<String>()
    private var planningStarts = true

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        activity = controller.setup().get()
        activity.setContentView(R.layout.task_screen)
        activity.getSharedPreferences("eqo_voice_settings", Activity.MODE_PRIVATE).edit { clear() }
        settings = VoiceSettings(activity)
        shadowOf(activity.application).grantPermissions(Manifest.permission.RECORD_AUDIO)
    }

    @After
    fun teardown() {
        if (::voice.isInitialized) voice.close()
        controller.pause().stop().destroy()
        Dispatchers.resetMain()
    }

    private fun create(transcribe: suspend (File, String) -> String = { _, _ -> "spoken words" }) {
        voice =
            TaskVoiceInput(
                activity,
                recordingFactory = { cache, _ -> FakeRecording(cache).also { clip = it } },
                ioDispatcher = dispatcher,
                transcribe = transcribe,
                onTranscribed = {
                    planned += it
                    planningStarts
                },
            )
    }

    private fun ai() {
        settings.engine = VoiceEngine.OPENROUTER
        settings.audioModel = "test/audio"
        settings.consent = true
    }

    private fun tap() = activity.findViewById<Button>(R.id.task_voice_button).performClick()

    private fun draft() = activity.findViewById<EditText>(R.id.task_request).text.toString()

    private fun voiceLine() = activity.findViewById<TextView>(R.id.task_voice_state).text.toString()

    private fun speak() {
        tap()
        tap()
    }

    @Test
    fun successfulTranscriptStartsPlanningOnceAndKeepsTheVoiceDraft() =
        runTest(dispatcher) {
            ai()
            activity.findViewById<EditText>(R.id.task_request).setText("typed")
            create()
            speak()
            advanceUntilIdle()
            assertEquals(listOf("spoken words"), planned)
            assertEquals("typed spoken words", draft())
            assertTrue("the readout flag must stay set for a voice-started task", voice.hasVoiceDraft)
            assertEquals(activity.getString(R.string.voice_heard_planning), voiceLine())
            assertFalse(clip.file.exists())
        }

    @Test
    fun voiceLineKeepsTheReviewHintWhenPlanningDidNotStart() =
        runTest(dispatcher) {
            ai()
            planningStarts = false
            create()
            speak()
            advanceUntilIdle()
            assertEquals(listOf("spoken words"), planned)
            assertEquals("spoken words", draft())
            assertEquals(activity.getString(R.string.voice_review), voiceLine())
        }

    @Test
    fun failedTranscriptionDoesNotPlanAndKeepsTheAudioForRetry() =
        runTest(dispatcher) {
            ai()
            create { _, _ -> error("synthetic failure") }
            speak()
            advanceUntilIdle()
            assertTrue(clip.file.exists())
            assertEquals(emptyList<String>(), planned)
            assertEquals("", draft())
        }

    @Test
    fun blankTranscriptDoesNotPlan() =
        runTest(dispatcher) {
            ai()
            create { _, _ -> "   " }
            speak()
            advanceUntilIdle()
            assertEquals(emptyList<String>(), planned)
            assertEquals(activity.getString(R.string.voice_not_caught), voiceLine())
        }

    @Test
    fun cancelledTranscriptionDoesNotPlan() =
        runTest(dispatcher) {
            ai()
            create { _, _ -> awaitCancellation() }
            speak()
            advanceUntilIdle()
            tap()
            advanceUntilIdle()
            assertEquals(emptyList<String>(), planned)
            assertEquals("", draft())
        }

    @Test
    fun editingDuringTranscriptionDoesNotPlan() =
        runTest(dispatcher) {
            ai()
            val gate = CompletableDeferred<Unit>()
            create { _, _ ->
                gate.await()
                "late words"
            }
            speak()
            advanceUntilIdle()
            activity.findViewById<EditText>(R.id.task_request).setText("my edit")
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(emptyList<String>(), planned)
            assertEquals("my edit", draft())
            assertFalse(voice.hasVoiceDraft)
        }

    @Test
    fun leavingTheScreenDuringTranscriptionDoesNotPlan() =
        runTest(dispatcher) {
            ai()
            val gate = CompletableDeferred<Unit>()
            create { _, _ ->
                gate.await()
                "late words"
            }
            speak()
            advanceUntilIdle()
            voice.pause()
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(emptyList<String>(), planned)
            assertEquals("", draft())
        }

    @Test
    fun retrySuccessPlansExactlyOnce() =
        runTest(dispatcher) {
            ai()
            var calls = 0
            create { _, _ ->
                calls++
                if (calls == 1) throw AudioKeyMissingException()
                "recovered words"
            }
            speak()
            advanceUntilIdle()
            assertEquals(emptyList<String>(), planned)
            assertTrue(clip.file.exists())
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            advanceUntilIdle()
            assertEquals(2, calls)
            assertEquals(listOf("recovered words"), planned)
            assertEquals("recovered words", draft())
            assertFalse(clip.file.exists())
        }

    @Test
    fun typingNeverPlansThroughTheVoiceHook() =
        runTest(dispatcher) {
            ai()
            create()
            activity.findViewById<EditText>(R.id.task_request).setText("open notes")
            advanceUntilIdle()
            assertEquals(emptyList<String>(), planned)
            assertFalse(voice.hasVoiceDraft)
        }

    @Test
    fun phoneRecognizerFinalResultPlansOnceAndIgnoresLateResults() {
        installPhoneRecognizer()
        create()
        tap()
        shadowOf(Looper.getMainLooper()).idle()
        val speech = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        speech.triggerOnResults(results("open notes"))
        speech.triggerOnResults(results("late text"))
        assertEquals(listOf("open notes"), planned)
        assertEquals("open notes", draft())
        assertTrue(voice.hasVoiceDraft)
    }

    @Test
    fun phoneRecognizerPartialKeptAfterTimeoutIsLeftForReviewNotPlanned() {
        installPhoneRecognizer()
        create()
        tap()
        shadowOf(Looper.getMainLooper()).idle()
        val speech = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        speech.triggerOnPartialResults(results("open no"))
        speech.triggerOnEndOfSpeech()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))
        assertEquals("open no", draft())
        assertEquals(emptyList<String>(), planned)
    }

    private fun installPhoneRecognizer() {
        settings.engine = VoiceEngine.PHONE
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

    private fun results(words: String) =
        Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(words))
        }

    private class FakeRecording(
        cache: File,
    ) : VoiceRecording {
        override val file = File.createTempFile("fake-voice-", ".wav", cache)

        override fun setProgressListener(listener: (Long, Int) -> Unit) = Unit

        override fun start(onLimit: () -> Unit) {
            file.writeBytes(ByteArray(60))
        }

        override fun stopCapture() = Unit

        override suspend fun finish(): File = file

        override fun cancel() {
            file.delete()
        }
    }
}
