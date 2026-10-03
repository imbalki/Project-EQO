/*
 * EQO (TASK-008, issue #13): the first-run guided pairing flow, entirely on the phone.
 *
 * Every instruction names Android-owned settings screens verbatim
 * ([AndroidSettingsNames]); nothing is paraphrased. The guide is data, not UI, so the
 * step text can be host-tested and rendered by any surface (launcher screen, dialog,
 * study APK onboarding in TASK-015).
 */
package ai.eqo.adb.pairing

data class GuideStep(
    val id: String,
    val instruction: String,
    /** The Android-owned name this step shows verbatim, when it names one. */
    val androidName: String? = null,
)

object PairingGuide {
    val steps: List<GuideStep> =
        listOf(
            GuideStep(
                id = "developer-options",
                instruction =
                    "Open ${AndroidSettingsNames.DEVELOPER_OPTIONS}. If it is missing, tap " +
                        "${AndroidSettingsNames.BUILD_NUMBER} in About phone seven times first.",
                androidName = AndroidSettingsNames.DEVELOPER_OPTIONS,
            ),
            GuideStep(
                id = "wifi",
                instruction =
                    "Connect the phone to Wi-Fi: ${AndroidSettingsNames.NETWORK_AND_INTERNET} > " +
                        "${AndroidSettingsNames.WIFI}. Wireless debugging only works on the network you pair on.",
                androidName = AndroidSettingsNames.WIFI,
            ),
            GuideStep(
                id = "wireless-debugging",
                instruction =
                    "Turn on ${AndroidSettingsNames.WIRELESS_DEBUGGING} in " +
                        "${AndroidSettingsNames.DEVELOPER_OPTIONS}. Leave that screen open — it shows the " +
                        "${AndroidSettingsNames.IP_ADDRESS_AND_PORT} value EQO needs for the connection step.",
                androidName = AndroidSettingsNames.WIRELESS_DEBUGGING,
            ),
            GuideStep(
                id = "pairing-code",
                instruction =
                    "Tap ${AndroidSettingsNames.PAIR_DEVICE_WITH_PAIRING_CODE}. The dialog shows a " +
                        "six-digit ${AndroidSettingsNames.PAIRING_CODE} and its own " +
                        "${AndroidSettingsNames.IP_ADDRESS_AND_PORT} value. That port is the PAIRING " +
                        "port — it is NOT the connection port from the previous step.",
                androidName = AndroidSettingsNames.PAIR_DEVICE_WITH_PAIRING_CODE,
            ),
            GuideStep(
                id = "enter-code-and-ports",
                instruction =
                    "Type the ${AndroidSettingsNames.PAIRING_CODE}, then the pairing port and the " +
                        "connection port, into EQO. Keep the dialog open until EQO reports the pair step passed.",
                androidName = AndroidSettingsNames.PAIRING_CODE,
            ),
            GuideStep(
                id = "verify",
                instruction =
                    "EQO runs five checks in order: pair, connect, helper start, authorize and " +
                        "binder health. Each fails on its own with its own recovery guidance; a passing check " +
                        "proves nothing about the next one.",
            ),
        )
}
