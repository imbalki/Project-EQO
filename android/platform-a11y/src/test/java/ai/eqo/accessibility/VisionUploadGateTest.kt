/*
 * EQO (t_cfbe3719): the vision upload gate must fail closed. Unavailable or
 * throwing secure-window metadata is unknown, never permission to upload; only a
 * positively verified non-secure screen may be captured and sent.
 */
package ai.eqo.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisionUploadGateTest {
    @Test
    fun `throwing or unavailable secure metadata is unverifiable never non-secure`() {
        assertEquals(WindowSecurity.UNVERIFIABLE, VisionUploadGate.classify { error("hidden API is not in the SDK") })
        assertEquals(
            WindowSecurity.UNVERIFIABLE,
            VisionUploadGate.classify { throw IllegalAccessException("blocked") },
        )
        assertEquals(WindowSecurity.SECURE, VisionUploadGate.classify { true })
        assertEquals(WindowSecurity.NOT_SECURE, VisionUploadGate.classify { false })
    }

    @Test
    fun `only a fully verified non-secure screen may be sent`() {
        assertNull(VisionUploadGate.refusal(listOf(WindowSecurity.NOT_SECURE, WindowSecurity.NOT_SECURE)))
    }

    @Test
    fun `a secure window refuses with the protected reason`() {
        assertEquals(
            VisionUploadGate.PROTECTED_REASON,
            VisionUploadGate.refusal(listOf(WindowSecurity.NOT_SECURE, WindowSecurity.SECURE)),
        )
    }

    @Test
    fun `an unverifiable window refuses rather than uploads`() {
        assertEquals(
            VisionUploadGate.UNVERIFIABLE_REASON,
            VisionUploadGate.refusal(listOf(WindowSecurity.UNVERIFIABLE)),
        )
        assertEquals(
            VisionUploadGate.UNVERIFIABLE_REASON,
            VisionUploadGate.refusal(listOf(WindowSecurity.NOT_SECURE, WindowSecurity.UNVERIFIABLE)),
        )
    }

    @Test
    fun `an empty window list cannot be verified and refuses`() {
        assertEquals(VisionUploadGate.UNVERIFIABLE_REASON, VisionUploadGate.refusal(emptyList()))
    }
}
