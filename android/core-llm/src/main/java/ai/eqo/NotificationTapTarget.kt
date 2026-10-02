/*
 * ADAPTER (TASK-004 Phase 2) - replaces the moved code's hard reference to
 * com.opendroid.ai.MainActivity (Compose UI, not in Phase One scope) for
 * notification-tap intents. The EQO app layer binds an implementation that
 * returns the launch intent for its own main activity.
 */
package ai.eqo

import android.content.Context
import android.content.Intent

interface NotificationTapTarget {
    fun launchIntent(context: Context): Intent
}
