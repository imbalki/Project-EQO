package ai.eqo.task

import ai.eqo.R
import ai.eqo.actions.impl.LaunchableAppResolver
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.ShoppingPlans
import ai.eqo.core.agent.TaskIntent
import android.app.AlertDialog
import android.content.Intent
import android.os.Looper
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@OptIn(ExperimentalCoroutinesApi::class)
class PlanPreviewPolishTest {
    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        TaskRunSession.pending = null
        TaskRunSession.controller = null
    }

    @After fun tearDown() {
        TaskRunSession.pending = null
        TaskRunSession.controller = null
        Dispatchers.resetMain()
    }

    @Test fun plannerQuestionPrecedesPreviewResolutionPermissionsAndApproval(): Unit =
        runBlocking {
            val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
            val activity = lifecycle.get()
            val steps =
                listOf(
                    LoopStep("open", ExecutedAction("OPEN_APP", mapOf("appName" to "Uninstalled notes"))),
                    LoopStep("call", ExecutedAction("MAKE_CALL", mapOf("contact" to "Synthetic person"))),
                    LoopStep("ask", ExecutedAction("ASK_USER", mapOf("question" to "What should the note say?"))),
                )
            val pending = CoroutineScope(Dispatchers.Unconfined).async { activity.prepareAndShowPlan(steps) }
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals("What should the note say?", shadowOf(dialog).title.toString())
            assertEquals("Use this answer", dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
            assertTrue(activity.findViewById<TextView>(R.id.task_preview).text.isEmpty())
            assertNull(shadowOf(activity).lastRequestedPermission)
            assertNull(TaskRunSession.pending)
            assertNull(TaskRunSession.controller)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("Cancellation must settle before awaiting it", pending.isCompleted)
            pending.await()
            assertNull(TaskRunSession.pending)
            lifecycle.pause().stop().destroy()
        }

    @Test fun permissionPreviewContainsOnlyPermissionsForTheActualSteps() {
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
        val activity = lifecycle.get()
        val preview = TaskActivity::class.java.getDeclaredMethod("permissionPreview", List::class.java)
        preview.isAccessible = true
        val note = listOf(LoopStep("note", ExecutedAction("CREATE_NOTE", mapOf("body" to "Synthetic note"))))
        val question = listOf(LoopStep("ask", ExecutedAction("ASK_USER", mapOf("question" to "Which note?"))))
        assertEquals("", preview.invoke(activity, note))
        assertEquals("", preview.invoke(activity, question))
        val location = listOf(LoopStep("place", ExecutedAction("SHARE_LOCATION", mapOf("to" to "Sam", "via" to "sms"))))
        val access = preview.invoke(activity, location) as String
        assertTrue(access.contains("location", ignoreCase = true))
        assertFalse(access.contains("phone setting", ignoreCase = true))
        assertFalse(access.contains("camera", ignoreCase = true))
        lifecycle.pause().stop().destroy()
    }

    @Test fun questionOnlyFallbackUsesIntentSheetThenReplans() = fallbackQuestion(mixed = false, cancel = false)

    @Test fun mixedFallbackDiscardsAppChoicesAndPermissionStepsBeforeQuestion() = fallbackQuestion(true, false)

    @Test fun cancellingFallbackQuestionDoesNotBecomeBrowserFailureOrStoreChoice() = fallbackQuestion(true, true)

    @Test fun fallbackQuestionsRespectRemainingSharedBudget(): Unit =
        runBlocking {
            val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
            val inventory = listOf("com.android.chrome" to "Chrome")
            val resolver =
                LaunchableAppResolver(
                    { pkg -> if (pkg == "com.android.chrome") Intent().setPackage(pkg) else null },
                    { inventory },
                )
            for (remaining in 0..ai.eqo.core.agent.IntentPlanningSession.MAX_ROUNDS) {
                var asks = 0
                var plans = 0
                var stopped = false
                try {
                    TaskAppPlanning(lifecycle.get(), resolver).prepare(
                        "search shoes\nUser clarification 1: Previous answer",
                        listOf(LoopStep("missing", ExecutedAction("OPEN_APP", mapOf("appName" to "Absent store")))),
                        clarificationBudget = remaining,
                        ask = {
                            asks++
                            "Running shoes"
                        },
                    ) { request ->
                        plans++
                        assertTrue(request.contains("Previous answer"))
                        listOf(LoopStep("ask", ExecutedAction("ASK_USER", mapOf("question" to "Which shoes?"))))
                    }
                } catch (_: FallbackClarificationStopped) {
                    stopped = true
                }
                assertTrue(stopped)
                assertEquals(remaining, asks)
                assertEquals(remaining + 1, plans)
                assertNull(TaskRunSession.pending)
            }
            lifecycle.pause().stop().destroy()
        }

    private fun fallbackQuestion(
        mixed: Boolean,
        cancel: Boolean,
    ): Unit =
        runBlocking {
            val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
            val activity = lifecycle.get()
            val inventory =
                listOf("com.android.chrome" to "Chrome", "example.one" to "Notes One") +
                    ("example.two" to "Notes Two")
            val resolver =
                LaunchableAppResolver(
                    { pkg -> if (inventory.any { it.first == pkg }) Intent().setPackage(pkg) else null },
                    { inventory },
                )
            var calls = 0
            val pending =
                CoroutineScope(Dispatchers.Unconfined).async {
                    try {
                        TaskAppPlanning(activity, resolver).prepare(
                            "search shoes",
                            listOf(LoopStep("missing", ExecutedAction("OPEN_APP", mapOf("appName" to "Absent store")))),
                            ask = { activity.askFallbackQuestion(it, 0) },
                        ) { request ->
                            calls++
                            fallbackProposal(calls, request, mixed)
                        }
                    } catch (_: FallbackClarificationStopped) {
                        null
                    }
                }
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals("Which shoes?", shadowOf(dialog).title.toString())
            assertTrue(activity.findViewById<TextView>(R.id.task_preview).text.isEmpty())
            assertNull(shadowOf(activity).lastRequestedPermission)
            assertNull(TaskRunSession.pending)
            assertNull(TaskRunSession.controller)
            if (cancel) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            } else {
                dialog.listView.performItemClick(null, 0, 0)
            }
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(pending.isCompleted)
            val result = pending.await()
            if (cancel) {
                assertNull(result)
            } else {
                val steps = (result as MissingAppFallback.Result.Ready).steps
                assertEquals("WEB_SEARCH", steps.single().action.name)
                assertEquals("chrome", steps.single().action.params["browser"])
            }
            assertEquals(if (cancel) 1 else 2, calls)
            assertNull(TaskRunSession.pending)
            lifecycle.pause().stop().destroy()
        }

    private fun fallbackProposal(
        calls: Int,
        request: String,
        mixed: Boolean,
    ): List<LoopStep> {
        val question =
            LoopStep(
                "ask",
                ExecutedAction("ASK_USER", mapOf("question" to "Which shoes?", "options" to "Running shoes")),
            )
        val mixedSteps =
            listOf(
                LoopStep("open", ExecutedAction("OPEN_APP", mapOf("appName" to "Notes"))),
                LoopStep("call", ExecutedAction("MAKE_CALL", mapOf("contact" to "Synthetic person"))),
            )
        return if (calls == 1) {
            (if (mixed) mixedSteps else emptyList()) + question
        } else {
            assertTrue(request.contains("User clarification 1: Running shoes"))
            assertTrue(request.contains("Use OPEN_URL or WEB_SEARCH with browser=chrome"))
            listOf(LoopStep("web", ExecutedAction("WEB_SEARCH", mapOf("query" to "Running shoes"))))
        }
    }

    @Test fun shoppingPaymentSummarySurvivesAppResolutionWithoutAChooser(): Unit =
        runBlocking {
            val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
            val activity = lifecycle.get()
            val resolver =
                LaunchableAppResolver(
                    { Intent().setPackage(it) },
                    { listOf("com.zeptoconsumerapp" to "Zepto") },
                )
            val intent =
                TaskIntent(
                    "Add milk to cart",
                    "Zepto",
                    "purchase",
                    mapOf("item" to "milk", "quantity" to "2"),
                    confidence = 1.0,
                )
            val steps = ShoppingPlans.plan(intent).mapIndexed { index, action -> LoopStep("task-${index + 1}", action) }
            val prepared =
                AppPlanResolution(resolver, RememberedApps(activity)) {
                    error("An exact installed Zepto target needs no chooser")
                }.prepare(steps)
            assertFalse(prepared.cancelled)
            for (field in listOf("purchasePreview", "paymentRequestedPreview")) {
                TaskActivity::class.java.getDeclaredField(field).apply {
                    isAccessible = true
                    setBoolean(activity, true)
                }
            }
            val understood = TaskActivity::class.java.getDeclaredMethod("understoodFor", List::class.java)
            understood.isAccessible = true
            val line = understood.invoke(activity, prepared.steps) as String
            assertTrue(
                line.contains(
                    "EQO will not pay. It will open Zepto, search milk, add 2 x milk to the cart " +
                        "and stop so you can pay yourself.",
                ),
            )
            assertFalse(line.contains("cannot do the shopping"))
            assertEquals(
                ShoppingPlans.CART,
                prepared.steps
                    .last()
                    .action.params["text"],
            )
            lifecycle.pause().stop().destroy()
        }

    @Test fun understoodLineUsesFinalPlanNotAnUnfulfilledPaymentGoal() {
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
        val activity = lifecycle.get()
        TaskActivity::class.java.getDeclaredField("purchasePreview").apply {
            isAccessible = true
            setBoolean(activity, true)
        }
        val understood = TaskActivity::class.java.getDeclaredMethod("understoodFor", List::class.java)
        understood.isAccessible = true
        val steps = listOf(LoopStep("open", ExecutedAction("OPEN_APP", mapOf("appName" to "Zepto"))))
        val line = understood.invoke(activity, steps) as String
        assertTrue(line.contains("EQO will not pay; it will open \"Zepto\" only"))
        assertFalse(line.contains("pay with UPI"))
        lifecycle.pause().stop().destroy()
    }
}
