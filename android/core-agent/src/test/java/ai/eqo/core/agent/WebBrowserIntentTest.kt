// Origin: EQO direct browser actions; synthetic installed labels and a fake provider, no phone or network.
package ai.eqo.core.agent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebBrowserIntentTest {
    private val apps =
        listOf(
            "Chrome",
            "Brave",
            "Firefox",
            "Opera",
            "HeyTap Browser",
            "Samsung Internet",
            "Microsoft Edge",
            "Gmail",
            "Zepto",
        )
    private val enabled = IntentParaphraseTest.ENABLED + setOf("OPEN_URL")

    private suspend fun prepare(
        request: String,
        memory: (String) -> String? = { null },
        answer: String? = null,
        installed: List<String> = apps,
    ): IntentPlanningSession.Result {
        val fake = IntentFixtureProvider(emptyList())
        val understanding = IntentUnderstanding(fake, installed, roleMemory = memory)
        val result =
            IntentPlanningSession(understanding, TaskPlanner(fake, enabled)).prepare(request) { answer }
                ?: error("Cancelled")
        assertTrue(request, fake.requests.isEmpty())
        return result
    }

    private fun preview(result: IntentPlanningSession.Result): String {
        val text = TaskPlanPreview.describe(result.steps)
        return text.removePrefix("EQO will:\n")
    }

    @Test fun addressesAreNormalizedAndUnsafeOnesRefused() {
        val safe =
            mapOf(
                "wikipedia.org" to "https://wikipedia.org",
                "www.example.com" to "https://www.example.com",
                "x.com/path?q=1#f" to "https://x.com/path?q=1#f",
                "HTTP://Example.org/a" to "http://Example.org/a",
                "example.org:8080/a" to "https://example.org:8080/a",
                "10.0.0.1" to "https://10.0.0.1",
            )
        safe.forEach { (typed, url) -> assertEquals(typed, url, WebTargets.siteUrl(typed)) }
        listOf(
            "wikipedia",
            "javascript:alert(1)",
            "file:///etc/passwd",
            "intent://x#Intent;end",
            "data:text/html,hi",
            "https://user:pw@example.org",
            "https://example.org@evil.example",
            "a b.com",
            "999.1.1.1",
            "https://exa<mple.org",
        ).forEach { assertNull(it, WebTargets.siteUrl(it)) }
        assertEquals(WebTargets.Target.Search("cats"), WebTargets.resolve("cats"))
        assertEquals(WebTargets.Target.Search("note: buy milk"), WebTargets.resolve("note: buy milk"))
        listOf("javascript:alert(1)", "mailto:a@example.org", "user@example.org", "https://wikipedia", "file:///x")
            .forEach { assertEquals(it, WebTargets.Target.Refused, WebTargets.resolve(it)) }
        assertEquals("https://www.google.com/search?q=cats+%26+dogs", WebTargets.searchUrl("cats & dogs"))
        assertEquals("wikipedia.org", WebTargets.display("https://wikipedia.org/"))
        assertTrue(WebTargets.looksLikeSite("wikipedia.org"))
        assertTrue(WebTargets.looksLikeSite("x.com/path"))
        listOf("notes.txt", "report.pdf", "user@gmail.com", "e.g.", "wikipedia").forEach {
            assertFalse(it, WebTargets.looksLikeSite(it))
        }
    }

    @Test fun aWebsiteInAnyKnownBrowserIsOneDirectStep() =
        runTest {
            val spoken =
                mapOf(
                    "Chrome" to "Chrome",
                    "Brave" to "Brave",
                    "Firefox" to "Firefox",
                    "Opera" to "Opera",
                    "HeyTap Browser" to "HeyTap Browser",
                    "HeyTap" to "HeyTap Browser",
                    "Edge" to "Microsoft Edge",
                    "Samsung Internet" to "Samsung Internet",
                    "the Brave browser" to "Brave",
                )
            spoken.forEach { (name, label) ->
                val result = prepare("open wikipedia.org in $name")
                val step = result.steps.single()
                assertEquals(name, "OPEN_URL", step.action.name)
                assertEquals(name, mapOf("url" to "https://wikipedia.org", "browser" to label), step.action.params)
                assertEquals(name, "1. open wikipedia.org in $label", preview(result))
                assertFalse(name, result.intent.needsClarification())
            }
        }

    @Test fun aSearchInANamedBrowserIsOneDirectStep() =
        runTest {
            val result = prepare("search cats in Brave")
            val step = result.steps.single()
            assertEquals("WEB_SEARCH", step.action.name)
            assertEquals(mapOf("query" to "cats", "browser" to "Brave"), step.action.params)
            assertEquals("1. search the web for cats in Brave", preview(result))
            val long = prepare("Search for best flights in Mumbai on Samsung Internet please")
            assertEquals(
                "best flights in Mumbai",
                long.steps
                    .single()
                    .action.params["query"],
            )
            assertEquals(
                "Samsung Internet",
                long.steps
                    .single()
                    .action.params["browser"],
            )
            // Words that merely end like a browser phrase stay part of the query.
            val place = prepare("search the web for cats in Mumbai")
            assertEquals(
                "cats in Mumbai",
                place.steps
                    .single()
                    .action.params["query"],
            )
            assertNull(
                place.steps
                    .single()
                    .action.params["browser"],
            )
        }

    @Test fun aSearchNamingTheBrowserInTheMiddleIsDirectToo() =
        runTest {
            val step = prepare("search the web in Brave for cats").steps.single()
            assertEquals(mapOf("query" to "cats", "browser" to "Brave"), step.action.params)
            val role = prepare("search the web in the browser for cats", memory = { "Firefox" }).steps.single()
            assertEquals(mapOf("query" to "cats", "browser" to "Firefox"), role.action.params)
        }

    @Test fun openingABrowserAndThenSearchingOrGoingToASiteIsDirect() =
        runTest {
            val site = prepare("open Chrome and then search wikipedia.org")
            assertEquals(
                "OPEN_URL",
                site.steps
                    .single()
                    .action.name,
            )
            assertEquals(
                "https://wikipedia.org",
                site.steps
                    .single()
                    .action.params["url"],
            )
            assertEquals(
                "Chrome",
                site.steps
                    .single()
                    .action.params["browser"],
            )
            val query = prepare("open Brave and search for cats")
            assertEquals(
                "WEB_SEARCH",
                query.steps
                    .single()
                    .action.name,
            )
            assertEquals(
                mapOf("query" to "cats", "browser" to "Brave"),
                query.steps
                    .single()
                    .action.params,
            )
            val visit = prepare("open Firefox and go to youtube.com")
            assertEquals(
                "Firefox",
                visit.steps
                    .single()
                    .action.params["browser"],
            )
            assertEquals(
                "https://youtube.com",
                visit.steps
                    .single()
                    .action.params["url"],
            )
        }

    @Test fun siteWithoutABrowserUsesTheRememberedBrowserElseTheDefault() =
        runTest {
            val plain = prepare("go to wikipedia.org").steps.single()
            assertEquals(mapOf("url" to "https://wikipedia.org"), plain.action.params)
            val remembered =
                prepare("go to wikipedia.org", memory = { if (it == "browser") "Brave" else null }).steps.single()
            assertEquals("Brave", remembered.action.params["browser"])
            val search = prepare("search the web for cats", memory = { "Firefox" }).steps.single()
            assertEquals(mapOf("query" to "cats", "browser" to "Firefox"), search.action.params)
            val defaultSearch = prepare("search the web for cats").steps.single()
            assertEquals(mapOf("query" to "cats"), defaultSearch.action.params)
            // A remembered browser that was uninstalled is never used.
            val gone = prepare("go to wikipedia.org", memory = { "Vivaldi" }).steps.single()
            assertNull(gone.action.params["browser"])
        }

    @Test fun searchInTheBrowserAsksWhichOneThenSearchesThere() =
        runTest {
            val fake = IntentFixtureProvider(emptyList())
            val understanding = IntentUnderstanding(fake, apps)
            val session = IntentPlanningSession(understanding, TaskPlanner(fake, enabled))
            var asked = 0
            val result =
                session.prepare("search cats in the browser") {
                    asked++
                    assertEquals("Which browser?", it.question)
                    "Chrome"
                }!!
            assertEquals(1, asked)
            assertEquals(
                mapOf("query" to "cats", "browser" to "Chrome"),
                result.steps
                    .single()
                    .action.params,
            )
            assertTrue(fake.requests.isEmpty())
            // The same phrase with a remembered browser needs no question.
            val silent = prepare("search cats in the browser", memory = { "Firefox" })
            assertEquals(
                "Firefox",
                silent.steps
                    .single()
                    .action.params["browser"],
            )
        }

    @Test fun aWebsiteInTheBrowserWithSeveralInstalledStillAsks() =
        runTest {
            val fake = IntentFixtureProvider(emptyList())
            val intent = IntentUnderstanding(fake, apps).understand("open wikipedia.org in the browser")
            assertTrue(intent.needsClarification())
            assertEquals("Which browser?", intent.question)
            assertEquals("browser", intent.role)
        }

    @Test fun aBareWordInANamedBrowserIsSearchedNotOpenedAsAHost() =
        runTest {
            val step = prepare("open wikipedia in Edge").steps.single()
            assertEquals("WEB_SEARCH", step.action.name)
            assertEquals(mapOf("query" to "wikipedia", "browser" to "Microsoft Edge"), step.action.params)
        }

    @Test fun aBrowserThatIsNotInstalledKeepsItsNameForTheMissingAppFlow() =
        runTest {
            val step = prepare("open wikipedia.org in Vivaldi").steps.single()
            assertEquals("OPEN_URL", step.action.name)
            assertEquals("Vivaldi", step.action.params["browser"])
        }

    @Test fun otherRequestsAreLeftToTheModelAndUnsafeAddressesNeverBecomeSteps() =
        runTest {
            val fake = IntentFixtureProvider(emptyList())
            val parse = { text: String -> WebRequests.parse(text) { null } }
            listOf(
                "open Gmail",
                "go to Gmail",
                "open Gmail and then send hi to Rahul",
                "search my notes",
                "find my keys",
                "open report.pdf",
                "open the settings page",
            ).forEach { assertNull(it, parse(it)) }
            assertTrue(fake.requests.isEmpty())
            val unsafe =
                TaskIntent(
                    "Open a page",
                    action = "open_url",
                    params = mapOf("url" to "javascript:alert(1)"),
                    confidence = 1.0,
                )
            assertNull(IntentSkills.plan(unsafe, enabled))
            listOf("file:///sdcard/x", "intent://x#Intent;end", "https://user:pw@example.org").forEach { url ->
                val step = LoopStep("1", ExecutedAction("OPEN_URL", mapOf("url" to url)))
                assertTrue(url, RegistryPlanVocabulary.errors(listOf(step), enabled).isNotEmpty())
            }
            val ok = LoopStep("1", ExecutedAction("OPEN_URL", mapOf("url" to "cats", "browser" to "Brave")))
            assertTrue(RegistryPlanVocabulary.errors(listOf(ok), enabled).isEmpty())
        }

    @Test fun aBrowserPinSurvivesApprovalButIsNeverAcceptedFromTheModel() {
        val pinned =
            LoopStep(
                "task-1",
                ExecutedAction(
                    "OPEN_URL",
                    mapOf("url" to "https://wikipedia.org", "browser" to "Brave", "packageName" to "example.brave"),
                ),
            )
        assertTrue(ApprovedTaskPlan(listOf(pinned)).matches(listOf(pinned)))
        assertTrue(RegistryPlanVocabulary.errors(listOf(pinned), enabled).isNotEmpty())
        val bad = pinned.copy(action = pinned.action.copy(params = pinned.action.params + ("packageName" to "x y")))
        assertTrue(runCatching { ApprovedTaskPlan(listOf(bad)) }.isFailure)
    }

    @Test fun aBuyRequestThatOnlyOpensTheAppIsAskedOnceThenSaidPlainly() =
        runTest {
            val open = """{"steps":[{"action":"OPEN_APP","params":{"appName":"Zepto"}}]}"""
            val fake = IntentFixtureProvider(listOf(open, open))
            val intent =
                TaskIntent(
                    "Open Zepto and search for two milk",
                    "Zepto",
                    "search",
                    mapOf("query" to "two milk"),
                    confidence = 0.9,
                )
            val planned = TaskPlanner(fake, enabled).planWithNotes("buy two milk on Zepto", intent = intent)
            assertEquals(2, fake.requests.size)
            assertTrue(
                fake.requests
                    .last()
                    .systemPrompt
                    .contains("two milk"),
            )
            assertEquals(listOf("OPEN_APP"), planned.steps.map { it.action.name })
            assertEquals(listOf("EQO cannot do the shopping part yet: it will only open Zepto."), planned.notes)
        }

    @Test fun aRepairedBuyPlanKeepsTheSearchAndSaysItStopsBeforeBuying() =
        runTest {
            val open = """{"steps":[{"action":"OPEN_APP","params":{"appName":"Zepto"}}]}"""
            val typed =
                """{"steps":[{"action":"OPEN_APP","params":{"appName":"Zepto"}},""" +
                    """{"action":"WAIT","params":{"durationMs":"3000"}},""" +
                    """{"action":"TYPE_TEXT","params":{"searchText":"Search","content":"two milk"}}]}"""
            val fake = IntentFixtureProvider(listOf(open, typed))
            val other = TaskIntent("Buy two milk on Zepto", confidence = 0.8)
            val planned = TaskPlanner(fake, enabled).planWithNotes("buy two milk on Zepto", intent = other)
            assertEquals(2, fake.requests.size)
            assertEquals(listOf("OPEN_APP", "WAIT", "TYPE_TEXT"), planned.steps.map { it.action.name })
            assertEquals(1, planned.notes.size)
            assertTrue(planned.notes.single().startsWith("EQO cannot place orders or pay yet"))
            // A plan that already searches needs no second request, and the note is still shown.
            val once = IntentFixtureProvider(listOf(typed))
            val direct = TaskPlanner(once, enabled).planWithNotes("buy two milk on Zepto", intent = other)
            assertEquals(1, once.requests.size)
            assertEquals(planned.notes, direct.notes)
        }

    @Test fun aDroppedWebsiteIsRepairedFromAModelPlan() =
        runTest {
            val chromeOnly = """{"steps":[{"action":"OPEN_APP","params":{"appName":"Chrome"}}]}"""
            val direct =
                """{"steps":[{"action":"OPEN_URL","params":{"url":"https://wikipedia.org","browser":"Chrome"}}]}"""
            val fake = IntentFixtureProvider(listOf(chromeOnly, direct))
            val intent = TaskIntent("Open Wikipedia", confidence = 0.8)
            val planned =
                TaskPlanner(fake, enabled).planWithNotes("take me to wikipedia.org using Chrome", intent = intent)
            assertEquals(listOf("OPEN_URL"), planned.steps.map { it.action.name })
            assertTrue(planned.notes.isEmpty())
            assertTrue(
                fake.requests
                    .last()
                    .systemPrompt
                    .contains("https://wikipedia.org"),
            )
            // An open-app template cannot carry the site, so the model plans it instead of a silent app open.
            val templated = IntentFixtureProvider(listOf(chromeOnly, direct))
            val open = TaskIntent("Open Chrome", "Chrome", "open_app", confidence = 1.0)
            val steps = TaskPlanner(templated, enabled).plan("open wikipedia.org with Chrome", intent = open)
            assertEquals("OPEN_URL", steps.single().action.name)
        }

    @Test fun aSiteThatCannotBeAddedIsSaidPlainly() =
        runTest {
            val chromeOnly = """{"steps":[{"action":"OPEN_APP","params":{"appName":"Chrome"}}]}"""
            val fake = IntentFixtureProvider(listOf(chromeOnly, chromeOnly))
            val intent = TaskIntent("Open Wikipedia", confidence = 0.8)
            val planned =
                TaskPlanner(fake, enabled).planWithNotes("take me to wikipedia.org using Chrome", intent = intent)
            assertEquals(
                listOf("EQO could not add the website step: it will only open Chrome. Open wikipedia.org yourself."),
                planned.notes,
            )
        }

    @Test fun theOneRepairBudgetIsSharedAndAFailedExtraRequestKeepsTheValidPlan() =
        runTest {
            val bad = """{"steps":[{"action":"NOT_A_REAL_ACTION","params":{}}]}"""
            val open = """{"steps":[{"action":"OPEN_APP","params":{"appName":"Zepto"}}]}"""
            val intent = TaskIntent("Search", "Zepto", "search", mapOf("query" to "two milk"), confidence = 0.9)
            val shared = IntentFixtureProvider(listOf(bad, open))
            val first = TaskPlanner(shared, enabled).planWithNotes("search two milk on Zepto", intent = intent)
            assertEquals(2, shared.requests.size)
            assertEquals(1, first.notes.size)
            val failing = IntentFixtureProvider(listOf(open))
            val second = TaskPlanner(failing, enabled).planWithNotes("search two milk on Zepto", intent = intent)
            assertEquals(listOf("OPEN_APP"), second.steps.map { it.action.name })
            assertEquals(1, second.notes.size)
        }
}
