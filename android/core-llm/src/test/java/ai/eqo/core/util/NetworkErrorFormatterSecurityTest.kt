// TASK-006 security pass F2 (issue #11): the unhandled-error log site must not
// quote raw exception messages.
package ai.eqo.core.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * F2 of the TASK-006 security pass: the unhandled-error log site interpolates
 * neither the exception message nor a caller-supplied technical string — either
 * can quote a key-bearing URL. The log carries the exception class name and
 * fixed text only; the user-facing message is unchanged.
 *
 * Robolectric captures real logcat via [ShadowLog].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkErrorFormatterSecurityTest {
    /** Synthetic; only its absence from log lines is asserted. */
    private val keyFragment = "sk-or...f2log"

    private val keyBearingMessage =
        "Failed to connect to https://host.example/v1/models?key=$keyFragment"

    @Before
    fun setUp() {
        ShadowLog.clear()
    }

    @After
    fun tearDown() {
        ShadowLog.clear()
    }

    @Test
    fun `an unhandled throwable logs the class name and fixed text, never the raw message`() {
        val userMessage = NetworkErrorFormatter.toUserMessage(RuntimeException(keyBearingMessage))

        assertEquals("Something went wrong. Please try again.", userMessage)
        val lines = formatterLogLines()
        assertTrue("expected a NetworkErrorFormatter log line", lines.isNotEmpty())
        lines.forEach { line -> assertLineIsSafe(line) }
        assertTrue(
            "expected the exception class name in $lines",
            lines.any { it.contains("RuntimeException") },
        )
    }

    @Test
    fun `the string overload logs no raw message either`() {
        val userMessage = NetworkErrorFormatter.toUserMessage(keyBearingMessage)

        assertEquals("Something went wrong. Please try again.", userMessage)
        val lines = formatterLogLines()
        assertTrue("expected a NetworkErrorFormatter log line", lines.isNotEmpty())
        lines.forEach { line -> assertLineIsSafe(line) }
    }

    private fun assertLineIsSafe(line: String) {
        assertFalse("raw exception message leaked: $line", line.contains(keyFragment))
        assertFalse("raw exception message leaked: $line", line.contains("Failed to connect"))
    }

    private fun formatterLogLines(): List<String> =
        ShadowLog
            .getLogs()
            .filter { it.tag == "NetworkErrorFormatter" }
            .map { it.msg }
}
