/*
 * EQO (TASK-012, issue #17): security follow-up SF-4 — resume is user-initiated
 * only. No agent-reachable code path may clear a latched takeover; resume only
 * after explicit user confirmation (spec criterion 4).
 */
package ai.eqo.core.agent

import ai.eqo.accessibility.TakeoverDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TakeoverResumeUserOnlyTest {
    private fun androidRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile && File(dir, "app").isDirectory) {
                return dir
            }
            dir = dir.parentFile ?: break
        }
        error("could not locate the android/ root from ${System.getProperty("user.dir")}")
    }

    @Test
    fun `the detector has no parameterless resume`() {
        val zeroArg = TakeoverDetector::class.java.methods.any { it.name == "resume" && it.parameterCount == 0 }
        assertFalse("no unguarded resume() may exist on TakeoverDetector", zeroArg)
        val resume = TakeoverDetector::class.java.getMethod("resume", UserResumeConfirmation::class.java)
        assertEquals(UserResumeConfirmation::class.java, resume.parameterTypes.single())
    }

    @Test
    fun `a latched takeover clears only through a user confirmation token`() {
        val detector = TakeoverDetector()
        detector.onAgentActionStarted()
        assertTrue(detector.onTouch(TakeoverDetector.TouchSource.USER, nowMs = 10L))
        detector.onAgentActionFinished()
        assertTrue(detector.isPaused)

        // No recovery path may clear it.
        detector.onSelfGestureFinished(20L)
        assertTrue("recovery must not clear the latch", detector.isPaused)

        detector.resume(UserResumeConfirmation.forExplicitUserConfirmation(99L))
        assertFalse(detector.isPaused)
    }

    @Test
    fun `no production code mints a user confirmation outside the user-facing UI`() {
        val mint = Regex("""\bforExplicitUserConfirmation\b""")
        // TASK-015 (issue #20): the ONE mint site in app code is the resume button's click
        // handler in the study task screen — a real user gesture, exactly the case this
        // test's name describes. Loop, recovery, agent and service code stay banned.
        val userFacingMintSite = "app/src/main/kotlin/ai/eqo/task/TaskActivity.kt"
        val offenders = mutableListOf<String>()
        listOf(
            "app/src/main",
            "core-agent/src/main",
            "core-llm/src/main",
            "core-security/src/main",
            "platform-a11y/src/main",
        ).forEach { rel ->
            File(androidRoot(), rel)
                .walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "java") }
                .filter {
                    it.canonicalFile !=
                        File(
                            androidRoot(),
                            "core-agent/src/main/java/ai/eqo/core/agent/UserResumeConfirmation.kt",
                        ).canonicalFile
                }.filter {
                    it.canonicalFile != File(androidRoot(), userFacingMintSite).canonicalFile
                }.forEach { file ->
                    if (mint.containsMatchIn(file.readText())) {
                        offenders += file.name
                    }
                }
        }
        assertEquals(
            "SF-4: agent-reachable code must never mint a resume confirmation",
            emptyList<String>(),
            offenders,
        )
        assertTrue(
            "SF-4: the single allowlisted user-facing mint site must exist",
            File(androidRoot(), userFacingMintSite).isFile,
        )
    }

    @Test
    fun `no production code calls a parameterless resume`() {
        val offenders = mutableListOf<String>()
        listOf(
            "app/src/main",
            "core-agent/src/main",
            "core-llm/src/main",
            "core-security/src/main",
            "platform-a11y/src/main",
        ).forEach { rel ->
            File(androidRoot(), rel)
                .walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "java") }
                .forEach { file ->
                    file.readText().lines().forEachIndexed { index, line ->
                        if (Regex("""\.resume\(\s*\)""").containsMatchIn(line)) {
                            offenders += "${file.name}:${index + 1}"
                        }
                    }
                }
        }
        assertEquals(
            "SF-4: no parameterless resume() calls may exist in production code",
            emptyList<String>(),
            offenders,
        )
    }
}
