/*
 * EQO (TASK-012, issue #17): security follow-up SF-1 for the donor automators —
 * while the takeover has paused the loop, no send-button click may be attempted.
 */
package ai.eqo.accessibility

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SmsAutomatorTakeoverGateTest {
    @Test
    fun `a paused takeover blocks the send flow before any click`() =
        runTest {
            val detector = TakeoverDetector()
            detector.onAgentActionStarted()
            detector.onTouch(TakeoverDetector.TouchSource.USER, nowMs = 100L)
            detector.onAgentActionFinished()
            check(detector.isPaused)

            val ops = FakeServiceActionOps().also { it.detector = detector }
            val actions =
                GatedServiceActions(
                    EqoAutomation(
                        rootProvider = { null },
                        serviceState = { EqoAutomation.ServiceState.AVAILABLE },
                        takeover = detector,
                    ),
                    ops,
                )
            val previous = SmsAutomator.actionsProvider
            try {
                SmsAutomator.actionsProvider = { actions }
                val sent = SmsAutomator.automateSend()
                assertFalse("the paused loop must not act on screen", sent)
                assertEquals("no raw action may run while paused", emptyList<String>(), ops.calls)
            } finally {
                SmsAutomator.actionsProvider = previous
            }
        }

    @Test
    fun `the send flow reaches the gated facade when the loop runs`() =
        runTest {
            val detector = TakeoverDetector()
            val ops = FakeServiceActionOps().also { it.detector = detector }
            val actions =
                GatedServiceActions(
                    EqoAutomation(
                        rootProvider = { null },
                        serviceState = { EqoAutomation.ServiceState.AVAILABLE },
                        takeover = detector,
                    ),
                    ops,
                )
            val previous = SmsAutomator.actionsProvider
            try {
                SmsAutomator.actionsProvider = { actions }
                val sent = SmsAutomator.automateSend()
                // Fake ops answer true on the first send-button id: flow succeeds via the gate.
                assertEquals(true, sent)
                assertEquals(
                    listOf("findAndClickById:Compose:Draft:Send"),
                    ops.calls,
                )
            } finally {
                SmsAutomator.actionsProvider = previous
            }
        }
}
