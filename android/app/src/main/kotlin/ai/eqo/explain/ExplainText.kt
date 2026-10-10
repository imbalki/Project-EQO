package ai.eqo.explain

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan

/** Deliberately not HTML: only line prefixes and paired bold delimiters have styling semantics. */
internal object ExplainText {
    private val heading = Regex("^#{1,6}\\s+")
    private val bullet = Regex("^\\s*[-*+]\\s+")
    private val number = Regex("^\\s*\\d+[.)]\\s+")
    private val bold = Regex("\\*\\*(.+?)\\*\\*")
    private val link = Regex("\\[([^]]+)]\\([^)]+\\)")

    fun styled(markdown: String): CharSequence {
        val result = SpannableStringBuilder()
        markdown.lines().forEachIndexed { index, source ->
            if (index > 0) result.append('\n')
            val title = heading.containsMatchIn(source)
            val line = source.replace(heading, "").replace(bullet, "• ")
            val start = result.length
            var cursor = 0
            bold.findAll(line).forEach { match ->
                result.append(line.substring(cursor, match.range.first))
                val from = result.length
                result.append(match.groupValues[1])
                result.setSpan(StyleSpan(Typeface.BOLD), from, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                cursor = match.range.last + 1
            }
            result.append(line.substring(cursor))
            if (title && result.length > start) {
                result.setSpan(StyleSpan(Typeface.BOLD), start, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                result.setSpan(RelativeSizeSpan(HEADING_SCALE), start, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return result
    }

    /** Strip markdown punctuation for synthesis. No URL or HTML evaluation. */
    fun spoken(markdown: String): String =
        markdown
            .lines()
            .filterNot { it.trim().startsWith("```") }
            .joinToString("\n") { line ->
                line
                    .replace(heading, "")
                    .replace(bullet, "")
                    .replace(number, "")
                    .replace(link, "$1")
                    .replace(bold, "$1")
                    .replace(Regex("[*#_`]"), "")
            }.trim()

    private const val HEADING_SCALE = 1.15f
}

/** Pause stops synthesis; Continue repeats the interrupted chunk, never controls an agent run. */
internal class ExplainSpeech(
    private val play: (String, String) -> Unit,
    private val halt: () -> Unit,
    private val changed: (State) -> Unit,
    private val chunkSize: Int,
) {
    enum class State { SPEAKING, PAUSED, STOPPED }

    var state = State.STOPPED
        private set
    private var chunks = emptyList<String>()
    private var index = 0
    private var generation = 0
    private var currentId = ""

    fun read(text: String) {
        stop()
        chunks = ExplainText.spoken(text).chunked(chunkSize)
        if (chunks.isNotEmpty()) next()
    }

    fun pause() {
        if (state != State.SPEAKING) return
        generation++
        halt()
        update(State.PAUSED)
    }

    fun continueReading() {
        if (state == State.PAUSED) next()
    }

    fun stop() {
        generation++
        halt()
        chunks = emptyList()
        index = 0
        update(State.STOPPED)
    }

    fun completed(id: String) {
        if (state != State.SPEAKING || id != currentId) return
        index++
        if (index < chunks.size) next() else stop()
    }

    fun failed(id: String) {
        if (id == currentId && state == State.SPEAKING) stop()
    }

    private fun next() {
        currentId = "explain-${++generation}-$index"
        update(State.SPEAKING)
        play(chunks[index], currentId)
    }

    private fun update(value: State) {
        state = value
        changed(value)
    }
}
