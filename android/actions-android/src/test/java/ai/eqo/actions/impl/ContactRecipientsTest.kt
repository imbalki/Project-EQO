// Origin: EQO contacts task t_212ea16c, fake-backed recipient routing and approval regressions.
package ai.eqo.actions.impl

import ai.eqo.accessibility.EqoAutomation
import ai.eqo.accessibility.TakeoverDetector
import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.Contact
import ai.eqo.core.agent.ContactResolution
import ai.eqo.core.agent.ContactResolver
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.TaskPlanPreview
import android.Manifest
import android.app.Application
import android.content.Intent
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
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ContactRecipientsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private var allowed = true
    private var lookups = 0
    private val requested = mutableListOf<String>()
    private var resolution: ContactResolution = ContactResolution.Found(Contact("Alice Example", PHONE))
    private val resolver =
        object : ContactResolver(context) {
            override suspend fun resolveWithDisambiguation(input: String): ContactResolution {
                if (input != "Alice") return super.resolveWithDisambiguation(input)
                lookups++
                return resolution
            }

            override suspend fun resolveEmailWithDisambiguation(input: String): ContactResolution {
                if (input != "Alice") return super.resolveEmailWithDisambiguation(input)
                lookups++
                return if (resolution is ContactResolution.Found) {
                    ContactResolution.Found(Contact("Alice Example", EMAIL, type = "Email"))
                } else {
                    resolution
                }
            }
        }

    private fun registry() =
        AndroidActionRegistry.create(
            context,
            PermissionRequester {
                requested += (it as ActionPermission.Runtime).name
                allowed
            },
            { EqoAutomation({ null }, { EqoAutomation.ServiceState.AVAILABLE }, TakeoverDetector()) },
            options = RegistryOptions().also { it.contactResolver = resolver },
        )

    private fun params(
        action: String,
        recipient: String = "Alice",
    ): Map<String, String> =
        when (action) {
            "MAKE_CALL" -> mapOf("contact" to recipient)
            "SEND_EMAIL" -> mapOf("to" to recipient, "subject" to "test", "body" to "hello")
            else -> mapOf("contact" to recipient, "message" to "hello")
        }

    private fun handler(intent: Intent) {
        shadowOf(context.packageManager).addResolveInfoForIntent(
            intent,
            ResolveInfo().apply {
                activityInfo =
                    android.content.pm.ActivityInfo().apply {
                        packageName = "test.recipient"
                        name = "ComposeActivity"
                        applicationInfo =
                            android.content.pm
                                .ApplicationInfo()
                                .apply { packageName = "test.recipient" }
                    }
            },
        )
    }

    @Test
    fun `all five actions resolve a single word name to the actual intent destination`() =
        runTest {
            handler(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$PHONE")))
            handler(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")))
            handler(Intent(Intent.ACTION_VIEW, Uri.parse("tg://msg?to=$PHONE&text=hello")))
            handler(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$PHONE")))
            val registry = registry()
            ACTIONS.forEach { action ->
                registry.execute(action, params(action))
                val launched = shadowOf(context).nextStartedActivity
                when (action) {
                    "SEND_EMAIL" -> assertEquals(EMAIL, launched.getStringArrayExtra(Intent.EXTRA_EMAIL)!!.single())
                    "SEND_SMS" -> assertEquals("smsto:$PHONE", launched.dataString)
                    "SEND_WHATSAPP" -> assertTrue(launched.dataString!!.contains("phone=$PHONE&"))
                    "SEND_TELEGRAM" -> assertEquals("tg://msg?to=$PHONE&text=hello", launched.dataString)
                    "MAKE_CALL" -> assertEquals("tel:$PHONE", launched.dataString)
                }
            }
            assertEquals(ACTIONS.size, lookups)
            assertEquals(ACTIONS.size, requested.count { it == Manifest.permission.READ_CONTACTS })
            assertNoContactLogs()
        }

    @Test
    fun `ambiguity is a names-only failure and launches nothing for every action`() =
        runTest {
            resolution =
                ContactResolution.Ambiguous(
                    "Alice",
                    listOf(Contact("Alice Example", PHONE), Contact("Alice Raj", "+15557654321")),
                )
            val registry = registry()
            ACTIONS.forEach { action ->
                val result = registry.execute(action, params(action))
                assertFalse(result.success)
                assertTrue(result.error!!.contains("Alice Example, Alice Raj"))
                assertTrue(result.error!!.contains("Nothing was done"))
                assertFalse(result.error!!.contains(PHONE))
                assertNull(shadowOf(context).nextStartedActivity)
            }
            assertNoContactLogs()
        }

    @Test
    fun `denial stops all actions before lookup or launch`() =
        runTest {
            allowed = false
            val registry = registry()
            ACTIONS.forEach { action ->
                val result = registry.execute(action, params(action))
                assertFalse(result.success)
                assertTrue(result.error!!.contains("Contacts permission is needed"))
                assertTrue(result.error!!.contains("Nothing was done"))
                assertNull(shadowOf(context).nextStartedActivity)
            }
            assertEquals(0, lookups)
        }

    @Test
    fun `missing Telegram name never falls back to an invented username`() =
        runTest {
            resolution = ContactResolution.NotFound("Alice")
            val result = registry().execute("SEND_TELEGRAM", params("SEND_TELEGRAM"))
            assertFalse(result.success)
            assertNull(shadowOf(context).nextStartedActivity)
            assertEquals(listOf(Manifest.permission.READ_CONTACTS), requested)
        }

    @Test
    fun `preview resolves before approval and execution keeps the approved destination after contacts change`() =
        runTest {
            val registry = registry()
            val steps = ACTIONS.map { LoopStep(it, ExecutedAction(it, params(it), irreversible = true)) }
            val prepared = registry.prepareRecipients(steps)
            val approved = ApprovedTaskPlan(prepared.steps)
            val preview = TaskPlanPreview.describe(approved.steps(), prepared.names)
            assertTrue(preview.contains("Alice Example"))
            assertTrue(preview.contains(PHONE))
            assertTrue(preview.contains(EMAIL))
            assertEquals(ACTIONS.size, lookups)
            assertNull(shadowOf(context).nextStartedActivity)
            resolution = ContactResolution.Found(Contact("Alice Raj", "+15557654321"))
            registry.execute(
                "SEND_WHATSAPP",
                approved
                    .steps()
                    .first { it.action.name == "SEND_WHATSAPP" }
                    .action.params,
            )
            assertTrue(shadowOf(context).nextStartedActivity.dataString!!.contains("phone=$PHONE&"))
            assertEquals(ACTIONS.size, lookups)
            assertTrue(approved.matches(prepared.steps))
            assertNoContactLogs()
        }

    @Test
    fun `literal numbers emails and explicit Telegram handles need no contacts permission`() =
        runTest {
            val steps =
                ACTIONS.map {
                    LoopStep(
                        it,
                        ExecutedAction(it, params(it, if (it == "SEND_EMAIL") EMAIL else PHONE), irreversible = true),
                    )
                } +
                    LoopStep(
                        "handle",
                        ExecutedAction("SEND_TELEGRAM", params("SEND_TELEGRAM", "@balan"), irreversible = true),
                    )
            registry().prepareRecipients(steps)
            assertTrue(requested.isEmpty())
            assertEquals(0, lookups)
        }

    @Test
    fun `preview denial and ambiguity stop without launching anything`() =
        runTest {
            val step = LoopStep("sms", ExecutedAction("SEND_SMS", params("SEND_SMS"), irreversible = true))
            allowed = false
            try {
                registry().prepareRecipients(listOf(step))
                throw AssertionError("denial must stop preview")
            } catch (failure: RecipientPreparationException) {
                assertTrue(failure.message!!.contains("Contacts permission is needed"))
            }
            allowed = true
            resolution =
                ContactResolution.Ambiguous(
                    "Alice",
                    listOf(Contact("Alice Example", PHONE), Contact("Alice Raj", "+15557654321")),
                )
            try {
                registry().prepareRecipients(listOf(step))
                throw AssertionError("ambiguity must stop preview")
            } catch (failure: RecipientPreparationException) {
                assertTrue(failure.message!!.contains("Alice Example, Alice Raj"))
                assertFalse(failure.message!!.contains(PHONE))
            }
            assertNull(shadowOf(context).nextStartedActivity)
            assertNoContactLogs()
        }

    private fun assertNoContactLogs() {
        ShadowLog.getLogs().forEach {
            assertFalse(it.msg.contains("Alice"))
            assertFalse(it.msg.contains(PHONE))
            assertFalse(it.msg.contains(EMAIL))
        }
    }

    companion object {
        private const val PHONE = "+15551234567"
        private const val EMAIL = "balan@example.test"
        private val ACTIONS = listOf("SEND_SMS", "SEND_WHATSAPP", "SEND_TELEGRAM", "MAKE_CALL", "SEND_EMAIL")
    }
}
