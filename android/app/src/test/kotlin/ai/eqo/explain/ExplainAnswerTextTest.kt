package ai.eqo.explain

import org.junit.Assert.assertEquals
import org.junit.Test

class ExplainAnswerTextTest {
    @Test fun `headings and blank lines stay readable`() {
        assertEquals("Title\n\nSection\nBody\nSubtitle", render("# Title\n\n## Section ##\nBody\nSubtitle\n---"))
        assertEquals("Title", render("Title\n===="))
    }

    @Test fun `bold italic and nested emphasis lose only their delimiters`() {
        assertEquals("bold and strong, italic and emphasis", render("**bold** and __strong__, *italic* and _emphasis_"))
        assertEquals("all and bold with italic", render("***all*** and **bold with _italic_**"))
        assertEquals("deleted", render("~~deleted~~"))
    }

    @Test fun `bullets preserve nesting and line breaks`() {
        assertEquals("• one\n  • two\n• three\n\n• four", render("- one\n  * **two**\n+ three\n\n- four"))
    }

    @Test fun `numbered lists keep numbers and nested formatting`() {
        assertEquals("1. First\n2. Second\n  10. Tenth", render("1. **First**\n2) _Second_\n  10. Tenth"))
    }

    @Test fun `inline code is literal and surrounding emphasis is removed`() {
        assertEquals("a_b **literal** and  `tick` ", render("**`a_b **literal**`** and `` `tick` ``"))
        assertEquals("{value} and snake_case and 2 * 3", render("`{value}` and snake_case and 2 * 3"))
    }

    @Test fun `fenced code keeps content without language or fence delimiters`() {
        assertEquals(
            "Before\n\n# literal\n- not a bullet\n**code**\nAfter",
            render("Before\n\n```kotlin\n# literal\n- not a bullet\n**code**\n```\nAfter"),
        )
        assertEquals("x\n```\ny", render("~~~~\nx\n```\ny\n~~~~"))
        assertEquals("unclosed", render("```\nunclosed"))
    }

    @Test fun `links and images retain labels not destinations`() {
        assertEquals(
            "Read this and diagram",
            render("[**Read this**](https://example.invalid/a_(b)) and ![diagram](image.png)"),
        )
        assertEquals("• nested label", render("- **[nested _label_](https://example.invalid)**"))
    }

    @Test fun `HTML and bare URLs remain literal text`() {
        val text = "<b>literal</b> <a href=\"https://example.invalid\">label</a> &amp; https://example.invalid"
        assertEquals(text, render(text))
    }

    @Test fun `escapes quotes and horizontal rules are readable`() {
        assertEquals("*literal* and [label](target)", render("\\*literal\\* and \\[label](target)"))
        assertEquals("Quoted bold\n\nText", render("> **Quoted bold**\n\n***\nText"))
    }

    @Test fun `empty input whitespace and newline styles are safe`() {
        assertEquals("", render(""))
        assertEquals(" \n\n", render(" \n\n"))
        assertEquals("One\n\nTwo\n", render("One\r\n\r\nTwo\r"))
        assertEquals("literal \uE0000\uE001 and code", render("literal \uE0000\uE001 and `code`"))
    }

    private fun render(text: String): String = ExplainAnswerText.render(text)
}
