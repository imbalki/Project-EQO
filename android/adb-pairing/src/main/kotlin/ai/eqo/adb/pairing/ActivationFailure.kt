/*
 * EQO (TASK-008, issue #13): typed activation failures with recovery guidance.
 *
 * Acceptance criterion: "wrong code, port confusion, revoke, reboot and Wi-Fi change are
 * each tested and recover with guidance, not silently". Every failure therefore carries a
 * non-empty `guidance` that names the Android-owned settings screens verbatim, and the
 * failure surface is closed: a new failure mode must be added here and classified in
 * [FailureClassifier] instead of being swallowed into a generic error.
 */
package ai.eqo.adb.pairing

sealed class ActivationFailure {
    /** The check that produced this failure. */
    abstract val check: ActivationCheck

    /** Recovery guidance shown to the owner. Never empty — this is the "not silently" contract. */
    abstract val guidance: String

    /** The six-digit code was rejected by the pairing handshake (wrong or expired code). */
    data class WrongCode(
        override val check: ActivationCheck,
    ) : ActivationFailure() {
        override val guidance: String =
            "The ${AndroidSettingsNames.PAIRING_CODE} was rejected. Codes expire as soon as the " +
                "${AndroidSettingsNames.PAIR_DEVICE_WITH_PAIRING_CODE} dialog closes, so open it again " +
                "on the ${AndroidSettingsNames.WIRELESS_DEBUGGING} screen and type the fresh code and port."
    }

    /** What the owner typed is not a six-digit pairing code. */
    data class MalformedCode(
        val raw: String,
    ) : ActivationFailure() {
        override val check: ActivationCheck = ActivationCheck.PAIR
        override val guidance: String =
            "Enter the six-digit ${AndroidSettingsNames.PAIRING_CODE} shown in the " +
                "${AndroidSettingsNames.PAIR_DEVICE_WITH_PAIRING_CODE} dialog, digits only."
    }

    /** The pairing port and the connection port were confused (or one of them is stale). */
    data class PortConfusion(
        val endpoints: WirelessAdbEndpoints,
    ) : ActivationFailure() {
        override val check: ActivationCheck = ActivationCheck.PAIR
        override val guidance: String =
            "The pairing port and the connection port are different ports. The pairing port is the one " +
                "inside the ${AndroidSettingsNames.PAIR_DEVICE_WITH_PAIRING_CODE} dialog, next to the " +
                "${AndroidSettingsNames.PAIRING_CODE}. The connection port is the " +
                "${AndroidSettingsNames.IP_ADDRESS_AND_PORT} value on the " +
                "${AndroidSettingsNames.WIRELESS_DEBUGGING} screen itself. Read both again and re-enter them."
    }

    /** adbd no longer trusts our key (owner revoked, or keys were rotated). */
    data class PairingRevoked(
        override val check: ActivationCheck,
    ) : ActivationFailure() {
        override val guidance: String =
            "This device no longer trusts EQO's key — usually after " +
                "${AndroidSettingsNames.REVOKE_USB_DEBUGGING_AUTHORIZATIONS}. Pair again from the " +
                "${AndroidSettingsNames.WIRELESS_DEBUGGING} screen using " +
                "${AndroidSettingsNames.PAIR_DEVICE_WITH_PAIRING_CODE}."
    }

    /** Wireless debugging is off or the ports are stale after a reboot. */
    data class DeviceRebooted(
        override val check: ActivationCheck,
    ) : ActivationFailure() {
        override val guidance: String =
            "Ports and pairing state do not survive a reboot. Open " +
                "${AndroidSettingsNames.DEVELOPER_OPTIONS} > ${AndroidSettingsNames.WIRELESS_DEBUGGING}, " +
                "turn it on again, and read the new ${AndroidSettingsNames.IP_ADDRESS_AND_PORT} value."
    }

    /** The phone moved to a different Wi-Fi network; wireless debugging is network-scoped. */
    data class WifiChanged(
        override val check: ActivationCheck,
    ) : ActivationFailure() {
        override val guidance: String =
            "The ${AndroidSettingsNames.WIFI} network changed. Reconnect to the same ${AndroidSettingsNames.WIFI} " +
                "network, reopen ${AndroidSettingsNames.WIRELESS_DEBUGGING} and use the new " +
                "${AndroidSettingsNames.IP_ADDRESS_AND_PORT} value."
    }

    /** The check failed for a reason without a more specific recovery path. */
    data class StepFailed(
        override val check: ActivationCheck,
        val detail: String,
    ) : ActivationFailure() {
        override val guidance: String =
            "Step ${check.name} failed: $detail. Repeat the step from the " +
                "${AndroidSettingsNames.WIRELESS_DEBUGGING} screen; if it keeps failing, toggle " +
                "${AndroidSettingsNames.WIRELESS_DEBUGGING} off and on and retry."
    }
}
