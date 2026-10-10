// Origin: EQO-authored voice v2 regressions; fake recording/provider, no microphone or network.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.llm.providers.AudioIncompleteException
import ai.eqo.core.llm.providers.AudioKeyMissingException
import ai.eqo.core.llm.providers.AudioProviderException
import ai.eqo.core.llm.providers.AudioUnsupportedException
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
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
    private var failStart = false

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
        voice =
            TaskVoiceInput(
                activity,
                recordingFactory = { cache, _ -> FakeRecording(cache, failStart).also { clip = it } },
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
    fun aiDefaultEngineCanSwitchAndConsentPrecedesRecording() {
        assertEquals(VoiceEngine.OPENROUTER, settings.engine)
        assertFalse(settings.consent)
        ai(false)
        create()
        tap()
        assertFalse(::clip.isInitialized)
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(settings.consent)
        tap()
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(settings.consent)
        assertTrue(clip.started)
        assertEquals(
            activity.getString(R.string.voice_stop_recording),
            activity.findViewById<Button>(R.id.task_voice_button).text,
        )
    }

    @Test
    fun voiceSharesExistingKeyAndGivesExactSetupRoute() {
        assertTrue(activity.getString(R.string.voice_ai_disclosure).contains("Uses your existing OpenRouter key"))
        assertTrue(activity.getString(R.string.voice_ai_key_missing).contains("Setup > Model key"))
        assertTrue(activity.getString(R.string.voice_ai_key_missing).contains("same key"))
    }

    @Test
    fun successDeletesAudioAndAppendsOnlyToDraft() =
        runTest(dispatcher) {
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
    fun failureKeepsAudioAndPreservesDraft() =
        runTest(dispatcher) {
            ai()
            activity.findViewById<EditText>(R.id.task_request).setText("keep me")
            create { _, _ -> error("synthetic failure") }
            tap()
            tap()
            advanceUntilIdle()
            assertTrue(clip.file.exists())
            assertEquals("keep me", activity.findViewById<EditText>(R.id.task_request).text.toString())
            assertEquals(
                activity.getString(R.string.voice_ai_error),
                activity.findViewById<TextView>(R.id.task_voice_state).text,
            )
            assertFalse(submitted)
        }

    @Test
    fun unsupportedModelOffersRetryBeforePhoneEngine() =
        runTest(dispatcher) {
            ai()
            create { _, _ -> throw AudioUnsupportedException() }
            tap()
            tap()
            advanceUntilIdle()
            assertTrue(clip.file.exists())
            assertEquals(
                activity.getString(R.string.voice_ai_unsupported),
                activity.findViewById<TextView>(R.id.task_voice_state).text,
            )
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals(
                activity.getString(R.string.voice_try_again),
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).text,
            )
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(VoiceEngine.PHONE, settings.engine)
            assertFalse(clip.file.exists())
        }

    @Test
    fun cancellationDuringUploadDeletesFileAndDoesNotFillDraft() =
        runTest(dispatcher) {
            ai()
            var called = false
            create { _, _ ->
                called = true
                awaitCancellation()
            }
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
    fun editingDuringTranscriptionCancelsItAndKeepsUserText() =
        runTest(dispatcher) {
            ai()
            create { _, _ -> awaitCancellation() }
            tap()
            tap()
            advanceUntilIdle()
            activity.findViewById<EditText>(R.id.task_request).setText("my edit")
            advanceUntilIdle()
            assertFalse(clip.file.exists())
            assertEquals("my edit", activity.findViewById<EditText>(R.id.task_request).text.toString())
            assertFalse(submitted)
        }

    @Test
    fun captureLimitFinishesRecording() =
        runTest(dispatcher) {
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
    fun wavHeaderIs16KhzMonoAndCapIs90Seconds() {
        val header = ByteBuffer.wrap(VoiceAudioRecorder.wavHeader(320)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, header.getShort(22).toInt())
        assertEquals(16000, header.getInt(24))
        assertEquals(16, header.getShort(34).toInt())
        assertEquals(320, header.getInt(40))
        assertEquals(2880000, VoiceAudioRecorder.MAX_BYTES)
    }

    @Test
    fun stopCaptureHappensBeforeAnyQueuedTranscriptionWork() {
        ai()
        create()
        tap()
        assertFalse(clip.stopped)
        tap()
        assertTrue(clip.stopped)
        assertTrue(clip.file.exists())
        voice.pause()
        assertFalse(clip.file.exists())
    }

    @Test
    fun temporaryAudioBoundaryDeletesOnSuccessErrorAndCancellation() =
        runTest(dispatcher) {
            val success = File.createTempFile("boundary-voice-", ".wav", activity.cacheDir)
            assertEquals("words", transcribeTemporaryAudio(success) { "words" })
            assertFalse(success.exists())
            val failure = File.createTempFile("boundary-voice-", ".wav", activity.cacheDir)
            try {
                transcribeTemporaryAudio(failure) { throw java.io.IOException("synthetic") }
            } catch (_: java.io.IOException) {
                assertFalse(failure.exists())
            }
            val cancelled = File.createTempFile("boundary-voice-", ".wav", activity.cacheDir)
            val job = launch { transcribeTemporaryAudio(cancelled) { awaitCancellation() } }
            advanceUntilIdle()
            job.cancelAndJoin()
            assertFalse(cancelled.exists())
        }

    @Test
    fun wallClockCaptureLimitStopsEvenIfRecorderDoesNotSignal() =
        runTest(dispatcher) {
            ai()
            create()
            tap()
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(89))
            assertFalse(clip.stopped)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
            advanceUntilIdle()
            assertFalse(clip.file.exists())
            assertEquals("spoken words", activity.findViewById<EditText>(R.id.task_request).text.toString())
        }

    @Test
    fun staleCleanupDeletesOnlyOldOwnedAudio() {
        val stale = File.createTempFile("eqo-voice-", ".wav", activity.cacheDir)
        val recent = File.createTempFile("eqo-voice-", ".wav", activity.cacheDir)
        val other = File.createTempFile("other-", ".wav", activity.cacheDir)
        val now = System.currentTimeMillis()
        stale.setLastModified(now - 600001)
        VoiceAudioRecorder.removeStaleFiles(activity.cacheDir, now)
        assertFalse(stale.exists())
        assertTrue(recent.exists())
        assertTrue(other.exists())
        recent.delete()
        other.delete()
    }

    @Test
    fun recordingTimerLevelAndProcessingCancelButtonReflectState() =
        runTest(dispatcher) {
            ai()
            create { _, _ -> awaitCancellation() }
            tap()
            val meter = activity.findViewById<ProgressBar>(R.id.task_voice_level)
            assertEquals(View.VISIBLE, meter.visibility)
            clip.progress(2000, 42)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2250))
            assertEquals(42, meter.progress)
            assertEquals(
                activity.getString(R.string.voice_recording, 0L, 2L),
                activity.findViewById<TextView>(R.id.task_voice_state).text,
            )
            tap()
            assertTrue(clip.stopped)
            assertEquals(View.GONE, meter.visibility)
            assertEquals(
                activity.getString(R.string.voice_processing),
                activity.findViewById<TextView>(R.id.task_voice_state).text,
            )
            assertEquals(
                activity.getString(android.R.string.cancel),
                activity.findViewById<Button>(R.id.task_voice_button).text,
            )
            advanceUntilIdle()
            tap()
            advanceUntilIdle()
            assertFalse(clip.file.exists())
            assertEquals(
                activity.getString(R.string.voice_ready),
                activity.findViewById<TextView>(R.id.task_voice_state).text,
            )
        }

    @Test
    fun silencePolicySignalStopsAndTranscribesExactlyOnce() =
        runTest(dispatcher) {
            ai()
            var calls = 0
            create { _, _ ->
                calls++
                "words"
            }
            tap()
            val silence = ByteArray(32000)
            val policy = VoiceCapturePolicy()
            repeat(5) { policy.accept(silence, silence.size) }
            assertFalse(policy.shouldStop)
            assertFalse(clip.stopped)
            policy.accept(silence, silence.size)
            if (policy.shouldStop) clip.onLimit()
            shadowOf(Looper.getMainLooper()).idle()
            advanceUntilIdle()
            clip.onLimit()
            shadowOf(Looper.getMainLooper()).idle()
            advanceUntilIdle()
            assertEquals(1, calls)
            assertFalse(clip.file.exists())
            assertFalse(submitted)
        }

    @Test
    fun recordingStartFailureDeletesAudioWithoutUpload() {
        ai()
        failStart = true
        create { _, _ -> error("must not upload") }
        tap()
        assertFalse(clip.file.exists())
        assertEquals(View.GONE, activity.findViewById<ProgressBar>(R.id.task_voice_level).visibility)
        assertEquals(
            activity.getString(R.string.voice_ai_error),
            activity.findViewById<TextView>(R.id.task_voice_state).text,
        )
    }

    @Test
    fun networkAndKeyErrorsArePlainAndKeepAudioUntilCancelled() =
        runTest(dispatcher) {
            ai()
            val cases =
                listOf(
                    java.io.IOException("synthetic") to R.string.voice_ai_network,
                    AudioKeyMissingException() to R.string.voice_ai_key_missing,
                    AudioIncompleteException() to R.string.voice_ai_incomplete,
                    AudioProviderException(401) to R.string.voice_ai_key_rejected,
                    AudioProviderException(400) to R.string.voice_ai_unsupported,
                    AudioProviderException(429) to R.string.voice_ai_error,
                )
            for ((error, message) in cases) {
                create { _, _ -> throw error }
                tap()
                tap()
                advanceUntilIdle()
                assertTrue(clip.file.exists())
                assertEquals(activity.getString(message), activity.findViewById<TextView>(R.id.task_voice_state).text)
                ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                shadowOf(Looper.getMainLooper()).idle()
                assertFalse(clip.file.exists())
                voice.close()
            }
        }

    @Test
    fun retryUsesSameRecordingAndDeletesOnlyAfterSuccess() =
        runTest(dispatcher) {
            ai()
            var calls = 0
            var first: File? = null
            create { file, _ ->
                calls++
                if (calls == 1) {
                    first = file
                    throw AudioKeyMissingException()
                }
                assertEquals(first, file)
                assertTrue(file.exists())
                "recovered words"
            }
            tap()
            tap()
            advanceUntilIdle()
            assertTrue(clip.file.exists())
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            advanceUntilIdle()
            assertEquals(2, calls)
            assertFalse(clip.file.exists())
            assertEquals("recovered words", activity.findViewById<EditText>(R.id.task_request).text.toString())
            assertFalse(submitted)
        }

    @Test
    fun failedRecordingExpiresAfterTenMinutesAndRetryDoesNotExtendIt() =
        runTest(dispatcher) {
            ai()
            create { _, _ -> throw java.io.IOException("synthetic") }
            tap()
            tap()
            advanceUntilIdle()
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMinutes(9))
            assertTrue(clip.file.exists())
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            advanceUntilIdle()
            // Cross the deadline with bounded looper dispatch slack, without resetting it on retry.
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(61))
            assertFalse(clip.file.exists())
            assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing)
        }

    @Test
    fun leavingAfterFailureDeletesRetainedAudio() =
        runTest(dispatcher) {
            ai()
            create { _, _ -> throw AudioKeyMissingException() }
            tap()
            tap()
            advanceUntilIdle()
            assertTrue(clip.file.exists())
            voice.close()
            assertFalse(clip.file.exists())
            assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing)
        }

    private class FakeRecording(
        cache: File,
        private val failStart: Boolean = false,
    ) : VoiceRecording {
        override val file = File.createTempFile("fake-voice-", ".wav", cache)
        var started = false
        var stopped = false
        lateinit var onLimit: () -> Unit
        var progress: (Long, Int) -> Unit = { _, _ -> }

        override fun setProgressListener(listener: (Long, Int) -> Unit) {
            progress = listener
        }

        override fun start(onLimit: () -> Unit) {
            started = true
            this.onLimit = onLimit
            file.writeBytes(ByteArray(60))
            check(!failStart)
        }

        override fun stopCapture() {
            stopped = true
        }

        override suspend fun finish(): File = file

        override fun cancel() {
            file.delete()
        }
    }
}
