// Origin: EQO stage-one phone-action awareness; synthetic installed labels and a fake provider, no phone.
package ai.eqo.core.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneActionIntentTest {
    private val apps = listOf("Settings", "Assistance", "Camera", "Chrome", "Gmail", "Clock")
    private val registry = ActionSchema.ALL_ACTIONS.map { it.name }.toSet()
    private val hints = PhoneActions.hints(registry)

    private fun understanding(fake: IntentFixtureProvider) = IntentUnderstanding(fake, apps, phoneActions = hints)

    @Test fun hintsAreEnabledRegistryActionsWithoutSkillsOrScreenPrimitives() {
        val names = hints.map { it.name }
        assertTrue("TOGGLE_FLASHLIGHT" in names && "SET_VOLUME" in names && "SET_ALARM" in names)
        assertTrue(names.none { it in setOf("OPEN_APP", "CLICK_TEXT", "TYPE_TEXT", "SEND_SMS", "WAIT") })
        assertTrue(hints.all { it.description.isNotBlank() && it.description.length <= 70 })
        assertEquals(listOf("TOGGLE_WIFI"), PhoneActions.hints(setOf("TOGGLE_WIFI", "OPEN_APP")).map { it.name })
        assertTrue(PhoneActions.hints(emptySet()).isEmpty())
    }

    @Test fun plainPhoneCommandsGoToTheRegistryAndNeverAskWhichApp() =
        runTest {
            val cases =
                mapOf(
                    "turn on the flashlight" to "TOGGLE_FLASHLIGHT",
                    "Turn on flashlight" to "TOGGLE_FLASHLIGHT",
                    "switch off the torch" to "TOGGLE_FLASHLIGHT",
                    "set volume to 50" to "SET_VOLUME",
                    "volume up" to "SET_VOLUME",
                    "set brightness to 30%" to "SET_BRIGHTNESS",
                    "turn off wifi" to "TOGGLE_WIFI",
                    "turn on Wi-Fi" to "TOGGLE_WIFI",
                    "turn on bluetooth" to "TOGGLE_BLUETOOTH",
                    "enable hotspot" to "TOGGLE_HOTSPOT",
                    "turn off mobile data" to "TOGGLE_MOBILE_DATA",
                    "turn on do not disturb" to "TOGGLE_DND",
                    "set an alarm for 6 am" to "SET_ALARM",
                    "set a timer for 5 minutes" to "SET_TIMER",
                )
            cases.forEach { (text, action) ->
                val fake = IntentFixtureProvider(emptyList())
                val intent = understanding(fake).understand(text)
                assertEquals(text, "other", intent.action)
                assertEquals(text, action, intent.params[PhoneActions.PARAM])
                assertNull(text, intent.app)
                assertNull(text, intent.question)
                assertFalse(text, intent.needsClarification())
                assertTrue(text, fake.requests.isEmpty())
            }
        }

    @Test fun aFlashlightRequestReachesTheRegistryPlannerAndNotSettingsOrCamera() =
        runTest {
            val settings = """{"steps":[{"action":"OPEN_APP","params":{"appName":"Settings"}}]}"""
            val torch = """{"steps":[{"action":"TOGGLE_FLASHLIGHT","params":{"state":"on"}}]}"""
            val fake = IntentFixtureProvider(listOf(settings, torch))
            val planner = TaskPlanner(fake, setOf("OPEN_APP", "TOGGLE_FLASHLIGHT", "WAIT"))
            val session = IntentPlanningSession(planner.understanding(apps), planner)
            val result = session.prepare("turn on the flashlight") { error("Must not ask which app") }!!
            assertEquals(listOf("TOGGLE_FLASHLIGHT"), result.steps.map { it.action.name })
            // One planner request, then one repair because the first plan opened Settings instead of the action.
            assertEquals(2, fake.requests.size)
            assertTrue(fake.requests.last().systemPrompt.contains("TOGGLE_FLASHLIGHT"))
            assertTrue(result.notes.isEmpty())
        }

    @Test fun theModelIsToldTheActionsAndAnAppQuestionForAPhoneSettingIsDropped() =
        runTest {
            val asked =
                TaskIntent(
                    "Turn on the flashlight",
                    "Settings",
                    "open_app",
                    mapOf(PhoneActions.PARAM to "TOGGLE_FLASHLIGHT"),
                    confidence = 0.5,
                    question = "Turn on flashlight via which app?",
                    options = listOf("Settings", "Assistance", "Camera"),
                )
            val fake = IntentFixtureProvider(listOf(Json.encodeToString(asked)))
            val intent = understanding(fake).understand("light up my phone's back lamp please")
            val text =
                fake.requests
                    .single()
                    .messages
                    .single()
                    .text
            assertTrue(text.contains("phone_actions"))
            assertTrue(text.contains("TOGGLE_FLASHLIGHT: "))
            assertTrue(fake.requests.single().systemPrompt.contains("never ask \"via which app\""))
            assertEquals("other", intent.action)
            assertNull(intent.app)
            assertNull(intent.question)
            assertTrue(intent.options.isEmpty())
            assertFalse(intent.needsClarification())
        }

    @Test fun anInventedOrDisabledActionNameIsDroppedAndGenuineAmbiguityStillAsks() =
        runTest {
            val invented =
                TaskIntent(
                    "Do a thing",
                    action = "other",
                    params = mapOf(PhoneActions.PARAM to "FORMAT_PHONE"),
                    confidence = 0.9,
                )
            val kept = IntentUnderstanding.parse(Json.encodeToString(invented), apps, setOf("SET_VOLUME"))
            assertTrue(kept.params.isEmpty())
            val ambiguous =
                TaskIntent(
                    "Open a tool",
                    confidence = 0.4,
                    question = "Calculator or Clock?",
                    options = listOf("Calculator", "Clock"),
                )
            val fake = IntentFixtureProvider(listOf(Json.encodeToString(ambiguous)))
            val intent = understanding(fake).understand("open a tool")
            assertTrue(intent.needsClarification())
            assertEquals(listOf("Calculator", "Clock"), intent.options)
        }

    @Test fun questionsCompoundRequestsAndOtherSkillsAreNotTakenForPhoneCommands() {
        val enabled = registry
        listOf(
            "what is the volume of a sphere",
            "turn on wifi and then send hi to Rahul",
            "message Rahul that the alarm is set",
            "how does a torch work",
            "search for wifi routers",
            "wifi",
        ).forEach { assertNull(it, PhoneActions.match(it, enabled)) }
        assertEquals("TOGGLE_WIFI", PhoneActions.match("wifi off", enabled))
        assertNull(PhoneActions.match("turn on the flashlight", emptySet()))
    }
}
