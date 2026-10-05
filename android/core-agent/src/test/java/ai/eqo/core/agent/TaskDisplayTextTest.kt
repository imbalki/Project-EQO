package ai.eqo.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDisplayTextTest {
    @Test fun formatAndControlCharactersAreVisibleNotActive() {
        val codes =
            (0x202A..0x202E) + (0x2066..0x2069) + (0x200B..0x200F) +
                listOf(0xFEFF, 0x061C, 0x000A, 0x000D, 0x0009, 0x2028, 0x2029)
        codes.forEach { code ->
            val raw = String(Character.toChars(code))
            assertEquals("a\\u{${code.toString(16).uppercase().padStart(4, '0')}}b", TaskDisplayText.escape("a${raw}b"))
        }
        assertEquals("hello नमस्ते 😀", TaskDisplayText.escape("hello नमस्ते 😀"))
        assertEquals("\\\\u{202E}", TaskDisplayText.escape("\\u{202E}"))
    }

    @Test fun everyPreviewParameterIsEscapedAndSnapshotBytesAreUnchanged() {
        val hidden = "hello\u202E\u200F\u2066\uFEFF"
        PlanValidator.STUDY_PARAMS.forEach { (verb, keys) ->
            val params =
                keys.associateWith { key ->
                    when (key) {
                        "direction" -> "down"
                        "to" -> ""
                        else -> hidden
                    }
                }
            val step = LoopStep("1", ExecutedAction(verb, params))
            val snapshot = ApprovedTaskPlan(listOf(step))
            val preview = TaskPlanPreview.describe(snapshot.steps())
            assertFalse(verb, preview.any { Character.getType(it) == Character.FORMAT.toInt() })
            if (hidden in params.values) assertTrue(verb, preview.contains("\\u{202E}"))
            assertEquals(step, snapshot.steps().single())
            assertTrue(snapshot.permits(step))
        }
    }
}
