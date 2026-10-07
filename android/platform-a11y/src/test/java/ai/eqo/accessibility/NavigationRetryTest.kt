package ai.eqo.accessibility

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationRetryTest {
    @Test
    fun waitsForDelayedNodeAndStopsOnSuccess() =
        runTest {
            var attempts = 0
            val result =
                GenericAppAutomator.retryUntilSettled {
                    attempts++
                    if (attempts < 3) {
                        A11yResult.failure(A11yError.NodeNotFound("field"))
                    } else {
                        A11yResult.success("ready")
                    }
                }
            assertTrue(result.isSuccess)
            assertEquals(3, attempts)
            assertEquals(600L, testScheduler.currentTime)
        }

    @Test
    fun missingNodeHasHardAttemptAndWaitBounds() =
        runTest {
            var attempts = 0
            val result =
                GenericAppAutomator.retryUntilSettled {
                    attempts++
                    A11yResult.failure(A11yError.NodeNotFound("field"))
                }
            assertEquals(A11yError.NodeNotFound("field"), (result as A11yResult.Failure).error)
            assertEquals(18, attempts)
            assertEquals(5100L, testScheduler.currentTime)
        }

    @Test
    fun disabledTakeoverAndRejectedActionsNeverRetry() =
        runTest {
            listOf(
                A11yError.AccessibilityDisabled,
                A11yError.TakeoverDetected,
                A11yError.ActionRejected("field"),
            ).forEach { error ->
                var attempts = 0
                val result =
                    GenericAppAutomator.retryUntilSettled {
                        attempts++
                        A11yResult.failure(error)
                    }
                assertEquals(error, (result as A11yResult.Failure).error)
                assertEquals(1, attempts)
            }
            assertEquals(0L, testScheduler.currentTime)
        }
}
