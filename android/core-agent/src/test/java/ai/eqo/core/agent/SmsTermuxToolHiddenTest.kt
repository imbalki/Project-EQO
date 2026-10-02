// TASK-014 (issue #19): the Termux bridge stays inert (D-005).
package ai.eqo.core.agent

import ai.eqo.core.llm.prompts.PlanningPrompts
import ai.eqo.core.llm.prompts.SystemPrompts
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-005: the bundled ClosePaw Python bridge must stay inert — no shell tool is
 * exposed to the model and nothing in EQO calls Termux. These guards keep the
 * `termux_shell` tool (and any shell-execution surface) from reappearing in
 * the action schema or the model-facing prompts.
 */
class SmsTermuxToolHiddenTest {
    @Test
    fun `action schema exposes no termux or shell tool`() {
        val names = ActionSchema.getAllActionNames()
        assertTrue("schema must not be empty", names.isNotEmpty())
        val banned =
            names.filter {
                val upper = it.uppercase()
                upper.contains("TERMUX") || upper == "SHELL" || upper.endsWith("_SHELL")
            }
        assertTrue("no termux/shell action exposed: $banned", banned.isEmpty())
    }

    @Test
    fun `model-facing prompts never mention termux`() {
        val planningPrompt = PlanningPrompts.buildPlanningPrompt()
        val systemPrompt =
            SystemPrompts.buildMainPrompt(
                registeredActions = ActionSchema.getAllActionNames(),
                memoryContext = "",
                currentDateTime = "2026-10-02T00:00:00Z",
                deviceState = "",
            )
        assertFalse("planning prompt mentions termux", planningPrompt.contains("termux", ignoreCase = true))
        assertFalse("system prompt mentions termux", systemPrompt.contains("termux", ignoreCase = true))
    }
}
