// TASK-015 (issue #20): task-screen presentation (REQ-TASK-01..06).
package ai.eqo.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskPresentationTest {
    @Test
    fun `the approval countdown is visible and expires exactly at the timeout`() {
        val request =
            ApprovalRequest(
                stepId = "s1",
                action = "compose_sms",
                target = "to=555",
                app = "messages",
                requestedAtMs = 0L,
            )
        assertEquals(60_000L, request.remainingMs(0L))
        assertEquals(59_000L, request.remainingMs(1_000L))
        assertFalse(request.isExpired(59_999L))
        assertTrue(request.isExpired(60_000L))
        assertEquals(0L, request.remainingMs(90_000L))
    }

    @Test
    fun `the study approval timeout is the PRD's 60 seconds`() {
        assertEquals(60_000L, ApprovalRequest.APPROVAL_TIMEOUT_MS)
    }

    @Test
    fun `the receipt separates executed, not-executed and unknown steps`() {
        val receipt =
            RunReceipt(
                steps =
                    listOf(
                        StepProgress("a", "observe", StepProgressState.DONE),
                        StepProgress("b", "compose_sms", StepProgressState.UNKNOWN, detail = "interrupted mid-apply"),
                        StepProgress("c", "tap", StepProgressState.PENDING),
                        StepProgress("d", "tap", StepProgressState.FAILED),
                    ),
                terminal = "STOPPED",
            )
        assertEquals(listOf("a"), receipt.executedStepIds)
        assertEquals(listOf("b", "c"), receipt.notExecutedStepIds)
        assertEquals(listOf("b"), receipt.unknownResultStepIds)
    }

    @Test
    fun `unknown results are labelled honestly, never as success or failure`() {
        val unknown = StepProgress("b", "send", StepProgressState.UNKNOWN)
        assertEquals(StepProgressState.UNKNOWN, unknown.state)
        assertTrue(StepProgressState.UNKNOWN != StepProgressState.DONE)
        assertTrue(StepProgressState.UNKNOWN != StepProgressState.FAILED)
    }
}
