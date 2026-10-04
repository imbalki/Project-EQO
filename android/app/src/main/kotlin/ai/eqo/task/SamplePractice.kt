package ai.eqo.task

import ai.eqo.core.agent.ActionLoop

/** Only the sample task opts in; production plans retain ActionLoop's normal timing. */
object SamplePractice {
    private const val STEP_DELAY_MS = 8_000L
    private const val MS_PER_SECOND = 1_000L

    val config = ActionLoop.Config(interStepDelayMs = STEP_DELAY_MS, delayAfterLastStep = false)

    fun seconds(remainingMs: Long): Int = ((remainingMs + MS_PER_SECOND - 1) / MS_PER_SECOND).toInt()
}
