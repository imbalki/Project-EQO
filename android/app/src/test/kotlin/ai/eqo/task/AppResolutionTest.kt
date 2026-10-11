// Origin: EQO synthetic launcher/choice regressions; no phone, network or personal data.
package ai.eqo.task

import ai.eqo.actions.impl.AppNameMatcher
import ai.eqo.actions.impl.LaunchableAppResolver
import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.TaskPlanPreview
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
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
class AppResolutionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val inventory =
        listOf(
            "com.google.android.keep" to "Keep notes",
            "com.whatsapp" to "WhatsApp",
            "com.google.android.gm" to "Gmail",
            "com.android.chrome" to "Chrome",
            "example.calculator" to "Calculator",
        )

    @Before fun clearMemory() {
        context
            .getSharedPreferences("eqo_remembered_apps", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun resolver(apps: List<Pair<String, String>> = inventory): LaunchableAppResolver =
        LaunchableAppResolver(
            { pkg -> if (apps.any { it.first == pkg }) Intent().setPackage(pkg) else null },
            { apps },
        )

    private fun step(
        name: String,
        legacy: Boolean = false,
    ): LoopStep =
        LoopStep(
            "1",
            ExecutedAction(if (legacy) "open_app" else "OPEN_APP", mapOf((if (legacy) "app" else "appName") to name)),
        )

    @Test fun aliasesPrefixesAndTyposHaveClearWinners() {
        val matcher = resolver()
        val names =
            mapOf(
                "Keep" to "com.google.android.keep",
                "Google Keep" to "com.google.android.keep",
                "Whatsapp" to "com.whatsapp",
                "whats app" to "com.whatsapp",
                "gmail" to "com.google.android.gm",
                "google mail" to "com.google.android.gm",
                "calc" to "example.calculator",
                "chorme" to "com.android.chrome",
            )
        names.forEach { (name, pkg) -> assertEquals(name, pkg, matcher.resolve(name)?.`package`) }
        assertEquals("keep", AppNameMatcher.normalize("Google KEEP!! notes"))
        assertNull(matcher.resolve("totally unrelated unicorn"))
    }

    @Test fun aliasesRankFirstAndGoogleIsAmbiguous() {
        val apps = inventory + ("example.fake" to "Gmail")
        val matcher = resolver(apps)
        assertEquals("com.google.android.gm", matcher.candidates("gmail").first().packageName)
        val google = matcher.candidates("Google")
        assertTrue(google.size >= 2)
        assertNull(matcher.clearWinner(google))
        assertEquals(google.sortedByDescending { it.score }, google)
    }

    @Test fun resolutionPinsExactAppAndApprovalShowsLabelForBothVocabularies() =
        runBlocking {
            listOf(false, true).forEach { legacy ->
                val result =
                    AppPlanResolution(resolver(), RememberedApps(context)) { error("No choice needed") }
                        .prepare(listOf(step("Google Keep", legacy)))
                val approved = ApprovedTaskPlan(result.steps)
                assertTrue(TaskPlanPreview.describe(approved.steps()).contains("open \"Keep notes\""))
                assertEquals(
                    "com.google.android.keep",
                    result.steps
                        .single()
                        .action.params["packageName"],
                )
                val changed =
                    result.steps.single().copy(
                        action =
                            result.steps.single().action.copy(
                                params =
                                    result.steps
                                        .single()
                                        .action.params + ("packageName" to "com.android.chrome"),
                            ),
                    )
                assertFalse(approved.permits(changed))
            }
        }

    @Test fun ambiguousChoicePersistsAcrossInstancesAndCanBeDeleted() =
        runBlocking {
            var choices = 0
            val memory = RememberedApps(context)
            val result =
                AppPlanResolution(resolver(), memory) { apps ->
                    choices++
                    AppChoice.Selected(apps.first { it.packageName == "com.google.android.keep" })
                }.prepare(listOf(step("Google")))
            assertEquals(
                "com.google.android.keep",
                result.steps
                    .single()
                    .action.params["packageName"],
            )
            val restored = RememberedApps(context)
            AppPlanResolution(resolver(), restored) { error("Remembered choice should bypass chooser") }
                .prepare(listOf(step("GOOGLE")))
            assertEquals(1, choices)
            assertEquals(listOf("com.google.android.keep"), restored.entries().values.toList())
            assertTrue(restored.entries().keys.all { it.matches(Regex("[0-9a-f]{64}")) })
            restored.delete(restored.entries().keys.single())
            assertNull(RememberedApps(context).packageFor("Google"))
        }

    @Test fun memoryWinsEvenAfterInstalledLabelChangesAndStaleMemoryDoesNotRedirect() =
        runBlocking {
            val memory = RememberedApps(context)
            memory.remember("my notes", "com.google.android.keep")
            val renamed = listOf("com.google.android.keep" to "Notebook")
            val ready =
                AppPlanResolution(resolver(renamed), memory) { error("Remembered package still installed") }
                    .prepare(listOf(step("my notes")))
            assertEquals(
                "Notebook",
                ready.steps
                    .single()
                    .action.params["appName"],
            )
            val absent =
                AppPlanResolution(resolver(emptyList()), memory) { error("No candidates") }
                    .prepare(listOf(step("my notes")))
            assertEquals(setOf("my notes"), absent.rejected)
            assertNull(resolver(emptyList()).resolvePackage("com.google.android.keep"))
        }

    @Test fun weakMatchRequiresChoiceAndNoneUsesExistingFallback() =
        runBlocking {
            val matcher = resolver()
            var offered = false
            val result =
                AppPlanResolution(matcher, RememberedApps(context)) { apps ->
                    offered = true
                    assertTrue(apps.size <= 5)
                    AppChoice.None
                }.prepare(listOf(step("my calculator")))
            assertTrue(offered)
            val fallback = MissingAppFallback { it !in result.rejected && matcher.resolve(it) != null }
            val ready =
                fallback.prepare("open calculator", result.steps) {
                    listOf(LoopStep("2", ExecutedAction("OPEN_URL", mapOf("url" to "https://example.org"))))
                } as MissingAppFallback.Result.Ready
            assertEquals(listOf("my calculator"), ready.missing)
            assertEquals(
                "chrome",
                ready.steps
                    .single()
                    .action.params["browser"],
            )
        }

    @Test fun noMatchDoesNotOfferChooserAndCancelDoesNotSaveOrApprove() =
        runBlocking {
            val memory = RememberedApps(context)
            val none =
                AppPlanResolution(resolver(), memory) { error("No candidates") }
                    .prepare(listOf(step("unrelated unicorn")))
            assertEquals(setOf("unrelated unicorn"), none.rejected)
            val cancelled =
                AppPlanResolution(resolver(), memory) { AppChoice.Cancelled }
                    .prepare(listOf(step("Google")))
            assertTrue(cancelled.cancelled)
            assertTrue(cancelled.steps.isEmpty())
            assertTrue(memory.entries().isEmpty())
        }

    @Test fun realDialogShowsBoundedIconRowsAndCancelReturnsWithoutChoice() =
        runBlocking {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val choice = async(start = CoroutineStart.UNDISPATCHED) {
                AppChoiceDialog(activity).choose(resolver().candidates("Google"))
            }
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals("Which app did you mean?", shadowOf(dialog).title.toString())
            assertTrue(dialog.listView.adapter.count <= 5)
            val row = dialog.listView.adapter.getView(0, null, dialog.listView) as android.widget.TextView
            assertTrue(row.compoundDrawables[0] != null)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(AppChoice.Cancelled, choice.await())
            activity.finish()
        }
}
