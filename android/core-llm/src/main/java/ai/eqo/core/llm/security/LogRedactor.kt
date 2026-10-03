package ai.eqo.core.llm.security

import ai.eqo.core.llm.error.SecretRegistry
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Outcome of the one redaction pipeline that covers app logs and crash
 * reports. Raw input never reaches [Redacted.text]; a pipeline failure
 * produces [Withheld] instead of text.
 */
sealed class RedactionResult {
    /** Nothing registered matched, or registered material was masked. */
    data class Redacted(
        val text: String,
        val rules: List<String>,
    ) : RedactionResult()

    /** No text is emitted: redaction failed and raw input must not escape. */
    data object Withheld : RedactionResult()
}

/**
 * The single render path for strings that may carry provider credentials.
 *
 * Secret material enters through [register] while it is live (a candidate key
 * under test, a key resolved for a request, a custom endpoint URL); every log
 * line and crash-report field goes through [redactToText] before it leaves the
 * process. State is shared with the existing error-boundary scrub scope
 * ([SecretRegistry]), so a credential registered once while a request runs is
 * scrubbed in both the typed-error path and this pipeline.
 *
 * Fail-closed contract:
 *  - a registered secret inside the input masks it (longest match first),
 *  - a fault inside the pipeline returns [RedactionResult.Withheld], never raw
 *    or partially redacted text,
 *  - re-entering the pipeline while it is running returns [RedactionResult.Withheld].
 */
object LogRedactor {
    private const val MASK = "«redacted»"
    private const val WITHHELD = "«withheld»"

    private val lock = ReentrantLock()
    private var insidePipeline = false

    /** Test seam: fires inside the pipeline so the fail-closed path is reachable. */
    internal var faultInjector: ((String) -> Unit)? = null

    /**
     * Registers [secret] for redaction until the returned closeable runs.
     * Delegates to [SecretRegistry] so log redaction and error-boundary
     * scrubbing share one registry.
     */
    fun register(secret: String): AutoCloseable = SecretRegistry.register(secret)

    fun registeredSecrets(): Set<String> = SecretRegistry.snapshot()

    /**
     * Renders [raw] with every registered secret masked. Any failure inside
     * the pipeline withholds the whole output.
     */
    fun redact(raw: String): RedactionResult =
        if (enterPipeline()) {
            render(raw)
        } else {
            RedactionResult.Withheld
        }

    private fun enterPipeline(): Boolean =
        lock.withLock {
            if (insidePipeline) {
                false
            } else {
                insidePipeline = true
                true
            }
        }

    private fun render(raw: String): RedactionResult = tryRedact(raw) ?: RedactionResult.Withheld

    /** Returns null on any internal fault so the caller withholds. */
    private inline fun tryRedact(raw: String): RedactionResult? =
        try {
            faultInjector?.invoke(raw)
            val matches =
                registeredSecrets()
                    .filter { raw.contains(it) }
                    .sortedByDescending { it.length }
            if (matches.isEmpty()) {
                RedactionResult.Redacted(text = raw, rules = emptyList())
            } else {
                var redacted = raw
                matches.forEach { redacted = redacted.replace(it, MASK) }
                RedactionResult.Redacted(text = redacted, rules = matches.map { "registered-secret" })
            }
        } catch (_: Throwable) {
            // N3: fail closed on any throwable, including a direct Throwable
            // subclass that is neither an Exception nor an Error — nothing may
            // escape the pipeline as raw text.
            null
        } finally {
            lock.withLock { insidePipeline = false }
        }

    /** Plain-text convenience for callers that render single strings. */
    fun redactToText(raw: String): String =
        when (val result = redact(raw)) {
            is RedactionResult.Redacted -> result.text
            RedactionResult.Withheld -> WITHHELD
        }

    /** Fixed, observable text for withheld fields. */
    fun withheldText(): String = WITHHELD
}
