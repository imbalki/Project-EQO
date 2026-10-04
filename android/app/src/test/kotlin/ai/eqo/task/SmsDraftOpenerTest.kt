package ai.eqo.task

import android.content.ActivityNotFoundException
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmsDraftOpenerTest {
    @Test
    fun `trailing lambda opens a compose-only intent with recipient and body`() {
        var launched: Intent? = null
        val opener = SmsDraftOpener { intent -> launched = intent }

        assertTrue(opener.open("+15551234567", "Hello from EQO"))

        assertDraft(launched, "+15551234567", "Hello from EQO")
        assertNull(launched?.`package`)
    }

    @Test
    fun `empty recipient still opens SENDTO without a chooser`() {
        var launched: Intent? = null
        val opener = SmsDraftOpener { intent -> launched = intent }

        assertTrue(opener.open("", "Choose a recipient"))

        assertDraft(launched, "", "Choose a recipient")
        assertNull(launched?.`package`)
    }

    @Test
    fun `default SMS package targets messaging app with or without recipient`() {
        var launched: Intent? = null
        var resolutions = 0
        val opener =
            SmsDraftOpener(
                defaultSmsPackage = {
                    resolutions++
                    "com.example.messaging"
                },
            ) { intent -> launched = intent }

        for (recipient in listOf("+15551234567", "")) {
            assertTrue(opener.open(recipient, "Draft body"))
            assertDraft(launched, recipient, "Draft body")
            assertEquals("com.example.messaging", launched?.`package`)
        }
        assertEquals(2, resolutions)
    }

    @Test
    fun `missing SMS handler returns false and reports no SMS app once`() {
        var failures = 0
        var launches = 0
        val opener =
            SmsDraftOpener(onNoSmsApp = { failures++ }) {
                launches++
                throw ActivityNotFoundException("No SMS app")
            }

        assertFalse(opener.open("", "Draft body"))
        assertEquals(1, launches)
        assertEquals(1, failures)
    }

    @Test
    fun `missing handler supports default no-op callback`() {
        val opener = SmsDraftOpener { throw ActivityNotFoundException("No SMS app") }

        assertFalse(opener.open("555", "Draft body"))
    }

    @Test
    fun `successful launch does not report missing SMS app`() {
        var failures = 0
        val opener = SmsDraftOpener(onNoSmsApp = { failures++ }) { }

        assertTrue(opener.open("555", "Draft body"))
        assertEquals(0, failures)
    }

    @Test
    fun `other launch failures return false without reporting missing SMS app`() {
        var failures = 0
        val opener =
            SmsDraftOpener(onNoSmsApp = { failures++ }) {
                throw SecurityException("Launch blocked")
            }

        assertFalse(opener.open("555", "Draft body"))
        assertEquals(0, failures)
    }

    private fun assertDraft(
        intent: Intent?,
        recipient: String,
        body: String,
    ) {
        assertNotNull(intent)
        val draft = requireNotNull(intent)
        assertEquals(Intent.ACTION_SENDTO, draft.action)
        assertEquals("smsto:$recipient", draft.dataString)
        assertEquals("smsto", draft.data?.scheme)
        assertEquals(body, draft.getStringExtra("sms_body"))
        assertEquals(setOf("sms_body"), draft.extras?.keySet())
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, draft.flags)
        assertNull(draft.component)
        assertNull(draft.type)
        assertFalse(draft.hasExtra(Intent.EXTRA_INTENT))
    }
}
