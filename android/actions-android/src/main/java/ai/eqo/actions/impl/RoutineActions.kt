// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51
// Origin: path: app/src/main/java/com/opendroid/ai/actions/RoutineActions.kt; EQO TASK-078 port.
package ai.eqo.actions.impl

import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import ai.eqo.data.db.entities.HabitRoutineEntity
import android.content.Context

/**
 * RUN_ROUTINE, DETECT_ROUTINES and APPROVE_ROUTINE. GET_MORNING_BRIEFING stays with the productivity family (batch 3).
 */
internal class RoutineActions(
    private val daos: AutomationDaos,
    private val engine: HabitRoutineEngine,
) {
    fun getActions(): List<Action> =
        listOf(
            RegisteredExecutor("RUN_ROUTINE") { params, context -> runRoutine(params, context) },
            RegisteredExecutor("DETECT_ROUTINES") { params, _ -> detect(params) },
            RegisteredExecutor("APPROVE_ROUTINE") { params, _ -> approve(params) },
        )

    private suspend fun runRoutine(
        params: Map<String, String>,
        context: Context,
    ): ActionResult {
        val requested = params["routineName"] ?: params["name"]
        val target = find(params["routineId"], requested, allowPartial = true)
        return target?.let { engine.executeRoutine(it.id, context) } ?: notFound(params["routineId"], requested)
    }

    private suspend fun detect(params: Map<String, String>): ActionResult {
        val requested = params["lookbackDays"]?.trim()?.toIntOrNull() ?: DEFAULT_LOOKBACK_DAYS
        val lookback = requested.coerceIn(1, MAX_LOOKBACK_DAYS)
        val routines = engine.detectRoutines(lookbackDays = lookback)
        val text =
            if (routines.isEmpty()) {
                "No new recurring routines detected yet. Continue using your device normally to build habit patterns."
            } else {
                val lines = routines.joinToString("\n") { "• ${it.name} (${it.triggerLabel}): ${it.description}" }
                "Detected ${routines.size} routines:\n$lines"
            }
        return ActionResult.Success(mapOf("message" to text))
    }

    private suspend fun approve(params: Map<String, String>): ActionResult {
        val requested = params["routineName"]
        val target = find(params["routineId"], requested, allowPartial = false)
        val clash = target?.let { daos.macros.getMacroByName(it.name) }
        return when {
            target == null -> notFound(params["routineId"], requested)
            clash != null && clash.id != target.macroId ->
                ActionResult.Failure("A macro named '${target.name}' already exists; rename or delete it first.")
            else -> {
                val macro = engine.approveRoutine(target.id)
                if (macro == null) {
                    ActionResult.Failure("Could not activate routine.")
                } else {
                    ActionResult.Success(mapOf("message" to "Routine '${macro.name}' has been approved and automated!"))
                }
            }
        }
    }

    private suspend fun find(
        routineId: String?,
        routineName: String?,
        allowPartial: Boolean,
    ): HabitRoutineEntity? =
        when {
            !routineId.isNullOrBlank() -> daos.habits.getRoutineById(routineId.trim())
            !routineName.isNullOrBlank() -> {
                val all = daos.habits.getAllRoutines()
                val name = routineName.trim()
                all.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: all.firstOrNull { allowPartial && it.name.contains(name, ignoreCase = true) }
            }
            else -> null
        }

    private fun notFound(
        routineId: String?,
        routineName: String?,
    ): ActionResult.Failure =
        when {
            !routineId.isNullOrBlank() -> ActionResult.Failure("Routine with ID '$routineId' not found.")
            !routineName.isNullOrBlank() -> ActionResult.Failure("Routine '$routineName' not found.")
            else -> ActionResult.Failure("routineId or routineName parameter missing")
        }
}

private const val DEFAULT_LOOKBACK_DAYS = 14
private const val MAX_LOOKBACK_DAYS = 90
