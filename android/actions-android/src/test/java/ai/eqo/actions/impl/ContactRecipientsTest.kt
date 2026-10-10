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
    @Test
    @Config(sdk = [33])
    fun locationInventoryIncludesAndroidTwelveCompanionGrant() {
        val step = LoopStep("location", ExecutedAction("SHARE_LOCATION", mapOf("to" to PHONE, "via" to "whatsapp")))
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            registry().plannedRuntimePermissions(listOf(step)).map { it.name },
        )
    }

    @Test fun emailSharingRejectsSeveralRecipientsBeforeCachedDestinationReuse() =
        runTest {
            val input = "first@example.test, second@example.test"
            val steps = listOf(
                emailStep(input),
                LoopStep("location", ExecutedAction("SHARE_LOCATION", mapOf("to" to input, "via" to "email"))),
            )
            try {
                registry().prepareRecipients(steps)
                throw AssertionError("Sharing must not approve an unusable multi-recipient destination")
            } catch (refused: IllegalArgumentException) {
                assertTrue(refused.message!!.contains("one email recipient"))
            }
            assertEquals(0, lookups)
            assertTrue(requested.isEmpty())
            assertNull(shadowOf(context).nextStartedActivity)
        }

    @Test fun textAndLocationFreezeOneRecipientWithOneLookup() =
        runTest {
            val steps =
                listOf(
                    LoopStep("text", ExecutedAction("SEND_WHATSAPP", params("SEND_WHATSAPP"))),
                    LoopStep("location", ExecutedAction("SHARE_LOCATION", mapOf("to" to "Alice", "via" to "whatsapp"))),
                )
            val registry = registry()
            assertEquals(
                setOf(Manifest.permission.READ_CONTACTS, Manifest.permission.ACCESS_FINE_LOCATION),
                registry.plannedRuntimePermissions(steps).map { it.name }.toSet(),
            )
            val prepared = registry.prepareRecipients(steps)
            assertEquals(1, lookups)
            assertEquals(listOf(Manifest.permission.READ_CONTACTS), requested)
            assertEquals(
                PHONE,
                prepared.steps
                    .first()
                    .action.params["contact"],
            )
            assertEquals(
                PHONE,
                prepared.steps
                    .last()
                    .action.params["to"],
            )
            assertEquals(
                listOf(Manifest.permission.ACCESS_FINE_LOCATION),
                registry.plannedRuntimePermissions(prepared.steps).map { it.name },
            )
            assertNull(shadowOf(context).nextStartedActivity)
        }

    @Test fun emailShareNameNeedsContactsAndIsFrozenBeforeApproval() =
        runTest {
            val registry = registry()
            val step = LoopStep("location", ExecutedAction("SHARE_LOCATION", mapOf("to" to "Alice", "via" to "email")))
            assertTrue(
                registry.plannedRuntimePermissions(listOf(step)).any { it.name == Manifest.permission.READ_CONTACTS },
            )
            val prepared = registry.prepareRecipients(listOf(step))
            assertEquals(
                EMAIL,
                prepared.steps
                    .single()
                    .action.params["to"],
            )
            assertEquals(1, lookups)
            assertEquals(
                listOf(Manifest.permission.ACCESS_FINE_LOCATION),
                registry.plannedRuntimePermissions(prepared.steps).map { it.name },
            )
        }

    @Test fun typedShareRecipientDoesNotReadContactsAndDeniedAccessDoesNotExecute() =
        runTest {
            val registry = registry()
            val step = LoopStep("location", ExecutedAction("SHARE_LOCATION", mapOf("to" to PHONE, "via" to "whatsapp")))
            assertEquals(
                PHONE,
                registry
                    .prepareRecipients(listOf(step))
                    .steps
                    .single()
                    .action.params["to"],
            )
            assertTrue(requested.isEmpty())
            assertEquals(0, lookups)
            allowed = false
            val result = registry.execute(step.action.name, step.action.params)
            assertTrue(result is ai.eqo.actions.base.ActionResult.UserActionRequired)
            val refusal = result as ai.eqo.actions.base.ActionResult.UserActionRequired
            assertTrue(refusal.message.contains("This step did not run"))
            assertNull(shadowOf(context).nextStartedActivity)
        }

    @Test fun locationToTypedNumberOnlyRequestsLocationNotContacts() =
        runTest {
            registry().execute("SHARE_LOCATION", mapOf("to" to PHONE, "via" to "sms"))
            assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), requested)
            assertEquals(0, lookups)
        }

    @Test fun whatsappDraftOpensTextWithoutSendOrContactsPermission() =
        runTest {
            val result = registry().execute("SEND_WHATSAPP", params("SEND_WHATSAPP", PHONE) + ("draftOnly" to "true"))
            assertTrue(result is ai.eqo.actions.base.ActionResult.UserActionRequired)
            val draft = result as ai.eqo.actions.base.ActionResult.UserActionRequired
            assertTrue(draft.message.contains("You press Send"))
            val intent = shadowOf(context).nextStartedActivity
            assertEquals("hello", intent.data!!.getQueryParameter("text"))
            assertTrue(requested.isEmpty())
            assertEquals(0, lookups)
            assertNull(shadowOf(context).nextStartedActivity)
        }

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private var allowed = true
    private var lookups = 0
    private val emailInputs = mutableListOf<String>()
    private val emailResolutions = mutableMapOf<String, ContactResolution>()
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
                emailInputs += input
                val configured = emailResolutions[input]
                if (configured != null || input == "Alice") {
                    lookups++
                    return configured ?: if (resolution is ContactResolution.Found) {
                        ContactResolution.Found(Contact("Alice Example", EMAIL, type = "Email"))
                    } else {
                        resolution
                    }
                }
                return super.resolveEmailWithDisambiguation(input)
            }
        }

    private fun registry(options: RegistryOptions = RegistryOptions()) =
        AndroidActionRegistry.create(
            context,
            PermissionRequester {
                requested += (it as ActionPermission.Runtime).name
                allowed
            },
            { EqoAutomation({ null }, { EqoAutomation.ServiceState.AVAILABLE }, TakeoverDetector()) },
            options = options.also { it.contactResolver = resolver },
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

    private fun handler(
        intent: Intent,
        targetPackage: String = "test.recipient",
    ) {
        shadowOf(context.packageManager).addResolveInfoForIntent(
            intent,
            ResolveInfo().apply {
                activityInfo =
                    android.content.pm.ActivityInfo().apply {
                        packageName = targetPackage
                        name = "ComposeActivity"
                        applicationInfo =
                            android.content.pm
                                .ApplicationInfo()
                                .apply { packageName = targetPackage }
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
            // Per-plan caching replaces five per-action lookups with one phone and one email lookup.
            // The approved literal destinations, not repeated Contacts reads, are the safety guarantee.
            assertEquals(2, lookups)
            approved.steps().forEach { step ->
                val email = step.action.name == "SEND_EMAIL"
                assertEquals(if (email) EMAIL else PHONE, step.action.params[if (email) "to" else "contact"])
                assertEquals("Alice Example", prepared.names[step.stepId])
            }
            assertNull(shadowOf(context).nextStartedActivity)
            resolution = ContactResolution.Found(Contact("Alice Raj", "+15557654321"))
            allowed = false
            registry.execute(
                "SEND_WHATSAPP",
                approved
                    .steps()
                    .first { it.action.name == "SEND_WHATSAPP" }
                    .action.params,
            )
            assertTrue(shadowOf(context).nextStartedActivity.dataString!!.contains("phone=$PHONE&"))
            assertEquals(2, lookups)
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

    @Test
    fun `email literals split all separators without permission or resolver calls`() =
        runTest {
            allowed = false
            handler(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")))
            val input = "  $EMAIL ; bob@example.test AND carol@example.test, dave@example.test  "
            val registry = registry()
            val prepared = registry.prepareRecipients(listOf(emailStep(input)))
            registry.execute(
                "SEND_EMAIL",
                prepared.steps
                    .single()
                    .action.params,
            )
            assertEquals(
                listOf(EMAIL, "bob@example.test", "carol@example.test", "dave@example.test"),
                shadowOf(context).nextStartedActivity.getStringArrayExtra(Intent.EXTRA_EMAIL)!!.toList(),
            )
            assertTrue(requested.isEmpty())
            assertTrue(emailInputs.isEmpty())
        }

    @Test
    fun `mixed email preview freezes every address and only looks up names`() =
        runTest {
            emailResolutions["Bob"] =
                ContactResolution.Found(Contact("Bob Example", "bob@example.test", type = "Email"))
            val registry = registry()
            val prepared = registry.prepareRecipients(listOf(emailStep("Alice; $EMAIL and Bob")))
            val approved = ApprovedTaskPlan(prepared.steps)
            val preview = TaskPlanPreview.describe(approved.steps(), prepared.names)
            assertTrue(preview.contains("Alice Example"))
            assertTrue(preview.contains("Bob Example"))
            assertTrue(preview.contains("bob@example.test"))
            assertEquals(listOf("Alice", "Bob"), emailInputs)
            assertEquals(listOf(Manifest.permission.READ_CONTACTS), requested)
            resolution = ContactResolution.NotFound("Alice")
            emailResolutions["Bob"] = ContactResolution.NotFound("Bob")
            allowed = false
            handler(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")))
            registry.execute(
                "SEND_EMAIL",
                approved
                    .steps()
                    .single()
                    .action.params,
            )
            assertEquals(
                listOf(EMAIL, EMAIL, "bob@example.test"),
                shadowOf(context).nextStartedActivity.getStringArrayExtra(Intent.EXTRA_EMAIL)!!.toList(),
            )
            assertEquals(listOf("Alice", "Bob"), emailInputs)
            assertEquals(listOf(Manifest.permission.READ_CONTACTS), requested)
            assertTrue(approved.matches(prepared.steps))
        }

    @Test
    fun `email missing ambiguous or addressless later recipient fails atomically by name`() =
        runTest {
            val failures =
                listOf(
                    ContactResolution.NotFound("Bob"),
                    ContactResolution.Ambiguous("Bob", listOf(Contact("Bob One", EMAIL), Contact("Bob Two", EMAIL))),
                    ContactResolution.Found(Contact("Bob", "", type = "Email")),
                )
            failures.forEach { failure ->
                emailResolutions["Bob"] = failure
                val registry = registry()
                val result = registry.execute("SEND_EMAIL", params("SEND_EMAIL", "Alice and Bob"))
                assertFalse(result.success)
                assertTrue(result.error!!.contains("Email recipient 'Bob'"))
                try {
                    registry.prepareRecipients(listOf(emailStep("Alice, Bob")))
                    throw AssertionError("a failed recipient must stop preview")
                } catch (error: RecipientPreparationException) {
                    assertTrue(error.message!!.contains("Email recipient 'Bob'"))
                }
                assertNull(shadowOf(context).nextStartedActivity)
            }
        }

    @Test
    fun `mixed email denial stops preview and execution before any lookup`() =
        runTest {
            allowed = false
            val registry = registry()
            val input = "$EMAIL; Alice"
            assertFalse(registry.execute("SEND_EMAIL", params("SEND_EMAIL", input)).success)
            try {
                registry.prepareRecipients(listOf(emailStep(input)))
                throw AssertionError("denial must stop preview")
            } catch (error: RecipientPreparationException) {
                assertTrue(error.message!!.contains("Contacts permission is needed"))
            }
            assertTrue(emailInputs.isEmpty())
            assertNull(shadowOf(context).nextStartedActivity)
        }

    @Test
    fun `attachment email intent carries separate recipients`() =
        runTest {
            val root = java.io.File(context.cacheDir, "email-storage").apply { mkdirs() }
            java.io.File(root, "note.txt").writeText("hello")
            val options =
                RegistryOptions().also {
                    it.storageRoot = root
                    it.allFilesAccess = { true }
                    it.shareUri = { file -> Uri.parse("content://test/${file.name}") }
                }
            handler(
                Intent(Intent.ACTION_SEND).setType("text/plain").setPackage("com.google.android.gm"),
                "com.google.android.gm",
            )
            registry(options).execute(
                "SEND_EMAIL",
                params("SEND_EMAIL", "Alice; bob@example.test") + ("attachment" to "note.txt"),
            )
            val intent = shadowOf(context).nextStartedActivity
            assertEquals(listOf(EMAIL, "bob@example.test"), intent.getStringArrayExtra(Intent.EXTRA_EMAIL)!!.toList())
            assertEquals("com.google.android.gm", intent.`package`)
            assertEquals(listOf("Alice"), emailInputs)
        }

    @Test
    fun `email names reach fake composer as a complete ordered recipient list`() =
        runTest {
            emailResolutions["Bob"] =
                ContactResolution.Found(Contact("Bob Example", "bob@example.test", type = "Email"))
            val composed = mutableListOf<List<String>>()
            val action =
                CommunicationActions.SendEmailAction(
                    EmailComposer { _, to, subject, body ->
                        composed += listOf(to, subject, body)
                        EmailComposeOutcome.VERIFIED_SENT
                    },
                    resolver,
                )
            val registry =
                AndroidActionRegistry(
                    context,
                    listOf(listOf(action)),
                    PermissionRequester { true },
                    UnknownActionSink {},
                    resolver,
                )
            assertTrue(registry.execute("SEND_EMAIL", params("SEND_EMAIL", "Alice and Bob")).success)
            assertEquals(listOf(listOf("$EMAIL, bob@example.test", "test", "hello")), composed)
            assertEquals(listOf("Alice", "Bob"), emailInputs)
        }

    @Test
    fun `separator only email recipients fail before lookup or launch`() =
        runTest {
            val registry = registry()
            assertFalse(registry.execute("SEND_EMAIL", params("SEND_EMAIL", " ; , ")).success)
            try {
                registry.prepareRecipients(listOf(emailStep(" ; , ")))
                throw AssertionError("empty recipients must stop preview")
            } catch (error: RecipientPreparationException) {
                assertTrue(error.message!!.contains("At least one email recipient"))
            }
            assertTrue(emailInputs.isEmpty())
            assertTrue(requested.isEmpty())
            assertNull(shadowOf(context).nextStartedActivity)
        }

    private fun emailStep(input: String) =
        LoopStep(
            "email",
            ExecutedAction("SEND_EMAIL", params("SEND_EMAIL", input), irreversible = true),
        )

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
