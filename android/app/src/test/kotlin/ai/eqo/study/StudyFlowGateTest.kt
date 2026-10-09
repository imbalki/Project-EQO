// TASK-015 (issue #20): the study-flow gates stay off in the shipped build (SF-1).
package ai.eqo.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StudyFlowGateTest {
    @Test
    fun `only the pinned wireless connect plane is open in the study flow`() {
        assertTrue(
            "TASK-080: the wireless connect plane is open only because it pins the enrolled server key",
            StudyFlowGate.WIRELESS_CONNECT_PLANE_IN_STUDY_FLOW,
        )
        assertFalse(
            "TASK-010 SF-1: the CDP relay must stay gated off",
            StudyFlowGate.CHROME_CDP_IN_STUDY_FLOW,
        )
    }

    @Test
    fun `the open wireless gate is backed by pinning in the connect client source`() {
        val client = File("../adb-pairing/src/main/kotlin/ai/eqo/adb/pairing/AdbTlsClient.kt")
        assertTrue("connect client source must exist", client.isFile)
        val code = codeLines(client).joinToString("\n")
        assertFalse("no trust-all manager may return", code.contains("TrustAll", ignoreCase = true))
        assertTrue("connect must require an enrollment", code.contains("enrollment.current()"))
        assertTrue("connect must pin the enrolled key", code.contains("PinnedServerTrustManager"))
        assertTrue("connect must fail closed when nothing is enrolled", code.contains("ServerNotEnrolledException"))
    }

    @Test
    fun `permits reads the same constants the evidence quotes`() {
        assertTrue(StudyFlowGate.permits(StudyFlowGate.StudyTransport.WIRELESS_CONNECT_PLANE))
        assertFalse(StudyFlowGate.permits(StudyFlowGate.StudyTransport.CHROME_CDP))
    }

    @Test
    fun `the safeguard and pending-work reasons are named, not hand-waved`() {
        assertTrue(StudyFlowGate.WIRELESS_CONNECT_SAFEGUARD.contains("pinning"))
        assertTrue(StudyFlowGate.CHROME_CDP_PENDING_WORK.contains("socket-owner"))
    }

    @Test
    fun `no app main source touches the raw connect client or a CDP client`() {
        val banned =
            listOf(
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
        assertTrue("the app must use the pinned runner, not the raw client or CDP: $offenders", offenders.isEmpty())
    }

    @Test
    fun `only wireless setup and its notification reply construct the pinned runner`() {
        val users =
            File("src/main")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file -> codeLines(file).any { it.contains("WirelessAdbActivationRunner(") } }
                .map { it.name }
                .toSet()
        assertEquals(
            "only setup and its explicit notification reply may use the pinned runner",
            setOf("WirelessAdbSetupActivity.kt", "WirelessPairingService.kt"),
            users,
        )
        val service = codeLines(File("src/main/kotlin/ai/eqo/onboarding/WirelessPairingService.kt")).joinToString("\n")
        assertTrue(service.contains("NoHelperConsent"))
        assertTrue(service.contains("pairNotificationReply("))
        assertFalse("notification must never authorize or start the helper", service.contains("StudyHelperHooks("))
        assertFalse("notification must never mark activation complete", service.contains("markActive("))
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
