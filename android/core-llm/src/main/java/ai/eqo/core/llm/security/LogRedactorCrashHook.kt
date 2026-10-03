package ai.eqo.core.llm.security

/**
 * Crash report that carries only redacted text: the process-wide uncaught
 * handler substitutes this for the original throwable, so any subsequent dump
 * (Android's `FATAL EXCEPTION` logcat block, a crash-report pipeline) can
 * only render [RuntimeException.message], which is the redacted report.
 */
class RedactedCrashException(
    /** Unredacted type only; never carries a credential. */
    val originalType: String,
    redactedReport: String,
) : RuntimeException(redactedReport)

/**
 * Routes the process uncaught-crash path through [LogRedactor]: the crash
 * report text (message plus stack trace) is rendered and redacted before it
 * reaches the default handler, so a registered key can never appear in crash
 * text. A pipeline fault yields the fixed withheld marker, never raw text.
 */
object LogRedactorCrashHook {
    /**
     * Renders [throwable] as one report string and passes it through the
     * redactor. Fail-closed: any pipeline fault returns [LogRedactor.withheldText].
     */
    fun render(throwable: Throwable): String =
        LogRedactor.redactToText(
            buildString {
                appendLine(throwable.toString())
                throwable.stackTrace.forEach { appendLine("    at $it") }
                throwable.suppressed.forEach { appendLine("Suppressed: $it") }
                throwable.cause?.let { appendLine("Caused by: $it") }
            },
        )

    /**
     * Wraps the current default uncaught handler so every uncaught crash is
     * delegated as a [RedactedCrashException]. Returns a closeable that
     * restores the previous handler.
     */
    fun install(): AutoCloseable {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val redacted = RedactedCrashException(throwable.javaClass.name, render(throwable))
            previous?.uncaughtException(thread, redacted) ?: throw redacted
        }
        return AutoCloseable { Thread.setDefaultUncaughtExceptionHandler(previous) }
    }
}
