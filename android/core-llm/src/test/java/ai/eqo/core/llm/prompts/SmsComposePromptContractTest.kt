// TASK-014 (issue #19): guard tests for the prompt layer (compose-only SMS wording).
package ai.eqo.core.llm.prompts

import ai.eqo.core.agent.ActionSchema
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The planning prompt is built directly from ActionSchema, so asserting the
 * rendered prompt text keeps the model-facing contract in sync with the
 * compose-only SMS policy (TASK-014, issue #19).
 */
class SmsComposePromptContractTest {
    private fun planningPrompt(): String = PlanningPrompts.buildPlanningPrompt()

    private fun systemPrompt(): String =
        SystemPrompts.buildMainPrompt(
            registeredActions = ActionSchema.getAllActionNames(),
            memoryContext = "",
            currentDateTime = "2026-10-02T00:00:00Z",
            deviceState = "",
        )

    @Test
    fun `planning prompt describes SEND_SMS as a draft the user sends`() {
        val prompt = planningPrompt()
        assertTrue(prompt.contains("SEND_SMS only opens a pre-filled draft (the user taps Send)"))
    }

    @Test
    fun `main prompt no longer documents a direct SmsManager send`() {
        val prompt = systemPrompt()
        assertTrue("compose wording present", prompt.contains("the user reviews and taps Send"))
        assertTrue("compose implementation wording present", prompt.contains("SMS compose intent (smsto:)"))
        assertFalse("no direct-send implementation claim", prompt.contains("SmsManager + messaging app fallback"))
        assertFalse("no direct-send capability claim", prompt.contains("Sends SMS or opens SMS compose directly"))
    }
}
