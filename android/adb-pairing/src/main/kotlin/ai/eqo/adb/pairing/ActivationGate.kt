/*
 * EQO (TASK-008, issue #13): the activation gate that refuses privileged entry points.
 *
 * Acceptance criterion: "Fresh install shows 'activation required'; privileged entry
 * points refuse with guidance until done". This gate is the wireless-ADB pairing auth
 * plane only — pairing, helper authorization, accessibility and CDP consent are separate
 * planes and one passing proves nothing about the others (task Notes).
 */
package ai.eqo.adb.pairing

enum class ActivationStatus {
    /** Fresh install, or activation has not completed. */
    ACTIVATION_REQUIRED,

    /** Every activation check has passed on this install. */
    ACTIVE,
}

/** Outcome of a privileged entry point asking the gate for access. */
sealed class PrivilegedResult<out T> {
    data class Allowed<T>(
        val value: T,
    ) : PrivilegedResult<T>()

    data class Refused(
        val feature: String,
        val guidance: String,
    ) : PrivilegedResult<Nothing>()
}

interface ActivationGate {
    val status: ActivationStatus

    /** Runs [block] only when this auth plane is active; otherwise refuses with guidance. */
    fun <T> runPrivileged(
        feature: String,
        block: () -> T,
    ): PrivilegedResult<T>
}

/**
 * Install-scoped wireless-ADB activation state. Starts at [ActivationStatus.ACTIVATION_REQUIRED]
 * (fresh install) and only flips to [ActivationStatus.ACTIVE] when [markActive] is called by
 * [ActivationSequence] after all five checks pass. Nothing here is persisted yet — process
 * death returns to ACTIVATION_REQUIRED, which fails closed.
 */
class WirelessAdbActivation : ActivationGate {
    @Volatile
    override var status: ActivationStatus = ActivationStatus.ACTIVATION_REQUIRED
        private set

    /** Shown by the launcher UI while activation is pending (device-asserted non-empty). */
    val activationRequiredMessage: String = "Activation required"

    fun markActive() {
        status = ActivationStatus.ACTIVE
    }

    override fun <T> runPrivileged(
        feature: String,
        block: () -> T,
    ): PrivilegedResult<T> =
        if (status == ActivationStatus.ACTIVE) {
            PrivilegedResult.Allowed(block())
        } else {
            PrivilegedResult.Refused(feature, refusalGuidance(feature))
        }

    fun refusalGuidance(feature: String): String =
        "$feature needs wireless-ADB pairing activation first: $activationRequiredMessage. " +
            "Follow the pairing guide (${AndroidSettingsNames.DEVELOPER_OPTIONS} > " +
            "${AndroidSettingsNames.WIRELESS_DEBUGGING} > " +
            "${AndroidSettingsNames.PAIR_DEVICE_WITH_PAIRING_CODE}) and finish every step."
}
