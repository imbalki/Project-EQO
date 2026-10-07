// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51
// Origin: path: app/src/main/java/com/opendroid/ai/core/routine/HabitRoutineEngine.kt
// Origin: EQO TASK-078 port (suggested routine step templates).
package ai.eqo.actions.impl

import ai.eqo.data.models.PlanStep

/** The name, wording and steps suggested for a detected routine, chosen by time of day and the apps involved. */
internal object RoutineTemplates {
    private val workApps = listOf("Gmail", "Email", "Calendar", "Slack", "Teams")

    class Template(
        val name: String,
        val description: String,
        val suggestion: String,
        val steps: List<PlanStep>,
    )

    fun forBucket(
        bucket: RoutineTimeBucket,
        frequent: List<String>,
    ): Template {
        val morning = bucket == RoutineTimeBucket.MORNING || bucket == RoutineTimeBucket.EARLY_MORNING
        val usesWorkApps =
            frequent.any { app -> workApps.any { app.contains(it, ignoreCase = true) } }
        return when {
            morning && usesWorkApps ->
                Template(
                    "Morning Routine",
                    "Weekday morning briefing and task preparation (${frequent.joinToString(", ")})",
                    "I noticed you usually do these tasks every weekday morning. Would you like me to automate them?",
                    morningSteps(),
                )
            bucket == RoutineTimeBucket.EVENING || bucket == RoutineTimeBucket.NIGHT ->
                Template(
                    "Evening Wrap-up",
                    "Evening review and next day preparation (${frequent.joinToString(", ")})",
                    "I noticed you usually wrap up your tasks in the evening. " +
                        "Would you like me to automate this routine?",
                    eveningSteps(),
                )
            else ->
                Template(
                    "${frequent.first()} & Workflow",
                    "Automated sequence: ${frequent.joinToString(" → ")}",
                    "I noticed you usually do these tasks around ${bucket.label}. Would you like me to automate them?",
                    openAppSteps(frequent),
                )
        }
    }

    private class Spec(
        val description: String,
        val action: String,
        val params: Map<String, String> = emptyMap(),
    )

    private fun steps(specs: List<Spec>): List<PlanStep> =
        specs.mapIndexed { index, spec ->
            PlanStep(
                stepId = "step_${index + 1}",
                order = index + 1,
                description = spec.description,
                action = spec.action,
                params = spec.params,
            )
        }

    private fun morningSteps(): List<PlanStep> =
        steps(
            listOf(
                Spec("Read calendar", "LIST_CALENDAR_TODAY"),
                Spec("Summarize today's meetings", "GET_MORNING_BRIEFING", mapOf("section" to "schedule")),
                Spec("Check important notifications", "READ_NOTIFICATIONS", mapOf("count" to "5")),
                Spec("Prepare task list", "READ_NOTES"),
                Spec("Read selected messages", "READ_NOTIFICATIONS", mapOf("count" to "3")),
                Spec("Give morning briefing", "GET_MORNING_BRIEFING", mapOf("section" to "full", "speak" to "true")),
            ),
        )

    private fun eveningSteps(): List<PlanStep> =
        steps(
            listOf(
                Spec("Check tomorrow's calendar", "LIST_CALENDAR_WEEK"),
                Spec("Check pending notifications", "READ_NOTIFICATIONS", mapOf("count" to "5")),
                Spec("Evening summary", "GET_MORNING_BRIEFING", mapOf("section" to "evening")),
            ),
        )

    private fun openAppSteps(apps: List<String>): List<PlanStep> =
        steps(apps.take(MacroSteps.MAX_STEPS).map { Spec("Open $it", "OPEN_APP", mapOf("appName" to it)) })
}
