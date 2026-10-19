package ai.eqo

import ai.eqo.accessibility.handle.EdgeHandleFeatures
import ai.eqo.explain.ExplainNotificationService
import ai.eqo.handle.prepareHandlePanel
import android.app.Application

/** Phase-One uses an explicit runtime, not the unsupported donor Hilt graph. */
class EqoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EdgeHandleFeatures.dictationTap = ai.eqo.task.HandleDictation::tap
        EdgeHandleFeatures.recordingTap = ai.eqo.handle.HandleRecording::tap
        EdgeHandleFeatures.recordingStop = ai.eqo.handle.HandleRecording::stop
        EdgeHandleFeatures.recordingShare = ai.eqo.handle.HandleRecording::share
        EdgeHandleFeatures.recordingTranscribe = ai.eqo.handle.HandleRecording::transcribe
        EdgeHandleFeatures.recordingCopy = ai.eqo.handle.HandleRecording::copy
        EdgeHandleFeatures.recordingKeep = ai.eqo.handle.HandleRecording::keep
        EdgeHandleFeatures.recordingDismiss = ai.eqo.handle.HandleRecording::dismiss
        EdgeHandleFeatures.dictationCancel = ai.eqo.task.HandleDictation::cancel
        EdgeHandleFeatures.dictationSettings = ai.eqo.task.HandleDictation::openSettings
        EdgeHandleFeatures.screenshotShare = ai.eqo.handle.HandleScreenshot::share
        EdgeHandleFeatures.screenshotDismiss = ai.eqo.handle.HandleScreenshot::dismiss
        EdgeHandleFeatures.refreshEntries = ExplainNotificationService::refresh
        EdgeHandleFeatures.configurationChanged = ai.eqo.explain.ExplainOverlay::refreshLayout
        ai.eqo.accessibility.handle.EdgeHandleFeatures.explain = { context, source ->
            ai.eqo.explain.ExplainEntryActivity
                .launch(context, source)
        }
        ai.eqo.accessibility.handle.EdgeHandleFeatures
            .install(ai.eqo.handle.createHandleRegistry(this), ::prepareHandlePanel)
    }
}
