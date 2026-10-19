// Origin: EQO-authored Android audio-note storage, consent, card and foreground-notification regressions.
package ai.eqo.handle

import ai.eqo.R
import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.handle.EdgeHandleFeatures
import ai.eqo.accessibility.handle.EdgeHandleOverlay
import ai.eqo.accessibility.handle.HandlePreferences
import android.app.NotificationManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class HandleRecordingTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        HandlePreferences(context).preferences.edit { clear() }
        EdgeHandleFeatures.recordingActive = false
        HandleRecording.dismiss()
    }

    @After
    fun cleanup() {
        EdgeHandleFeatures.recordingActive = false
        HandleRecording.dismiss()
        RecordingService.visibleStartApproved = false
        RecordingService.startFinished = null
    }

    @Test
    fun `record switch defaults on and typed limit is persisted`() {
        assertTrue(createHandleRegistry(context).enabled(EdgeHandleFeatures.RECORDING_ID))
        val preferences = HandlePreferences(context)
        assertEquals("", preferences.recordingLimit)
        preferences.recordingLimit = "15"
        assertEquals("15", HandlePreferences(context).recordingLimit)
        val registry = createHandleRegistry(context)
        registry.setEnabled(EdgeHandleFeatures.RECORDING_ID, false)
        assertFalse(createHandleRegistry(context).enabled(EdgeHandleFeatures.RECORDING_ID))
    }

    @Test
    fun `transcript Copy is local and Keep makes one CREATE_NOTE plan awaiting normal approval`() {
        ai.eqo.task.TaskRunSession.controller = null
        HandleRecording.saved(context, Uri.parse("content://media/external/audio/media/9"), "synthetic.m4a", 5000)
        HandleRecording.showTranscript("synthetic transcript")
        HandleRecording.copy(context)
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        assertEquals(
            "synthetic transcript",
            clipboard.primaryClip!!
                .getItemAt(0)
                .text
                .toString(),
        )
        HandleRecording.keep(context)
        val plan = HandleRecording.takeNotePlan()!!
        assertEquals(1, plan.size)
        assertEquals("CREATE_NOTE", plan.single().action.name)
        assertEquals("Keep", plan.single().action.params["appName"])
        assertEquals("synthetic transcript", plan.single().action.params["body"])
        assertNull(HandleRecording.takeNotePlan())
        assertTrue(EdgeHandleFeatures.recordingHasAudio)
        assertTrue(EdgeHandleFeatures.recordingHasTranscript)
    }

    @Test
    fun `record entry opens visible confirmation and never directly starts service`() {
        HandleRecording.tap(context)
        val launched = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(EdgeHandleSettingsActivity::class.java.name, launched.component?.className)
        assertTrue(launched.getBooleanExtra(HandleRecording.START_RECORDING, false))
        assertNull(shadowOf(context as android.app.Application).nextStartedService)
    }

    @Test
    fun `unapproved service start refuses without opening microphone`() {
        val service = Robolectric.buildService(RecordingService::class.java).create().get()
        service.onStartCommand(Intent(context, RecordingService::class.java), 0, 1)
        assertFalse(EdgeHandleFeatures.recordingActive)
        assertTrue(EdgeHandleFeatures.recordingMessage.isNotBlank())
        assertNull(
            service.javaClass
                .getDeclaredField("recorder")
                .apply { isAccessible = true }
                .get(service),
        )
        service.onDestroy()
    }

    @Test
    fun `foreground notification is quiet low importance with only stop`() {
        val service = Robolectric.buildService(RecordingService::class.java).create().get()
        service.javaClass
            .getDeclaredMethod("foreground")
            .apply { isAccessible = true }
            .invoke(service)
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel("eqo_recording")
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        val notification = shadowOf(service).lastForegroundNotification
        assertEquals("EQO recording", notification.extras.getString("android.title"))
        assertEquals(1, notification.actions.size)
        assertEquals(
            "Stop",
            notification.actions
                .single()
                .title
                .toString(),
        )
        service.onDestroy()
    }

    @Test
    fun `MediaStore publishes AAC path name pending duration and never deletes a saved note on Done`() {
        val file = File.createTempFile("recording-fixture-", ".m4a", context.cacheDir)
        val provider = AudioProvider(file)
        provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
        try {
            val store = RecordingStore(context)
            store.open()
            assertEquals("audio/mp4", provider.inserted?.getAsString(MediaStore.Audio.Media.MIME_TYPE))
            assertEquals("Music/EQO Recordings", provider.inserted?.getAsString(MediaStore.Audio.Media.RELATIVE_PATH))
            assertEquals(1, provider.inserted?.getAsInteger(MediaStore.Audio.Media.IS_PENDING))
            assertTrue(store.name.matches(Regex("eqo-recording-[0-9]{8}-[0-9]{6}-[0-9]{3}\\.m4a")))
            store.publish(15_000)
            assertEquals(0, provider.updated?.getAsInteger(MediaStore.Audio.Media.IS_PENDING))
            assertEquals(15_000L, provider.updated?.getAsLong(MediaStore.Audio.Media.DURATION))
            HandleRecording.saved(context, provider.audio, store.name, 15_000)
            assertTrue(EdgeHandleFeatures.recordingHasAudio)
            HandleRecording.dismiss()
            assertEquals(0, provider.deletes)
            assertEquals("Recordings/EQO", RecordingStore.relativePath(31))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `long note refusal makes no network call and audio remains shareable with read grant`() {
        val uri = Uri.parse("content://media/external/audio/media/9")
        HandleRecording.saved(context, uri, "synthetic.m4a", 120_000)
        HandleRecording.transcribe(context)
        assertTrue(EdgeHandleFeatures.recordingMessage.contains("90 seconds only"))
        assertTrue(EdgeHandleFeatures.recordingHasAudio)
        HandleRecording.share(context)
        val chooser = shadowOf(context as android.app.Application).nextStartedActivity
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("audio/mp4", send.type)
        assertEquals(uri, send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(send.clipData)
    }

    @Test
    fun `no consent refuses short note without starting transcription`() {
        context.getSharedPreferences("eqo_voice_settings", Context.MODE_PRIVATE).edit { clear() }
        HandleRecording.saved(context, Uri.parse("content://media/external/audio/media/9"), "synthetic.m4a", 5000)
        HandleRecording.transcribe(context)
        assertEquals(context.getString(R.string.dictation_consent), EdgeHandleFeatures.recordingMessage)
        assertTrue(EdgeHandleFeatures.recordingHasAudio)
    }

    @Test
    fun `tiny recording dot expands on touch and stop invokes only explicit callback`() {
        val service = Robolectric.buildService(EQOAccessibilityService::class.java).create().get()
        val preferences = HandlePreferences(service)
        preferences.enabled = true
        preferences.hintShown = true
        val overlay = EdgeHandleOverlay(service, service.getSystemService(WindowManager::class.java))
        var stops = 0
        EdgeHandleFeatures.recordingStop = { stops++ }
        try {
            overlay.setProbeAvailable(true)
            EdgeHandleFeatures.recordingActive = true
            EdgeHandleFeatures.recordingMessage = "Recording – tap to stop. 5 s elapsed · No limit"
            EdgeHandleFeatures.recordingChanged()
            shadowOf(Looper.getMainLooper()).idle()
            val dot = descendants(view(overlay)!!).filterIsInstance<TextView>().first { it.text == "●" }
            assertEquals(0, stops)
            dot.performClick()
            shadowOf(Looper.getMainLooper()).idle()
            val root = view(overlay)!!
            assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.contains("5 s elapsed") })
            descendants(root).filterIsInstance<Button>().first { it.text == "Recording – tap to stop" }.performClick()
            assertEquals(1, stops)
        } finally {
            EdgeHandleFeatures.recordingActive = false
            overlay.destroy()
        }
    }

    @Test
    fun `blocked recording channel refuses an invisible recording indicator`() {
        val service = Robolectric.buildService(RecordingService::class.java).create().get()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            android.app.NotificationChannel("eqo_recording", "Audio notes", NotificationManager.IMPORTANCE_NONE),
        )
        assertFalse(service.notificationsVisible())
        service.onDestroy()
    }

    @Test
    @Suppress("LongMethod") // Both card modes share one overlay fixture and restore every callback in finally.
    fun `saved and transcript cards dispatch explicit actions and long transcript can scroll`() {
        val service = Robolectric.buildService(EQOAccessibilityService::class.java).create().get()
        HandlePreferences(service).apply {
            enabled = true
            hintShown = true
        }
        val overlay = EdgeHandleOverlay(service, service.getSystemService(WindowManager::class.java))
        val share = EdgeHandleFeatures.recordingShare
        val transcribe = EdgeHandleFeatures.recordingTranscribe
        val copy = EdgeHandleFeatures.recordingCopy
        val keep = EdgeHandleFeatures.recordingKeep
        val dismiss = EdgeHandleFeatures.recordingDismiss
        val calls = mutableListOf<String>()
        EdgeHandleFeatures.recordingShare = { calls.add("share") }
        EdgeHandleFeatures.recordingTranscribe = { calls.add("transcribe") }
        EdgeHandleFeatures.recordingCopy = { calls.add("copy") }
        EdgeHandleFeatures.recordingKeep = { calls.add("keep") }
        EdgeHandleFeatures.recordingDismiss = { calls.add("dismiss") }
        try {
            overlay.setProbeAvailable(true)
            HandleRecording.saved(context, Uri.parse("content://media/external/audio/media/9"), "synthetic.m4a", 5000)
            shadowOf(Looper.getMainLooper()).idle()
            val savedButtons = descendants(view(overlay)!!).filterIsInstance<Button>()
            savedButtons.first { it.text == "Share" }.performClick()
            savedButtons.first { it.text == "Transcribe" }.performClick()
            savedButtons.first { it.text == "Done" }.performClick()
            assertEquals(listOf("share", "transcribe", "dismiss"), calls)
            val transcript = (1..20).joinToString("\n") { "synthetic transcript" }
            HandleRecording.showTranscript(transcript)
            shadowOf(Looper.getMainLooper()).idle()
            val result = descendants(view(overlay)!!)
            val text = result.filterIsInstance<TextView>().first { it.text.toString() == transcript }
            assertTrue(text.maxLines > 5)
            assertTrue((text.parent as android.widget.ScrollView).layoutParams.height > 0)
            result.filterIsInstance<Button>().first { it.text == "Copy" }.performClick()
            result.filterIsInstance<Button>().first { it.text == "Save as Keep note" }.performClick()
            result.filterIsInstance<Button>().first { it.text == "Dismiss" }.performClick()
            assertEquals(listOf("share", "transcribe", "dismiss", "copy", "keep", "dismiss"), calls)
        } finally {
            overlay.destroy()
            EdgeHandleFeatures.recordingShare = share
            EdgeHandleFeatures.recordingTranscribe = transcribe
            EdgeHandleFeatures.recordingCopy = copy
            EdgeHandleFeatures.recordingKeep = keep
            EdgeHandleFeatures.recordingDismiss = dismiss
        }
    }

    private fun view(overlay: EdgeHandleOverlay): View? =
        overlay.javaClass
            .getDeclaredField("view")
            .apply { isAccessible = true }
            .get(overlay) as? View

    private fun descendants(root: View): List<View> =
        listOf(root) +
            if (root is ViewGroup) {
                (0 until root.childCount).flatMap { descendants(root.getChildAt(it)) }
            } else {
                emptyList()
            }

    private class AudioProvider(
        private val file: File,
    ) : ContentProvider() {
        val audio: Uri = Uri.parse("content://media/external/audio/media/9")
        var inserted: ContentValues? = null
        var updated: ContentValues? = null
        var deletes = 0

        override fun onCreate() = true

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri {
            inserted = ContentValues(values)
            return audio
        }

        override fun openFile(
            uri: Uri,
            mode: String,
        ): ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            updated = ContentValues(values)
            return 1
        }

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            deletes++
            return 1
        }

        override fun getType(uri: Uri): String = "audio/mp4"

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): android.database.Cursor? = null
    }
}
