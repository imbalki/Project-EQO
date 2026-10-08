// Origin: EQO missing-app fallback regressions with local fakes.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.actions.impl.LaunchableAppResolver
import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.TextView
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class MissingAppFallbackTest {
    private fun app(name: String) = LoopStep("1", ExecutedAction("OPEN_APP", mapOf("appName" to name)))

    private val searchUrl = "https://www.google.com/search?q=shoes"

    private fun web() = listOf(LoopStep("1", ExecutedAction("OPEN_URL", mapOf("url" to searchUrl))))

    @Test fun installedPlanUnchangedAndLocalCheckNeverCallsPlanner() =
        runTest {
            val first = listOf(app("Flipkart"))
            val fallback = MissingAppFallback { true }
            assertTrue(fallback.missing(first).isEmpty())
            val result = fallback.prepare("search shoes", first) { error("No network permitted") }
            assertSame(first, (result as MissingAppFallback.Result.Ready).steps)
            assertTrue(result.missing.isEmpty())
        }

    @Test fun missingAppAutomaticallyReplansOnceWithSameRequestAndNotice() =
        runTest {
            var calls = 0
            val first = listOf(app("Flipkart"), app("flipkart"))
            val result =
                MissingAppFallback { it == "Chrome" }.prepare("search shoes", first) { request ->
                    calls++
                    assertTrue(request.startsWith("search shoes\n"))
                    assertTrue(request.contains("Flipkart"))
                    assertTrue(request.contains("OPEN_URL"))
                    web()
                } as MissingAppFallback.Result.Ready
            assertEquals(1, calls)
            assertEquals(listOf("Flipkart"), result.missing)
            assertEquals(
                "chrome",
                result.steps
                    .single()
                    .action.params["browser"],
            )
            assertEquals("Flipkart", first.first().action.params["appName"])
        }

    @Test fun secondMissingPlanStopsInsteadOfAskingAgain() =
        runTest {
            var calls = 0
            val result =
                MissingAppFallback { it == "Chrome" }.prepare("search", listOf(app("Flipkart"))) {
                    calls++
                    listOf(app("Other app"))
                }
            assertTrue(result is MissingAppFallback.Result.Stopped)
            assertEquals(1, calls)
        }

    @Test fun chromeMissingStopsWithoutNetwork() =
        runTest {
            assertTrue(
                MissingAppFallback { false }.prepare("search", listOf(app("Flipkart"))) {
                    error("Must not call model")
                } is MissingAppFallback.Result.Stopped,
            )
        }

    @Test fun resolverHandlesLabelsPackagesAliasesBlankAmbiguityAndNonLaunchableApps() {
        val apps = listOf("example.notes" to "Keep Notes", "example.other" to "Other Notes")
        val resolver =
            LaunchableAppResolver(
                { pkg -> if (pkg in apps.map { it.first }) Intent().setPackage(pkg) else null },
                { apps },
            )
        assertEquals("example.notes", resolver.resolve(" keep notes ")?.`package`)
        assertEquals("example.notes", resolver.resolve("example.notes")?.`package`)
        assertEquals("example.notes", resolver.resolve("Keep")?.`package`)
        assertNull(resolver.resolve("Notes"))
        assertNull(resolver.resolve(" "))
        assertNull(resolver.resolve("Chrome"))
        assertNull(LaunchableAppResolver({ null }, { listOf("example.hidden" to "Hidden") }).resolve("Hidden"))
        assertEquals(android.provider.Settings.ACTION_SETTINGS, resolver.resolve("Settings")?.action)
    }

    @Test fun playStoreSearchHasEncodedQueryAndWebFallbackAndRunsNoTask() {
        val intents = mutableListOf<Intent>()
        val opener =
            StoreSearchOpener {
                intents += it
                if (it.data?.scheme == "market") throw ActivityNotFoundException()
            }
        assertTrue(opener.search("Example & Store"))
        assertEquals(2, intents.size)
        assertEquals("market", intents.first().data?.scheme)
        assertEquals("Example & Store", intents.last().data?.getQueryParameter("q"))
        assertEquals("play.google.com", intents.last().data?.host)
    }

    @Test fun approveUsesExactSnapshotAndCancelRunsNothing() {
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
        val plan = ApprovedTaskPlan(web())
        var accepted: ApprovedTaskPlan? = null
        val surface = PlanFallbackDialog(lifecycle.get()) { accepted = it }
        surface
            .show(plan, "Chrome search", listOf("Flipkart"))
            .getButton(AlertDialog.BUTTON_NEGATIVE)
            .performClick()
        org.robolectric.Shadows
            .shadowOf(android.os.Looper.getMainLooper())
            .idle()
        assertNull(accepted)
        surface
            .show(plan, "Chrome search", listOf("Flipkart"))
            .getButton(AlertDialog.BUTTON_POSITIVE)
            .performClick()
        org.robolectric.Shadows
            .shadowOf(android.os.Looper.getMainLooper())
            .idle()
        assertSame(plan, accepted)
        lifecycle.pause().stop().destroy()
    }

    private fun show(activity: TaskActivity) {
        val method =
            TaskActivity::class.java.getDeclaredMethod(
                "showPreparedPlan",
                ApprovedTaskPlan::class.java,
                List::class.java,
            )
        method.isAccessible = true
        method.invoke(activity, ApprovedTaskPlan(web()), listOf("Flipkart"))
    }

    @Test fun approvalDialogShowsNoticeAndInstallChoiceDoesNotStartTask() {
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
        val activity = lifecycle.get()
        show(activity)
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.findViewById<TextView>(android.R.id.message).text.startsWith("Flipkart is not installed"))
        assertEquals("Install Flipkart from Play Store", dialog.getButton(AlertDialog.BUTTON_NEUTRAL).text.toString())
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        org.robolectric.Shadows
            .shadowOf(android.os.Looper.getMainLooper())
            .idle()
        assertTrue(activity.findViewById<TextView>(R.id.task_state).text.contains("task did not run"))
        assertNull(TaskRunSession.pending)
        lifecycle.pause().stop().destroy()
    }

    @Test fun approvalOffKeepsNoticeAndSkipsApprovalDialog() {
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
        val activity = lifecycle.get()
        PlanApprovalSettings.setRequired(activity, false)
        show(activity)
        assertTrue(activity.findViewById<TextView>(R.id.task_preview).text.startsWith("Flipkart is not installed"))
        assertFalse(ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true)
        assertEquals(web(), TaskRunSession.pending?.steps())
        TaskRunSession.pending = null
        PlanApprovalSettings.setRequired(activity, true)
        lifecycle.pause().stop().destroy()
    }
}
