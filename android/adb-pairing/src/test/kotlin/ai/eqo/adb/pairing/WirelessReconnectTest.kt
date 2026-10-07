package ai.eqo.adb.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** TASK-080: reconnect without re-pairing, the four plain-language states and the helper command. */
class WirelessReconnectTest {
    private class Recorder(
        private val failConnect: StepSignal? = null,
    ) : ActivationStepRunner {
        val calls = mutableListOf<ActivationCheck>()

        override fun pair(
            endpoints: WirelessAdbEndpoints,
            code: AdbPairingCode,
        ) {
            calls += ActivationCheck.PAIR
        }

        override fun connect(endpoints: WirelessAdbEndpoints) {
            calls += ActivationCheck.CONNECT
            failConnect?.let { throw StepSignalException(it, "synthetic") }
        }

        override fun startHelper() {
            calls += ActivationCheck.HELPER_START
        }

        override fun authorizeHelper() {
            calls += ActivationCheck.AUTHORIZE
        }

        override fun checkBinder() {
            calls += ActivationCheck.BINDER_HEALTH
        }
    }

    private val endpoints = WirelessAdbEndpoints.forReconnect(42_137)

    @Test
    fun reconnectWithoutEnrollmentFailsClosedAndRunsNothingElse() {
        val runner = Recorder()
        val report = ActivationSequence(runner).reconnect(endpoints, enrolled = false)
        assertTrue(runner.calls.isEmpty())
        val failure = report.firstFailure
        assertTrue(failure is ActivationFailure.NeedsRepair)
        assertTrue(failure!!.guidance.contains(AndroidSettingsNames.PAIR_DEVICE_WITH_PAIRING_CODE))
        assertEquals(WirelessLinkState.NEEDS_REPAIR, WirelessLink.stateOf(enrolled = false, report = report))
    }

    @Test
    fun reconnectWithEnrollmentSkipsOnlyThePairCodeStep() {
        val runner = Recorder()
        val activation = WirelessAdbActivation()
        val report = ActivationSequence(runner, activation).reconnect(endpoints, enrolled = true)
        assertEquals(ActivationCheck.entries - ActivationCheck.PAIR, runner.calls)
        assertTrue(report.allPassed)
        assertEquals(ActivationStatus.ACTIVE, activation.status)
    }

    @Test
    fun keyMismatchOnConnectIsNeedsRepairAndNothingActivates() {
        val activation = WirelessAdbActivation()
        val report =
            ActivationSequence(Recorder(StepSignal.SERVER_KEY_MISMATCH), activation)
                .reconnect(endpoints, enrolled = true)
        val failure = report.firstFailure as ActivationFailure.NeedsRepair
        assertTrue(failure.mismatch)
        assertEquals(ActivationCheck.CONNECT, failure.check)
        assertEquals(ActivationStatus.ACTIVATION_REQUIRED, activation.status)
        assertEquals(WirelessLinkState.NEEDS_REPAIR, WirelessLink.stateOf(true, report))
        assertTrue(report.outcomeOf(ActivationCheck.HELPER_START) is CheckOutcome.NotRun)
    }

    @Test
    fun theFourPlainLanguageStates() {
        val ok = ActivationSequence(Recorder()).reconnect(endpoints, true)
        val refused = ActivationSequence(Recorder(StepSignal.PORT_REFUSED)).reconnect(endpoints, true)
        val revoked = ActivationSequence(Recorder(StepSignal.AUTH_REJECTED)).reconnect(endpoints, true)
        assertEquals(WirelessLinkState.NOT_PAIRED, WirelessLink.stateOf(false, null))
        assertEquals(WirelessLinkState.PAIRED_AND_CONNECTED, WirelessLink.stateOf(true, ok))
        assertEquals(WirelessLinkState.CONNECTION_LOST, WirelessLink.stateOf(true, refused))
        assertEquals(WirelessLinkState.CONNECTION_LOST, WirelessLink.stateOf(true, null))
        assertEquals(WirelessLinkState.NEEDS_REPAIR, WirelessLink.stateOf(true, revoked))
    }

    @Test
    fun repairGuidanceNamesAndroidsOwnScreens() {
        listOf(true, false).forEach { mismatch ->
            val text = ActivationFailure.NeedsRepair(ActivationCheck.CONNECT, mismatch).guidance
            assertTrue(text.contains(AndroidSettingsNames.WIRELESS_DEBUGGING))
            assertTrue(text.contains(AndroidSettingsNames.PAIR_DEVICE_WITH_PAIRING_CODE))
            assertTrue(text.contains(AndroidSettingsNames.PAIRING_CODE))
        }
    }

    @Test
    fun forReconnectEndpointsNeverLookLikePortConfusion() {
        listOf(1, 2, 42_137, 65_535).forEach { assertFalse(WirelessAdbEndpoints.forReconnect(it).isPortConfusion) }
    }

    @Test
    fun helperStartCommandIsExactlyTheStarterWithTheApk() {
        val base = "/data/app/~~Rps3-2D12uiyBIB5jhdfTQ==/ai.eqo.app-UysQ5vmE70Yrbi7v5QdHKw=="
        val command = HelperStartCommand.build("$base/lib/arm64", "$base/base.apk")
        assertEquals("$base/lib/arm64/libeqo-starter.so --apk=$base/base.apk", command)
        assertTrue(HelperStartCommand.isHelperStart(command))
    }

    @Test
    fun helperStartCommandRejectsShellMetacharactersAndOtherCommands() {
        listOf("/lib; id", "/lib && id", "/lib\$(id)", "/lib`id`", "lib/relative", "/lib/../x", "/lib x", "").forEach {
            assertThrows(IllegalArgumentException::class.java) { HelperStartCommand.build(it, "/data/app/base.apk") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            HelperStartCommand.build("/lib", "/data/app/base.apk; id")
        }
        assertThrows(IllegalArgumentException::class.java) { HelperStartCommand.build("/lib", "/data/app/other.bin") }
        listOf("id", "getprop", "/lib/libeqo-starter.so --apk=/a.apk; id", "rm -rf /sdcard").forEach {
            assertFalse(HelperStartCommand.isHelperStart(it))
        }
    }

    @Test
    fun launcherRefusesAnythingButTheHelperStartBeforeDialling() {
        val launcher = AdbShellLauncher(AdbCryptoKeyStore(File("never-created-keys")))
        assertThrows(IllegalArgumentException::class.java) { launcher.runHelperStart("127.0.0.1", 1, "id", 1) }
        assertFalse(File("never-created-keys").exists())
    }

    @Test
    fun adbShellServiceIsOnlyOpenedByTheHelperLauncher() {
        val adbServices = Regex("openShell|\"shell:|\"exec:|\"tcp:|\"reverse:|\"sync:|\"root:|remount")
        val offenders =
            File("src/main/kotlin")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "AdbProtocol.kt" }
                .filter { adbServices.containsMatchIn(it.readText()) }
                .map { it.name }
                .toList()
        assertEquals(listOf("AdbShellLauncher.kt"), offenders)
    }
}
