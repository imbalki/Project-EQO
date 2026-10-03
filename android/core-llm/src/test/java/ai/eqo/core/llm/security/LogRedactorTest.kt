package ai.eqo.core.llm.security

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TASK-006 acceptance coverage for the one redaction pipeline.
 *
 *  - a registered secret inside a rendered string is masked,
 *  - an exception inside the pipeline withholds text (fail closed),
 *  - 1000 synthetic secrets through the pipeline leak nothing.
 */
class LogRedactorTest {
    @After
    fun tearDown() {
        LogRedactor.faultInjector = null
    }

    @Test
    fun `registered credential is masked in rendered text`() {
        val secret = "sk-or-v1-registered-secret-0123456789"
        val registration = LogRedactor.register(secret)
        try {
            val result = LogRedactor.redact("Bearer $secret failed")
            assertTrue(result is RedactionResult.Redacted)
            assertFalse((result as RedactionResult.Redacted).text.contains(secret))
            assertTrue(result.text.contains("«redacted»"))
        } finally {
            registration.close()
        }
    }

    @Test
    fun `redactor exception results in withheld text never raw text`() {
        val secret = "sk-or-v1-fault-injection-secret-7777"
        val registration = LogRedactor.register(secret)
        try {
            LogRedactor.faultInjector = { throw IllegalStateException("pipeline boom $secret") }

            val result = LogRedactor.redact("line with $secret inside")
            assertEquals(RedactionResult.Withheld, result)
            val withheldText = LogRedactor.redactToText("line with $secret")
            assertEquals(LogRedactor.withheldText(), withheldText)
            assertFalse(withheldText.contains(secret))
        } finally {
            LogRedactor.faultInjector = null
            registration.close()
        }
    }

    /** N3: a Throwable that is neither an Exception nor an Error. */
    private class NonExceptionThrowable(
        message: String,
    ) : Throwable(message)

    @Test
    fun `a plain Throwable subclass in the pipeline yields withheld text`() {
        // N3: catching only IllegalStateException/RuntimeException/Error let a
        // direct Throwable subclass escape the pipeline. Fail closed on any
        // throwable instead.
        val secret = "sk-or-...9311"
        val registration = LogRedactor.register(secret)
        try {
            LogRedactor.faultInjector = { throw NonExceptionThrowable("pipeline fault $secret") }

            val result = LogRedactor.redact("line with $secret inside")
            assertEquals(RedactionResult.Withheld, result)
            assertFalse(LogRedactor.redactToText("line with $secret").contains(secret))
        } finally {
            LogRedactor.faultInjector = null
            registration.close()
        }
    }

    @Test
    fun `pipeline re-entry while running withholds instead of rendering`() {
        val secret = "sk-or-v1-reentrancy-probe-secret-4242"
        val registration = LogRedactor.register(secret)
        try {
            var captured: RedactionResult? = null
            LogRedactor.faultInjector = { captured = LogRedactor.redact("re-entered $secret") }
            // The re-entrant call must not render the secret; it withholds.
            LogRedactor.redact("probe $secret")
            assertEquals(RedactionResult.Withheld, captured)
        } finally {
            LogRedactor.faultInjector = null
            registration.close()
        }
    }

    @Test
    fun `redaction output never contains unrenderable partial secrets`() {
        val secret = "abcdefgh-single-secret-0000"
        val registration = LogRedactor.register(secret)
        try {
            val rendered = LogRedactor.redactToText("head $secret tail $secret more")
            assertFalse(rendered.contains(secret))
            assertEquals(rendered.count { it == '«' }, rendered.count { it == '»' })
        } finally {
            registration.close()
        }
    }

    @Test
    fun `1000 synthetic secrets through the redactor produce zero leaks`() {
        // One fuzz pass: 1000 distinct synthetic secrets, each rendered inside
        // a noisy line, through the same pipeline the app uses for logs and
        // crash reports. Zero raw-secret occurrences are tolerated.
        val fuzzSecrets = List(FUZZ_SECRET_COUNT, ::syntheticSecret)
        fuzzSecrets.forEachIndexed { index, secret ->
            val registration = LogRedactor.register(secret)
            try {
                val line = "openrouter call $index: Authorization Bearer $secret status=200 ok"
                val rendered = LogRedactor.redact(line)
                val observable =
                    listOf(
                        (rendered as RedactionResult.Redacted).text,
                        rendered.rules.joinToString(),
                    ).joinToString(" ")
                assertFalse("leak: $secret in $observable", observable.contains(secret))
                assertFalse("head-fragment leak", observable.contains(secret.take(9)))
                assertFalse("tail-fragment leak", observable.contains(secret.takeLast(9)))
            } finally {
                registration.close()
            }
        }
        // The registry also holds repository-hydrated secrets (TASK-006 wiring),
        // so hygiene is asserted per fuzz secret, not on global emptiness.
        val lingeringFuzzSecrets = fuzzSecrets.filter { it in LogRedactor.registeredSecrets() }
        assertTrue("fuzz secrets remained registered: $lingeringFuzzSecrets", lingeringFuzzSecrets.isEmpty())
    }

    @Test
    fun `a registered secret is masked next to an unregistered lookalike across 500 variants`() {
        // Inverse shape: one registered secret rendered alongside an
        // unregistered lookalike in a single line stays masked. Longest match
        // first keeps a short registered prefix from defeating a longer key.
        repeat(FUZZ_SECRET_COUNT / 2) { index ->
            val secret = syntheticSecret(index)
            val registration = LogRedactor.register(secret)
            try {
                val noise = syntheticSecret((index + 1) % FUZZ_SECRET_COUNT)
                val line = "$secret then $noise"
                val rendered = LogRedactor.redactToText(line)
                assertFalse(rendered.contains(secret))
            } finally {
                registration.close()
            }
        }
    }

    private fun syntheticSecret(index: Int): String =
        when (index % 5) {
            0 -> "sk-or-v1-${(index * 7919).toString(36)}-fuzz${index % 97}"
            1 -> "jur-cred-${index.toString(36).padStart(6, '0')}"
            2 -> "huggingface-token-${(index * 104729).toString(36)}"
            3 -> "endpoint:https://host-${(index * 65537).toString(36)}.example"
            else -> "cred${(index * 15485863).toString(36)}${18 + index % 27}"
        }

    private companion object {
        const val FUZZ_SECRET_COUNT = 1000
    }
}
