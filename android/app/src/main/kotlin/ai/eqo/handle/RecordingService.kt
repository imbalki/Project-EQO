// Origin: EQO-authored user-started microphone foreground service; durable local AAC notes, no network.
package ai.eqo.handle

import ai.eqo.R
import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.EqoServiceRuntime
import ai.eqo.accessibility.handle.EdgeHandleFeatures
import ai.eqo.accessibility.handle.HandlePreferences
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock

/** START_NOT_STICKY: process death must never restart the microphone without a new visible user gesture. */
@Suppress("TooManyFunctions") // Lifecycle, storage finalization and notification belong to the owning service.
class RecordingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var recorder: MediaRecorder? = null
    private var store: RecordingStore? = null
    private var state: RecordingState? = null
    private var startedAt = 0L
    private var activeLimit = RecordingLimit(null)
    private var stopping = false
    private var lowStorageStop = false
    private val tick = Runnable { monitor() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == STOP) {
            finishRecording()
        } else if (recorder == null && !stopping) {
            startRecording()
        }
        return START_NOT_STICKY
    }

    @Suppress("TooGenericExceptionCaught") // Native failures are plain messages, never exception payloads.
    private fun startRecording() {
        android.util.Log.i("EqoRun", "handle=record start=foreground_service")
        try {
            check(visibleStartApproved)
            visibleStartApproved = false
            check(!HandleRecording.transcriptionActive)
            check(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            check(EQOAccessibilityService.getInstance() != null)
            check(HandlePreferences(this).enabled)
            check(EdgeHandleFeatures.registry?.enabled(EdgeHandleFeatures.RECORDING_ID) == true)
            val limit = checkNotNull(RecordingLimit.parse(HandlePreferences(this).recordingLimit))
            activeLimit = limit
            val next = RecordingState(limit)
            state = next
            check(next.start(captureAvailable()))
            check(storageAvailable())
            foreground()
            val output = RecordingStore(this)
            store = output
            @Suppress("DEPRECATION") // API 30 constructor is required for the Android 11 baseline.
            val audio = MediaRecorder()
            recorder = audio
            audio.setAudioSource(MediaRecorder.AudioSource.MIC)
            audio.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            audio.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            audio.setAudioSamplingRate(SAMPLE_RATE)
            audio.setAudioChannels(1)
            audio.setAudioEncodingBitRate(BIT_RATE)
            limit.millis?.takeIf { it <= Int.MAX_VALUE }?.let { audio.setMaxDuration(it.toInt()) }
            audio.setOutputFile(output.open().fileDescriptor)
            audio.setOnErrorListener { _, _, _ -> finishRecording() }
            audio.setOnInfoListener { _, _, _ -> finishRecording() }
            audio.prepare()
            audio.start()
            HandleRecording.dismiss()
            startedAt = SystemClock.elapsedRealtime()
            EdgeHandleFeatures.recordingActive = true
            HandleRecording.message(getString(R.string.recording_status, 0L, limitLabel()))
            android.util.Log.i("EqoRun", "handle=record result=started reason=local_audio")
            handler.postDelayed(tick, POLL_MS)
            startFinished?.invoke()
            startFinished = null
        } catch (_: Exception) {
            release()
            runCatching { store?.discardIncomplete() }
            store = null
            val error =
                when {
                    !notificationsVisible() -> R.string.recording_notification_required
                    !storageAvailable() -> R.string.recording_low_storage
                    else -> R.string.recording_refused
                }
            HandleRecording.feedback(this, error, "refused", "capture_notification_or_storage_unavailable")
            stopSelf()
        }
    }

    private fun foreground() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(
                CHANNEL,
                getString(R.string.recording_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        channel.setSound(null, null)
        channel.enableVibration(false)
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
        check(notificationsVisible())
        val stop =
            PendingIntent.getService(
                this,
                0,
                Intent(this, RecordingService::class.java).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            Notification
                .Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(getString(R.string.recording_notification))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(Notification.Action.Builder(null, getString(R.string.recording_stop), stop).build())
                .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    // Revocation and stale metadata stop and save; each independent safety check must pass.
    @Suppress("TooGenericExceptionCaught", "ComplexCondition")
    private fun monitor() {
        try {
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            val current = checkNotNull(state)
            val service = EQOAccessibilityService.getInstance()
            val ownWindow = ownWindow(service)
            val unsafe =
                EqoServiceRuntime.sharedTakeover.isPaused ||
                    service == null ||
                    service.isSecureWindowActive() ||
                    (ownWindow && elapsed > START_GRACE_MS) ||
                    !HandlePreferences(this).enabled ||
                    !notificationsVisible() ||
                    EdgeHandleFeatures.registry?.enabled(EdgeHandleFeatures.RECORDING_ID) != true
            lowStorageStop = !storageAvailable()
            if (current.tick(elapsed) || unsafe || !captureAvailable() || lowStorageStop) {
                finishRecording()
            } else {
                HandleRecording.message(getString(R.string.recording_status, elapsed / SECOND_MS, limitLabel()))
                handler.postDelayed(tick, POLL_MS)
            }
        } catch (_: Exception) {
            finishRecording()
        }
    }

    private fun ownWindow(service: EQOAccessibilityService?): Boolean {
        val root = service?.rootInActiveWindow ?: return true
        return try {
            val foreground = root.packageName?.toString()
            foreground == null || foreground == packageName
        } finally {
            root.recycle()
        }
    }

    internal fun notificationsVisible(): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(CHANNEL)
        return manager.areNotificationsEnabled() && channel?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun limitLabel(): String {
        val millis = activeLimit.millis
        return millis?.let { getString(R.string.recording_minutes, it / MINUTE_MS) }
            ?: getString(R.string.recording_no_limit)
    }

    internal fun captureAvailable(): Boolean {
        val manager = getSystemService(AudioManager::class.java)
        val own = recorder?.activeRecordingConfiguration
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            RecordingCaptureSafety(
                normalAudioMode = manager.mode == AudioManager.MODE_NORMAL,
                dictationBusy = EdgeHandleFeatures.dictationBusy,
                foreignCapture =
                    manager.activeRecordingConfigurations.any {
                        it.clientAudioSessionId != own?.clientAudioSessionId
                    },
                silenced = own?.isClientSilenced == true,
                takeover = EqoServiceRuntime.sharedTakeover.isPaused,
            ).allowed()
    }

    private fun storageAvailable(): Boolean =
        runCatching { StatFs((getExternalFilesDir(null) ?: filesDir).path).availableBytes > MIN_FREE_BYTES }
            .getOrDefault(false)

    @Suppress("TooGenericExceptionCaught") // Finalization trouble must never delete a saved note.
    private fun finishRecording() {
        if (stopping) return
        stopping = true
        val output = store
        val audio = recorder
        try {
            if (audio != null && output != null) {
                audio.stop()
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                output.publish(elapsed)
                state?.stop(saved = true)
                HandleRecording.saved(this, checkNotNull(output.uri), output.name, elapsed)
                android.util.Log.i("EqoRun", "handle=record result=saved reason=stopped_or_limit")
                if (lowStorageStop) {
                    HandleRecording.message(
                        getString(R.string.recording_saved_low_storage, output.name, elapsed / SECOND_MS),
                    )
                }
            }
        } catch (_: Exception) {
            state?.stop(saved = false)
            HandleRecording.feedback(this, R.string.recording_save_failed, "failed", "finalization_unavailable")
        } finally {
            release()
            stopSelf()
        }
    }

    private fun release() {
        handler.removeCallbacks(tick)
        runCatching { recorder?.release() }
        recorder = null
        runCatching { store?.close() }
        EdgeHandleFeatures.recordingActive = false
        EdgeHandleFeatures.recordingChanged()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        finishRecording()
        super.onDestroy()
    }

    companion object {
        internal var visibleStartApproved = false
        internal var startFinished: (() -> Unit)? = null
        const val STOP = "ai.eqo.handle.STOP_RECORDING"
        private const val CHANNEL = "eqo_recording"
        private const val NOTIFICATION_ID = 704
        private const val SAMPLE_RATE = 16000
        private const val BIT_RATE = 64000
        private const val POLL_MS = 500L
        private const val START_GRACE_MS = 2000L
        private const val SECOND_MS = 1000L
        private const val MINUTE_MS = 60_000L
        private const val MIN_FREE_BYTES = 10L * 1024 * 1024
    }
}
