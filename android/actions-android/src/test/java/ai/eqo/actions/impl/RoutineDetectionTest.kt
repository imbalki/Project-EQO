// Origin: EQO TASK-078 (#20), pure pattern-detection, engine recording and nested-step policy tests.
package ai.eqo.actions.impl

import ai.eqo.core.agent.ActionCategory
import ai.eqo.core.agent.ActionSchema
import ai.eqo.data.db.EqoDatabase
import ai.eqo.data.db.entities.HabitEventEntity
import ai.eqo.data.db.entities.HabitRoutineEntity
import ai.eqo.data.models.HabitEventType
import ai.eqo.data.models.RoutineStatus
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class RoutineDetectionTest {
    private lateinit var context: Context
    private lateinit var database: EqoDatabase
    private lateinit var daos: AutomationDaos
    private lateinit var previousZone: TimeZone

    @Before
    fun setup() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, EqoDatabase::class.java).allowMainThreadQueries().build()
        daos = RoomAutomationDaos(lazyOf(database))
    }

    @After
    fun tearDown() {
        database.close()
        TimeZone.setDefault(previousZone)
    }

    /** 2026-10-05 is a Monday; [day] 0 to 4 gives Monday to Friday, [day] 5 a Saturday. */
    private fun event(
        day: Int,
        hour: Int,
        minute: Int,
        app: String,
    ): HabitEventEntity {
        val calendar =
            Calendar.getInstance().apply {
                clear()
                set(2026, Calendar.OCTOBER, 5 + day, hour, minute)
            }
        return HabitEventEntity(
            id = "$day-$hour-$minute-$app",
            eventType = HabitEventType.APP_OPEN.name,
            packageName = "pkg.$app",
            actionName = app,
            timestamp = calendar.timeInMillis,
            dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK),
            hourOfDay = hour,
            minuteOfHour = minute,
        )
    }

    private fun morningEvents(d: IntRange) = d.flatMap { listOf(event(it, 9, 0, "Gmail"), event(it, 9, 5, "Cal")) }

    @Test
    fun `sessions split a day at gaps over thirty minutes and drop single events`() {
        val events =
            listOf(
                event(0, 9, 0, "Gmail"),
                event(0, 9, 10, "Calendar"),
                event(0, 12, 0, "Chrome"),
                event(0, 18, 0, "Slack"),
                event(0, 18, 20, "Teams"),
            )
        val sessions = RoutineDetection.sessions(events)
        assertEquals(2, sessions.size)
        assertEquals(listOf("Gmail", "Calendar"), sessions[0].actions)
        assertTrue(sessions[0].isWeekday)
        assertEquals(listOf("Slack", "Teams"), sessions[1].actions)
        assertEquals(18, sessions[1].avgHour)
    }

    @Test
    fun `three weekday mornings of email and calendar suggest a weekday Morning Routine`() {
        val sessions = RoutineDetection.sessions(morningEvents(0..2))
        val found = RoutineDetection.discover(sessions, emptyMap(), 3, now = 1_000L)

        assertEquals(1, found.size)
        val routine = found.single()
        assertEquals("routine_weekday_morning", routine.id)
        assertEquals("Morning Routine", routine.name)
        assertEquals("Every weekday at 9:00 AM", routine.triggerLabel)
        assertEquals("0 9 * * 1-5", routine.triggerCron)
        assertEquals(RoutineStatus.SUGGESTED.name, routine.status)
        assertEquals(3, routine.repetitionCount)
        assertEquals(0.6f, routine.confidence, 0.0001f)
        val steps = MacroSteps.decode(routine.suggestedStepsJson)!!
        assertEquals(6, steps.size)
        assertEquals("LIST_CALENDAR_TODAY", steps.first().action)
    }

    @Test
    fun `fewer than three repetitions or a single repeated action suggest nothing`() {
        val twoDays = RoutineDetection.sessions(morningEvents(0..1))
        assertTrue(RoutineDetection.discover(twoDays, emptyMap(), 3, 0L).isEmpty())
        val oneApp = (0..2).flatMap { listOf(event(it, 9, 0, "Gmail"), event(it, 9, 5, "Gmail")) }
        assertTrue(RoutineDetection.discover(RoutineDetection.sessions(oneApp), emptyMap(), 3, 0L).isEmpty())
    }

    @Test
    fun `weekend only habits become a daily routine and non email apps get open app steps`() {
        val weekend = listOf(5, 6).flatMap { listOf(event(it, 14, 0, "Spotify"), event(it, 14, 5, "Maps")) }
        val extra = listOf(event(12, 14, 0, "Spotify"), event(12, 14, 5, "Maps"))
        val found = RoutineDetection.discover(RoutineDetection.sessions(weekend + extra), emptyMap(), 3, 0L)

        val routine = found.single()
        assertEquals("routine_daily_midday", routine.id)
        assertEquals("Daily at 12:30 PM", routine.triggerLabel)
        assertEquals("0 12 * * *", routine.triggerCron)
        val steps = MacroSteps.decode(routine.suggestedStepsJson)!!
        assertEquals(listOf("OPEN_APP", "OPEN_APP"), steps.map { it.action })
        assertEquals(listOf("Spotify", "Maps"), steps.map { it.params.getValue("appName") })
    }

    @Test
    fun `an earlier approve or dismiss decision is never overwritten and a suggestion keeps its creation time`() {
        val sessions = RoutineDetection.sessions(morningEvents(0..2))
        val suggested = RoutineDetection.discover(sessions, emptyMap(), 3, now = 10L).single()

        listOf(RoutineStatus.DISMISSED, RoutineStatus.APPROVED, RoutineStatus.ACTIVE).forEach { status ->
            val existing = mapOf(suggested.id to suggested.copy(status = status.name))
            assertTrue(status.name, RoutineDetection.discover(sessions, existing, 3, now = 20L).isEmpty())
        }
        val again = RoutineDetection.discover(sessions, mapOf(suggested.id to suggested), 3, now = 20L).single()
        assertEquals(10L, again.createdAt)
        assertEquals(20L, again.lastDetectedAt)
    }

    @Test
    fun `the engine detects from stored events and saves each suggestion once`() =
        runTest(timeout = 10.seconds) {
            val now = event(4, 12, 0, "x").timestamp
            val engine = HabitRoutineEngine(daos, NestedActionRunner { null }, clock = { now })
            assertTrue(engine.detectRoutines().isEmpty())

            daos.habits.insertEvents(morningEvents(0..2))
            val first = engine.detectRoutines()
            assertEquals(listOf("routine_weekday_morning"), first.map { it.id })
            assertEquals(1, engine.detectRoutines().size)
            // A short look-back finds no events and leaves the saved suggestion untouched.
            assertEquals(1, engine.detectRoutines(lookbackDays = 1).size)
        }

    @Test
    fun `recordAppOpen stores one debounced event with a friendly name`() {
        var now = 1_000_000L
        val engine =
            HabitRoutineEngine(daos, NestedActionRunner { null }, CoroutineScope(Dispatchers.IO), clock = { now })
        engine.recordAppOpen("com.google.android.gm")
        engine.recordAppOpen("com.google.android.gm")
        now += 6_000
        engine.recordAppOpen("com.whatsapp", metadata = mapOf("k" to "v"))

        val events = awaitEvents(2)
        assertEquals(listOf("Gmail", "WhatsApp"), events.sortedBy { it.timestamp }.map { it.actionName })
        assertEquals("""{"k":"v"}""", events.first { it.packageName == "com.whatsapp" }.metadataJson)
        assertEquals("Foo", engine.resolveFriendlyAppName("com.example.foo"))
    }

    private fun awaitEvents(expected: Int): List<HabitEventEntity> {
        var events = emptyList<HabitEventEntity>()
        repeat(300) {
            events = kotlinx.coroutines.runBlocking { daos.habits.getRecentEvents() }
            if (events.size >= expected) return events
            Thread.sleep(20)
        }
        return events
    }

    @Test
    fun `room round trips routines and orders them by last detection`() =
        runTest(timeout = 10.seconds) {
            fun routine(
                id: String,
                detected: Long,
                status: RoutineStatus,
            ) = HabitRoutineEntity(id, id, "d", "t", "", "[]", "[]", 1, 0.5f, status.name, "m", 1, detected)
            daos.habits.insertRoutine(routine("old", 1, RoutineStatus.SUGGESTED))
            daos.habits.insertRoutine(routine("new", 2, RoutineStatus.APPROVED))
            assertEquals(listOf("new", "old"), daos.habits.getAllRoutines().map { it.id })

            daos.habits.updateRoutineStatus("old", RoutineStatus.ACTIVE.name, "macro-1")
            assertEquals("macro-1", daos.habits.getRoutineById("old")!!.macroId)
            daos.habits.updateLastExecuted("old", 99L)
            assertEquals(99L, daos.habits.getRoutineById("old")!!.lastExecutedAt)
            assertNull(daos.habits.getRoutineById("missing"))
            assertNotNull(daos.habits.getRoutineById("new"))
        }

    @Test
    fun `nested step policy allows read only and reversible actions and refuses the rest`() {
        listOf("READ_NOTIFICATIONS", "LIST_CALENDAR_TODAY", "OPEN_APP", "READ_NOTES", "CHAT").forEach {
            assertNull(it, NestedStepPolicy.refusalReason(it))
        }
        listOf(
            "SEND_SMS",
            "MAKE_CALL",
            "RUN_MACRO",
            "CREATE_MACRO",
            "SCHEDULE_MACRO",
            "DELETE_MACRO",
            "LIST_MACROS",
            "RUN_ROUTINE",
            "DETECT_ROUTINES",
            "APPROVE_ROUTINE",
            "AUTO_REPLY_TOGGLE",
            "DISMISS_NOTIFICATION",
            "NOT_AN_ACTION",
        ).forEach { assertTrue(it, NestedStepPolicy.refusalReason(it) != null) }
    }

    @Test
    fun `no sending calling advanced control money macro or never auto approve action can be a nested step`() {
        val blocked =
            setOf(
                ActionCategory.MACRO,
                ActionCategory.ADVANCED,
                ActionCategory.COMMUNICATION,
                ActionCategory.FINANCE,
                ActionCategory.SMART_HOME,
                ActionCategory.TRANSPORT,
            )
        ActionSchema.ALL_ACTIONS.forEach {
            if (it.category in blocked || it.neverAutoApprove) {
                assertNotNull(it.name, NestedStepPolicy.refusalReason(it.name))
            }
        }
    }
}
