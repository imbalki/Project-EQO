// Origin: EQO exact-query search regression; fake activity launcher, no browser, network or phone.
package ai.eqo.actions.impl

import ai.eqo.accessibility.EqoAutomation
import ai.eqo.accessibility.TakeoverDetector
import ai.eqo.actions.base.ActionResult
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SearchIntentTest {
    private class FakeBrowserContext : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        val started = mutableListOf<Intent>()
        var failure: RuntimeException? = null

        override fun startActivity(intent: Intent) {
            failure?.let { throw it }
            started += intent
        }
    }

    private fun registry(context: FakeBrowserContext): AndroidActionRegistry =
        AndroidActionRegistry.create(
            context,
            PermissionRequester { false },
            { EqoAutomation({ null }, { EqoAutomation.ServiceState.AVAILABLE }, TakeoverDetector()) },
            options =
                RegistryOptions().also {
                    it.informationHttp =
                        object : InformationHttp {
                            override fun online() = true

                            override suspend fun weather(location: String): String = error("Not a weather request")
                        }
                },
        )

    @Test
    fun `cats in Chrome starts one exact results URL and completes successfully`() =
        runTest {
            val context = FakeBrowserContext()
            val result = registry(context).execute("WEB_SEARCH", mapOf("query" to "cats", "browser" to "Chrome"))
            assertTrue(result.toString(), result is ActionResult.Success)
            val intent = context.started.single()
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("com.android.chrome", intent.`package`)
            assertEquals("https://www.google.com/search?q=cats", intent.dataString)
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
            assertEquals("Search opened. Results were not retrieved or verified by EQO.", result.data)
        }

    @Test
    fun `default browser receives the exact query as one parameter without autocomplete input`() =
        runTest {
            val context = FakeBrowserContext()
            val query = " cats & dogs + café #?q=other "
            val result = registry(context).execute("WEB_SEARCH", mapOf("query" to query))
            assertTrue(result.toString(), result is ActionResult.Success)
            val intent = context.started.single()
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals(null, intent.`package`)
            assertEquals("https", intent.data?.scheme)
            assertEquals("www.google.com", intent.data?.host)
            assertEquals("/search", intent.data?.path)
            assertEquals(setOf("q"), intent.data?.queryParameterNames)
            assertEquals(query, intent.data?.getQueryParameter("q"))
        }

    @Test
    fun `missing or denied browser never reports success and never falls back`() =
        runTest {
            for (failure in listOf(ActivityNotFoundException(), SecurityException())) {
                val context = FakeBrowserContext().apply { this.failure = failure }
                val result = registry(context).execute("WEB_SEARCH", mapOf("query" to "cats", "browser" to "Chrome"))
                assertFalse(result.success)
                assertTrue(result is ActionResult.Failure)
                assertTrue(context.started.isEmpty())
            }
        }
}
