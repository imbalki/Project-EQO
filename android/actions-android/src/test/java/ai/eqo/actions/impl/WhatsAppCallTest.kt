// Origin: EQO WhatsApp call task, registry and gated fake-tree regressions. No real calls.
package ai.eqo.actions.impl

import ai.eqo.accessibility.A11yNode
import ai.eqo.accessibility.EqoAutomation
import ai.eqo.accessibility.TakeoverDetector
import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.Contact
import ai.eqo.core.agent.ContactResolution
import ai.eqo.core.agent.ContactResolver
import android.Manifest
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
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
class WhatsAppCallTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val phone = "+15551234567"
    private val requests = mutableListOf<String>()
    private val takeover = TakeoverDetector()
    private var available = true
    private var allowed = true
    private var protected = false
    private var root: Node? = null
    private val automation =
        EqoAutomation(
            { root },
            {
                if (available) {
                    EqoAutomation.ServiceState.AVAILABLE
                } else {
                    EqoAutomation.ServiceState.ACCESSIBILITY_DISABLED
                }
            },
            takeover,
            { protected },
        )
    private var resolution: ContactResolution = ContactResolution.Found(Contact("Alice", phone))
    private val resolver =
        object : ContactResolver(context) {
            override suspend fun resolveWithDisambiguation(input: String) = resolution
        }

    private fun registry(fakeResolver: Boolean = false): AndroidActionRegistry {
        val permissions =
            PermissionRequester {
                requests += (it as ActionPermission.Runtime).name
                allowed
            }
        if (!fakeResolver) return AndroidActionRegistry.create(context, permissions, { automation })
        val launcher = GatedIntentLauncher(context) { automation }
        return AndroidActionRegistry(
            context,
            listOf(
                CommunicationActions(
                    resolver,
                    CallFlowExecutor(AndroidCallFlowVerifier(), launcher),
                    launcher,
                ) { automation }.getActions(),
            ),
            permissions,
            UnknownActionSink {},
        )
    }

    private suspend fun call(
        video: String? = null,
        contact: String = phone,
        fakeResolver: Boolean = false,
    ): ActionResult =
        registry(fakeResolver).execute(
            "WHATSAPP_CALL",
            mapOf("contact" to contact) + (video?.let { mapOf("video" to it) } ?: emptyMap()),
        )

    @Test
    fun `voice opens targeted chat without message and presses once after settling from any state`() =
        runTest {
            for (state in listOf("other.app", "com.whatsapp.last.chat", "com.whatsapp.home")) {
                val stale = Node("Voice call", if (state == "other.app") state else "com.whatsapp")
                val voice = Node("Voice call")
                val video = Node("Video call")
                root = stale
                val job = async { call() }
                testScheduler.runCurrent()
                val intent = shadowOf(context).nextStartedActivity
                assertEquals(Intent.ACTION_VIEW, intent.action)
                assertEquals("com.whatsapp", intent.`package`)
                assertEquals("https://api.whatsapp.com/send?phone=$phone", intent.dataString)
                assertFalse(intent.dataString!!.contains("text="))
                assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
                assertEquals(0, stale.clicks)
                root = Node(children = listOf(video, voice))
                assertTrue(job.await() is ActionResult.Success)
                assertEquals(1, voice.clicks)
                assertEquals(0, stale.clicks)
                assertEquals(0, video.clicks)
            }
            assertTrue(requests.isEmpty())
        }

    @Test
    fun `voice fallback is exact Call description not Video call or text`() =
        runTest {
            val video = Node("Video call")
            val text = Node(text = "Call")
            val voice = Node("Call")
            root = Node(children = listOf(video, text, voice))
            val result = call()
            assertTrue(result.success)
            assertTrue(result.data!!.contains("voice"))
            assertEquals(1, voice.clicks)
            assertEquals(0, video.clicks)
            assertEquals(0, text.clicks)
        }

    @Test
    fun `explicit video presses video only and missing voice does not silently start video`() =
        runTest {
            val video = Node("Video call")
            val voice = Node("Voice call")
            root = Node(children = listOf(voice, video))
            assertTrue(call("true").data!!.contains("video"))
            assertEquals(1, video.clicks)
            assertEquals(0, voice.clicks)
            root = Node(children = listOf(video))
            assertTrue(call() is ActionResult.UserActionRequired)
            assertEquals(1, video.clicks)
            root = Node(children = listOf(voice))
            assertTrue(call("true") is ActionResult.UserActionRequired)
            assertEquals(0, voice.clicks)
        }

    @Test
    fun `rejected click stops without fallback or retry`() =
        runTest {
            val voice = Node("Voice call", accepts = false)
            val fallback = Node("Call")
            root = Node(children = listOf(voice, fallback))
            val result = call()
            assertFalse(result.success)
            assertEquals(1, voice.clicks)
            assertEquals(0, fallback.clicks)
        }

    @Test
    fun `wrong package and protected window cannot press call controls`() =
        runTest {
            val wrong = Node("Voice call", "other.app")
            root = wrong
            assertTrue(call() is ActionResult.UserActionRequired)
            assertEquals(0, wrong.clicks)
            val voice = Node("Voice call")
            root = voice
            protected = true
            assertFalse(call().success)
            assertEquals(0, voice.clicks)
        }

    @Test
    fun `takeover or disabled service during settle stops before any click`() =
        runTest {
            for (disable in listOf(true, false)) {
                available = true
                val voice = Node("Voice call")
                root = voice
                val job = async { call() }
                testScheduler.runCurrent()
                shadowOf(context).nextStartedActivity
                if (disable) {
                    available = false
                } else {
                    takeover.onAgentActionStarted()
                    takeover.onTouch(TakeoverDetector.TouchSource.USER, nowMs = 10000L)
                    takeover.onAgentActionFinished()
                }
                assertFalse(job.await().success)
                assertEquals(0, voice.clicks)
            }
        }

    @Test
    fun `invalid params and permission denial never open chat`() =
        runTest {
            assertTrue(NestedStepPolicy.refusalReason("WHATSAPP_CALL")!!.contains("needs its own approval"))
            assertFalse(call("not a boolean").success)
            assertFalse(call(contact = "").success)
            assertFalse(registry().execute("WHATSAPP_CALL", emptyMap()).success)
            allowed = false
            assertFalse(call(contact = "Alice").success)
            assertEquals(listOf(Manifest.permission.READ_CONTACTS), requests)
            assertNull(shadowOf(context).nextStartedActivity)
        }

    @Test
    fun `resolved name calls without sending and ambiguous or unknown names are refused plainly`() =
        runTest {
            val voice = Node("Voice call")
            root = voice
            assertTrue(call(contact = "Alice", fakeResolver = true).success)
            assertEquals("https://api.whatsapp.com/send?phone=$phone", shadowOf(context).nextStartedActivity.dataString)
            assertEquals(1, voice.clicks)
            resolution =
                ContactResolution.Ambiguous(
                    "Alice",
                    listOf(Contact("Alice A", phone), Contact("Alice B", "+155****4321")),
                )
            // Changed with the contacts-by-name work: several matches never open a picker or guess a person;
            // the call is refused with a plain message and nothing is launched.
            val ambiguous = call("true", "Alice", true)
            assertTrue(ambiguous is ActionResult.Failure)
            assertNull(shadowOf(context).nextStartedActivity)
            resolution = ContactResolution.NotFound("Nobody")
            assertTrue(call(contact = "Nobody", fakeResolver = true) is ActionResult.Failure)
            assertNull(shadowOf(context).nextStartedActivity)
        }

    private class Node(
        override val contentDescription: CharSequence? = null,
        override val packageName: CharSequence = "com.whatsapp",
        private val children: List<Node> = emptyList(),
        override val text: CharSequence? = null,
        private val accepts: Boolean = true,
    ) : A11yNode {
        var clicks = 0
        override val isPassword = false
        override val viewIdResourceName: String? = null
        override val className = "android.widget.ImageButton"
        override val isClickable = contentDescription != null || text != null
        override val isEditable = false
        override val isScrollable = false
        override val childCount get() = children.size
        override val parent: A11yNode? = null

        override fun childAt(index: Int) = children.getOrNull(index)

        override fun click(): Boolean {
            clicks++
            return accepts
        }

        override fun setText(value: CharSequence): Boolean = error("A WhatsApp call must not type or send a message")

        override fun scroll(forward: Boolean) = false
    }
}
