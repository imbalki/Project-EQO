// Origin: EQO-authored audio-note limits/lifecycle regressions with no microphone or network.
package ai.eqo.handle

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStateTest {
    @Test
    fun `calls dictation foreign recorder silencing and takeover independently refuse`() {
        val safe = RecordingCaptureSafety(true, false, false, false, false)
        assertTrue(safe.allowed())
        assertFalse(safe.copy(normalAudioMode = false).allowed())
        assertFalse(safe.copy(dictationBusy = true).allowed())
        assertFalse(safe.copy(foreignCapture = true).allowed())
        assertFalse(safe.copy(silenced = true).allowed())
        assertFalse(safe.copy(takeover = true).allowed())
    }

    @Test
    fun `fake transcription sends whole short audio once and never touches long audio`() =
        runTest {
            var decodes = 0
            var sends = 0
            val decode: suspend () -> ByteArray = {
                decodes++
                byteArrayOf(1, 2)
            }
            val send: suspend (ByteArray) -> String = {
                sends++
                assertEquals(2, it.size)
                "synthetic transcript"
            }
            assertEquals("synthetic transcript", RecordingTranscriptionPolicy.transcribe(5000, decode, send))
            assertEquals(1, decodes)
            assertEquals(1, sends)
            try {
                RecordingTranscriptionPolicy.transcribe(90_001, decode, send)
                throw AssertionError("Long audio must be refused")
            } catch (_: IllegalArgumentException) {
                assertEquals(1, decodes)
                assertEquals(1, sends)
            }
        }

    @Test
    fun `empty zero and no limit are unlimited and fifteen is minutes`() {
        listOf("", "0", "no limit", " No Limit ").forEach {
            assertEquals(RecordingLimit(null), RecordingLimit.parse(it))
        }
        assertEquals(RecordingLimit(900_000L), RecordingLimit.parse("15"))
        listOf("-1", "1.5", "abc", "9223372036854775807", "1e3").forEach {
            assertNull(RecordingLimit.parse(it))
        }
    }

    @Test
    fun `idle starts once reaches limit and saves idempotently`() {
        val state = RecordingState(RecordingLimit(60_000L))
        assertEquals(RecordingPhase.IDLE, state.phase)
        assertTrue(state.start(true))
        assertEquals(RecordingPhase.RECORDING, state.phase)
        assertFalse(state.start(true))
        assertFalse(state.tick(59_999L))
        assertTrue(state.tick(60_000L))
        assertTrue(state.limitReached)
        state.stop(saved = true)
        state.stop(saved = false)
        assertEquals(RecordingPhase.SAVED, state.phase)
        assertFalse(state.tick(60_001L))
    }

    @Test
    fun `unlimited does not stop on silence or ninety seconds and explicit stop saves`() {
        val state = RecordingState(RecordingLimit(null))
        state.start(true)
        assertFalse(state.tick(24L * 60 * 60 * 1000))
        state.stop(saved = true)
        assertEquals(RecordingPhase.SAVED, state.phase)
        assertFalse(state.limitReached)
    }

    @Test
    fun `refusal and recorder errors are terminal failures`() {
        val refused = RecordingState(RecordingLimit(null))
        assertFalse(refused.start(false))
        assertEquals(RecordingPhase.FAILED, refused.phase)
        assertFalse(refused.start(true))
        val failed = RecordingState(RecordingLimit(null))
        failed.start(true)
        failed.stop(saved = false)
        assertEquals(RecordingPhase.FAILED, failed.phase)
    }

    @Test
    fun `elapsed never moves backwards`() {
        val state = RecordingState(RecordingLimit(null))
        state.start(true)
        state.tick(2000)
        state.tick(1000)
        assertEquals(2000L, state.elapsed)
    }

    @Test
    fun `transcription refuses long recordings rather than silently truncating or segmenting`() {
        assertFalse(RecordingTranscriptionPolicy.supported(0))
        assertTrue(RecordingTranscriptionPolicy.supported(1))
        assertTrue(RecordingTranscriptionPolicy.supported(90_000))
        assertFalse(RecordingTranscriptionPolicy.supported(90_001))
        assertFalse(RecordingTranscriptionPolicy.supported(900_000))
        assertFalse(RecordingTranscriptionPolicy.supported(Long.MAX_VALUE))
    }
}
