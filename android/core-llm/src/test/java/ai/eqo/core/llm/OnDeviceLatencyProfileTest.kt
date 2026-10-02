// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/test/java/com/opendroid/ai/core/llm/OnDeviceLatencyProfileTest.kt
package ai.eqo.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnDeviceLatencyProfileTest {
    @Test
    fun `profile stays unprofiled until three measurements exist`() {
        val profile = OnDeviceLatencyProfile(samplesMs = listOf(100L, 120L))

        assertNull(profile.acceptedBudgetMs())
        assertEquals(LatencyBudgetStatus.UNPROFILED, OnDeviceLatencyBudget.classify(profile, 500L).status)
    }

    @Test
    fun `accepted budget is measured p95 rather than a model size threshold`() {
        val profile = OnDeviceLatencyProfile(samplesMs = listOf(100L, 120L, 140L, 200L))

        assertEquals(200L, profile.acceptedBudgetMs())
        assertEquals(
            LatencyBudgetStatus.EXCEEDED,
            OnDeviceLatencyBudget.classify(profile, 201L).status,
        )
        assertEquals(
            LatencyBudgetStatus.WITHIN_BUDGET,
            OnDeviceLatencyBudget.classify(profile, 200L).status,
        )
    }

    @Test
    fun `profile key separates hardware and model tier`() {
        assertEquals(
            "pixel:test|LITERT_LM:0.5B",
            OnDeviceLatencyBudget.profileKey("pixel:test", "qwen-2.5-0.5b-it-litert"),
        )
    }
}
