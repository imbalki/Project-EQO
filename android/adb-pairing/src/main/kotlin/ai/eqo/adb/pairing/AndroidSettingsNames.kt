/*
 * EQO (TASK-008, issue #13): Android-owned settings names, quoted verbatim.
 *
 * The activation guide must show the names exactly as Android shows them, so the owner
 * can match what EQO says against what the phone's Settings app says. Nothing here is
 * paraphrased, translated or "improved" — a change to any literal is a user-visible
 * change and must be re-verified against a real device (see the verbatim-name guard test
 * in this module's test sources).
 */
package ai.eqo.adb.pairing

object AndroidSettingsNames {
    /** Settings > System > (Developer options). */
    const val DEVELOPER_OPTIONS = "Developer options"

    /** Settings > About phone > (Build number) — tapped 7 times to unlock Developer options. */
    const val BUILD_NUMBER = "Build number"

    /** Settings > (Network & internet). */
    const val NETWORK_AND_INTERNET = "Network & internet"

    /** Settings > Network & internet > (Wi-Fi). */
    const val WIFI = "Wi-Fi"

    /** Developer options > (Wireless debugging). */
    const val WIRELESS_DEBUGGING = "Wireless debugging"

    /** The Wireless debugging overflow/menu entry that opens the pairing dialog. */
    const val PAIR_DEVICE_WITH_PAIRING_CODE = "Pair device with pairing code"

    /** Label of the six-digit code inside the pairing dialog. */
    const val PAIRING_CODE = "Pairing code"

    /** Label of the host:port shown on the Wireless debugging screen and in the pairing dialog. */
    const val IP_ADDRESS_AND_PORT = "IP address & Port"

    /** Developer options > (Revoke USB debugging authorizations). */
    const val REVOKE_USB_DEBUGGING_AUTHORIZATIONS = "Revoke USB debugging authorizations"
}
