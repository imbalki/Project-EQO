// Origin: EQO TASK-077 (#20), UI recreation and approval persistence regression tests.
package ai.eqo.task

import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.ExecuteResult
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.data.models.PlanStatus
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ForegroundPlanRunTest {
    @After fun reset() {
        TaskRunSession.controller = null
        TaskRunSession.pending = null
        TaskRunSession.receipt = null
        TaskRunSession.progress.clear()
        TaskRunSession.status = PlanStatus.PENDING
        PlanApprovalSettings.setRequired(ApplicationProvider.getApplicationContext(), true)
    }

    @Test fun everyEnabledRegistryActionIsReachableThroughExecutor() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val registry =
                ai.eqo.actions.impl.AndroidActionRegistry
                    .create(
                        context,
                        ai.eqo.actions.impl
                            .PermissionRequester { false },
                    )
            val reached = mutableSetOf<String>()
            registry.enabledActionNames.forEach { name ->
                val definition =
                    requireNotNull(
                        ai.eqo.core.agent.ActionSchema
                            .getAction(name),
                    )
                val params =
                    definition.params.filter { it.required }.associate { param ->
                        param.name to
                            when (param.type) {
                                ai.eqo.core.agent.ParamType.INT -> "1"
                                ai.eqo.core.agent.ParamType.BOOLEAN -> "true"
                                ai.eqo.core.agent.ParamType.ENUM -> param.enumValues.first()
                                ai.eqo.core.agent.ParamType.STRING -> "example"
                            }
                    }
                val step = LoopStep("1", ExecutedAction(name, params, irreversible = true))
                val plan = ApprovedTaskPlan(listOf(step))
                val executor =
                    StudyActionExecutor(EqoAutomationPort({ null }, { _, _ -> false }), plan) { action, _ ->
                        reached += action
                        ai.eqo.actions.base.ActionResult
                            .Success()
                    }
                assertTrue(executor.execute(step) is ExecuteResult.Success)
            }
            assertEquals(registry.enabledActionNames, reached)
        }

    @Test fun defaultOnAndTogglePersistsAcrossInstances() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(PlanApprovalSettings.required(context))
        PlanApprovalSettings.setRequired(context, false)
        assertFalse(PlanApprovalSettings.required(ApplicationProvider.getApplicationContext()))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun destroyRecreateKeepsControllerAndRegistryRunNeedsNoStepApproval() =
        runTest {
            val steps =
                listOf(
                    LoopStep("1", ExecutedAction("OPEN_APP", mapOf("appName" to "gmail"), irreversible = true)),
                )
            val approved = ApprovedTaskPlan(steps)
            var calls = 0
            val settle = CompletableDeferred<Unit>()
            val executor =
                StudyActionExecutor(
                    EqoAutomationPort({ null }, { _, _ -> false }),
                    approved,
                ) { name, params ->
                    assertEquals("OPEN_APP", name)
                    assertEquals("gmail", params["appName"])
                    calls++
                    settle.await()
                    ai.eqo.actions.base.ActionResult
                        .Success()
                }
            val controller =
                StudyTaskController(
                    steps,
                    StudyPermissionCheck({ true }, { true }, { false }),
                    StudyApprovalGate(
                        StudyApprovalSurface { error("Per-step approval must not show") },
                        { 0L },
                        approved,
                    ),
                    executor,
                    { "" },
                )
            TaskRunSession.controller = controller
            val first = Robolectric.buildActivity(TaskActivity::class.java).setup()
            val running = async { controller.run() }
            runCurrent()
            assertEquals(1, calls)
            first.pause().stop().destroy()
            assertSame(controller, TaskRunSession.controller)
            val second = Robolectric.buildActivity(TaskActivity::class.java).setup()
            assertSame(controller, TaskRunSession.controller)
            assertFalse(running.isCompleted)
            settle.complete(Unit)
            val receipt = running.await()
            assertEquals(listOf("1"), receipt.executedStepIds)
            assertEquals(1, calls)
            assertTrue(
                executor.execute(
                    steps.single().copy(action = ExecutedAction("OPEN_APP", mapOf("appName" to "phone"))),
                ) is ExecuteResult.Failure,
            )
            second.pause().stop().destroy()
        }
}
