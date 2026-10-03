package ai.eqo.core.llm.security

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TASK-006 acceptance coverage for the crash path: the process-wide uncaught
 * handler delegates a redacted report to the previous handler, so no crash
 * dump can carry a registered key, and a pipeline fault withholds everything.
 */
class LogRedactorCrashHookTest {
    @After
    fun tearDown() {
        LogRedactor.faultInjector = null
    }

    @Test
    fun `crash text containing a registered key is redacted before the default handler`() {
        val key = "sk-or-v1-crash-0004"
        val captured = mutableListOf<Throwable>()
        val original = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable -> captured += throwable }
        val restore = LogRedactorCrashHook.install()
        val registration = LogRedactor.register(key)
        try {
            val boom = IllegalStateException("request failed: Bearer $key")
            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), boom)

            val delivered = captured.single()
            assertTrue(
                "expected redacted crash type, was ${delivered.javaClass.name}",
                delivered is RedactedCrashException,
            )
            assertEquals("java.lang.IllegalStateException", (delivered as RedactedCrashException).originalType)
            assertFalse(delivered.message!!.contains(key))
            assertFalse(delivered.message!!.contains("Bearer $key"))
            assertTrue(delivered.message!!.contains("«redacted»"))
        } finally {
            registration.close()
            restore.close()
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }

    @Test
    fun `render withholds text when the pipeline faults`() {
        val key = "sk-or-v1-fault-0005"
        val registration = LogRedactor.register(key)
        try {
            LogRedactor.faultInjector = { throw IllegalStateException("pipeline boom $key") }
            val rendered = LogRedactorCrashHook.render(RuntimeException("crash $key"))
            assertEquals(LogRedactor.withheldText(), rendered)
            assertFalse(rendered.contains(key))
        } finally {
            LogRedactor.faultInjector = null
            registration.close()
        }
    }

    @Test
    fun `render masks the key in message and stack text`() {
        val key = "sk-or-v1-frames-0006"
        val registration = LogRedactor.register(key)
        try {
            val rendered = LogRedactorCrashHook.render(RuntimeException("outer failure $key"))
            assertFalse(rendered.contains(key))
            assertTrue(rendered.contains("«redacted»"))
        } finally {
            registration.close()
        }
    }
}
