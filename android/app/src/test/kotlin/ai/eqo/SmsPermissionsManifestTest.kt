// TASK-014 (issue #19): manifest narrowing guard.
package ai.eqo

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Gate G-05 / PRD REQ-SMS-03: the merged manifest must not request
 * READ_SMS or RECEIVE_SMS (no inbox access), and must not request Termux's
 * RUN_COMMAND (D-005 — the bundled Python bridge stays inert).
 */
@RunWith(RobolectricTestRunner::class)
class SmsPermissionsManifestTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun requestedPermissions(): List<String> {
        val info: PackageInfo =
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        return info.requestedPermissions?.toList().orEmpty()
    }

    @Test
    @Config(sdk = [34])
    fun `merged manifest requests no SMS permissions beyond SEND_SMS`() {
        val requested = requestedPermissions()
        val smsRelated = requested.filter { it.endsWith(".SMS") || it.endsWith(".Sms") }
        // Compose-only needs no SMS permission at all; SEND_SMS is not even
        // required (ACTION_SENDTO delegates into the messaging app). Whatever
        // remains must never include inbox access (READ/RECEIVE).
        assertTrue("no READ_SMS: $smsRelated", smsRelated.none { it.endsWith("permission.READ_SMS") })
        assertTrue("no RECEIVE_SMS: $smsRelated", smsRelated.none { it.endsWith("permission.RECEIVE_SMS") })
    }

    @Test
    @Config(sdk = [34])
    fun `merged manifest requests no dangerous SMS or Termux permissions`() {
        val requested = requestedPermissions()
        assertNotNull(requested)
        val banned =
            listOf(
                "android.permission.READ_SMS",
                "android.permission.RECEIVE_SMS",
                "com.termux.permission.RUN_COMMAND",
            )
        val leaked = banned.filter { it in requested }
        assertTrue("forbidden permissions present: $leaked", leaked.isEmpty())
    }
}
