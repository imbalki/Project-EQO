// Origin: EQO direct browser actions; synthetic packages and labels, no real browser, network or phone.
package ai.eqo.actions.impl

import ai.eqo.accessibility.EqoAutomation
import ai.eqo.accessibility.TakeoverDetector
import ai.eqo.actions.base.ActionResult
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class BrowserActionsTest {
    private lateinit var context: Context
    private lateinit var launcher: GatedIntentLauncher
    private val online =
        object : InformationHttp {
            var connected = true

            override fun online() = connected

            override suspend fun weather(location: String): String = ""
        }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val automation = { EqoAutomation({ null }, { EqoAutomation.ServiceState.AVAILABLE }, TakeoverDetector()) }
        launcher = GatedIntentLauncher(context, automation)
        install("example.brave", "Brave")
        install("example.firefox", "Firefox")
    }

    private fun install(
        packageName: String,
        label: String,
    ) {
        val application = ApplicationInfo().apply { this.packageName = packageName }
        val activity =
            ActivityInfo().apply {
                this.packageName = packageName
                name = "$packageName.Main"
                applicationInfo = application
                nonLocalizedLabel = label
            }
        val info =
            ResolveInfo().apply {
                activityInfo = activity
                nonLocalizedLabel = label
            }
        val shadow = shadowOf(context.packageManager)
        shadow.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), info)
        listOf(Intent.CATEGORY_INFO, Intent.CATEGORY_LAUNCHER).forEach { category ->
            shadow.addResolveInfoForIntent(
                Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(packageName),
                info,
            )
        }
    }

    private suspend fun open(params: Map<String, String>): ActionResult {
        val action = SystemActions(launcher, PermissionRequester { false }, { null }, null).getActions()
        return executeRegistered(action.single { it.name == "OPEN_URL" }, params, context)
    }

    private suspend fun search(params: Map<String, String>): ActionResult {
        val action = InformationActions(launcher, PermissionRequester { false }, online) { null }.getActions()
        return executeRegistered(action.single { it.name == "WEB_SEARCH" }, params, context)
    }

    private fun started() = shadowOf(context as Application).nextStartedActivity

    @Test(timeout = 20_000)
    fun `a named browser opens the site directly in its own package`() =
        runTest(timeout = 15.seconds) {
            val result = open(mapOf("url" to "wikipedia.org", "browser" to "Brave"))
            assertTrue(result.error.orEmpty(), result.success)
            val intent = started()
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("https://wikipedia.org", intent.dataString)
            assertEquals("example.brave", intent.`package`)
        }

    @Test(timeout = 20_000)
    fun `an approved package pin is used as is and Chrome keeps its fixed package`() =
        runTest(timeout = 15.seconds) {
            val pinned = mapOf("url" to "https://example.org", "browser" to "Brave", "packageName" to "example.firefox")
            assertTrue(open(pinned).success)
            assertEquals("example.firefox", started().`package`)
            assertTrue(open(mapOf("url" to "https://example.org", "browser" to "Chrome")).success)
            assertEquals("com.android.chrome", started().`package`)
            assertTrue(open(mapOf("url" to "https://example.org")).success)
            assertNull(started().`package`)
        }

    @Test(timeout = 20_000)
    fun `an unknown browser or a package that is gone opens nothing`() =
        runTest(timeout = 15.seconds) {
            assertFalse(open(mapOf("url" to "https://example.org", "browser" to "Vivaldi")).success)
            val gone = mapOf("url" to "https://example.org", "browser" to "Brave", "packageName" to "gone.app")
            assertFalse(open(gone).success)
            assertNull(shadowOf(context as Application).nextStartedActivity)
        }

    @Test(timeout = 20_000)
    fun `bare words are searched and unsafe addresses are refused`() =
        runTest(timeout = 15.seconds) {
            assertTrue(open(mapOf("url" to "wikipedia cats", "browser" to "Firefox")).success)
            val intent = started()
            assertEquals("example.firefox", intent.`package`)
            assertEquals("https://www.google.com/search?q=wikipedia+cats", intent.dataString)
            listOf("javascript:alert(1)", "file:///sdcard/a", "intent://x#Intent;end", "https://u:p@example.org")
                .forEach { assertFalse(it, open(mapOf("url" to it, "browser" to "Brave")).success) }
            assertNull(shadowOf(context as Application).nextStartedActivity)
        }

    @Test(timeout = 20_000)
    fun `a web search in a named browser is one direct intent`() =
        runTest(timeout = 15.seconds) {
            val result = search(mapOf("query" to "cats & dogs", "browser" to "Firefox"))
            assertTrue(result is ActionResult.UserActionRequired)
            val intent = started()
            assertEquals("example.firefox", intent.`package`)
            assertEquals("cats & dogs", intent.data?.getQueryParameter("q"))
            assertEquals("www.google.com", intent.data?.host)
            // Without a browser the search keeps going to the system default.
            assertTrue(search(mapOf("query" to "cats")) is ActionResult.UserActionRequired)
            assertNull(started().`package`)
            assertFalse(search(mapOf("query" to "cats", "browser" to "Vivaldi")).success)
            online.connected = false
            assertFalse(search(mapOf("query" to "cats", "browser" to "Firefox")).success)
        }
}
