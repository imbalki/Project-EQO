// TASK-015 (issue #20): per-capability readiness — the "none inferred" rule.
package ai.eqo.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityReadinessTest {
    private fun status(
        id: CapabilityId,
        state: CapabilityState,
        guidance: String = if (state == CapabilityState.READY) "" else "repair this",
    ) = CapabilityStatus(id = id, state = state, probeName = id.probeName, detail = "detail", guidance = guidance)

    @Test
    fun `a capability may only be written by its own probe`() {
        val thrown =
            runCatching {
                CapabilityStatus(
                    id = CapabilityId.MODEL_KEY,
                    state = CapabilityState.READY,
                    probeName = CapabilityId.ACCESSIBILITY.probeName,
                )
            }.exceptionOrNull()
        assertTrue("cross-probe write must be refused", thrown is IllegalArgumentException)
    }

    @Test
    fun `failed and gated rows must carry guidance`() {
        listOf(CapabilityState.FAILED, CapabilityState.GATED, CapabilityState.DEPENDENT_RECHECK).forEach { state ->
            val thrown =
                runCatching {
                    CapabilityStatus(id = CapabilityId.HELPER, state = state, probeName = CapabilityId.HELPER.probeName)
                }.exceptionOrNull()
            assertTrue("$state without guidance must be refused", thrown is IllegalArgumentException)
        }
    }

    @Test
    fun `one capability's state never changes another row`() {
        val snapshot =
            ReadinessSnapshot()
                .record(status(CapabilityId.MODEL_KEY, CapabilityState.READY))
                .record(status(CapabilityId.ACCESSIBILITY, CapabilityState.READY))
                .record(status(CapabilityId.WIRELESS_ADB, CapabilityState.FAILED))
                .record(status(CapabilityId.HELPER, CapabilityState.READY))
                .record(status(CapabilityId.CHROME_CONSENT, CapabilityState.READY))

        assertEquals(CapabilityState.FAILED, snapshot.rowFor(CapabilityId.WIRELESS_ADB)?.state)
        assertEquals(CapabilityState.READY, snapshot.rowFor(CapabilityId.MODEL_KEY)?.state)
        assertEquals(CapabilityState.READY, snapshot.rowFor(CapabilityId.ACCESSIBILITY)?.state)
        assertEquals(CapabilityState.READY, snapshot.rowFor(CapabilityId.HELPER)?.state)
    }

    @Test
    fun `nothing is ready until every capability has been probed and is ready`() {
        val incomplete =
            ReadinessSnapshot()
                .record(status(CapabilityId.MODEL_KEY, CapabilityState.READY))
        assertFalse("an unprobed capability must not read as all ready", incomplete.allReady())

        val full =
            CapabilityId.entries.fold(ReadinessSnapshot()) { snapshot, id ->
                snapshot.record(status(id, CapabilityState.READY))
            }
        assertTrue(full.allReady())

        val oneBroken =
            full.record(status(CapabilityId.HELPER, CapabilityState.FAILED))
        assertFalse(oneBroken.allReady())
    }

    @Test
    fun `a broken dependency invalidates its dependents for re-check and nothing else`() {
        val snapshot =
            CapabilityId.entries.fold(ReadinessSnapshot()) { acc, id ->
                acc.record(status(id, CapabilityState.READY))
            }

        snapshot.noteDependencyBroken(CapabilityId.HELPER)

        assertEquals(CapabilityState.DEPENDENT_RECHECK, snapshot.rowFor(CapabilityId.CHROME_CONSENT)?.state)
        assertEquals(CapabilityState.READY, snapshot.rowFor(CapabilityId.MODEL_KEY)?.state)
        assertEquals(CapabilityState.READY, snapshot.rowFor(CapabilityId.ACCESSIBILITY)?.state)
        assertEquals(CapabilityState.READY, snapshot.rowFor(CapabilityId.HELPER)?.state)
    }

    @Test
    fun `attention list orders failures first`() {
        val snapshot =
            ReadinessSnapshot()
                .record(status(CapabilityId.MODEL_KEY, CapabilityState.NOT_STARTED))
                .record(status(CapabilityId.HELPER, CapabilityState.FAILED))
                .record(status(CapabilityId.CHROME_CONSENT, CapabilityState.GATED))
                .record(status(CapabilityId.ACCESSIBILITY, CapabilityState.READY))
        assertEquals(CapabilityId.HELPER, snapshot.needingAttention().first().id)
    }

    @Test
    fun `every capability names its requirement ids and user flow`() {
        CapabilityId.entries.forEach { id ->
            assertTrue(id.requirementIds.isNotBlank())
            assertTrue(id.userFlow.isNotBlank())
            assertTrue(id.requirementIds.startsWith("REQ-"))
            assertTrue(id.userFlow.startsWith("UF-"))
        }
    }
}
