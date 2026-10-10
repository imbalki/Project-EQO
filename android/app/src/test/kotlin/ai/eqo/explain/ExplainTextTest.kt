package ai.eqo.explain

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ExplainTextTest {
    @Test fun `safe subset styles text and never interprets HTML`() {
        val text = ExplainText.styled("## Title\n- **Bold**\n1. Next\n<script>alert(1)</script>") as Spanned
        assertEquals("Title\n• Bold\n1. Next\n<script>alert(1)</script>", text.toString())
        assertEquals(2, text.getSpans(0, text.length, StyleSpan::class.java).count { it.style == Typeface.BOLD })
        assertEquals(1, text.getSpans(0, text.length, RelativeSizeSpan::class.java).size)
        assertEquals("unclosed **bold", ExplainText.styled("unclosed **bold").toString())
    }

    @Test fun `speech has no heading bullet bold code or URL formatting`() {
        assertEquals(
            "Title\nBold\nNext\nlink\nx + y",
            ExplainText.spoken("## Title\n- **Bold**\n1. Next\n[link](https://example.test)\n```\nx + y\n```"),
        )
    }

    @Test fun `pause continue stop and stale speech completions use fake engine`() {
        val clips = mutableListOf<Pair<String, String>>()
        val states = mutableListOf<ExplainSpeech.State>()
        var stops = 0
        val speech = ExplainSpeech({ text, id -> clips.add(text to id) }, { stops++ }, states::add, 5)
        speech.read("**hello** world")
        assertEquals("hello", clips.last().first)
        val first = clips.last().second
        speech.pause()
        speech.completed(first)
        assertEquals(ExplainSpeech.State.PAUSED, speech.state)
        speech.continueReading()
        val repeated = clips.last().second
        assertFalse(first == repeated)
        speech.completed(first)
        assertEquals(2, clips.size)
        speech.completed(repeated)
        assertEquals(" worl", clips.last().first)
        speech.stop()
        speech.completed(clips.last().second)
        assertEquals(ExplainSpeech.State.STOPPED, speech.state)
        assertTrue(stops >= 3)
        assertTrue(ExplainSpeech.State.SPEAKING in states)
        speech.continueReading()
        assertEquals(3, clips.size)
    }

    @Test fun `explanation prompt uses device language short simple explanations and never copies the page`() {
        val prompt = explainPrompt(java.util.Locale.forLanguageTag("hi-IN"))
        listOf("hi-IN", "120 words", "unless the user asks", "formulas and code", "main controls", "next step",
            "Do not copy", "markdown headings", "never execute actions", "Do not invent").forEach {
            assertTrue(prompt.contains(it))
        }
    }
}
