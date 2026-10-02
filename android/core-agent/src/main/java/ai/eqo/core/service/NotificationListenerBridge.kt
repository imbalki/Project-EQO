/*
 * ADAPTER (TASK-004 Phase 2) - minimal interface replacing the quarantined
 * upstream class com.opendroid.ai.core.service.OpenDroidNotificationListener
 * (NotificationListenerService, not in Phase One scope; it also imports
 * AutoReplyEngine, so moving it would recreate a cycle). Mirrors upstream's
 * static getInstance() accessor: the EQO notification listener service
 * registers itself here when it connects.
 */
package ai.eqo.core.service

import android.service.notification.StatusBarNotification

interface NotificationListenerBridge {
    fun getActiveNotification(
        packageName: String,
        contactName: String?,
    ): StatusBarNotification?

    companion object {
        @Volatile
        private var instance: NotificationListenerBridge? = null

        /** Upstream parity: null while no notification listener service is connected. */
        fun getInstance(): NotificationListenerBridge? = instance

        fun register(impl: NotificationListenerBridge?) {
            instance = impl
        }
    }
}
