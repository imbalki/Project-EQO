/*
 * EQO (TASK-008, issue #13): the activation sequence — five separately failing checks.
 *
 * Scope: "Each check (pair, connect, helper start, authorize, binder health) is a
 * separate, individually failing step." This runs [ActivationCheck] in declaration order,
 * records a per-check outcome, never fakes a pass for a check that did not run, and only
 * flips the gate to ACTIVE when every check has passed.
 */
package ai.eqo.adb.pairing

/** Everything the on-phone flow collects from the Android screens. */
data class PairingInput(
    val endpoints: WirelessAdbEndpoints,
    val code: AdbPairingCode,
)

/** Device-side implementations of the five checks. Each reports [StepSignalException] on failure. */
interface ActivationStepRunner {
    fun pair(
        endpoints: WirelessAdbEndpoints,
        code: AdbPairingCode,
    )

    fun connect(endpoints: WirelessAdbEndpoints)

    fun startHelper()

    fun authorizeHelper()

    fun checkBinder()
}

sealed class CheckOutcome {
    object Passed : CheckOutcome()

    data class Failed(
        val failure: ActivationFailure,
    ) : CheckOutcome()

    /** The check never ran because [because] failed first. Explicit, never silent. */
    data class NotRun(
        val because: ActivationCheck,
    ) : CheckOutcome()
}

data class CheckRecord(
    val check: ActivationCheck,
    val outcome: CheckOutcome,
)

data class ActivationReport(
    val records: List<CheckRecord>,
) {
    val firstFailure: ActivationFailure?
        get() = records.firstNotNullOfOrNull { (it.outcome as? CheckOutcome.Failed)?.failure }

    val allPassed: Boolean
        get() =
            records.size == ActivationCheck.entries.size &&
                records.all { it.outcome is CheckOutcome.Passed }

    fun outcomeOf(check: ActivationCheck): CheckOutcome? = records.firstOrNull { it.check == check }?.outcome
}

class ActivationSequence(
    private val runner: ActivationStepRunner,
    private val activation: WirelessAdbActivation = WirelessAdbActivation(),
) {
    fun run(input: PairingInput): ActivationReport =
        runAll(input.endpoints) {
            if (input.endpoints.isPortConfusion) {
                throw StepSignalException(StepSignal.PORT_REFUSED, "pairing port equals connection port")
            }
            runner.pair(input.endpoints, input.code)
        }

    /**
     * TASK-080: connect again after an earlier successful pairing, without a new code. The PAIR
     * check passes only if [enrolled] says an enrollment exists; otherwise it fails with
     * [StepSignal.SERVER_NOT_ENROLLED] and nothing else runs (fail closed). The connect step
     * still pins the server key, so this path cannot reach a device that was not paired.
     */
    fun reconnect(
        endpoints: WirelessAdbEndpoints,
        enrolled: Boolean,
    ): ActivationReport =
        runAll(endpoints) {
            if (!enrolled) throw StepSignalException(StepSignal.SERVER_NOT_ENROLLED, "no enrollment")
        }

    @Suppress("TooGenericExceptionCaught") // unexpected failures surface as StepFailed, never crash
    private fun runAll(
        endpoints: WirelessAdbEndpoints,
        pairStep: () -> Unit,
    ): ActivationReport {
        val records = mutableListOf<CheckRecord>()
        var failed: ActivationCheck? = null

        for (check in ActivationCheck.entries) {
            val blocker = failed
            if (blocker != null) {
                records += CheckRecord(check, CheckOutcome.NotRun(blocker))
                continue
            }
            val outcome =
                try {
                    runCheck(check, endpoints, pairStep)
                    CheckOutcome.Passed
                } catch (e: StepSignalException) {
                    CheckOutcome.Failed(
                        FailureClassifier.classify(check, e.signal, endpoints, e.detail),
                    )
                } catch (t: Throwable) {
                    // Deliberate: an unexpected failure must surface as a visible step
                    // failure with guidance, never crash the activation flow or pass silently.
                    CheckOutcome.Failed(
                        ActivationFailure.StepFailed(check, t.message ?: t.javaClass.simpleName),
                    )
                }
            records += CheckRecord(check, outcome)
            if (outcome is CheckOutcome.Failed) failed = check
        }

        val report = ActivationReport(records)
        if (report.allPassed) activation.markActive()
        return report
    }

    private fun runCheck(
        check: ActivationCheck,
        endpoints: WirelessAdbEndpoints,
        pairStep: () -> Unit,
    ) {
        when (check) {
            ActivationCheck.PAIR -> pairStep()
            ActivationCheck.CONNECT -> runner.connect(endpoints)
            ActivationCheck.HELPER_START -> runner.startHelper()
            ActivationCheck.AUTHORIZE -> runner.authorizeHelper()
            ActivationCheck.BINDER_HEALTH -> runner.checkBinder()
        }
    }
}
