// Origin: EQO reminder regression tests; synthetic providers, no device access.
package ai.eqo.core.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarPlanningTest {
    private val enabled = ActionSchema.ALL_ACTIONS.map { it.name }.toSet()

    @Test fun datedReminderSkipsTimeQuestionAndPlansCalendarDraft() =
        runTest {
            val fake =
                IntentFixtureProvider(
                    listOf(
                        Json.encodeToString(
                            TaskIntent(
                                "Puja reminder",
                                question = "What time should I set the reminder for the puja on October 19th?",
                            ),
                        ),
                    ),
                )
            val result =
                session(fake).prepare("create a reminder for the puja on October 19th") {
                    error("A date without a time must not ask for a time")
                }!!
            assertEquals(0, result.clarificationRounds)
            assertFalse(result.bestGuess)
            assertEquals(
                ExecutedAction(
                    "CREATE_CALENDAR_EVENT",
                    mapOf("title" to "puja", "date" to "October 19th"),
                    irreversible = true,
                ),
                result.steps
                    .single()
                    .action,
            )
        }

    @Test fun allDayAnswerToLegacyTimeQuestionStillPlansCalendarDraft() =
        runTest {
            val first = TaskIntent("Puja reminder", question = "What time should I set the reminder?")
            val answered =
                TaskIntent(
                    "Puja reminder",
                    params = mapOf("title" to "Puja", "date" to "October 19th", "time" to "all day"),
                    confidence = 1.0,
                )
            val fake = IntentFixtureProvider(listOf(Json.encodeToString(first), Json.encodeToString(answered)))
            val result = session(fake).prepare("remind me about the puja next Monday") { "all day" }!!
            assertEquals(1, result.clarificationRounds)
            assertFalse(result.bestGuess)
            assertEquals(
                ExecutedAction(
                    "CREATE_CALENDAR_EVENT",
                    mapOf("title" to "Puja", "date" to "October 19th", "time" to "all day"),
                    irreversible = true,
                ),
                result.steps
                    .single()
                    .action,
            )
        }

    @Test fun explicitTimeIsCarriedWithoutBecomingAllDay() =
        runTest {
            val fake = IntentFixtureProvider(emptyList())
            val result =
                session(fake).prepare("create a reminder for the puja on October 19th at 6 pm") {
                    error("The supplied time is complete")
                }!!
            val action = result.steps.single().action
            assertEquals("6 pm", action.params["time"])
            assertEquals("October 19th", action.params["date"])
        }

    @Test fun calendarContractAcceptsDateOnlyAndKeepsLocationAndGuests() =
        runTest {
            val intent =
                TaskIntent(
                    "Puja",
                    action = "create_calendar_event",
                    params =
                        mapOf(
                            "title" to "Puja",
                            "date" to "October 19th",
                            "location" to "Home",
                            "attendees" to "Priya",
                        ),
                    confidence = 1.0,
                )
            val fake = IntentFixtureProvider(listOf(Json.encodeToString(intent)))
            val result = session(fake).prepare("prepare a puja calendar entry") { error("Complete calendar slots") }!!
            val action = result.steps.single().action
            assertEquals(intent.params, action.params)
        }

    @Test fun legacyCalendarSlotsDismissOnlyAnOptionalTimeQuestion() {
        val prior =
            TaskIntent(
                "Puja",
                params = mapOf("text" to "Puja", "datetime" to "October 19th"),
                question = "What time should I set the reminder?",
            )
        val dated = CalendarIntents.normalize("remind me about the puja on October 19th", prior)
        assertEquals("create_calendar_event", dated.action)
        assertFalse(dated.needsClarification())
        assertEquals(mapOf("title" to "Puja", "date" to "October 19th"), dated.params)
        val timed = CalendarIntents.normalize("remind me about the puja on October 19th at 6 pm", prior)
        assertTrue(timed.needsClarification())
        val changedQuestion = prior.copy(question = "Where is the puja?")
        assertEquals(
            changedQuestion.question,
            CalendarIntents.normalize("remind me about the puja", changedQuestion).question,
        )
    }

    @Test fun allDayMergeRetainsEarlierSlotsButDoesNotOverrideTaskCorrections() {
        val prior =
            TaskIntent(
                "Puja",
                action = "create_calendar_event",
                params = mapOf("title" to "Puja", "date" to "October 19th"),
            )
        val lost = TaskIntent("Puja", question = "What time should I use?")
        val merged = CalendarIntents.merge("remind me about the puja", prior, lost, lost, "all day")
        assertFalse(merged.needsClarification())
        assertEquals(prior.params + ("time" to "all day"), merged.params)
        val correction = TaskIntent("Open Camera", "Camera", "open_app", confidence = 1.0)
        assertEquals(correction, CalendarIntents.merge("remind me about the puja", prior, correction, lost, "all day"))
    }

    @Test fun noteContentCompoundTasksAndMissingDatesAreNotSilentlyCompleted() {
        assertNull(CalendarIntents.from("make a note saying create a reminder for the puja on October 19th"))
        assertNull(CalendarIntents.from("create a reminder for the puja on October 19th and call Priya"))
        assertNull(CalendarIntents.from("create a reminder for the puja"))
        assertNull(CalendarIntents.from("create a reminder for puja on October 19th at 6 pm for 2 hours"))
        assertNull(CalendarIntents.from("create a reminder for puja on October 19th at 6 pm in the temple"))
        assertNull(CalendarIntents.from("create a reminder for puja at 6 pm on October 19th"))
        val incomplete = TaskIntent("Puja", action = "create_calendar_event", params = mapOf("title" to "Puja"))
        assertEquals("date", incomplete.missingParameter())
        assertTrue(incomplete.needsClarification())
        val note = TaskIntent("Note", "Keep", "create_note", params = mapOf("body" to "reminder for the puja"))
        assertEquals(note, CalendarIntents.normalize("make a note", note))
    }

    @Test fun todayDateOnlyTemplateExplicitlyUsesTheAllDayEditorPath() =
        runTest {
            val result =
                session(IntentFixtureProvider(emptyList())).prepare("create a reminder for puja on today") {
                    error("Today without a time is an all-day draft")
                }!!
            val action = result.steps.single().action
            assertEquals("all day", action.params["time"])
        }

    @Test fun legacyDateAndDatetimeBothSurviveForFreePlanningRatherThanLosingTheTime() =
        runTest {
            val legacy =
                TaskIntent(
                    "Puja",
                    params = mapOf("title" to "Puja", "date" to "October 19th", "datetime" to "October 19th at 6 pm"),
                    confidence = 1.0,
                )
            val plan =
                """{"steps":[{"action":"CREATE_CALENDAR_EVENT","params":
                    {"title":"Puja","date":"October 19th","time":"6 pm"}}]}"""
            val fake = IntentFixtureProvider(listOf(Json.encodeToString(legacy), plan))
            val result =
                session(fake).prepare("remind me about the puja on October 19th at 6 pm") {
                    error("Date and datetime are complete")
                }!!
            val action = result.steps.single().action
            assertEquals(legacy.params, result.intent.params)
            assertEquals("6 pm", action.params["time"])
            assertEquals(2, fake.requests.size)
        }

    private fun session(fake: IntentFixtureProvider) =
        IntentPlanningSession(IntentUnderstanding(fake, emptyList()), TaskPlanner(fake, enabled))
}
