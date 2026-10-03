package ai.eqo.core.llm.security

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
 * TASK-006 acceptance coverage for the real logging path: lines emitted
 * through [RedactingLog] reach Android's logcat (captured here via Robolectric's
 * ShadowLog) and can never carry a registered key, and a pipeline fault
 * emits the fixed withheld marker instead of raw text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RedactingLogTest {
    @Before
    fun setUp() {
        ShadowLog.clear()
    }

    @After
    fun tearDown() {
        LogRedactor.faultInjector = null
    }

    @Test
    fun `real log output after storing a key contains no key text`() {
        val key = "sk-or-v1-logaccept-0001"
        val registration = LogRedactor.register(key)
        try {
            RedactingLog.e("OpenRouter", "chat failed: Authorization Bearer $key status=401")
            RedactingLog.d("OpenRouter", "reconnecting after key rotation")

            val emitted = ShadowLog.getLogs().map { it.msg }
            assertTrue("expected log lines to be emitted", emitted.isNotEmpty())
            emitted.forEach { line -> assertFalse("leak in line: $line", line.contains(key)) }
            assertTrue(emitted.any { it.contains("«redacted»") })
        } finally {
            registration.close()
        }
    }

    @Test
    fun `log line carrying a throwable renders a redacted report`() {
        val key = "sk-or-v1-stack-0002"
        val registration = LogRedactor.register(key)
        try {
            RedactingLog.e("OpenRouter", "request failed", IllegalStateException("Bearer $key rejected"))

            val emitted = ShadowLog.getLogs().map { it.msg }
            emitted.forEach { line -> assertFalse("leak in line: $line", line.contains(key)) }
            assertTrue(emitted.any { it.contains("redacted") })
        } finally {
            registration.close()
        }
    }

    @Test
    fun `redactor fault on the log path emits withheld text never raw`() {
        val key = "sk-or-v1-withhold-0003"
        val registration = LogRedactor.register(key)
        try {
            LogRedactor.faultInjector = { throw IllegalStateException("pipeline fault $key") }
            RedactingLog.w("OpenRouter", "credential $key unusable")

            val emitted = ShadowLog.getLogs().map { it.msg }
            assertEquals(listOf(LogRedactor.withheldText()), emitted)
            emitted.forEach { line -> assertFalse(line.contains(key)) }
        } finally {
            LogRedactor.faultInjector = null
            registration.close()
        }
    }
}
