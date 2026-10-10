// Origin: EQO-authored synthetic PCM regressions; no microphone or network.
package ai.eqo.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceCapturePolicyTest {
    private fun second(amplitude: Short = 0): ByteArray =
        ByteBuffer
            .allocate(32000)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                repeat(16000) { putShort(amplitude) }
            }.array()

    private fun feed(
        policy: VoiceCapturePolicy,
        seconds: Int,
        amplitude: Short = 0,
    ) {
        val pcm = second(amplitude)
        repeat(seconds) { policy.accept(pcm, pcm.size) }
    }

    @Test
    fun initialSilenceStopsAtSixSecondsNotBefore() {
        val policy = VoiceCapturePolicy()
        feed(policy, 5)
        assertFalse(policy.shouldStop)
        feed(policy, 1)
        assertTrue(policy.shouldStop)
        assertEquals(6000L, policy.elapsedMillis)
        assertEquals(0, policy.level)
    }

    @Test
    fun pausesShorterThanSixSecondsContinueAndSpeechResetsSilence() {
        val policy = VoiceCapturePolicy()
        feed(policy, 1, 3000)
        feed(policy, 5)
        assertFalse(policy.shouldStop)
        feed(policy, 1, 3000)
        feed(policy, 5)
        assertFalse(policy.shouldStop)
        feed(policy, 1)
        assertTrue(policy.shouldStop)
    }

    @Test
    fun continuedSpeechStopsAtNinetySeconds() {
        val policy = VoiceCapturePolicy()
        feed(policy, 89, 1000)
        assertFalse(policy.shouldStop)
        feed(policy, 1, 1000)
        assertTrue(policy.shouldStop)
        assertEquals(90000L, policy.elapsedMillis)
    }

    @Test
    fun negativeFullScaleHasBoundedLevelAndCountsAsSpeech() {
        val policy = VoiceCapturePolicy()
        feed(policy, 6, Short.MIN_VALUE)
        assertFalse(policy.shouldStop)
        assertEquals(100, policy.level)
    }

    @Test
    fun quietNoiseStillStopsAndSubsecondFramesAreAccumulated() {
        val policy = VoiceCapturePolicy()
        val frame = byteArrayOf(1, 0)
        repeat(95999) { policy.accept(frame, frame.size) }
        assertFalse(policy.shouldStop)
        policy.accept(frame, frame.size)
        assertTrue(policy.shouldStop)
        assertEquals(6000L, policy.elapsedMillis)
    }

    @Test
    fun onlyValidReadLengthIsMeasured() {
        val policy = VoiceCapturePolicy()
        val pcm = second(2000)
        policy.accept(pcm, 0)
        assertEquals(0L, policy.elapsedMillis)
        assertThrows(IllegalArgumentException::class.java) { policy.accept(pcm, 1) }
        assertThrows(IllegalArgumentException::class.java) { policy.accept(pcm, pcm.size + 2) }
    }
}
