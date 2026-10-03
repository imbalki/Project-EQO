/*
 * EQO (TASK-008, issue #13): maps a raw step signal to a typed failure with guidance.
 *
 * The transport layers (extracted pairing/wire code) throw plain IOExceptions; this is
 * the one place where those become owner-facing recovery paths. Classification is a pure
 * function so every mapping is host-testable without a device.
 */
package ai.eqo.adb.pairing

/** Raw signals a step implementation may report. */
enum class StepSignal {
    /** The pairing handshake rejected the PSK (wrong or expired code). */
    PAIRING_CODE_REJECTED,

    /** Typed input is not a six-digit code. */
    MALFORMED_CODE,

    /** Nothing is listening on the port that was tried. */
    PORT_REFUSED,

    /** adbd answered but would not authorize our key (revoked / rotated). */
    AUTH_REJECTED,

    /** Wireless debugging is off — reboot, manual toggle or revocation. */
    WIRELESS_DEBUGGING_OFF,

    /** The phone is on a different Wi-Fi network than at pairing time. */
    NETWORK_CHANGED,

    /** The privileged helper process did not start. */
    HELPER_NOT_STARTED,

    /** The privileged helper is running but EQO is not authorized against it. */
    HELPER_NOT_AUTHORIZED,

    /** The helper binder is dead. */
    BINDER_DEAD,
}

/** Thrown by [ActivationStepRunner] implementations to report a specific signal. */
class StepSignalException(
    val signal: StepSignal,
    val detail: String = signal.name,
    cause: Throwable? = null,
) : Exception("activation step signal: ${signal.name} ($detail)", cause)

object FailureClassifier {
    fun classify(
        check: ActivationCheck,
        signal: StepSignal,
        endpoints: WirelessAdbEndpoints? = null,
        detail: String = signal.name,
    ): ActivationFailure =
        when (signal) {
            StepSignal.PAIRING_CODE_REJECTED -> ActivationFailure.WrongCode(check)
            StepSignal.MALFORMED_CODE -> ActivationFailure.MalformedCode(detail)
            StepSignal.PORT_REFUSED -> ActivationFailure.PortConfusion(endpoints ?: UNKNOWN_ENDPOINTS)
            StepSignal.AUTH_REJECTED -> ActivationFailure.PairingRevoked(check)
            StepSignal.WIRELESS_DEBUGGING_OFF -> ActivationFailure.DeviceRebooted(check)
            StepSignal.NETWORK_CHANGED -> ActivationFailure.WifiChanged(check)
            StepSignal.HELPER_NOT_STARTED,
            StepSignal.HELPER_NOT_AUTHORIZED,
            StepSignal.BINDER_DEAD,
            -> ActivationFailure.StepFailed(check, detail)
        }

    /** Placeholder ports for a refusal that happened before any endpoint was parsed. */
    private val UNKNOWN_ENDPOINTS = WirelessAdbEndpoints(pairingPort = 1, connectionPort = 1)
}
