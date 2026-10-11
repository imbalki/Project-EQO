// Origin: EQO regression for app launches refused while the accessibility service is off.
package ai.eqo.actions.impl

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GatedLaunchTest {
    @Test fun refusedLaunchTellsTheOwnerToTurnAccessibilityBackOn() {
        val launcher = GatedIntentLauncher(ApplicationProvider.getApplicationContext()) { null }
        val refused = assertThrows(GatedLaunchException::class.java) { launcher.open(Intent()) }
        assertTrue(refused.message.orEmpty().contains("accessibility", ignoreCase = true))
    }
}
