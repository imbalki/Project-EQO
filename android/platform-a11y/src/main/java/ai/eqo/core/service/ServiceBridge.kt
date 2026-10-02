/*
 * ADAPTER (TASK-004 Phase 2) - minimal interface replacing the quarantined
 * upstream class com.opendroid.ai.core.service.OpenDroidService (foreground
 * service + notification + voice wiring; not in Phase One scope). The EQO app
 * layer binds an implementation that starts its own recording service.
 */
package ai.eqo.core.service

import android.content.Context

interface ServiceBridge {
    /** Start the recording foreground service (upstream triggerMicrophoneAction). */
    fun triggerRecord(context: Context)

    companion object {
        const val ACTION_TRIGGER_RECORD = "ai.eqo.action.TRIGGER_RECORD"
    }
}
