// Origin: EQO files-attachments, the guided "All files access" setup row.
package ai.eqo.onboarding

import ai.eqo.study.CapabilityState
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AllFilesAccessTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the row is ready only when Android says all files access is on`() {
        assertEquals(CapabilityState.READY, AllFilesAccess.state(true))
        assertEquals(CapabilityState.NOT_STARTED, AllFilesAccess.state(false))
    }

    @Test
    fun `it reads the platform answer and fails closed when Android cannot answer`() {
        assertTrue(AllFilesAccess.isGranted { true })
        assertFalse(AllFilesAccess.isGranted { false })
        assertFalse(AllFilesAccess.isGranted { throw IllegalStateException("no answer") })
        // The default check runs the real platform call, which has no grant in a unit test.
        assertFalse(AllFilesAccess.isGranted())
    }

    @Test
    fun `the row opens EQOs own entry on the All files access page`() {
        val intent = AllFilesAccess.settingsIntent(context)
        assertEquals(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `the fallback opens the list of all apps`() {
        assertEquals(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION, AllFilesAccess.fallbackIntent().action)
    }
}
