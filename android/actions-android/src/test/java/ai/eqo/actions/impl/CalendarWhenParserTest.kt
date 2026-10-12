// Origin: EQO-authored (fix/r6-cal): pure date, time and duration parsing regressions for Calendar hand-offs.
package ai.eqo.actions.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class CalendarWhenParserTest {
    // Monday 12 October 2026, 3:00 pm in India.
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 10, 12, 15, 0, 0, 0, zone)
    private val clock = Clock.fixed(now.toInstant(), zone)

    private fun resolve(
        vararg params: Pair<String, String>,
        at: Clock = clock,
    ): CalendarWhenResult = CalendarWhenParser.resolve(mapOf(*params), at)

    private fun resolved(
        vararg params: Pair<String, String>,
        at: Clock = clock,
    ): CalendarWhen = (resolve(*params, at = at) as CalendarWhenResult.Resolved).value

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
        minute: Int = 0,
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun assertAllDay(
        expected: LocalDate,
        value: CalendarWhen,
    ) {
        assertTrue(value.allDay)
        assertEquals(utcMidnight(expected.year, expected.monthValue, expected.dayOfMonth), value.startMillis)
        assertEquals(value.startMillis, value.endMillis)
    }

    @Test
    fun `the reported puja date is that day and not today`() {
        val value = resolved("date" to "2026-10-19")
        assertAllDay(LocalDate.of(2026, 10, 19), value)
        assertEquals("19 Oct 2026, all day", value.label)
    }

    @Test
    fun `every phrasing of the same date resolves to 19 October`() {
        val phrasings =
            listOf(
                "2026-10-19",
                "2026/10/19",
                "19 October",
                "October 19",
                "19th October 2026",
                "October 19th, 2026",
                "Oct 19th",
                "19 oct",
                "19th of October",
                "on 19 October",
                "19/10",
                "19-10-2026",
                "19.10.2026",
                "19/10/26",
                "10/19",
                "19-Oct-2026",
                "  19   October  ",
                "MONDAY",
                "next monday",
            )
        for (phrase in phrasings) {
            assertAllDay(LocalDate.of(2026, 10, 19), resolved("date" to phrase))
        }
    }

    @Test
    fun `relative days and weekdays count from today and a bare weekday never means today`() {
        val expected =
            mapOf(
                "today" to LocalDate.of(2026, 10, 12),
                "tomorrow" to LocalDate.of(2026, 10, 13),
                "day after tomorrow" to LocalDate.of(2026, 10, 14),
                "the day after tomorrow" to LocalDate.of(2026, 10, 14),
                "Tuesday" to LocalDate.of(2026, 10, 13),
                "this friday" to LocalDate.of(2026, 10, 16),
                "sun" to LocalDate.of(2026, 10, 18),
                "monday" to LocalDate.of(2026, 10, 19),
                "in 3 days" to LocalDate.of(2026, 10, 15),
                "in 2 weeks" to LocalDate.of(2026, 10, 26),
            )
        for ((phrase, day) in expected) assertAllDay(day, resolved("date" to phrase))
    }

    @Test
    fun `a date without a year is the nearest future occurrence and rolls into next year`() {
        assertAllDay(LocalDate.of(2026, 10, 12), resolved("date" to "12 October"))
        assertAllDay(LocalDate.of(2026, 12, 31), resolved("date" to "31/12"))
        assertAllDay(LocalDate.of(2027, 10, 5), resolved("date" to "5 October"))
        assertAllDay(LocalDate.of(2027, 1, 1), resolved("date" to "1 January"))
        assertAllDay(LocalDate.of(2028, 2, 29), resolved("date" to "29 February"))
        val lateDecember = Clock.fixed(ZonedDateTime.of(2026, 12, 30, 9, 0, 0, 0, zone).toInstant(), zone)
        assertAllDay(LocalDate.of(2027, 1, 2), resolved("date" to "2 January", at = lateDecember))
        assertAllDay(LocalDate.of(2026, 12, 30), resolved("date" to "30 December", at = lateDecember))
    }

    @Test
    fun `a year that was written is kept even when it is already past`() {
        assertAllDay(LocalDate.of(2026, 10, 5), resolved("date" to "5 October 2026"))
        assertAllDay(LocalDate.of(2025, 3, 1), resolved("date" to "2025-03-01"))
    }

    @Test
    fun `a time makes a timed event ending after the default hour`() {
        val value = resolved("date" to "2026-10-19", "time" to "6 pm")
        assertFalse(value.allDay)
        assertEquals(local(2026, 10, 19, 18), value.startMillis)
        assertEquals(local(2026, 10, 19, 19), value.endMillis)
        assertEquals("19 Oct 2026, 6:00 pm", value.label)
    }

    @Test
    fun `twelve and twenty-four hour times read the same`() {
        val expected =
            mapOf(
                "6 pm" to (18 to 0),
                "6pm" to (18 to 0),
                "6:30pm" to (18 to 30),
                "6:30 PM" to (18 to 30),
                "6.30 p.m." to (18 to 30),
                "at 6 pm" to (18 to 0),
                "18:00" to (18 to 0),
                "18:00:00" to (18 to 0),
                "09:15" to (9 to 15),
                "9 am" to (9 to 0),
                "12 am" to (0 to 0),
                "12 pm" to (12 to 0),
                "noon" to (12 to 0),
                "midnight" to (0 to 0),
            )
        for ((text, hm) in expected) {
            val value = resolved("date" to "2026-10-19", "time" to text)
            assertEquals(text, local(2026, 10, 19, hm.first, hm.second), value.startMillis)
        }
    }

    @Test
    fun `a time written inside the date text is split out`() {
        val evening = local(2026, 10, 19, 18)
        val phrasings =
            listOf(
                "date" to "2026-10-19T18:00:00",
                "date" to "2026-10-19 18:00",
                "date" to "2026-10-19T18:00:00+05:30",
                "date" to "2026-10-19T18:00:00Z",
                "datetime" to "2026-10-19T18:00",
                "datetime" to "19 October 6pm",
                "datetime" to "October 19 at 6 pm",
                "datetime" to "19th October 2026 18:00",
                "datetime" to "monday at 6 pm",
                "datetime" to "6 pm on 19 October",
            )
        for ((field, text) in phrasings) {
            assertEquals(text, evening, resolved(field to text).startMillis)
        }
        assertEquals(local(2026, 10, 13, 18), resolved("date" to "tomorrow at 6 pm").startMillis)
        assertEquals(local(2026, 10, 13, 8), resolved("date" to "tomorrow morning").startMillis)
        assertEquals(local(2026, 10, 12, 21), resolved("date" to "tonight").startMillis)
        assertEquals(local(2026, 10, 19, 19, 7), resolved("datetime" to "19 Oct at 19:07").startMillis)
    }

    @Test
    fun `an ISO midnight stamp is a plain date and stays all day`() {
        assertAllDay(LocalDate.of(2026, 10, 19), resolved("datetime" to "2026-10-19T00:00:00"))
    }

    @Test
    fun `a time with no date is its next occurrence`() {
        assertEquals(local(2026, 10, 12, 18), resolved("time" to "6 pm").startMillis)
        assertEquals(local(2026, 10, 13, 9), resolved("time" to "9 am").startMillis)
        assertEquals(local(2026, 10, 12, 18), resolved("datetime" to "6 pm").startMillis)
        assertEquals(local(2026, 10, 13, 9), resolved("datetime" to "9am").startMillis)
    }

    @Test
    fun `duration wording is read in minutes and bad wording keeps the default`() {
        fun minutes(text: String): Long {
            val value = resolved("date" to "2026-10-19", "time" to "6 pm", "duration" to text)
            return (value.endMillis - value.startMillis) / MINUTE_MS
        }
        assertEquals(60L, minutes("1 hour"))
        assertEquals(30L, minutes("30 min"))
        assertEquals(90L, minutes("90 minutes"))
        assertEquals(120L, minutes("2 hours"))
        assertEquals(90L, minutes("1.5 hours"))
        assertEquals(90L, minutes("1 hour 30 minutes"))
        assertEquals(90L, minutes("1h30m"))
        assertEquals(30L, minutes("half an hour"))
        assertEquals(60L, minutes("an hour"))
        assertEquals(45L, minutes("45"))
        assertEquals(60L, minutes("a while"))
        assertEquals(60L, minutes(""))
        assertEquals(60L, minutes("99999 hours"))
    }

    @Test
    fun `a missing duration defaults to one hour`() {
        val value = resolved("date" to "2026-10-19", "time" to "7 pm")
        assertEquals(HOUR_MS, value.endMillis - value.startMillis)
    }

    @Test
    fun `now starts now and a date of now with a time is today`() {
        val value = resolved("date" to "now")
        assertFalse(value.allDay)
        assertEquals(now.toInstant().toEpochMilli(), value.startMillis)
        assertEquals("12 Oct 2026, 3:00 pm", value.label)
        assertEquals(local(2026, 10, 12, 18), resolved("date" to "now", "time" to "6 pm").startMillis)
    }

    @Test
    fun `unreadable dates and times are invalid and never fall back to today`() {
        for (text in listOf("someday", "31 April", "32/13", "19 Octember", "yesterday-ish", "13/13/2026")) {
            val result = resolve("date" to text)
            assertTrue(text, result is CalendarWhenResult.Invalid)
            assertEquals("date", (result as CalendarWhenResult.Invalid).field)
            assertEquals(text, result.text)
        }
        val badTime = resolve("date" to "2026-10-19", "time" to "banana")
        assertEquals(CalendarWhenResult.Invalid("time", "banana"), badTime)
        assertTrue(resolve("date" to "2026-10-19T25:00") is CalendarWhenResult.Invalid)
    }

    @Test
    fun `nothing given is unspecified`() {
        assertEquals(CalendarWhenResult.Unspecified, resolve())
        assertEquals(CalendarWhenResult.Unspecified, resolve("date" to "  ", "time" to ""))
        assertEquals(CalendarWhenResult.Unspecified, resolve("title" to "x"))
    }

    @Test
    fun `timed events use the local zone while all day events use UTC midnight`() {
        val newYork = ZoneId.of("America/New_York")
        val clockNy = Clock.fixed(now.toInstant(), newYork)
        val timed = resolved("date" to "2026-10-19", "time" to "6 pm", at = clockNy)
        val expected = ZonedDateTime.of(2026, 10, 19, 18, 0, 0, 0, newYork).toInstant().toEpochMilli()
        assertEquals(expected, timed.startMillis)
        assertAllDay(LocalDate.of(2026, 10, 19), resolved("date" to "2026-10-19", at = clockNy))
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60 * MINUTE_MS
        const val DAY_MS = 24 * HOUR_MS
    }
}
