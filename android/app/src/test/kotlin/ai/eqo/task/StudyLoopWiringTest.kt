// TASK-015 (issue #20): the production ActionLoop wiring uses REAL gates (no fail-open defaults).
package ai.eqo.task

import ai.eqo.core.agent.ApprovalDecision
import ai.eqo.core.agent.ExecuteResult
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.PermissionDecision
import ai.eqo.study.ApprovalOutcome
import ai.eqo.study.ApprovalRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StudyLoopWiringTest {
    private fun step(
        name: String,
        params: Map<String, String> = emptyMap(),
        permission: String? = null,
    ) = LoopStep(
        stepId = "step-$name",
        action = ExecutedAction(name = name, params = params),
        requiredPermission = permission,
    )

    private fun permissionCheck(
        granted: Boolean = true,
        a11y: Boolean = true,
        helper: Boolean = true,
    ) = StudyPermissionCheck(
        manifestPermissionGranted = { granted },
        accessibilityServiceEnabled = { a11y },
        helperBinderAlive = { helper },
    )

    @Test
    fun `a missing manifest permission is denied with a repair path, never granted by default`() {
        val decision =
            permissionCheck(granted = false).check(step("observe", permission = "android.permission.SEND_SMS"))
        assertTrue(decision is PermissionDecision.Denied)
        assertTrue((decision as PermissionDecision.Denied).reason.contains("Settings > Apps > EQO > Permissions"))
    }

    @Test
    fun `a disabled accessibility service blocks automation verbs with the A11yLost repair`() {
        val decision = permissionCheck(a11y = false).check(step("tap", params = mapOf("text" to "OK")))
        assertTrue(decision is PermissionDecision.Denied)
        assertTrue((decision as PermissionDecision.Denied).reason.contains("Accessibility"))
    }

    @Test
    fun `a dead helper binder blocks helper steps with the binder repair`() {
        val decision =
            permissionCheck(helper = false).check(step("observe", permission = StudyPermission.HELPER_BINDER))
        assertTrue(decision is PermissionDecision.Denied)
        assertTrue((decision as PermissionDecision.Denied).reason.contains("helper"))
    }

    @Test
    fun `everything granted is the only case that passes`() {
        val decision = permissionCheck().check(step("tap", params = mapOf("text" to "OK")))
        assertEquals(PermissionDecision.Granted, decision)
    }

    @Test
    fun `D-010 the app block policy carries no hard-block list but is a real consulted policy`() {
        val policy = StudyAppBlockPolicy()
        assertTrue(policy.decisionFor("com.example.bank") is ai.eqo.core.agent.AppBlockDecision.Allow)
        assertTrue(policy.decisionFor(null) is ai.eqo.core.agent.AppBlockDecision.Allow)
        val blocked = StudyAppBlockPolicy(setOf("com.example.blocked")).decisionFor("com.example.blocked")
        assertTrue(blocked is ai.eqo.core.agent.AppBlockDecision.Block)
    }

    @Test
    fun `an expired approval card is a rejection, never an approval`() =
        runBlocking {
            val gate =
                StudyApprovalGate(
                    surface = StudyApprovalSurface { ApprovalOutcome.TimedOut },
                    nowMs = { 1000L },
                )
            val decision = gate.request(step("send_sms", params = mapOf("to" to "+10000000000")))
            assertTrue(decision is ApprovalDecision.Rejected)
            assertTrue((decision as ApprovalDecision.Rejected).reason.contains("timed out"))
        }

    @Test
    fun `the approval card carries the action, the target and the app`() =
        runBlocking {
            var seen: ApprovalRequest? = null
            val gate =
                StudyApprovalGate(
                    surface =
                        StudyApprovalSurface { request ->
                            seen = request
                            ApprovalOutcome.Approved
                        },
                    nowMs = { 42L },
                )
            val decision =
                gate.request(
                    step("compose_sms", params = mapOf("to" to "555", "body" to "hi", "package" to "com.example.sms")),
                )
            assertEquals(ApprovalDecision.Approved, decision)
            assertEquals("compose_sms", seen?.action)
            assertEquals("com.example.sms", seen?.app)
            assertTrue(seen?.target?.contains("555") == true)
            assertEquals(ApprovalRequest.APPROVAL_TIMEOUT_MS, seen?.timeoutMs)
        }

    @Test
    fun `sms verbs are compose-only and say so`() =
        runBlocking {
            var opened: Pair<String, String>? = null
            val executor =
                StudyActionExecutor(
                    object : StudyAutomationPort {
                        override fun observe() = "screen"

                        override fun tap(text: String) = true

                        override fun tapById(viewId: String) = true

                        override fun typeText(text: String) = true

                        override fun scroll(direction: String) = true

                        override fun back() = true

                        override fun home() = true

                        override fun composeSmsDraft(
                            recipient: String,
                            body: String,
                        ): Boolean {
                            opened = recipient to body
                            return true
                        }
                    },
                )
            val result =
                executor.execute(step("compose_sms", params = mapOf("to" to "555", "body" to "hello")))
            assertTrue(result is ExecuteResult.Success)
            assertTrue((result as ExecuteResult.Success).detail.contains("nothing was sent"))
            assertEquals("555" to "hello", opened)
        }

    @Test
    fun `a verb with no study executor fails typed instead of faking success`() =
        runBlocking {
            val executor =
                StudyActionExecutor(
                    object : StudyAutomationPort {
                        override fun observe() = ""

                        override fun tap(text: String) = true

                        override fun tapById(viewId: String) = true

                        override fun typeText(text: String) = true

                        override fun scroll(direction: String) = true

                        override fun back() = true

                        override fun home() = true

                        override fun composeSmsDraft(
                            recipient: String,
                            body: String,
                        ) = true
                    },
                )
            val result = executor.execute(step("wire_money", params = mapOf("amount" to "10")))
            assertTrue(result is ExecuteResult.Failure)
            assertTrue((result as ExecuteResult.Failure).reason.contains("wire_money"))
        }

    @Test
    fun `the loop is wired with the real gates, not the fail-open test defaults`() {
        val controller = codeLines(File("src/main/kotlin/ai/eqo/task/StudyTaskController.kt"))
        assertFalse("no ALLOW_ALL test default may ship", controller.any { it.contains("ALLOW_ALL") })
        assertTrue(
            "the explicit block policy must be wired",
            controller.any { it.contains("StudyAppBlockPolicy()") },
        )
        assertTrue(
            "the real permission check must be wired",
            controller.any { it.contains("permissionCheck.check(step)") },
        )
        assertFalse(
            "no default-Granted permission check may ship",
            controller.any { it.contains("permissionCheck: suspend (LoopStep) -> PermissionDecision =") },
        )
    }

    @Test
    @Suppress("MaxLineLength") // Exact source allowlist expressions must stay auditable.
    fun `resume confirmations are minted only in the Run click and resume confirmation handlers`() {
        val mint = Regex("""\bforExplicitUserConfirmation\b""")
        val sites = mutableListOf<String>()
        File("src/main")
            .walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .forEach { file ->
                val matches = mint.findAll(codeLines(file).joinToString("\n")).count()
                if (matches > 0) sites += "${file.relativeTo(File("src/main")).invariantSeparatorsPath}=$matches"
            }
        val activityPath = "kotlin/ai/eqo/task/TaskActivity.kt"
        assertEquals("SF-4: exactly two UI mint sites", listOf("$activityPath=2"), sites)
        val uiCode = codeLines(File("src/main/$activityPath")).joinToString("\n")
        val factoryCall = """UserResumeConfirmation\.forExplicitUserConfirmation\(SystemClock\.elapsedRealtime\(\)\)"""
        val runHandler =
            Regex(
                """val startButton = findViewById<Button>\(R\.id\.task_start_button\)\s*""" +
                    """startButton\.setOnClickListener\s*\{\s*""" +
                    """val confirmation = $factoryCall\s*""" +
                    """TakeoverDetector\.shared\.resume\(confirmation\)\s*planRequest\(\)\s*\}""",
            )
        val resumeButton =
            Regex(
                """val resumeButton = findViewById<Button>\(R\.id\.task_resume_button\)\s*""" +
                    """protectConfirmationTouches\(resumeButton\)\s*""" +
                    """resumeButton\.setOnClickListener\s*\{ confirmResume\(\) \}""",
            )
        val resumeHandler =
            Regex(
                """private fun confirmResume\(\)\s*\{\s*""" +
                    """if \(\(TaskRunSession\.controller \?: controller\)\?\.currentState\(\) != LoopState\.PAUSED\)\s*\{\s*""" +
                    """renderControlFeedback\(TaskControlFeedback\.NOT_PAUSED\)\s*return\s*\}\s*""" +
                    """AlertDialog\s*\.Builder\(this\)\s*\.setTitle\(R\.string\.task_resume_confirm_title\)\s*""" +
                    """\.setMessage\(R\.string\.task_resume_confirm_message\)\s*""" +
                    """\.setPositiveButton\(R\.string\.task_resume_confirm_yes\)\s*\{ _, _ ->\s*""" +
                    """val confirmation = $factoryCall\s*\(TaskRunSession\.controller \?: controller\)\?\.resume\(confirmation\)\s*\}""",
            )
        assertEquals("SF-4: Run mint is inside the Run click", 1, runHandler.findAll(uiCode).count())
        assertEquals("SF-4: protected Resume click opens the confirmation", 1, resumeButton.findAll(uiCode).count())
        assertEquals("SF-4: Resume mint is inside its positive-button click", 1, resumeHandler.findAll(uiCode).count())
    }

    /** Non-comment source lines: guards check code, documentation may name things. */
    private fun codeLines(file: File): List<String> =
        file.readText().lines().filter { line ->
            val trimmed = line.trim()
            !(trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*"))
        }
}
