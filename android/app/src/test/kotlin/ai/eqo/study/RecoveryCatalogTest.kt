// TASK-015 (issue #20): recovery guidance per failure class (PRD §7.10, UF-12).
package ai.eqo.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCatalogTest {
    @Test
    fun `every failure class carries repair guidance and a resume rule`() {
        FailureClass.all().forEach { failure ->
            assertTrue("${failure.name} repair", failure.repair.isNotBlank())
            assertTrue("${failure.name} resume", failure.resumeRule.isNotBlank())
            assertTrue("${failure.name} state name", failure.stateName.isNotBlank())
        }
    }

    @Test
    fun `every PRD recovery requirement REQ-REC-01 to REQ-REC-10 is covered`() {
        val covered = FailureClass.all().map { it.requirementId }.toSet()
        (1..10).forEach { n ->
            assertTrue("REQ-REC-$n must be covered", "REQ-REC-%02d".format(n) in covered)
        }
    }

    @Test
    fun `state names are unique and never generic`() {
        val names = FailureClass.all().map { it.stateName }
        assertEquals(names.size, names.toSet().size)
        names.forEach { name ->
            assertFalse("no generic state names, got '$name'", name.equals("error", ignoreCase = true))
        }
    }

    @Test
    fun `no recovery class says anything auto-resumes or auto-retries`() {
        FailureClass.all().forEach { failure ->
            val rule = failure.resumeRule.lowercase()
            assertFalse("${failure.name} must not promise auto-resume", rule.contains("automatically resume"))
            assertFalse("${failure.name} must not promise auto-retry", rule.contains("automatically retry"))
        }
    }

    @Test
    fun `model error codes map to the REQ-REC-01 to 05 classes`() {
        assertEquals(FailureClass.MODEL_UNAUTHORIZED, FailureClass.forLlmErrorCode("AUTH_MISSING"))
        assertEquals(FailureClass.MODEL_UNAUTHORIZED, FailureClass.forLlmErrorCode("AUTH_INVALID"))
        assertEquals(FailureClass.MODEL_RATE_LIMITED, FailureClass.forLlmErrorCode("RATE_LIMITED"))
        assertEquals(FailureClass.MODEL_CREDIT, FailureClass.forLlmErrorCode("QUOTA_EXHAUSTED"))
        assertEquals(FailureClass.MODEL_INCOMPATIBLE, FailureClass.forLlmErrorCode("MODEL_UNAVAILABLE"))
        assertEquals(FailureClass.MODEL_NETWORK, FailureClass.forLlmErrorCode("NETWORK"))
    }

    @Test
    fun `an unknown error code gets no silent generic copy`() {
        assertNull(FailureClass.forLlmErrorCode("WEIRD_NEW_CODE"))
        assertNull(FailureClass.fromStateName("NotAState"))
    }

    @Test
    fun `the hard-failure classes map to their UF-12 rows`() {
        assertEquals("UF-R3", FailureClass.BINDER_DEAD.userFlow)
        assertEquals("UF-R4", FailureClass.A11Y_LOST.userFlow)
        assertEquals("UF-R2", FailureClass.ADB_REVOKED.userFlow)
        assertEquals("UF-R6", FailureClass.VIRTUAL_DISPLAY_FAILED.userFlow)
    }
}
