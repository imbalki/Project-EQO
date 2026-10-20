// Origin: EQO t_c25cbd9b; source guard for the legacy loop's owner-handoff boundary, no device access.
package ai.eqo.core.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OwnerHandoffWiringTest {
    @Test fun legacyHandoffSettlesThePlanWithoutContinuingOrReplanning() {
        val root =
            generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
                .first { File(it, "settings.gradle.kts").isFile }
        val source = File(root, "platform-a11y/src/main/java/ai/eqo/core/agent/AgentLoop.kt").readText()
        val branch =
            source
                .substringAfter("} else if (actionResult is ActionResult.UserActionRequired) {")
                .substringBefore("} else if (actionResult is ActionResult.UnknownAction)")
                .replace(Regex("//[^\\n]*"), "")
        assertTrue(branch.contains("if (actionResult.handoffCompleted) StepStatus.COMPLETED else StepStatus.FAILED"))
        assertTrue(branch.contains("if (actionResult.handoffCompleted) PlanStatus.COMPLETED else PlanStatus.FAILED"))
        assertTrue(branch.contains("Needs you:"))
        assertTrue(Regex("\\breturn\\b").containsMatchIn(branch))
        assertFalse(Regex("\\bcontinue\\b|reEvalEngine|executeAction").containsMatchIn(branch))
        assertTrue(source.contains("success = actionResult.success || completedHandoff != null"))
    }
}
