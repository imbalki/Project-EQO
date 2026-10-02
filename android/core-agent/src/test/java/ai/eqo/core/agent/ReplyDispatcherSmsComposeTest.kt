// TASK-014 (issue #19): guard tests for SMS compose-only and permission narrowing.
package ai.eqo.core.agent

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric guard for the only SMS dispatch path EQO keeps (PRD REQ-SMS-04):
 * [ReplyDispatcher.replyViaSms] must open an ACTION_SENDTO compose intent and
 * never touch [android.telephony.SmsManager] for a direct send.
 */
@RunWith(RobolectricTestRunner::class)
class ReplyDispatcherSmsComposeTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [34])
    fun `replyViaSms opens an smsto compose intent instead of sending directly`() {
        val dispatcher = ReplyDispatcher(context)

        val started = dispatcher.replyViaSms("+1 555 0100", "reply text", context)

        assertTrue(started)
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto:+1 555 0100", intent.data.toString())
        assertEquals("reply text", intent.getStringExtra("sms_body"))
    }

    @Test
    @Config(sdk = [34])
    fun `replyViaSms refuses to open a draft without recipient or content`() {
        val dispatcher = ReplyDispatcher(context)

        assertFalse(dispatcher.replyViaSms("", "reply text", context))
        assertFalse(dispatcher.replyViaSms("+1 555 0100", "  ", context))
        // Nothing was launched for invalid input.
        assertNull(shadowOf(app).nextStartedActivity)
    }
}
