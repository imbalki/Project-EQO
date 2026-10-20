// Origin: EQO guarded app-search entry run wiring and per-run type-step regressions.
package ai.eqo.actions.impl

import ai.eqo.accessibility.A11yError
import ai.eqo.accessibility.A11yResult
import ai.eqo.accessibility.SearchEntryRecovery
import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.AppSearchEntry
import ai.eqo.core.agent.ExecutedAction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEntryGuardTest {
    private val spec =
        AppSearchEntry.Spec(
            entryStepId = "task-3",
            entry = "exact:Search",
            typeStepId = "task-4",
            query = "bread",
            app = "Zepto",
        )
    private val entry = ExecutedAction("CLICK_TEXT", mapOf("text" to "exact:Search"))
    private val typing =
        ExecutedAction("TYPE_TEXT", mapOf("searchText" to "search:Search", "content" to "bread"))
    private val missing = ActionResult.Failure("control_not_found")

    @Test fun queryEnteredByTheRecoverySkipsThePlansTypeStep() =
        runTest {
            var calls = 0
            val guard =
                SearchEntryGuard(spec) { _, _, _ ->
                    SearchEntryRecovery.Outcome(
                        A11yResult.success("typed into the open search field"),
                        queryEntered = true,
                    )
                }
            val result =
                guard.execute(entry, "task-3") {
                    calls++
                    missing
                }
            assertTrue(result.success)
            assertEquals(1, calls)
            val typed =
                guard.execute(typing, "task-4") {
                    calls++
                    ActionResult.Success()
                }
            assertEquals("the type step is already done", 1, calls)
            assertEquals("Typed into the open search field.", typed.data)
        }

    @Test fun entryOpenedAfterBacksLetsThePlansTypeStepRun() =
        runTest {
            val guard =
                SearchEntryGuard(spec) { _, _, _ ->
                    SearchEntryRecovery.Outcome(A11yResult.success("clicked exact:Search"), queryEntered = false)
                }
            assertTrue(guard.execute(entry, "task-3") { missing }.success)
            var calls = 0
            val result =
                guard.execute(typing, "task-4") {
                    calls++
                    ActionResult.Success()
                }
            assertTrue(result.success)
            assertEquals(1, calls)
        }

    @Test fun aMissingEntryFailsWithThePlainReason() =
        runTest {
            val guard =
                SearchEntryGuard(spec) { _, _, _ ->
                    SearchEntryRecovery.Outcome(
                        A11yResult.failure(A11yError.NodeNotFound("exact:Search")),
                        queryEntered = false,
                        missingReason = AppSearchEntry.missingReason("Zepto"),
                    )
                }
            val result = guard.execute(entry, "task-3") { missing }
            assertFalse(result.success)
            assertEquals("I could not find Search in Zepto", result.error)
        }

    @Test fun successfulEntryAndOtherFailuresNeverRecover() =
        runTest {
            var recovered = 0
            val guard =
                SearchEntryGuard(spec) { _, _, _ ->
                    recovered++
                    error("recovery must not run")
                }
            assertTrue(guard.execute(entry, "task-3") { ActionResult.Success() }.success)
            val rejected = ActionResult.Failure("Android did not accept the requested action.")
            assertEquals(rejected, guard.execute(entry, "task-3") { rejected })
            assertEquals(0, recovered)
        }

    @Test fun unguardedPlansRunEveryStepUntouched() =
        runTest {
            var recovered = 0
            val guard =
                SearchEntryGuard(null) { _, _, _ ->
                    recovered++
                    error("no spec, no recovery")
                }
            assertTrue(guard.execute(entry, "task-3") { ActionResult.Success() }.success)
            assertEquals(missing, guard.execute(entry, "task-3") { missing })
            assertEquals(missing, guard.execute(typing, "task-4") { missing })
            assertEquals(0, recovered)
        }

    @Test fun gateFailuresPropagateWithoutThePlainReason() =
        runTest {
            val guard =
                SearchEntryGuard(spec) { _, _, _ ->
                    SearchEntryRecovery.Outcome(A11yResult.failure(A11yError.SecureWindow), queryEntered = false)
                }
            val result = guard.execute(entry, "task-3") { missing }
            assertFalse(result.success)
            assertEquals("This is a protected screen. EQO will not read it.", result.error)
        }
}
