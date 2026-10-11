// Origin: EQO app-ask policy; synthetic installed labels and fake provider, no phone, network or personal data.
package ai.eqo.core.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNameResolutionTest {
    private val phone = listOf("Keep notes", "WhatsApp", "Gmail", "Chrome", "Calculator", "Firefox", "Messages")

    @Test fun exactNormalizedAndAliasNamesResolveToTheOneInstalledLabel() {
        val expected =
            mapOf(
                "WhatsApp" to "WhatsApp",
                "whatsapp" to "WhatsApp",
                "  WHATSAPP! " to "WhatsApp",
                "whats app" to "WhatsApp",
                "Google Keep" to "Keep notes",
                "Keep notes" to "Keep notes",
                "keep" to "Keep notes",
                "Google Chrome" to "Chrome",
                "google mail" to "Gmail",
                "Gmail" to "Gmail",
            )
        expected.forEach { (typed, label) -> assertEquals(typed, label, InstalledAppResolver.resolve(typed, phone)) }
    }

    @Test fun nearTyposAndUniquePrefixesResolveButUnknownNamesDoNot() {
        val expected =
            mapOf(
                "Whatsap" to "WhatsApp",
                "watsapp" to "WhatsApp",
                "whatsaap" to "WhatsApp",
                "chorme" to "Chrome",
                "gmal" to "Gmail",
                "calc" to "Calculator",
                "calcu" to "Calculator",
                "fire" to "Firefox",
            )
        expected.forEach { (typed, label) -> assertEquals(typed, label, InstalledAppResolver.resolve(typed, phone)) }
        for (unknown in listOf("Instagram", "totally unrelated unicorn", "", "  ", "open", "x")) {
            assertNull(unknown, InstalledAppResolver.resolve(unknown, phone))
        }
    }

    @Test fun exactLabelWinsOverLongerLabelsAndTwoCloseLabelsReturnNull() {
        val both = listOf("WhatsApp", "WhatsApp Business", "Gmail")
        assertEquals("WhatsApp", InstalledAppResolver.resolve("whatsapp", both))
        assertEquals("WhatsApp Business", InstalledAppResolver.resolve("WhatsApp Business", both))
        // A typo is a prefix of both: the person must choose.
        assertNull(InstalledAppResolver.resolve("whatsap", both))
        assertNull(InstalledAppResolver.resolve("whats", both))
        // Two labels one edit away from the typed name are never picked at random.
        assertNull(InstalledAppResolver.resolve("notez", listOf("Notes One", "Notes", "Notex")))
        assertNull(InstalledAppResolver.resolve("kep", listOf("Keep", "Keel")))
    }

    @Test fun genericRolesAndPublisherClaimsNeverMatchLoosely() {
        val browsers = listOf("Chrome", "Firefox", "Brave Browser")
        assertNull(InstalledAppResolver.resolve("browser", browsers))
        assertNull(InstalledAppResolver.resolve("the browser", browsers))
        assertNull(InstalledAppResolver.resolve("mail", listOf("Gmail", "Outlook")))
        assertNull(InstalledAppResolver.resolve("music", listOf("Music Player Pro", "Musicly")))
        assertEquals("Music", InstalledAppResolver.resolve("music", listOf("Music", "Musicly")))
        // "Google X" is only an alias or a literal label, never stripped to match a look-alike.
        assertNull(InstalledAppResolver.resolve("Google Photos", listOf("Photoshop", "Photos Plus")))
        assertNull(InstalledAppResolver.resolve("Google Sheets", listOf("Sheets Pro")))
        assertEquals("Google Sheets", InstalledAppResolver.resolve("google sheets", listOf("Google Sheets", "Sheets")))
    }

    @Test fun typedOpenRequestsNeverCallTheModelWhenOneAppFits() =
        runTest {
            val empty = IntentFixtureProvider(emptyList())
            val understanding = IntentUnderstanding(empty, phone)
            val requests =
                mapOf(
                    "open Whatsap" to "WhatsApp",
                    "open watsapp" to "WhatsApp",
                    "Open WHATSAPP" to "WhatsApp",
                    "launch chorme" to "Chrome",
                    "start calc" to "Calculator",
                    "open Google Keep" to "Keep notes",
                    "open keep notes" to "Keep notes",
                    "open Google Chrome" to "Chrome",
                    "open gmail" to "Gmail",
                )
            requests.forEach { (request, label) ->
                val intent = understanding.understand(request)
                assertEquals(request, label, intent.app)
                assertEquals(request, "open_app", intent.action)
                assertEquals(request, "Open $label", intent.goal)
                assertFalse(request, intent.needsClarification())
            }
            assertTrue(empty.requests.isEmpty())
        }

    @Test fun whatsappIsNotAskedAboutWhenBusinessIsAlsoInstalled() =
        runTest {
            val both = listOf("WhatsApp", "WhatsApp Business", "Gmail")
            val empty = IntentFixtureProvider(emptyList())
            assertEquals("WhatsApp", IntentUnderstanding(empty, both).understand("open whatsapp").app)
            assertEquals("WhatsApp Business", IntentUnderstanding(empty, both).understand("open whatsapp business").app)
            assertTrue(empty.requests.isEmpty())
        }

    @Test fun ambiguousOrUnknownOpenRequestsStillReachTheModelWhichMayAsk() =
        runTest {
            val asks =
                TaskIntent(
                    "Open a browser",
                    confidence = 0.4,
                    question = "Which browser?",
                    options = listOf("Chrome", "Firefox"),
                )
            val asked = Json.encodeToString(asks)
            val both = listOf("WhatsApp", "WhatsApp Business", "Chrome", "Firefox")
            // "open the browser" never reaches the model: the role guard asks locally (see AppRolesTest).
            listOf("open whatsap", "open Instagram", "open").forEach { request ->
                val fake = IntentFixtureProvider(listOf(asked))
                val intent = IntentUnderstanding(fake, both).understand(request)
                assertEquals(request, 1, fake.requests.size)
                assertTrue(request, intent.needsClarification())
                assertEquals(listOf("Chrome", "Firefox"), intent.options)
            }
        }

    @Test fun requestsWithExtraInstructionsAreNotMistakenForAnAppName() =
        runTest {
            val note =
                TaskIntent(
                    "Create a note",
                    "Keep notes",
                    "create_note",
                    mapOf("title" to "Test"),
                    send = "no",
                    confidence = 0.9,
                )
            val fake = IntentFixtureProvider(listOf(Json.encodeToString(note)))
            val intent = IntentUnderstanding(fake, phone).understand("open keep and write a note called Test")
            assertEquals("create_note", intent.action)
            assertEquals(1, fake.requests.size)
        }

    @Test fun modelAppNearMissSnapsToTheInstalledLabelButUnknownAppsStillFail() {
        val near = Json.encodeToString(TaskIntent("Open WhatsApp", "Whatsap", "open_app", confidence = 0.9))
        assertEquals("WhatsApp", IntentUnderstanding.parse(near, phone).app)
        val alias = Json.encodeToString(TaskIntent("Open Keep", "Google Keep", "open_app", confidence = 0.9))
        assertEquals("Keep notes", IntentUnderstanding.parse(alias, phone).app)
        val exact = Json.encodeToString(TaskIntent("Open Gmail", "Gmail", "open_app", confidence = 0.9))
        assertEquals("Gmail", IntentUnderstanding.parse(exact, phone).app)
        val unknown = Json.encodeToString(TaskIntent("Open Instagram", "Instagram", "open_app", confidence = 0.9))
        assertThrows(IllegalArgumentException::class.java) { IntentUnderstanding.parse(unknown, phone) }
        val ambiguous = Json.encodeToString(TaskIntent("Open WhatsApp", "Whatsap", "open_app", confidence = 0.9))
        assertThrows(IllegalArgumentException::class.java) {
            IntentUnderstanding.parse(ambiguous, listOf("WhatsApp", "WhatsApp Business"))
        }
    }
}
