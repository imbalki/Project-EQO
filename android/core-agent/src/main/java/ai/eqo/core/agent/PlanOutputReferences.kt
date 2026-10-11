// Origin: EQO intent v1 owner addendum; approved, backward-only data references, never executable instructions.
package ai.eqo.core.agent

/** Syntax: {{step:1:result}} or {{step:1:message}}. Step numbering is the approved preview's one-based order. */
object PlanOutputReferences {
    // Closing braces are escaped on purpose: Android's regex engine rejects a bare "}" that the desktop JVM accepts.
    private val reference = Regex("\\{\\{step:([1-9][0-9]*):([a-z][a-z0-9_]{0,31})\\}\\}")
    private val payloads =
        mapOf(
            "SEND_SMS" to setOf("message"),
            "SEND_WHATSAPP" to setOf("message"),
            "SEND_TELEGRAM" to setOf("message"),
            "SEND_EMAIL" to setOf("subject", "body"),
            "TYPE_TEXT" to setOf("content"),
            "CALCULATE" to setOf("expression"),
            "WEB_SEARCH" to setOf("query"),
            "compose_sms" to setOf("body"),
            "compose_email" to setOf("subject", "body"),
            "type_text" to setOf("text"),
            "paste" to setOf("text"),
        )
    private const val MAX_VALUE = 8000

    fun errors(steps: List<LoopStep>): List<String> =
        buildList {
            steps.forEachIndexed { index, step ->
                step.action.params.forEach { (key, value) ->
                    if (value.contains("{{step:")) {
                        val matches = reference.findAll(value).toList()
                        if (key !in payloads[step.action.name].orEmpty()) add("Output reference is not a payload field")
                        if (reference.replace(value, "").contains("{{step:")) add("Malformed output reference")
                        if (matches.any { it.groupValues[1].toIntOrNull() !in 1..index }) {
                            add("Output reference must name an earlier step")
                        }
                    }
                }
            }
        }

    fun describe(value: String): String =
        reference.replace(value) {
            "the ${it.groupValues[2]} result of step ${it.groupValues[1]}"
        }

    fun resolve(
        value: String,
        outputs: Map<Int, Map<String, String>>,
    ): String {
        val resolved =
            reference.replace(value) {
                outputs[it.groupValues[1].toInt()]?.get(it.groupValues[2]) ?: error("previous_result_unavailable")
            }
        require(resolved.length <= MAX_VALUE) { "previous_result_too_large" }
        // No recursive substitution. A result is data even if it contains placeholder-like text.
        return resolved
    }
}

/** Process-only results, scoped to one approved executor. Nothing carries over between runs or retries. */
class ApprovedPlanOutputs(
    private val steps: List<LoopStep>,
) {
    private val outputs = mutableMapOf<Int, Map<String, String>>()

    fun resolve(step: LoopStep): Map<String, String> {
        require(PlanOutputReferences.errors(steps).isEmpty()) { "invalid_output_reference" }
        if (step.action.params.values
                .any { it.contains("{{step:") }
        ) {
            require(steps.any { it == step }) { "output_reference_not_approved" }
        }
        return step.action.params.mapValues { PlanOutputReferences.resolve(it.value, outputs) }
    }

    fun record(
        stepId: String,
        values: Map<String, String>,
    ) {
        val index = steps.indexOfFirst { it.stepId == stepId }
        if (index >= 0) outputs[index + 1] = values.toMap()
    }
}
