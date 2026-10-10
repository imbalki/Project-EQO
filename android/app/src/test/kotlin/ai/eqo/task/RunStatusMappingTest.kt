package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.agent.ExecuteResult
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.llm.error.LLMError
import ai.eqo.core.llm.error.LLMErrorMapper
import ai.eqo.study.FailureClass
import ai.eqo.study.RunReceipt
import ai.eqo.study.StepProgress
import ai.eqo.study.StepProgressState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException

class RunStatusMappingTest {
    @Test fun permissionInstructionsAndFailureKindsContainNoPrivateDetail() {
        assertEquals(
            "Tap Allow for Contacts.",
            RunStatusMapping.permissionInstruction("android.permission.READ_CONTACTS"),
        )
        assertEquals(
            "Tap Allow for Location.",
            RunStatusMapping.permissionInstruction("android.permission.ACCESS_FINE_LOCATION"),
        )
        assertEquals(
            "Turn on All files access for EQO, then return here.",
            RunStatusMapping.permissionInstruction(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            ),
        )
        assertEquals("not_found", RunDiagnostics.failureKind(ExecuteResult.Failure("private-name not found")))
        assertEquals(
            "needs_all_files_access",
            RunDiagnostics.failureKind(
                ExecuteResult.Failure("All files access needed for private/path"),
            ),
        )
    }

    @Test fun missingControlStopsAsNeedsYouWithPlainCopyAndFixedDiagnostics() {
        val progress = RunStatusMapping.progress(null, failed("control_not_found").copy(name = "CLICK_TEXT"), null)
        assertEquals(StepProgressState.NEEDS_YOU, progress.state)
        assertEquals(R.string.run_control_not_found, RunStatusMapping.detail(progress)?.resource)
        assertEquals("NEEDS_YOU", RunStatusMapping.terminal(RunReceipt(listOf(progress), "FAILED")))
        assertEquals("control_not_found", RunDiagnostics.code(ExecuteResult.Failure("control_not_found")))
        assertEquals("control_not_found", RunDiagnostics.failureKind(ExecuteResult.Failure("control_not_found")))
        assertTrue(RunReceipt(listOf(progress), "FAILED").executedStepIds.isEmpty())
    }

    private fun failed(
        reason: String,
        target: String = "",
    ) = StepProgress("1", "TYPE_TEXT", StepProgressState.FAILED, detail = reason, targetLabel = target)

    @Test fun typedHandoffsAreNeutralAndNeverCountAsSent() {
        val cases = listOf("SEND_SMS" to R.string.run_sms_draft, "SEND_EMAIL" to R.string.run_email_draft)
        for ((action, resource) in cases) {
            val progress =
                RunStatusMapping.progress(
                    null,
                    failed("draft opened").copy(name = action),
                    "draft opened; nothing sent",
                )
            assertEquals(StepProgressState.NEEDS_YOU, progress.state)
            assertEquals(resource, RunStatusMapping.detail(progress)?.resource)
            val receipt = RunReceipt(listOf(progress), "FAILED")
            assertEquals("NEEDS_YOU", RunStatusMapping.terminal(receipt))
            assertTrue(receipt.executedStepIds.isEmpty())
            assertTrue(receipt.notExecutedStepIds.isEmpty())
            assertEquals(listOf("1"), receipt.needsYouStepIds)
            assertEquals("FAILED", receipt.terminal)
        }
    }

    @Test fun composeSuccessMeansDraftNotDeliveryAndOrdinarySuccessStaysDone() {
        for (action in listOf("compose_sms", "compose_email")) {
            val progress =
                RunStatusMapping.progress(
                    null,
                    StepProgress("1", action, StepProgressState.DONE, detail = "draft opened; nothing was sent"),
                    null,
                )
            assertEquals(StepProgressState.NEEDS_YOU, progress.state)
            assertEquals("NEEDS_YOU", RunStatusMapping.terminal(RunReceipt(listOf(progress), "COMPLETED")))
        }
        val done = StepProgress("1", "SEND_SMS", StepProgressState.DONE, detail = "Send pressed; delivery not verified")
        assertEquals(done, RunStatusMapping.progress(null, done, null))
    }

    @Test fun needsInputDoesNotInventAnOpenedDraft() {
        val progress =
            RunStatusMapping.progress(
                null,
                failed("Pick a recipient").copy(name = "SEND_SMS"),
                "Pick a recipient",
            )
        assertEquals(StepProgressState.NEEDS_YOU, progress.state)
        assertEquals(R.string.run_user_action, RunStatusMapping.detail(progress)?.resource)
        assertEquals("Pick a recipient", RunStatusMapping.detail(progress)?.argument)
    }

    @Test fun uncertaintyFailureAndExplicitStopAreNotHiddenByHandoff() {
        val handoff = StepProgress("1", "SEND_EMAIL", StepProgressState.NEEDS_YOU)
        for (state in listOf(StepProgressState.UNKNOWN, StepProgressState.FAILED)) {
            val receipt = RunReceipt(listOf(handoff, StepProgress("2", "tap", state)), "FAILED")
            assertEquals("FAILED", RunStatusMapping.terminal(receipt))
        }
        for (terminal in listOf("STOPPED", "CANCELLED", "PAUSED")) {
            assertEquals(terminal, RunStatusMapping.terminal(RunReceipt(listOf(handoff), terminal)))
        }
        val unknown = StepProgress("1", "SEND_EMAIL", StepProgressState.UNKNOWN)
        assertEquals(unknown, RunStatusMapping.progress(null, unknown, "draft opened"))
    }

    @Test fun knownFailuresNameTheMissingAccessAppAndTarget() {
        assertEquals(R.string.run_accessibility_off, RunStatusMapping.detail(failed("a11y_disabled"))?.resource)
        assertEquals(
            R.string.run_accessibility_off,
            RunStatusMapping.detail(failed(FailureClass.A11Y_LOST.repair))?.resource,
        )
        val permissionReason = "Android permission android.permission.READ_CONTACTS is not granted to EQO."
        val permission = RunStatusMapping.detail(failed(permissionReason))
        assertEquals(R.string.run_permission_missing, permission?.resource)
        assertEquals("Contacts", permission?.argument)
        assertEquals("Phone", RunStatusMapping.permissionName("android.permission.CALL_PHONE"))
        assertEquals(
            permission,
            RunStatusMapping.detail(
                failed("permission denied: $permissionReason"),
            ),
        )
        assertEquals(
            R.string.run_accessibility_off,
            RunStatusMapping.detail(failed("permission denied: ${FailureClass.A11Y_LOST.repair}"))?.resource,
        )
        val denied =
            RunStatusMapping.detail(
                failed(
                    "Contacts access is needed to find a recipient. " +
                        "Permission was not granted; this step did not run.",
                ),
            )
        assertEquals(R.string.run_permission_explanation, denied?.resource)
        assertTrue(denied!!.argument.contains("Contacts"))
    }

    @Test fun missingAppAndFieldFailuresNameThePlannedLabel() {
        val missingApp = RunStatusMapping.detail(failed("App 'Meet' not installed."))
        assertEquals(R.string.run_app_missing, missingApp?.resource)
        assertEquals("Meet", missingApp?.argument)
        assertEquals("WhatsApp", RunStatusMapping.detail(failed("WhatsApp is not installed."))?.argument)
        val step =
            LoopStep(
                "1",
                ExecutedAction("TYPE_TEXT", mapOf("target" to "Search Keep", "text" to "private query")),
            )
        val progress = RunStatusMapping.progress(step, failed("a11y_node_not_found"), null)
        assertEquals("Search Keep", RunStatusMapping.detail(progress)?.argument)
        assertEquals(R.string.run_target_missing, RunStatusMapping.detail(progress)?.resource)
        val registryStep =
            LoopStep(
                "1",
                ExecutedAction("TYPE_TEXT", mapOf("searchText" to "Search Keep", "content" to "private query")),
            )
        val registryProgress =
            RunStatusMapping.progress(
                registryStep,
                failed("The requested screen element was not found."),
                null,
            )
        assertEquals("Search Keep", RunStatusMapping.detail(registryProgress)?.argument)
        assertEquals(R.string.run_target_missing, RunStatusMapping.detail(registryProgress)?.resource)
        assertEquals(
            R.string.run_failed_unknown,
            RunStatusMapping.detail(failed("unrecognized private error"))?.resource,
        )
    }

    @Test fun timeoutAndUnreachableAndMissingKeyStayDistinctWithoutChangingRetries() {
        val slow = LLMErrorMapper.fromThrowable("OpenRouter", "model", SocketTimeoutException("secret"))
        val unreachable = LLMErrorMapper.fromThrowable("OpenRouter", "model", ConnectException("secret"))
        assertTrue(slow.timedOut)
        assertFalse(unreachable.timedOut)
        assertEquals(LLMError.Network, slow.error)
        assertFalse(slow.retryable)
        assertTrue(unreachable.retryable)
        assertEquals(R.string.run_model_slow, RunStatusMapping.planning(slow.error, slow.timedOut))
        assertEquals(R.string.task_call_failed, RunStatusMapping.planning(unreachable.error, unreachable.timedOut))
        assertEquals(R.string.task_key_needed, RunStatusMapping.planning(LLMError.AuthMissing))
        assertEquals(R.string.model_error_auth, RunStatusMapping.planning(LLMError.AuthInvalid))
    }

    @Test fun diagnosticCodesNeverIncludeArbitraryExecutorTextEvenIfIdentifierLike() {
        for (reason in listOf("secret", "App 'private app' not installed", "draft opened", "sk-or-private")) {
            assertEquals("execution_failed", RunDiagnostics.code(ExecuteResult.Failure(reason)))
        }
        assertEquals("a11y_node_not_found", RunDiagnostics.code(ExecuteResult.Failure("a11y_node_not_found")))
        assertEquals(
            "executor_success_not_independent_receipt",
            RunDiagnostics.code(ExecuteResult.Success("private message")),
        )
    }
}
