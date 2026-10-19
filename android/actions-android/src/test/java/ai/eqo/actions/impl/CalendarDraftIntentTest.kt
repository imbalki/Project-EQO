// Origin: EQO-authored (fix/r6-cal): calendar and reminder hand-offs carry the requested date, time, place and guests.
package ai.eqo.actions.impl

import ai.eqo.accessibility.EqoAutomation
import ai.eqo.accessibility.TakeoverDetector
import ai.eqo.actions.base.ActionResult
import android.app.Application
import android.content.Intent
import android.provider.CalendarContract
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
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class CalendarDraftIntentTest {
    // Monday 12 October 2026, 3:00 pm in India.
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 10, 12, 15, 0, 0, 0, zone)
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val automation =
        EqoAutomation({ null }, { EqoAutomation.ServiceState.AVAILABLE }, TakeoverDetector())
    private val registry: AndroidActionRegistry =
        run {
            val permissions = PermissionRequester { false }
            val launcher = GatedIntentLauncher(context) { automation }
            val calendar = CalendarActions(launcher, permissions, { automation }, Clock.fixed(now.toInstant(), zone))
            AndroidActionRegistry(context, listOf(calendar.getActions()), permissions, UnknownActionSink {})
        }

    private fun launched(): Intent? = shadowOf(context).nextStartedActivity

    private fun started(): Intent = requireNotNull(launched())

    private fun utcMidnight(
        year: Int,
        month: Int,
        day: Int,
    ): Long = LocalDate.of(year, month, day).toEpochDay() * DAY_MS

    private fun local(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
    ): Long = ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun Intent.begin() = getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, NONE)

    private fun Intent.end() = getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, NONE)

    @Test
    fun `a puja on 19 October is prepared for that day and not today`() =
        runTest {
            val result =
                registry.execute("CREATE_CALENDAR_EVENT", mapOf("title" to "Puja", "date" to "19 October"))
            assertEquals(
                ActionResult.UserActionRequired(
                    "Calendar is open with Puja on 19 Oct 2026, all day. Tap Save to keep it.",
                ),
                result,
            )
            val intent = started()
            assertEquals(Intent.ACTION_INSERT, intent.action)
            assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
            assertEquals("Puja", intent.getStringExtra(CalendarContract.Events.TITLE))
            assertEquals(utcMidnight(2026, 10, 19), intent.begin())
            assertEquals(utcMidnight(2026, 10, 19), intent.end()) // editor treats an all-day end as inclusive
            assertTrue(intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        }

    @Test
    fun `an invite with a time place and guests fills every Calendar field`() =
        runTest {
            val result =
                registry.execute(
                    "CREATE_CALENDAR_EVENT",
                    mapOf(
                        "title" to "Dasara dinner",
                        "date" to "2026-10-20",
                        "time" to "6:30 pm",
                        "duration" to "90 minutes",
                        "location" to "Home",
                        "attendees" to "alex@example.com, Priya and sam@example.org",
                        "description" to "Bring sweets",
                    ),
                )
            assertEquals(
                "Calendar is open with Dasara dinner on 20 Oct 2026, 6:30 pm. Tap Save to keep it.",
                (result as ActionResult.UserActionRequired).message,
            )
            val intent = started()
            val start = ZonedDateTime.of(2026, 10, 20, 18, 30, 0, 0, zone).toInstant().toEpochMilli()
            assertEquals(start, intent.begin())
            assertEquals(start + NINETY_MINUTES_MS, intent.end())
            assertFalse(intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true))
            assertEquals("Home", intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
            assertEquals("alex@example.com,sam@example.org", intent.getStringExtra(Intent.EXTRA_EMAIL))
            assertEquals("Bring sweets\nWith: Priya", intent.getStringExtra(CalendarContract.Events.DESCRIPTION))
        }

    @Test
    fun `guests who are only names are kept in the description and no email extra is sent`() =
        runTest {
            registry.execute(
                "CREATE_CALENDAR_EVENT",
                mapOf("title" to "Lunch", "date" to "tomorrow", "time" to "1 pm", "attendees" to "Priya"),
            )
            val intent = started()
            assertFalse(intent.hasExtra(Intent.EXTRA_EMAIL))
            assertEquals("Created by EQO\nWith: Priya", intent.getStringExtra(CalendarContract.Events.DESCRIPTION))
            assertEquals(local(2026, 10, 13, 13), intent.begin())
        }

    @Test
    fun `a reminder carries its date and time too`() =
        runTest {
            val result =
                registry.execute("SET_REMINDER", mapOf("text" to "Puja", "datetime" to "2026-10-19T18:00:00"))
            assertEquals(
                "Calendar is open with Puja on 19 Oct 2026, 6:00 pm. Tap Save to keep it.",
                (result as ActionResult.UserActionRequired).message,
            )
            val intent = started()
            assertEquals("Puja", intent.getStringExtra(CalendarContract.Events.TITLE))
            assertEquals(local(2026, 10, 19, 18), intent.begin())
            assertEquals(local(2026, 10, 19, 19), intent.end())
            assertFalse(intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true))
        }

    @Test
    fun `reminder date and time params and a time only reminder are read`() =
        runTest {
            registry.execute(
                "SET_REMINDER",
                mapOf("text" to "Puja", "date" to "19 October", "datetime" to "6 pm"),
            )
            assertEquals(local(2026, 10, 19, 18), started().begin())
            registry.execute("SET_REMINDER", mapOf("title" to "Medicine", "time" to "8pm"))
            val intent = started()
            assertEquals("Medicine", intent.getStringExtra(CalendarContract.Events.TITLE))
            assertEquals(local(2026, 10, 12, 20), intent.begin())
        }

    @Test
    fun `all day answers open a dated reminder editor rather than failing as an invalid time`() =
        runTest {
            val slots =
                listOf(
                    mapOf("date" to "October 19th"),
                    mapOf("date" to "October 19th", "datetime" to "all day"),
                    mapOf("date" to "October 19th", "time" to "all day", "datetime" to "October 19th"),
                    mapOf("datetime" to "October 19th all-day"),
                )
            for (params in slots) {
                val result =
                    registry.execute(
                        "SET_REMINDER",
                        mapOf("text" to "Puja") + params,
                    )
                assertEquals(
                    ActionResult.UserActionRequired(
                        "Calendar is open with Puja on 19 Oct 2026, all day. Tap Save to keep it.",
                    ),
                    result,
                )
                val intent = started()
                assertEquals(Intent.ACTION_INSERT, intent.action)
                assertEquals(utcMidnight(2026, 10, 19), intent.begin())
                assertEquals(utcMidnight(2026, 10, 19), intent.end())
                assertTrue(intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
            }
            val today =
                registry.execute(
                    "CREATE_CALENDAR_EVENT",
                    mapOf("title" to "Puja", "date" to "today", "time" to "all day"),
                )
            assertTrue(today is ActionResult.UserActionRequired)
            val intent = started()
            assertEquals(Intent.ACTION_INSERT, intent.action)
            assertEquals(utcMidnight(2026, 10, 12), intent.begin())
            assertTrue(intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
        }

    @Test
    fun `with no date or time the intent keeps its legacy now start and the message says to set the time`() {
        val intent =
            CalendarInsertIntent.build(
                mapOf("text" to "Call back"),
                "New Reminder",
                CalendarWhenResult.Unspecified,
                legacyBeginMillis = now.toInstant().toEpochMilli(),
            )
        assertEquals("Call back", intent.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals(now.toInstant().toEpochMilli(), intent.begin())
        assertFalse(intent.hasExtra(CalendarContract.EXTRA_EVENT_END_TIME))
        assertFalse(intent.hasExtra(CalendarContract.EXTRA_EVENT_ALL_DAY))
        assertEquals(
            "Calendar is open with Call back. Set the date and time there, then tap Save to keep it.",
            CalendarInsertIntent.preparedMessage("Call back", CalendarWhenResult.Unspecified),
        )
    }

    @Test
    fun `a date that cannot be read refuses and launches nothing`() =
        runTest {
            val event = registry.execute("CREATE_CALENDAR_EVENT", mapOf("title" to "Puja", "date" to "someday"))
            assertEquals(
                ActionResult.Failure("I could not understand the date 'someday'. Nothing was created."),
                event,
            )
            assertNull(launched())
            val reminder = registry.execute("SET_REMINDER", mapOf("text" to "Puja", "datetime" to "whenever"))
            assertEquals(
                ActionResult.Failure("I could not understand the date 'whenever'. Nothing was created."),
                reminder,
            )
            val badTime =
                registry.execute(
                    "CREATE_CALENDAR_EVENT",
                    mapOf("title" to "Puja", "date" to "2026-10-19", "time" to "after tea"),
                )
            assertEquals(
                ActionResult.Failure("I could not understand the time 'after tea'. Nothing was created."),
                badTime,
            )
            assertNull(launched())
        }

    @Test
    fun `an unreadable date is shortened and escaped in the refusal`() =
        runTest {
            val long = registry.execute("CREATE_CALENDAR_EVENT", mapOf("title" to "x", "date" to "z".repeat(100)))
            assertEquals(
                "I could not understand the date '${"z".repeat(40)}'. Nothing was created.",
                (long as ActionResult.Failure).errorMsg,
            )
            val hidden = registry.execute("CREATE_CALENDAR_EVENT", mapOf("title" to "x", "date" to "a‮b\nc"))
            assertEquals(
                "I could not understand the date 'a\\u{202E}b\\u{000A}c'. Nothing was created.",
                (hidden as ActionResult.Failure).errorMsg,
            )
            assertNull(launched())
        }

    @Test
    fun `only today and now are saved directly so a dated event always opens Calendar`() {
        assertTrue(isDirectCalendarInsert(mapOf("date" to "today")))
        assertTrue(isDirectCalendarInsert(mapOf("date" to "now")))
        assertFalse(isDirectCalendarInsert(mapOf("date" to "19 October")))
        assertFalse(isDirectCalendarInsert(mapOf("date" to "today", "time" to "6 pm")))
    }

    private companion object {
        const val NONE = -1L
        const val NINETY_MINUTES_MS = 90L * 60L * 1000L
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
