// Origin: EQO app-role choice; synthetic labels and a provider that must never be called, no phone or network.
package ai.eqo.task

import ai.eqo.core.agent.IntentUnderstanding
import ai.eqo.core.agent.TaskIntent
import ai.eqo.core.llm.LLMProvider
import ai.eqo.core.llm.LLMRequest
import ai.eqo.core.llm.LLMResponse
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Looper
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
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
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class RoleChoiceTest {
    private val apps =
        listOf(
            "com.android.chrome" to "Chrome",
            "example.brave" to "Brave",
            "example.firefox" to "Firefox",
            "com.whatsapp" to "WhatsApp",
        )
    private val question =
        TaskIntent(
            "Use the browser",
            confidence = 0.5,
            question = "Which browser?",
            options = listOf("Chrome", "Brave", "Firefox"),
            role = "browser",
        )

    @Before fun clearMemory() {
        androidx.test.core.app.ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("eqo_remembered_apps", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private object NoModel : LLMProvider {
        override val name = "none"
        override val availableModels = emptyList<String>()

        override suspend fun complete(request: LLMRequest): LLMResponse = error("The model must not be called")

        override fun streamComplete(request: LLMRequest) = emptyFlow<String>()

        override suspend fun isAvailable() = false
    }

    private fun pick(
        lifecycle: org.robolectric.android.controller.ActivityController<Activity>,
        button: Int?,
        memory: RememberedApps,
    ): String? =
        kotlinx.coroutines.runBlocking {
            val sheet = IntentChoiceSheet(lifecycle.get()) {}
            val answer = async(start = CoroutineStart.UNDISPATCHED) { sheet.askRole(question, apps, memory) }
            shadowOf(Looper.getMainLooper()).idle()
            val choices = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals("Which browser?", shadowOf(choices).title.toString())
            assertEquals(3, choices.listView.adapter.count)
            shadowOf(choices).clickOnItem(1)
            shadowOf(Looper.getMainLooper()).idle()
            yield()
            // Nothing is stored and nothing is answered until the person says This time or Always.
            assertFalse(answer.isCompleted)
            assertTrue(memory.entries().isEmpty())
            val scope = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals("Use Brave?", shadowOf(scope).title.toString())
            assertEquals("Always", scope.getButton(AlertDialog.BUTTON_POSITIVE).text)
            assertEquals("This time", scope.getButton(AlertDialog.BUTTON_NEGATIVE).text)
            scope.getButton(button ?: AlertDialog.BUTTON_NEUTRAL).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            answer.await()
        }

    @Test fun alwaysStoresThePackageUnderTheRoleKeyAndThisTimeStoresNothing() {
        val lifecycle = Robolectric.buildActivity(Activity::class.java).setup()
        val memory = RememberedApps(lifecycle.get())
        assertEquals("Brave", pick(lifecycle, AlertDialog.BUTTON_NEGATIVE, memory))
        assertTrue(memory.entries().isEmpty())
        assertNull(memory.packageForRole("browser"))
        assertEquals("Brave", pick(lifecycle, AlertDialog.BUTTON_POSITIVE, memory))
        assertEquals("example.brave", memory.packageForRole("browser"))
        assertEquals(mapOf(RememberedApps.roleKey("browser") to "example.brave"), memory.entries())
        assertEquals("Brave", memory.labelForRole("browser", apps))
        lifecycle.pause().stop().destroy()
    }

    @Test fun cancelAtTheScopeStepStopsTheTaskAndStoresNothing() {
        val lifecycle = Robolectric.buildActivity(Activity::class.java).setup()
        val memory = RememberedApps(lifecycle.get())
        assertNull(pick(lifecycle, null, memory))
        assertTrue(memory.entries().isEmpty())
        lifecycle.pause().stop().destroy()
    }

    @Test fun aRememberedRoleSkipsTheQuestionUntilTheAppIsUninstalled() =
        runTest {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val memory = RememberedApps(activity)
            memory.rememberRole("browser", "example.firefox")
            val labels = apps.map { it.second }
            val understanding =
                IntentUnderstanding(NoModel, labels, roleMemory = { memory.labelForRole(it, apps) })
            val intent = understanding.understand("open the browser")
            assertEquals("Firefox", intent.app)
            assertFalse(intent.needsClarification())
            // Uninstalled: the same memory no longer resolves, so the question comes back.
            val without = apps.filter { it.first != "example.firefox" }
            val again =
                IntentUnderstanding(
                    NoModel,
                    without.map { it.second },
                    roleMemory = { memory.labelForRole(it, without) },
                ).understand("open the browser")
            assertTrue(again.needsClarification())
            assertEquals(listOf("Chrome", "Brave"), again.options)
            assertEquals("browser", again.role)
        }

    @Test fun aFreeTextAnswerThatIsNotOneInstalledAppIsPassedThroughWithoutAScopeQuestion() =
        runTest {
            val lifecycle = Robolectric.buildActivity(Activity::class.java).setup()
            val memory = RememberedApps(lifecycle.get())
            val sheet = IntentChoiceSheet(lifecycle.get()) {}
            val answer = async(start = CoroutineStart.UNDISPATCHED) { sheet.askRole(question, apps, memory) }
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val input = edits(dialog.window!!.decorView).single()
            input.setText("some other thing")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("some other thing", answer.await())
            assertTrue(memory.entries().isEmpty())
            lifecycle.pause().stop().destroy()
        }

    @Test fun settingsListsTheRoleEntryAndForgetsIt() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val memory = RememberedApps(activity)
        memory.rememberRole("browser", "example.brave")
        val dialog = RememberedAppsSettings.show(activity)
        val adapter = dialog.listView.adapter
        val row = adapter.getItem(0).toString()
        assertTrue(row, row.startsWith("Default browser: "))
        dialog.listView.performItemClick(null, 0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(RememberedApps(activity).entries().isEmpty())
        activity.finish()
    }

    private fun edits(view: android.view.View): List<android.widget.EditText> =
        when (view) {
            is android.widget.EditText -> listOf(view)
            is android.view.ViewGroup -> (0 until view.childCount).flatMap { edits(view.getChildAt(it)) }
            else -> emptyList()
        }
}
