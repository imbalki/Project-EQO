// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51
// Origin: path: app/src/main/java/com/opendroid/ai/core/routine/HabitRoutineEngine.kt
// Origin: EQO TASK-078 port (pattern detection only).
package ai.eqo.actions.impl

import ai.eqo.data.db.entities.HabitEventEntity
import ai.eqo.data.db.entities.HabitRoutineEntity
import ai.eqo.data.models.RoutineStatus
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.Calendar

// The bucket table is data: hour ranges and the cron hour suggested for each.
@Suppress("MagicNumber")
internal enum class RoutineTimeBucket(
    val startHour: Int,
    val endHour: Int,
    val label: String,
    val cronHour: Int,
) {
    EARLY_MORNING(5, 7, "6:30 AM", 6),
    MORNING(8, 11, "9:00 AM", 9),
    MIDDAY(12, 14, "12:30 PM", 12),
    AFTERNOON(15, 17, "4:00 PM", 16),
    EVENING(18, 20, "7:00 PM", 19),
    NIGHT(21, 23, "10:00 PM", 22),
}

internal data class SessionCluster(
    val dayOfWeek: Int,
    val avgHour: Int,
    val actions: List<String>,
    val isWeekday: Boolean,
)

/** Pure pattern detection over recorded habit events: no database, clock or Android service is touched. */
internal object RoutineDetection {
    const val SESSION_WINDOW_MS = 30 * 60 * 1000L
    private const val MIN_SESSION_EVENTS = 2
    private const val CONFIDENCE_DAYS = 14f
    private const val MIN_CONFIDENCE = 0.6f
    private const val MAX_CONFIDENCE = 0.95f
    private val json = Json { ignoreUnknownKeys = true }
    private val decided = setOf(RoutineStatus.DISMISSED.name, RoutineStatus.APPROVED.name, RoutineStatus.ACTIVE.name)

    /** Groups events by calendar day, then splits each day into 30-minute sessions of two or more events. */
    fun sessions(events: List<HabitEventEntity>): List<SessionCluster> =
        events
            .groupBy { dayKey(it.timestamp) }
            .values
            .flatMap { dayEvents -> splitDay(dayEvents) }

    private fun dayKey(timestamp: Long): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        return calendar.get(Calendar.YEAR) * DAY_KEY_YEAR + calendar.get(Calendar.DAY_OF_YEAR)
    }

    private fun splitDay(dayEvents: List<HabitEventEntity>): List<SessionCluster> {
        val clusters = mutableListOf<MutableList<HabitEventEntity>>()
        for (event in dayEvents) {
            val last = clusters.lastOrNull()
            if (last != null && event.timestamp - last.last().timestamp <= SESSION_WINDOW_MS) {
                last.add(event)
            } else {
                clusters.add(mutableListOf(event))
            }
        }
        return clusters.filter { it.size >= MIN_SESSION_EVENTS }.map { toSession(it) }
    }

    private fun toSession(cluster: List<HabitEventEntity>): SessionCluster {
        val dayOfWeek = cluster.first().dayOfWeek
        return SessionCluster(
            dayOfWeek = dayOfWeek,
            avgHour = cluster.map { it.hourOfDay }.average().toInt(),
            actions = cluster.map { it.actionName }.distinct(),
            isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY,
        )
    }

    /** Routines suggested by the sessions, honoring the owner's earlier dismiss/approve decisions. */
    fun discover(
        sessions: List<SessionCluster>,
        existing: Map<String, HabitRoutineEntity>,
        minRepetitions: Int,
        now: Long,
    ): List<HabitRoutineEntity> =
        RoutineTimeBucket.entries.flatMap { bucket ->
            val inBucket = sessions.filter { it.avgHour in bucket.startHour..bucket.endHour }
            val weekday = inBucket.filter { it.isWeekday }
            val weekdayRoutine = synthesize(weekday, Slot(bucket, true), minRepetitions, existing, now)
            // A daily routine is only offered when there is no weekday pattern for the same time of day.
            val dailyRoutine =
                if (inBucket.size >= minRepetitions && weekday.size < minRepetitions) {
                    synthesize(inBucket, Slot(bucket, false), minRepetitions, existing, now)
                } else {
                    null
                }
            listOfNotNull(weekdayRoutine, dailyRoutine)
        }

    private class Slot(
        val bucket: RoutineTimeBucket,
        val weekdayOnly: Boolean,
    )

    private fun synthesize(
        sessions: List<SessionCluster>,
        slot: Slot,
        minRepetitions: Int,
        existing: Map<String, HabitRoutineEntity>,
        now: Long,
    ): HabitRoutineEntity? {
        val bucket = slot.bucket
        val weekdayOnly = slot.weekdayOnly
        val frequency = sessions.flatMap { it.actions }.groupingBy { it }.eachCount()
        val frequent = frequency.filter { it.value >= minRepetitions }.keys.toList()
        val id = "routine_${if (weekdayOnly) "weekday" else "daily"}_${bucket.name.lowercase()}"
        val previous = existing[id]
        return if (frequent.size < 2 || previous?.status in decided) {
            null
        } else {
            val repetitions = frequency.values.max()
            val template = RoutineTemplates.forBucket(bucket, frequent)
            HabitRoutineEntity(
                id = id,
                name = template.name,
                description = template.description,
                triggerLabel = (if (weekdayOnly) "Every weekday at " else "Daily at ") + bucket.label,
                triggerCron = "0 ${bucket.cronHour} * * ${if (weekdayOnly) "1-5" else "*"}",
                detectedActionsJson = json.encodeToString(ListSerializer(String.serializer()), frequent),
                suggestedStepsJson = MacroSteps.encode(template.steps),
                repetitionCount = repetitions,
                confidence = (repetitions / CONFIDENCE_DAYS).coerceIn(MIN_CONFIDENCE, MAX_CONFIDENCE),
                status = previous?.status ?: RoutineStatus.SUGGESTED.name,
                suggestionMessage = template.suggestion,
                createdAt = previous?.createdAt ?: now,
                lastDetectedAt = now,
                lastExecutedAt = previous?.lastExecutedAt,
                macroId = previous?.macroId,
            )
        }
    }

    private const val DAY_KEY_YEAR = 1000
}
