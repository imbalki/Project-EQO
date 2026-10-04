// TASK-015 (issue #20): the study-flow gates stay off in the shipped build (SF-1).
package ai.eqo.study

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StudyFlowGateTest {
    @Test
    fun `the shipped study flow uses neither unverified transport`() {
        assertFalse(
            "TASK-008 SF-1: the trust-all connect plane must be gated off",
            StudyFlowGate.WIRELESS_CONNECT_PLANE_IN_STUDY_FLOW,
        )
        assertFalse(
            "TASK-010 SF-1: the CDP relay must be gated off",
            StudyFlowGate.CHROME_CDP_IN_STUDY_FLOW,
        )
    }

    @Test
    fun `permits reads the same constants the evidence quotes`() {
        assertFalse(StudyFlowGate.permits(StudyFlowGate.StudyTransport.WIRELESS_CONNECT_PLANE))
        assertFalse(StudyFlowGate.permits(StudyFlowGate.StudyTransport.CHROME_CDP))
    }

    @Test
    fun `the pending-work reasons are named, not hand-waved`() {
        assertTrue(StudyFlowGate.WIRELESS_CONNECT_PENDING_WORK.contains("pinning"))
        assertTrue(StudyFlowGate.CHROME_CDP_PENDING_WORK.contains("socket-owner"))
    }

    @Test
    fun `no app main source constructs a trust-all connect or CDP client`() {
        val banned =
            listOf(
                "WirelessAdbActivationRunner(",
                "AdbTlsClient.",
                "AdbTlsClient(",
                "ChromeCdpClient(",
                "CdpTransport(",
                "connectWithStls(",
            )
        val offenders = mutableListOf<String>()
        File("src/main")
            .walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .forEach { file -> scanFileFor(file, banned, offenders) }
        assertTrue("the shipped study flow must construct no trust-all/CDP transport: $offenders", offenders.isEmpty())
    }

    /**
     * Comment lines may NAME the gated classes (that is the point of the gate
     * documentation); only real code may not construct them.
     */
    private fun scanFileFor(
        file: File,
        banned: List<String>,
        offenders: MutableList<String>,
    ) {
        codeLines(file).forEach { line ->
            banned.forEach { needle ->
                if (line.contains(needle)) offenders += "${file.name}:$needle"
            }
        }
    }

    /** Non-comment source lines: guards check code, documentation may name things. */
    private fun codeLines(file: File): List<String> =
        file.readText().lines().filter { line ->
            val trimmed = line.trim()
            !(trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*"))
        }
}
