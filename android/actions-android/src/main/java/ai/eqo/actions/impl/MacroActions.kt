// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51
// Origin: path: app/src/main/java/com/opendroid/ai/actions/MacroActions.kt; EQO TASK-078 port.
package ai.eqo.actions.impl

import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import ai.eqo.data.db.entities.MacroEntity
import ai.eqo.data.models.PlanStep
import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/** Saved macros: named step lists in Room, run through [NestedActionRunner] under [NestedStepPolicy]. */
internal class MacroActions(
    private val daos: AutomationDaos,
    private val runner: NestedActionRunner,
) {
    fun getActions(): List<Action> =
        listOf(
            RegisteredExecutor("RUN_MACRO") { params, context -> runMacro(params.getValue("macroName"), context) },
            RegisteredExecutor("CREATE_MACRO") { params, _ ->
                create(params.getValue("name"), params.getValue("steps"))
            },
            RegisteredExecutor("SCHEDULE_MACRO") { params, _ ->
                schedule(params.getValue("macroName"), params.getValue("cronExpression"))
            },
            RegisteredExecutor("DELETE_MACRO") { params, _ -> delete(params.getValue("macroName")) },
            RegisteredExecutor("LIST_MACROS") { _, _ -> list() },
        )

    private suspend fun runMacro(
        macroName: String,
        context: Context,
    ): ActionResult {
        val macro = daos.macros.getMacroByName(macroName.trim())
        val steps = macro?.let { MacroSteps.decode(it.stepsJson) }
        val refusal = steps?.let { MacroSteps.refusal(it, runner) }
        return when {
            macro == null -> ActionResult.Failure("Macro with name '$macroName' not found.")
            !macro.isEnabled -> ActionResult.Failure("Macro '$macroName' is turned off.")
            macro.stepsJson.isBlank() -> ActionResult.Failure("Macro '$macroName' has no step data.")
            steps == null -> ActionResult.Failure("Macro '$macroName' has invalid step data.")
            refusal != null -> ActionResult.Failure("Macro '$macroName' was not run: $refusal")
            else -> runner.sequence.execute(steps, context)
        }
    }

    private suspend fun create(
        name: String,
        stepsJson: String,
    ): ActionResult {
        val cleanName = name.trim()
        val steps = MacroSteps.decode(stepsJson)
        val problem =
            when {
                !validName(cleanName) -> "Macro name must be 1 to $MAX_NAME_CHARS plain characters."
                steps == null ->
                    "Macro steps must be a JSON list of steps (stepId, order, description, action, params)."
                else -> MacroSteps.refusal(steps, runner)
            }
        return when {
            problem != null -> ActionResult.Failure(problem)
            daos.macros.getMacroByName(cleanName) != null ->
                ActionResult.Failure("A macro named '$cleanName' already exists.")
            else -> {
                daos.macros.insertMacro(
                    MacroEntity(
                        id = UUID.randomUUID().toString(),
                        name = cleanName,
                        trigger = "manual",
                        stepsJson = MacroSteps.encode(steps.orEmpty()),
                        isSystem = false,
                        isEnabled = true,
                    ),
                )
                ActionResult.Success(mapOf("message" to "Macro '$cleanName' is ready to go!"))
            }
        }
    }

    private suspend fun schedule(
        macroName: String,
        cron: String,
    ): ActionResult {
        val macro = daos.macros.getMacroByName(macroName.trim())
        val expression = cron.trim()
        return when {
            macro == null -> ActionResult.Failure("Macro with name '$macroName' not found.")
            !CRON_FIELDS.matches(expression) ->
                ActionResult.Failure("Schedule must be a five-field cron expression, for example 0 7 * * *.")
            else -> {
                daos.macros.insertMacro(macro.copy(trigger = "cron:$expression"))
                // Nothing in EQO runs scheduled macros yet, so the message must not promise it.
                ActionResult.Success(
                    mapOf(
                        "message" to
                            "Saved the schedule '$expression' on macro '$macroName'. " +
                            "Automatic scheduled runs are not available yet, so it will not start by itself.",
                    ),
                )
            }
        }
    }

    private suspend fun delete(macroName: String): ActionResult {
        val macro = daos.macros.getMacroByName(macroName.trim())
        if (macro != null && !macro.isSystem) daos.macros.deleteMacro(macro.id)
        return when {
            macro == null -> ActionResult.Failure("Macro with name '$macroName' not found.")
            macro.isSystem -> ActionResult.Failure("System macro '$macroName' cannot be deleted.")
            daos.macros.getMacroById(macro.id) != null -> ActionResult.Failure("Couldn't delete macro '$macroName'.")
            else -> ActionResult.Success(mapOf("message" to "Macro '$macroName' deleted."))
        }
    }

    private suspend fun list(): ActionResult {
        val macros = daos.macros.getAllMacros()
        val names = macros.map { it.name }
        val lines = names.sorted().joinToString("\n") { "- $it" }
        val text = if (names.isEmpty()) "No macros found." else "Saved macros:\n$lines"
        return ActionResult.Success(mapOf("message" to text))
    }

    private fun validName(name: String): Boolean = name.length in 1..MAX_NAME_CHARS && name.none { it.isISOControl() }
}

private const val MAX_NAME_CHARS = 80
private val CRON_FIELDS = Regex("^[0-9*/,\\-]{1,20}( [0-9*/,\\-]{1,20}){4}$")

/** Reading and vetting the JSON step lists stored on macros and routines. */
internal object MacroSteps {
    const val MAX_STEPS = 20
    private const val MAX_JSON_CHARS = 16_000
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PlanStep.serializer())

    fun decode(stepsJson: String): List<PlanStep>? =
        try {
            if (stepsJson.length > MAX_JSON_CHARS) null else json.decodeFromString(serializer, stepsJson)
        } catch (_: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException: any unreadable list is simply not runnable.
            null
        }

    fun encode(steps: List<PlanStep>): String = json.encodeToString(serializer, steps)

    /** The first reason this step list may not run, checked before any step is dispatched. */
    fun refusal(
        steps: List<PlanStep>,
        runner: NestedActionRunner,
    ): String? =
        if (steps.isEmpty() || steps.size > MAX_STEPS) {
            "A macro needs 1 to $MAX_STEPS steps."
        } else {
            steps.firstNotNullOfOrNull { step ->
                runner.refusalReason(step.action)
                    ?: step.fallback.takeIf { it.isNotBlank() }?.let(runner::refusalReason)
            }
        }
}
