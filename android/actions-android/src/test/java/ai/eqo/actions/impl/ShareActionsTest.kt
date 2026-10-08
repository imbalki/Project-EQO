// Origin: imbalki/project-eqo (EQO-authored, share contact / share location actions)
package ai.eqo.actions.impl

import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.Contact
import ai.eqo.core.agent.ContactResolution
import ai.eqo.core.agent.ContactResolver
import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ShareActionsTest {
    private lateinit var context: Context
    private val sent = mutableListOf<String>()
    private val requested = mutableListOf<String>()
    private var grant = true
    private var fix: LocationFix? = LocationFix(12.9716, 77.5946)
    private var locationReads = 0
    private val book = mutableMapOf<String, ContactResolution>()

    private val resolver by lazy {
        object : ContactResolver(context) {
            override suspend fun resolveWithDisambiguation(input: String): ContactResolution =
                book[input.trim()] ?: ContactResolution.NotFound(input.trim())
        }
    }

    private val routes =
        object : ShareRoutes {
            override suspend fun whatsApp(
                phone: String,
                message: String,
            ): ActionResult {
                sent += "whatsapp|$phone|$message"
                return ActionResult.Success(mapOf("message" to "WhatsApp Send pressed; delivery is not verified."))
            }

            override suspend fun sms(
                phone: String,
                message: String,
            ): ActionResult {
                sent += "sms|$phone|$message"
                return ActionResult.UserActionRequired("SMS draft opened.")
            }

            override suspend fun email(
                to: String,
                subject: String,
                body: String,
                context: Context,
            ): ActionResult {
                sent += "email|$to|$subject|$body"
                return ActionResult.Success(mapOf("message" to "Email draft opened."))
            }
        }

    private fun found(
        name: String,
        number: String,
        source: String = "contacts",
    ) = ContactResolution.Found(Contact(name, number, source = source))

    private fun registry() =
        AndroidActionRegistry(
            context,
            listOf(
                ShareActions(
                    resolver,
                    LocationSource {
                        locationReads++
                        fix
                    },
                    routes,
                ).getActions(),
            ),
            PermissionRequester {
                requested += (it as ActionPermission.Runtime).name
                grant
            },
            UnknownActionSink {},
        )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        book["Alex"] = found("Alex Example", "+15550100")
        book["Sam"] = found("Sam Example", "+15550199")
    }

    @Test
    fun `share contact sends Name colon number through whatsapp`() =
        runTest {
            val result = registry().execute("SHARE_CONTACT", mapOf("contact" to "Alex", "to" to "Sam", "via" to "whatsapp"))
            assertTrue(result.success)
            assertEquals(listOf("whatsapp|+15550199|Alex Example: +15550100"), sent)
        }

    @Test
    fun `share contact uses the sms and email routes`() =
        runTest {
            registry().execute("SHARE_CONTACT", mapOf("contact" to "Alex", "to" to "Sam", "via" to "SMS"))
            registry().execute("SHARE_CONTACT", mapOf("contact" to "Alex", "to" to "sam@example.test", "via" to "email"))
            assertEquals(
                listOf(
                    "sms|+15550199|Alex Example: +15550100",
                    "email|sam@example.test|Contact: Alex Example|Alex Example: +15550100",
                ),
                sent,
            )
        }

    @Test
    fun `share contact with several matches fails listing names and never guesses`() =
        runTest {
            book["Alex"] =
                ContactResolution.Ambiguous(
                    "Alex",
                    listOf(Contact("Alex Rivera", "+15550111"), Contact("Alex Example", "+15550100")),
                )
            val result = registry().execute("SHARE_CONTACT", mapOf("contact" to "Alex", "to" to "Sam", "via" to "whatsapp"))
            assertFalse(result.success)
            assertTrue(result.error.orEmpty().contains("Alex Example, Alex Rivera"))
            assertFalse(result.error.orEmpty().contains("+1555"))
            assertTrue(sent.isEmpty())
        }

    @Test
    fun `share contact refuses unknown contacts, raw numbers and ambiguous recipients`() =
        runTest {
            assertFalse(registry().execute("SHARE_CONTACT", mapOf("contact" to "Nobody", "to" to "Sam", "via" to "sms")).success)
            book["+15550100"] = found("+15550100", "+15550100", source = "direct_input")
            assertFalse(registry().execute("SHARE_CONTACT", mapOf("contact" to "+15550100", "to" to "Sam", "via" to "sms")).success)
            book["Sam"] = ContactResolution.Ambiguous("Sam", listOf(Contact("Sam A", "+1"), Contact("Sam B", "+2")))
            val ambiguous = registry().execute("SHARE_CONTACT", mapOf("contact" to "Alex", "to" to "Sam", "via" to "sms"))
            assertTrue(ambiguous.error.orEmpty().contains("Sam A, Sam B"))
            assertFalse(registry().execute("SHARE_CONTACT", mapOf("contact" to "Alex", "to" to "not-an-email", "via" to "email")).success)
            assertTrue(sent.isEmpty())
        }

    @Test
    fun `share location sends the maps link and nothing else`() =
        runTest {
            val result = registry().execute("SHARE_LOCATION", mapOf("to" to "Sam", "via" to "whatsapp"))
            assertTrue(result.success)
            assertEquals(listOf("whatsapp|+15550199|My location: https://maps.google.com/?q=12.971600,77.594600"), sent)
            assertFalse(result.data.orEmpty().contains("12.97"))
        }

    @Test
    fun `share location without a fix or with an unknown recipient sends nothing`() =
        runTest {
            fix = null
            assertFalse(registry().execute("SHARE_LOCATION", mapOf("to" to "Sam", "via" to "sms")).success)
            fix = LocationFix(1.0, 2.0)
            locationReads = 0
            assertFalse(registry().execute("SHARE_LOCATION", mapOf("to" to "Nobody", "via" to "sms")).success)
            assertEquals("position is not read when the recipient is unusable", 0, locationReads)
            assertTrue(sent.isEmpty())
        }

    @Test
    fun `permissions are requested at the moment of need and a refusal stops the step`() =
        runTest {
            registry().execute("SHARE_CONTACT", mapOf("contact" to "Alex", "to" to "Sam", "via" to "whatsapp"))
            assertEquals(listOf(Manifest.permission.READ_CONTACTS), requested)
            requested.clear()
            registry().execute("SHARE_LOCATION", mapOf("to" to "Sam", "via" to "sms"))
            assertEquals(listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.ACCESS_FINE_LOCATION), requested)
            requested.clear()
            registry().execute("SHARE_LOCATION", mapOf("to" to "sam@example.test", "via" to "email"))
            assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), requested)
            requested.clear()
            sent.clear()
            grant = false
            locationReads = 0
            assertFalse(registry().execute("SHARE_LOCATION", mapOf("to" to "+15550199", "via" to "sms")).success)
            assertEquals(0, locationReads)
            assertTrue(sent.isEmpty())
        }

    @Test
    fun `invalid via is rejected before any lookup`() =
        runTest {
            assertFalse(registry().execute("SHARE_LOCATION", mapOf("to" to "Sam", "via" to "carrier-pigeon")).success)
            assertTrue(sent.isEmpty())
            assertEquals(0, locationReads)
        }

    @Test
    fun `a position never appears in its own string form`() {
        assertFalse(LocationFix(12.9716, 77.5946).toString().contains("12.97"))
    }
}
