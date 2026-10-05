// Origin: EQO TASK-069 (#20), call family registry-level verification regressions.
package ai.eqo.actions.impl

import ai.eqo.accessibility.EqoAutomation
import ai.eqo.accessibility.TakeoverDetector
import ai.eqo.actions.base.ActionResult
import ai.eqo.core.security.AndroidSensitiveMemoryStore
import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class CallFamilyTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val requests = mutableListOf<String>()
    private var allowed = true
    private var verified = false
    private var active = false
    private val verifier =
        object : CallFlowVerifier {
            override fun isCallInProgress(context: Context) = active

            override suspend fun awaitNewCallStarted(
                context: Context,
                wasAlreadyInProgress: Boolean,
            ) = verified && !wasAlreadyInProgress
        }

    private fun registry() =
        AndroidActionRegistry.createWithStore(
            context,
            PermissionRequester {
                requests += (it as ActionPermission.Runtime).name
                allowed
            },
            { EqoAutomation({ null }, { EqoAutomation.ServiceState.AVAILABLE }, TakeoverDetector()) },
            UnknownActionSink {},
            AndroidSensitiveMemoryStore(context),
            verifier,
        )

    @Test
    fun `phone denial stops the step before any call`() =
        runTest {
            shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY, true)
            allowed = false
            assertFalse(registry().execute("MAKE_CALL", mapOf("contact" to "+15551234567")).success)
            assertEquals(listOf(Manifest.permission.CALL_PHONE), requests)
            assertNull(shadowOf(context).nextStartedActivity)
        }

    @Test
    fun `direct call requires both runtime permissions and positive verification`() =
        runTest {
            shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY, true)
            shadowOf(context).grantPermissions(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
            verified = false
            assertFalse(registry().execute("MAKE_CALL", mapOf("contact" to "+15551234567")).success)
            val intent = shadowOf(context).nextStartedActivity
            assertEquals(Intent.ACTION_CALL, intent.action)
            assertEquals("tel:+15551234567", intent.dataString)
            assertEquals(listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE), requests)
            verified = true
            assertTrue(registry().execute("MAKE_CALL", mapOf("contact" to "+15551234567")).success)
            active = true
            assertFalse(registry().execute("MAKE_CALL", mapOf("contact" to "+15551234567")).success)
        }

    @Test
    fun `telephonyless dialer stays pending and without a dialer fails`() =
        runTest {
            shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY, false)
            shadowOf(context.packageManager).setSystemFeature("android.hardware.telephony.calling", false)
            val registry = registry()
            assertFalse(registry.execute("MAKE_CALL", mapOf("contact" to "+15551234567")).success)
            val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+15551234567"))
            shadowOf(context.packageManager).addResolveInfoForIntent(
                dial,
                ResolveInfo().apply {
                    activityInfo =
                        android.content.pm.ActivityInfo().apply {
                            packageName = "test.dialer"
                            name = "DialActivity"
                            applicationInfo =
                                android.content.pm
                                    .ApplicationInfo()
                                    .apply { packageName = "test.dialer" }
                        }
                },
            )
            assertTrue(registry.execute("MAKE_CALL", mapOf("contact" to "+15551234567")) is ActionResult.PendingUserAction)
            assertEquals(Intent.ACTION_DIAL, shadowOf(context).nextStartedActivity.action)
            assertTrue(requests.isEmpty())
        }
}
