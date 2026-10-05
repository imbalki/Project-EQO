package ai.eqo.core.agent

import ai.eqo.data.models.PlanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Ordered callback-boundary observations, not guesses based on a PAUSED history. */
internal class DispatchTrace {
    private val entries = mutableListOf<String>()
    private val statuses = mutableListOf<PlanStatus>()
    private val states = mutableListOf<LoopState>()
    private var afterTerminal = 0
    private var afterNonRunning = 0

    @Synchronized
    fun status(
        status: PlanStatus,
        state: LoopState,
    ) {
        statuses += status
        record("status=$status state=$state")
    }

    @Synchronized
    fun start(
        step: String,
        state: LoopState,
    ) {
        states += state
        if (statuses.any { it.isTerminalStatus() }) afterTerminal++
        if (statuses.any { it != PlanStatus.RUNNING }) afterNonRunning++
        record("dispatch-start=$step state=$state afterTerminal=$afterTerminal legacy=$afterNonRunning")
    }

    @Synchronized
    fun finish(
        step: String,
        state: LoopState,
    ) {
        record("dispatch-finish=$step state=$state")
    }

    @Synchronized
    fun command(
        command: String,
        accepted: Boolean,
        state: LoopState,
    ) {
        record("command-return=$command accepted=$accepted state=$state")
    }

    @Synchronized
    fun assertSafe(round: String) {
        assertTrue("$round: no dispatch while paused or stopped\n${dump()}", states.all { it == LoopState.RUNNING })
        assertEquals("$round: no dispatch after terminal\n${dump()}", 0, afterTerminal)
    }

    @Synchronized
    fun legacyViolations(): Int = afterNonRunning

    @Synchronized
    fun dump(): String = entries.joinToString("\n")

    private fun record(event: String) {
        entries += "${entries.size + 1} ns=${System.nanoTime()} thread=${Thread.currentThread().name} $event"
    }
}

internal fun PlanStatus.isTerminalStatus(): Boolean =
    this == PlanStatus.COMPLETED || this == PlanStatus.FAILED || this == PlanStatus.CANCELLED
