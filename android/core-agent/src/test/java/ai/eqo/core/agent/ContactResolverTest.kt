// Origin: EQO contacts task t_212ea16c, fake Contacts provider and privacy regressions.
package ai.eqo.core.agent

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class ContactResolverTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val provider = FakeContacts()
    private val resolver = ContactResolver(context)

    @Before
    fun setup() {
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY, provider)
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
    }

    @Test
    fun `phone and email names use the appropriate provider destination`() =
        runTest {
            provider.rows = listOf("Balan Kumar" to "+1 (555) 123-4567")
            val phone = resolver.resolveWithDisambiguation("Balan") as ContactResolution.Found
            assertEquals("Balan Kumar", phone.contact.name)
            assertEquals("+15551234567", phone.contact.phoneNumber)
            provider.rows = listOf("Balan Kumar" to "balan@example.test")
            val email = resolver.resolveEmailWithDisambiguation("Balan") as ContactResolution.Found
            assertEquals("balan@example.test", email.contact.phoneNumber)
            assertEquals(ContactsContract.CommonDataKinds.Email.CONTENT_URI, provider.lastUri)
            assertNoContactLogs()
        }

    @Test
    fun `exact name does not bypass another matching person and same name endpoints are ambiguous`() =
        runTest {
            provider.rows = listOf("Balan" to "+15551234567", "Balan Raj" to "+15557654321")
            assertTrue(resolver.resolveWithDisambiguation("Balan") is ContactResolution.Ambiguous)
            provider.rows = listOf("Balan" to "+15551234567", "Balan" to "+15557654321")
            val result = resolver.resolveWithDisambiguation("Balan") as ContactResolution.Ambiguous
            assertEquals(2, result.matches.size)
            provider.rows = listOf("Balan" to "first@example.test", "Balan" to "second@example.test")
            assertTrue(resolver.resolveEmailWithDisambiguation("Balan") is ContactResolution.Ambiguous)
            assertNoContactLogs()
        }

    @Test
    fun `different names sharing a number still require clarification`() =
        runTest {
            provider.rows = listOf("Balan Kumar" to "+15551234567", "Balan Raj" to "+15551234567")
            val result = resolver.resolveWithDisambiguation("Balan") as ContactResolution.Ambiguous
            assertEquals(2, result.matches.size)
        }

    @Test
    fun `duplicates are not extra matches and blank endpoints do not resolve`() =
        runTest {
            provider.rows = listOf("Balan" to "+15551234567", "Balan" to "+15551234567")
            assertTrue(resolver.resolveWithDisambiguation("Balan") is ContactResolution.Found)
            provider.rows = listOf("Balan" to "")
            assertTrue(resolver.resolveWithDisambiguation("Balan") is ContactResolution.NotFound)
        }

    @Test
    fun `permission denial and revocation during query are explicit`() =
        runTest {
            shadowOf(context).denyPermissions(Manifest.permission.READ_CONTACTS)
            assertEquals(ContactResolution.PermissionDenied, resolver.resolveWithDisambiguation("Balan"))
            assertEquals(ContactResolution.PermissionDenied, resolver.resolveEmailWithDisambiguation("Balan"))
            assertEquals(0, provider.queries)
            shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
            provider.denied = true
            assertEquals(ContactResolution.PermissionDenied, resolver.resolveWithDisambiguation("Balan"))
            assertEquals(ContactResolution.PermissionDenied, resolver.resolveEmailWithDisambiguation("Balan"))
            assertNoContactLogs()
        }

    @Test
    fun `literal destinations bypass the provider without permission`() =
        runTest {
            shadowOf(context).denyPermissions(Manifest.permission.READ_CONTACTS)
            assertTrue(resolver.resolveWithDisambiguation("+1 (555) 123-4567") is ContactResolution.Found)
            assertTrue(resolver.resolveEmailWithDisambiguation("balan@example.test") is ContactResolution.Found)
            assertTrue(resolver.resolveWithDisambiguation("(555) 123-4567") is ContactResolution.Found)
            assertEquals(ContactResolution.PermissionDenied, resolver.resolveWithDisambiguation("555+1234567"))
            assertEquals(0, provider.queries)
        }

    @Test
    fun `name wildcard characters are escaped instead of matching everyone`() =
        runTest {
            resolver.resolveWithDisambiguation("Balan%_")
            assertEquals("%Balan\\%\\_%", provider.lastArgument)
            assertTrue(provider.lastSelection!!.contains("ESCAPE"))
        }

    private fun assertNoContactLogs() {
        ShadowLog.getLogs().forEach {
            assertFalse(it.msg.contains("Balan"))
            assertFalse(it.msg.contains("15551234567"))
            assertFalse(it.msg.contains("balan@example.test"))
        }
    }

    private class FakeContacts : ContentProvider() {
        var rows: List<Pair<String, String>> = emptyList()
        var denied = false
        var queries = 0
        var lastUri: Uri? = null
        var lastArgument: String? = null
        var lastSelection: String? = null

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): MatrixCursor {
            queries++
            if (denied) throw SecurityException("Balan private provider error")
            lastUri = uri
            lastArgument = selectionArgs?.firstOrNull()
            lastSelection = selection
            return MatrixCursor(projection!!).apply {
                rows.forEach { (name, destination) -> addRow(arrayOf<Any>(name, destination, 2)) }
            }
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = null

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ) = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ) = 0
    }
}
