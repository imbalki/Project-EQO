// Origin: EQO note clarification regressions; synthetic providers only, no device access.
package ai.eqo.core.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NoteClarificationsTest {
    private val empty = TaskIntent("Make a note", action = "create_note", confidence = 0.4)
    private val lost =
        TaskIntent("Uncertain", action = "other", confidence = 0.3, question = "What should the note say?")
    private val enabled = ActionSchema.ALL_ACTIONS.map { it.name }.toSet()

    @Test fun contentAnswerSurvivesOtherAndSlotLosingResponsesWithInstalledKeepDefault() =
        runTest {
            for (updated in listOf(lost, empty)) {
                val fake = provider(empty.copy(question = "What would you like the note to say?"), updated)
                var asked = 0
                val result =
                    session(fake, listOf("Keep")).prepare("make a note") {
                        asked++
                        "Buy bananas"
                    }!!
                assertEquals(1, asked)
                assertEquals("create_note", result.intent.action)
                assertFalse(result.bestGuess)
                assertEquals(
                    ExecutedAction(
                        "CREATE_NOTE",
                        mapOf("appName" to "Keep", "body" to "Buy bananas"),
                        irreversible = true,
                    ),
                    result.steps.single().action,
                )
            }
        }

    @Test fun plannerContentQuestionAlsoRetainsNoteContextWhenModelDowngrades() =
        runTest {
            val other = TaskIntent("Make a note", action = "other", confidence = 1.0)
            val question = """{"steps":[{"action":"ASK_USER","params":{"question":"What should the note say?"}}]}"""
            val fake = IntentFixtureProvider(listOf(json(other), question, json(lost)))
            val result = session(fake, listOf("Keep")).prepare("make a note") { "Bring a notebook" }!!
            assertEquals(1, result.clarificationRounds)
            assertEquals(
                "CREATE_NOTE",
                result.steps
                    .single()
                    .action.name,
            )
            assertEquals(
                "Bring a notebook",
                result.steps
                    .single()
                    .action.params["body"],
            )
        }

    @Test fun installedKeepDefaultResolvesMissingAppQuestionWithoutAnotherAnswer() =
        runTest {
            val first = empty.copy(params = mapOf("body" to "Buy bananas"), question = "Which notes app?")
            val fake = provider(first)
            val result =
                session(fake, listOf("Google Keep")).prepare("make a note saying Buy bananas") {
                    error("Installed Keep must resolve an unspecified notes app")
                }!!
            assertEquals(0, result.clarificationRounds)
            val action = result.steps.single().action
            assertEquals("CREATE_NOTE", action.name)
            assertEquals(mapOf("appName" to "Google Keep", "body" to "Buy bananas"), action.params)
        }

    @Test fun withoutKeepAsksAppAndRetainsContentThroughAppAnswer() =
        runTest {
            val first = empty.copy(question = "What would you like the note to say?")
            val fake =
                IntentFixtureProvider(
                    listOf(
                        json(first),
                        json(lost),
                        json(lost),
                        """{"steps":[{"action":"OPEN_APP","params":{"appName":"Notebook"}}]}""",
                    ),
                )
            var asked = 0
            val result =
                session(fake, listOf("Notebook")).prepare("make a note") {
                    asked++
                    if (asked == 1) {
                        "Buy bananas"
                    } else {
                        assertEquals("Which notes app should I use?", it.clarificationQuestion())
                        "Notebook"
                    }
                }!!
            assertEquals(2, asked)
            assertEquals("Notebook", result.intent.app)
            assertEquals(mapOf("body" to "Buy bananas"), result.intent.params)
        }

    @Test fun titleAndBodySurviveAppQuestionAndExplicitKeepChoice() =
        runTest {
            val slots = mapOf("title" to "Packing", "body" to "Bring a notebook")
            val first = empty.copy(params = slots, question = "Which notes app?", app = "Notebook")
            val fake = provider(first, lost)
            val result = session(fake, listOf("Keep", "Notebook")).prepare("make a note") { "Keep" }!!
            assertEquals(slots, result.intent.params)
            assertEquals(
                slots + ("appName" to "Keep"),
                result.steps
                    .single()
                    .action.params,
            )
            assertEquals(
                "CREATE_NOTE",
                result.steps
                    .single()
                    .action.name,
            )
        }

    @Test fun explicitAppIsNotReplacedByDefaultOrAppChoiceUsedAsBody() =
        runTest {
            val first = empty.copy(app = "Notebook", question = "What should the note say?")
            val fake =
                IntentFixtureProvider(
                    listOf(
                        json(first),
                        json(lost),
                        """{"steps":[{"action":"OPEN_APP","params":{"appName":"Notebook"}}]}""",
                    ),
                )
            val result =
                session(fake, listOf("Keep", "Notebook")).prepare("make a note in Notebook") {
                    "Bring a notebook"
                }!!
            assertEquals("Notebook", result.intent.app)
            assertEquals("Bring a notebook", result.intent.params["body"])
            val corrected = provider(first, lost, lost)
            var asked = 0
            val keep =
                session(corrected, listOf("Keep", "Notebook")).prepare("make a note in Notebook") {
                    asked++
                    if (asked == 1) "Use Keep instead" else "Buy bananas"
                }!!
            assertEquals(2, asked)
            assertEquals(
                mapOf("appName" to "Keep", "body" to "Buy bananas"),
                keep.steps
                    .single()
                    .action.params,
            )
        }

    @Test fun cancellationAndUnresolvedAppChoiceRespectSharedBudget() =
        runTest {
            val first = empty.copy(question = "What should the note say?")
            val cancelled = provider(first)
            assertNull(session(cancelled, listOf("Keep")).prepare("make a note") { null })
            val fake = provider(first, lost, lost.copy(app = "Notebook"))
            var asked = 0
            assertNull(
                session(fake, listOf("Notebook")).prepare("make a note") {
                    asked++
                    if (asked == 1) "Buy bananas" else "Unknown app"
                },
            )
            assertEquals(IntentPlanningSession.MAX_ROUNDS, asked)
        }

    @Test fun explicitTaskCorrectionDoesNotReviveNoteContext() =
        runTest {
            val first = empty.copy(question = "What should the note say?")
            val open = TaskIntent("Open Camera", "Camera", "open_app", confidence = 1.0)
            val fake = provider(first, open)
            val result =
                session(fake, listOf("Keep", "Camera")).prepare("make a note") {
                    "Actually open Camera instead"
                }!!
            assertEquals("open_app", result.intent.action)
            assertEquals(
                "OPEN_APP",
                result.steps
                    .single()
                    .action.name,
            )
            assertEquals(
                "Camera",
                result.steps
                    .single()
                    .action.params["appName"],
            )
        }

    private fun session(
        fake: IntentFixtureProvider,
        apps: List<String>,
    ) = IntentPlanningSession(IntentUnderstanding(fake, apps), TaskPlanner(fake, enabled))

    private fun provider(vararg intents: TaskIntent) = IntentFixtureProvider(intents.map(::json))

    private fun json(intent: TaskIntent): String = Json.encodeToString(intent)
}
