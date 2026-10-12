// Origin: EQO direct browser steps; synthetic launcher inventory, no phone, network or personal data.
package ai.eqo.task

import ai.eqo.actions.impl.LaunchableAppResolver
import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class BrowserStepResolutionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val inventory =
        listOf(
            "com.android.chrome" to "Chrome",
            "example.brave" to "Brave",
            "example.firefox" to "Firefox",
            "com.google.android.gm" to "Gmail",
        )

    @Before fun clearMemory() {
        context
            .getSharedPreferences("eqo_remembered_apps", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun resolver(apps: List<Pair<String, String>> = inventory) =
        LaunchableAppResolver(
            { pkg -> if (apps.any { it.first == pkg }) Intent().setPackage(pkg) else null },
            { apps },
        )

    private fun web(
        name: String,
        vararg params: Pair<String, String>,
    ) = LoopStep("task-1", ExecutedAction(name, params.toMap(), irreversible = true))

    @Test fun aNamedBrowserOfAWebStepIsPinnedBeforeApproval() =
        runBlocking {
            val steps =
                listOf(
                    web("OPEN_URL", "url" to "https://wikipedia.org", "browser" to "brave"),
                    web("WEB_SEARCH", "query" to "cats", "browser" to "Firefox"),
                )
            val prepared =
                AppPlanResolution(resolver(), RememberedApps(context)) { error("A clear name needs no question") }
                    .prepare(steps)
            assertTrue(prepared.rejected.isEmpty())
            val (site, search) = prepared.steps
            assertEquals(
                mapOf("url" to "https://wikipedia.org", "browser" to "Brave", "packageName" to "example.brave"),
                site.action.params,
            )
            assertEquals("example.firefox", search.action.params["packageName"])
            assertEquals("Firefox", search.action.params["browser"])
            assertTrue(ApprovedTaskPlan(prepared.steps).matches(prepared.steps))
        }

    @Test fun aWebStepWithoutABrowserIsUntouched() =
        runBlocking {
            val step = web("OPEN_URL", "url" to "https://wikipedia.org")
            val prepared =
                AppPlanResolution(resolver(), RememberedApps(context)) { error("Nothing to resolve") }
                    .prepare(listOf(step))
            assertEquals(listOf(step), prepared.steps)
            assertNull(AppPlanResolution.appName(step))
        }

    @Test fun aMissingBrowserUsesTheExistingChromeFallbackAndDropsOtherPins() =
        runBlocking {
            val matcher = resolver()
            val missing = web("OPEN_URL", "url" to "https://wikipedia.org", "browser" to "Vivaldi")
            val prepared =
                AppPlanResolution(matcher, RememberedApps(context)) { AppChoice.None }.prepare(listOf(missing))
            assertEquals(setOf("Vivaldi"), prepared.rejected)
            val fallback = MissingAppFallback(matcher, prepared.rejected)
            val ready =
                fallback.prepare("open wikipedia.org in Vivaldi", prepared.steps) {
                    listOf(
                        web(
                            "WEB_SEARCH",
                            "query" to "wikipedia",
                            "browser" to "Brave",
                            "packageName" to "example.brave",
                        ),
                    )
                } as MissingAppFallback.Result.Ready
            assertEquals(listOf("Vivaldi"), ready.missing)
            assertEquals(
                mapOf("query" to "wikipedia", "browser" to "chrome"),
                ready.steps
                    .single()
                    .action.params,
            )
        }
}
