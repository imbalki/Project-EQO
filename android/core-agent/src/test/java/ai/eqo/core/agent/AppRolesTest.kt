// Origin: EQO app-role policy; synthetic installed labels and fake provider, no phone, network or personal data.
package ai.eqo.core.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRolesTest {
    private val phone =
        listOf("Chrome", "Brave", "Firefox", "Opera", "HeyTap Browser", "WhatsApp", "Gmail", "Calculator")
    private val bravePick =
        Json.encodeToString(TaskIntent("Open Brave", "Brave", "open_app", confidence = 0.95))

    private fun request(fake: IntentFixtureProvider): String =
        fake.requests
            .single()
            .messages
            .single()
            .text

    @Test fun genericBrowserRequestsAskInsteadOfLettingTheModelGuess() =
        runTest {
            val requests =
                listOf(
                    "open the browser",
                    "Open a browser",
                    "open my browser",
                    "open browser",
                    "launch the web browser",
                    "open the internet browser",
                    "search the web in the browser for cats",
                    "open the browsr",
                    "open the brower",
                    "open the broswer",
                )
            requests.forEach { text ->
                // The model would pick Brave at high confidence: the guard must not even ask it.
                val fake = IntentFixtureProvider(listOf(bravePick))
                val intent = IntentUnderstanding(fake, phone).understand(text)
                assertTrue(text, intent.needsClarification())
                assertEquals(text, "Which browser?", intent.question)
                assertEquals(text, listOf("Chrome", "Brave", "Firefox", "Opera"), intent.options)
                assertEquals(text, "browser", intent.role)
                assertNull(text, intent.app)
                assertTrue(text, intent.confidence < TaskIntent.CONFIDENT)
                assertTrue(text, fake.requests.isEmpty())
            }
        }

    @Test fun aModelAnswerOfBraveAtHighConfidenceIsOverriddenByTheGuard() =
        runTest {
            val fake = IntentFixtureProvider(listOf(bravePick))
            val intent = IntentUnderstanding(fake, phone).understand("open the browser")
            assertNull(intent.app)
            assertEquals("other", intent.action)
            assertTrue(fake.requests.isEmpty())
            // The same model answer is still accepted when the person named Brave.
            val named = IntentUnderstanding(IntentFixtureProvider(emptyList()), phone).understand("open Brave")
            assertEquals("Brave", named.app)
        }

    @Test fun optionsAreDeterministicAndUseInstalledLabelsOnly() =
        runTest {
            val shuffled = listOf("Opera", "Brave", "Gmail", "Chrome")
            val none = IntentFixtureProvider(emptyList())
            val first = IntentUnderstanding(none, shuffled).understand("open a browser")
            val second = IntentUnderstanding(none, shuffled.reversed()).understand("open a browser")
            assertEquals(listOf("Chrome", "Brave", "Opera"), first.options)
            assertEquals(first.options, second.options)
        }

    @Test fun oneInstalledBrowserOpensWithoutAQuestion() =
        runTest {
            val fake = IntentFixtureProvider(emptyList())
            val intent = IntentUnderstanding(fake, listOf("Brave", "WhatsApp", "Gmail")).understand("open the browser")
            assertEquals("Brave", intent.app)
            assertEquals("open_app", intent.action)
            assertFalse(intent.needsClarification())
            assertTrue(fake.requests.isEmpty())
        }

    @Test fun appsThatOnlyShareAFirstWordAreNotBrowsers() =
        runTest {
            val apps = listOf("Chrome", "Chrome Remote Desktop", "Gmail")
            val fake = IntentFixtureProvider(emptyList())
            val intent = IntentUnderstanding(fake, apps).understand("open the browser")
            assertEquals("Chrome", intent.app)
            val editions = listOf("Chrome Beta", "Chrome")
            assertEquals(listOf("Chrome", "Chrome Beta"), AppRoles.membersOf(AppRoles.BROWSER, editions))
            val longLabels =
                listOf("Firefox: Fast & Private Browser", "Brave Private Web Browser", "Chrome Remote Desktop")
            assertEquals(
                listOf("Brave Private Web Browser", "Firefox: Fast & Private Browser"),
                AppRoles.membersOf(AppRoles.BROWSER, longLabels),
            )
        }

    @Test fun anAppNamedInTheRequestResolvesWithoutAskingEvenWithManyBrowsers() =
        runTest {
            val expected =
                mapOf(
                    "open Brave" to "Brave",
                    "open chrome browser" to "Chrome",
                    "open the Brave browser" to "Brave",
                    "launch firefox" to "Firefox",
                    "open the opera browser" to "Opera",
                    "open Heytap browser" to "HeyTap Browser",
                    "open the chorme browser" to "Chrome",
                )
            expected.forEach { (text, label) ->
                val fake = IntentFixtureProvider(emptyList())
                val intent = IntentUnderstanding(fake, phone).understand(text)
                assertEquals(text, label, intent.app)
                assertFalse(text, intent.needsClarification())
                assertTrue(text, fake.requests.isEmpty())
            }
        }

    @Test fun anInstalledBrowserOutsideTheTableIsStillNamedWhenSpokenBeforeTheWordBrowser() =
        runTest {
            val apps = listOf("Chrome", "Brave", "Lemur")
            val fake = IntentFixtureProvider(emptyList())
            val intent = IntentUnderstanding(fake, apps).understand("open the lemur browser")
            assertEquals("Lemur", intent.app)
            assertFalse(intent.needsClarification())
        }

    @Test fun aRememberedRoleAnswersSilentlyButOnlyWhileTheAppIsInstalled() =
        runTest {
            val memory: (String) -> String? = { role -> if (role == "browser") "Firefox" else null }
            val fake = IntentFixtureProvider(emptyList())
            val remembered = IntentUnderstanding(fake, phone, roleMemory = memory).understand("open the browser")
            assertEquals("Firefox", remembered.app)
            assertFalse(remembered.needsClarification())
            assertTrue(fake.requests.isEmpty())
            // The remembered app was uninstalled: ask again instead of guessing another browser.
            val gone = listOf("Chrome", "Brave", "WhatsApp")
            val asked = IntentUnderstanding(fake, gone, roleMemory = memory).understand("open the browser")
            assertTrue(asked.needsClarification())
            assertEquals(listOf("Chrome", "Brave"), asked.options)
            // A name the person typed still wins over memory.
            val named = IntentUnderstanding(fake, phone, roleMemory = memory).understand("open Brave")
            assertEquals("Brave", named.app)
        }

    @Test fun aRememberedRoleIsGivenToTheModelForLongerRequests() =
        runTest {
            val search =
                TaskIntent(
                    "Search cats in Firefox",
                    "Firefox",
                    "search",
                    mapOf("query" to "cats"),
                    confidence = 0.9,
                )
            val fake = IntentFixtureProvider(listOf(Json.encodeToString(search)))
            val intent =
                IntentUnderstanding(fake, phone, roleMemory = { "Firefox" })
                    .understand("please look for cats on the web in the browser")
            assertEquals("Firefox", intent.app)
            assertTrue(request(fake).contains("Chosen browser: Firefox"))
        }

    @Test fun noInstalledBrowserKeepsTheExistingNotInstalledFlow() =
        runTest {
            val unsure =
                TaskIntent(
                    "Open a browser",
                    confidence = 0.3,
                    question = "No browser is installed. Which app should I use?",
                    options = listOf("Gmail"),
                )
            val fake = IntentFixtureProvider(listOf(Json.encodeToString(unsure)))
            val intent = IntentUnderstanding(fake, listOf("Gmail", "Calculator")).understand("open the browser")
            assertEquals(1, fake.requests.size)
            assertTrue(intent.needsClarification())
            assertNull(intent.role)
        }

    @Test fun onlyAGenericRolePhraseTriggersTheGuard() =
        runTest {
            listOf(
                "browse the web for cats",
                "create a note called \"browser ideas\"",
                "open Gmail",
                "what is 2 plus 2",
            ).forEach { text ->
                assertEquals(text, RoleDecision.NoRole, AppRoles.decide(text, phone) { null })
            }
            assertTrue(AppRoles.decide("open the browser", phone) { null } is RoleDecision.Ask)
            assertTrue(AppRoles.isBareOpen("open the browser"))
            assertFalse(AppRoles.isBareOpen("open the browser and search cats"))
            assertEquals("chrome", AppRoles.withoutRoleWords("the chrome browser"))
        }

    @Test fun answeringTheRoleQuestionOpensThatAppWithoutAnotherQuestion() =
        runTest {
            val fake = IntentFixtureProvider(emptyList())
            val enabled = IntentParaphraseTest.ENABLED
            val session = IntentPlanningSession(IntentUnderstanding(fake, phone), TaskPlanner(fake, enabled))
            var asked = 0
            val result =
                session.prepare("open the browser") { intent ->
                    asked++
                    assertEquals("browser", intent.role)
                    "Firefox"
                }!!
            assertEquals(1, asked)
            assertEquals("Firefox", result.intent.app)
            assertFalse(result.bestGuess)
            assertEquals(
                "OPEN_APP",
                result.steps
                    .first()
                    .action.name,
            )
            assertTrue(fake.requests.isEmpty())
        }

    @Test fun cancellingTheRoleQuestionProducesNoPlan() =
        runTest {
            val fake = IntentFixtureProvider(emptyList())
            val session =
                IntentPlanningSession(IntentUnderstanding(fake, phone), TaskPlanner(fake, IntentParaphraseTest.ENABLED))
            assertNull(session.prepare("open the browser") { null })
            assertTrue(fake.requests.isEmpty())
        }
}
