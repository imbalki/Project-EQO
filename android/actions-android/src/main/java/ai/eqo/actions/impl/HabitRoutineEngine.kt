// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51
// Origin: path: app/src/main/java/com/opendroid/ai/core/routine/HabitRoutineEngine.kt; EQO TASK-078 port.
package ai.eqo.actions.impl

import ai.eqo.actions.base.ActionResult
import ai.eqo.core.routine.HabitRoutineTracker
import ai.eqo.data.db.entities.HabitEventEntity
import ai.eqo.data.db.entities.HabitRoutineEntity
import ai.eqo.data.db.entities.MacroEntity
import ai.eqo.data.models.HabitEventType
import ai.eqo.data.models.RoutineStatus
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.UUID

/**
 * Habit routines: recording foreground-app events, detecting recurring patterns, approving a suggestion into a
 * macro and running an approved routine. Detection is [RoutineDetection]; this class owns the Room reads and writes.
 *
 * Nothing registers this as the app-open tracker, so no app-usage history is recorded until the owner approves that.
 * The donor's knowledge-graph updates and morning-briefing text are not part of this port.
 */
internal class HabitRoutineEngine(
    private val daos: AutomationDaos,
    private val runner: NestedActionRunner,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val clock: () -> Long = System::currentTimeMillis,
) : HabitRoutineTracker {
    private var lastRecordedPackage: String? = null
    private var lastRecordedTimestamp = 0L

    /** Records one foreground-app event, ignoring rapid repeats of the same package. */
    override fun recordAppOpen(
        packageName: String,
        appName: String?,
        metadata: Map<String, String>,
    ) {
        val now = clock()
        if (packageName == lastRecordedPackage && now - lastRecordedTimestamp < DEBOUNCE_MS) return
        lastRecordedPackage = packageName
        lastRecordedTimestamp = now
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val event =
            HabitEventEntity(
                id = UUID.randomUUID().toString(),
                eventType = HabitEventType.APP_OPEN.name,
                packageName = packageName,
                actionName = appName ?: resolveFriendlyAppName(packageName),
                timestamp = now,
                dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK),
                hourOfDay = calendar.get(Calendar.HOUR_OF_DAY),
                minuteOfHour = calendar.get(Calendar.MINUTE),
                metadataJson = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), metadata),
            )
        scope.launch {
            daos.habits.insertEvent(event)
            if (daos.habits.getEventCount() % DETECT_EVERY_EVENTS == 0) detectRoutines()
        }
    }

    /** Finds recurring patterns in the last [lookbackDays] days, saves new suggestions and returns every routine. */
    suspend fun detectRoutines(
        lookbackDays: Int = DEFAULT_LOOKBACK_DAYS,
        minRepetitions: Int = DEFAULT_MIN_REPETITIONS,
    ): List<HabitRoutineEntity> {
        val now = clock()
        val events = daos.habits.getEventsSince(now - lookbackDays.toLong() * MILLIS_PER_DAY)
        if (events.isNotEmpty()) {
            val existing = daos.habits.getAllRoutines().associateBy { it.id }
            val sessions = RoutineDetection.sessions(events)
            RoutineDetection.discover(sessions, existing, minRepetitions, now).forEach { daos.habits.insertRoutine(it) }
        }
        return daos.habits.getAllRoutines()
    }

    /** Turns a routine into a runnable macro and marks it approved. Null when the routine does not exist. */
    suspend fun approveRoutine(routineId: String): MacroEntity? {
        val routine = daos.habits.getRoutineById(routineId)
        return routine?.let {
            val macro =
                MacroEntity(
                    id = it.macroId ?: UUID.randomUUID().toString(),
                    name = it.name,
                    trigger = if (it.triggerCron.isNotBlank()) "cron:${it.triggerCron}" else "manual",
                    stepsJson = it.suggestedStepsJson,
                    isSystem = false,
                    isEnabled = true,
                )
            daos.macros.insertMacro(macro)
            daos.habits.updateRoutineStatus(routineId, RoutineStatus.APPROVED.name, macro.id)
            macro
        }
    }

    /** Runs the routine's steps once. A paused or dismissed routine is the owner's "no" and is not run. */
    suspend fun executeRoutine(
        routineId: String,
        context: Context,
    ): ActionResult {
        val routine = daos.habits.getRoutineById(routineId)
        val steps = routine?.let { MacroSteps.decode(it.suggestedStepsJson) }
        val refusal = steps?.let { MacroSteps.refusal(it, runner) }
        return when {
            routine == null -> ActionResult.Failure("Routine with ID '$routineId' not found.")
            routine.status in setOf(RoutineStatus.PAUSED.name, RoutineStatus.DISMISSED.name) ->
                ActionResult.Failure("Routine '${routine.name}' is ${routine.status.lowercase()} and was not run.")
            steps == null -> ActionResult.Failure("Invalid routine steps configuration.")
            refusal != null -> ActionResult.Failure("Routine '${routine.name}' was not run: $refusal")
            else -> {
                val result = runner.sequence.execute(steps, context)
                if (result.success) daos.habits.updateLastExecuted(routineId, clock())
                result
            }
        }
    }

    /** Resolves known Android package names to friendly names. */
    fun resolveFriendlyAppName(packageName: String): String =
        FRIENDLY_NAMES[packageName] ?: packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }

    private companion object {
        const val DEBOUNCE_MS = 5000L
        const val DETECT_EVERY_EVENTS = 10
        const val DEFAULT_LOOKBACK_DAYS = 14
        const val DEFAULT_MIN_REPETITIONS = 3
        const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
        val json = Json { ignoreUnknownKeys = true }
        val FRIENDLY_NAMES =
            mapOf(
                "com.google.android.gm" to "Gmail",
                "com.google.android.calendar" to "Calendar",
                "com.Slack" to "Slack",
                "com.slack" to "Slack",
                "com.android.chrome" to "Chrome",
                "com.google.android.apps.chrome" to "Chrome",
                "com.spotify.music" to "Spotify",
                "com.google.android.apps.maps" to "Google Maps",
                "com.whatsapp" to "WhatsApp",
                "org.telegram.messenger" to "Telegram",
                "org.telegram.messenger.web" to "Telegram",
                "org.telegram.plus" to "Telegram",
                "nekox.messenger" to "Telegram",
                "com.google.android.apps.messaging" to "Messages",
                "com.google.android.youtube" to "YouTube",
                "com.google.android.keep" to "Keep Notes",
                "com.microsoft.teams" to "Teams",
                "com.twitter.android" to "X (Twitter)",
                "com.instagram.android" to "Instagram",
                "com.linkedin.android" to "LinkedIn",
                "com.google.android.deskclock" to "Clock",
                "com.android.deskclock" to "Clock",
            )
    }
}
