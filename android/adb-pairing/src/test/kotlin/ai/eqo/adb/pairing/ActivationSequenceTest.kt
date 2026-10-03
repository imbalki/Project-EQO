package ai.eqo.adb.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Each check (pair, connect, helper start, authorize, binder health) is a separate,
 * individually failing step" — proven here per check, plus the five recovery scenarios
 * (wrong code, port confusion, revoke, reboot, Wi-Fi change) surfacing as guidance.
 */
class ActivationSequenceTest {
    private val input =
        PairingInput(
            endpoints = WirelessAdbEndpoints(pairingPort = 37_123, connectionPort = 42_137),
            code = requireNotNull(code("123456")),
        )

    private class FakeRunner(
        private val failAt: ActivationCheck? = null,
        private val signal: StepSignal = StepSignal.PAIRING_CODE_REJECTED,
    ) : ActivationStepRunner {
        val calls = mutableListOf<ActivationCheck>()

        private fun step(check: ActivationCheck) {
            calls += check
            if (check == failAt) throw StepSignalException(signal, "synthetic failure at ${check.name}")
        }

        override fun pair(
            endpoints: WirelessAdbEndpoints,
            code: AdbPairingCode,
        ) = step(ActivationCheck.PAIR)

        override fun connect(endpoints: WirelessAdbEndpoints) = step(ActivationCheck.CONNECT)

        override fun startHelper() = step(ActivationCheck.HELPER_START)

        override fun authorizeHelper() = step(ActivationCheck.AUTHORIZE)

        override fun checkBinder() = step(ActivationCheck.BINDER_HEALTH)
    }

    @Test
    fun allFiveChecksRunInOrderAndActivate() {
        val activation = WirelessAdbActivation()
        val runner = FakeRunner()
        val report = ActivationSequence(runner, activation).run(input)

        assertEquals(ActivationCheck.entries, runner.calls)
        assertTrue(report.allPassed)
        assertEquals(null, report.firstFailure)
        assertEquals(ActivationStatus.ACTIVE, activation.status)
    }

    @Test
    fun everyCheckFailsIndependentlyWithoutFakingLaterSteps() {
        for (failing in ActivationCheck.entries) {
            val activation = WirelessAdbActivation()
            val runner = FakeRunner(failAt = failing)
            val report = ActivationSequence(runner, activation).run(input)

            assertEquals(
                "calls up to $failing",
                ActivationCheck.entries.takeWhile { it != failing } + failing,
                runner.calls,
            )
            assertTrue("record for $failing", report.outcomeOf(failing) is CheckOutcome.Failed)
            for (later in ActivationCheck.entries.filter { it.ordinal > failing.ordinal }) {
                val outcome = report.outcomeOf(later)
                assertTrue("later check $later must be NotRun", outcome is CheckOutcome.NotRun)
                assertEquals(failing, (outcome as CheckOutcome.NotRun).because)
            }
            for (earlier in ActivationCheck.entries.filter { it.ordinal < failing.ordinal }) {
                assertTrue("earlier check $earlier must pass", report.outcomeOf(earlier) is CheckOutcome.Passed)
            }
            assertEquals(
                "gate must not flip on partial success",
                ActivationStatus.ACTIVATION_REQUIRED,
                activation.status,
            )
            assertTrue("failure must carry guidance", requireNotNull(report.firstFailure).guidance.isNotBlank())
        }
    }

    @Test
    fun wrongCodeScenarioRecoversWithGuidanceNotSilently() {
        val report = ActivationSequence(FakeRunner(ActivationCheck.PAIR), WirelessAdbActivation()).run(input)
        val failure = requireNotNull(report.firstFailure)
        assertTrue(failure is ActivationFailure.WrongCode)
        assertTrue(failure.guidance.contains(AndroidSettingsNames.PAIR_DEVICE_WITH_PAIRING_CODE))
    }

    @Test
    fun portConfusionIsCaughtBeforeAnyNetworkCall() {
        val confused = input.copy(endpoints = WirelessAdbEndpoints(pairingPort = 42_137, connectionPort = 42_137))
        val runner = FakeRunner()
        val report = ActivationSequence(runner, WirelessAdbActivation()).run(confused)

        assertEquals(emptyList<ActivationCheck>(), runner.calls)
        val failure = requireNotNull(report.firstFailure)
        assertTrue(failure is ActivationFailure.PortConfusion)
        assertTrue(failure.guidance.contains(AndroidSettingsNames.IP_ADDRESS_AND_PORT))
    }

    @Test
    fun revokeScenarioRecoversWithGuidance() {
        val report =
            ActivationSequence(
                FakeRunner(ActivationCheck.CONNECT, StepSignal.AUTH_REJECTED),
                WirelessAdbActivation(),
            ).run(input)
        val failure = requireNotNull(report.firstFailure)
        assertTrue(failure is ActivationFailure.PairingRevoked)
        assertTrue(failure.guidance.contains(AndroidSettingsNames.REVOKE_USB_DEBUGGING_AUTHORIZATIONS))
    }

    @Test
    fun rebootScenarioRecoversWithGuidance() {
        val report =
            ActivationSequence(
                FakeRunner(ActivationCheck.CONNECT, StepSignal.WIRELESS_DEBUGGING_OFF),
                WirelessAdbActivation(),
            ).run(input)
        val failure = requireNotNull(report.firstFailure)
        assertTrue(failure is ActivationFailure.DeviceRebooted)
        assertTrue(failure.guidance.contains(AndroidSettingsNames.WIRELESS_DEBUGGING))
    }

    @Test
    fun wifiChangeScenarioRecoversWithGuidance() {
        val report =
            ActivationSequence(
                FakeRunner(ActivationCheck.CONNECT, StepSignal.NETWORK_CHANGED),
                WirelessAdbActivation(),
            ).run(input)
        val failure = requireNotNull(report.firstFailure)
        assertTrue(failure is ActivationFailure.WifiChanged)
        assertTrue(failure.guidance.contains(AndroidSettingsNames.WIFI))
    }

    @Test
    fun unexpectedExceptionIsSurfacedAsStepFailedNotSwallowed() {
        val runner =
            object : ActivationStepRunner {
                override fun pair(
                    endpoints: WirelessAdbEndpoints,
                    code: AdbPairingCode,
                ) = error("boom")

                override fun connect(endpoints: WirelessAdbEndpoints) = Unit

                override fun startHelper() = Unit

                override fun authorizeHelper() = Unit

                override fun checkBinder() = Unit
            }
        val report = ActivationSequence(runner, WirelessAdbActivation()).run(input)
        val failure = requireNotNull(report.firstFailure)
        assertTrue(failure is ActivationFailure.StepFailed)
        assertEquals("boom", (failure as ActivationFailure.StepFailed).detail)
    }

    private fun code(raw: String): AdbPairingCode? {
        val parsed = AdbPairingCode.parse(raw)
        return (parsed as? AdbPairingCode.ParseResult.Ok)?.code
    }
}
