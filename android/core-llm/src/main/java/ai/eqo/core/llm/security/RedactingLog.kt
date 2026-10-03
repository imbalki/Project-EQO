package ai.eqo.core.llm.security

import android.util.Log

/** Severity of a redacted log line; the sink maps it to the platform call. */
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

/** Receives log lines after they passed the [LogRedactor] pipeline. */
fun interface LogSink {
    fun emit(
        level: LogLevel,
        tag: String,
        message: String,
    )
}

/**
 * Single, fail-closed fan-out for every log line that may carry provider
 * credentials. Each severity routes the message (and, where present, the
 * throwable's rendering) through [LogRedactor] before it reaches the sink,
 * so a registered key can never appear in log output: a pipeline fault
 * emits the fixed withheld marker, never raw text.
 *
 * The default sink is Android logcat; unit tests swap in a capturing sink.
 */
object RedactingLog {
    @Volatile
    var sink: LogSink = LogcatSink

    private object LogcatSink : LogSink {
        override fun emit(
            level: LogLevel,
            tag: String,
            message: String,
        ) {
            when (level) {
                LogLevel.DEBUG -> Log.d(tag, message)
                LogLevel.INFO -> Log.i(tag, message)
                LogLevel.WARN -> Log.w(tag, message)
                LogLevel.ERROR -> Log.e(tag, message)
            }
        }
    }

    fun d(
        tag: String,
        message: String,
    ) = emit(LogLevel.DEBUG, tag, message)

    fun i(
        tag: String,
        message: String,
    ) = emit(LogLevel.INFO, tag, message)

    fun w(
        tag: String,
        message: String,
    ) = emit(LogLevel.WARN, tag, message)

    fun e(
        tag: String,
        message: String,
    ) = emit(LogLevel.ERROR, tag, message)

    fun d(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = emit(LogLevel.DEBUG, tag, throwableText(message, throwable))

    fun i(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = emit(LogLevel.INFO, tag, throwableText(message, throwable))

    fun w(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = emit(LogLevel.WARN, tag, throwableText(message, throwable))

    fun e(
        tag: String,
        message: String,
        throwable: Throwable,
    ) = emit(LogLevel.ERROR, tag, throwableText(message, throwable))

    private fun emit(
        level: LogLevel,
        tag: String,
        message: String,
    ) {
        sink.emit(level, tag, LogRedactor.redactToText(message))
    }

    private fun throwableText(
        message: String,
        throwable: Throwable,
    ): String =
        buildString {
            appendLine(message)
            appendLine(throwable.toString())
            throwable.stackTrace.forEach { appendLine("    at $it") }
        }
}
