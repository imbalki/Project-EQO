/*
 * EQO (TASK-008, issue #13): the two wireless-ADB ports, kept apart.
 *
 * The pairing port and the connection port are DIFFERENT ports and are shown in two
 * different places on the phone:
 *   - pairing port:     inside the "Pair device with pairing code" dialog, next to the
 *                       "Pairing code". It exists only while that dialog is open.
 *   - connection port:  the "IP address & Port" value on the "Wireless debugging" screen
 *                       itself. It changes when wireless debugging is toggled, after a
 *                       reboot, or when the Wi-Fi network changes.
 * Mixing them up is the single most common guided-pairing failure (port confusion), so
 * the pair value object keeps both explicit and offers a confusion check before any
 * network call is attempted.
 */
package ai.eqo.adb.pairing

data class WirelessAdbEndpoints(
    val pairingPort: Int,
    val connectionPort: Int,
) {
    init {
        require(pairingPort in PORT_MIN..PORT_MAX) { "pairing port out of range: $pairingPort" }
        require(connectionPort in PORT_MIN..PORT_MAX) { "connection port out of range: $connectionPort" }
    }

    /**
     * True when both ports are identical. adbd never exposes the same port for pairing and
     * for connecting, so equality always means one of the two values was read from the wrong
     * place on screen.
     */
    val isPortConfusion: Boolean get() = pairingPort == connectionPort

    companion object {
        const val PORT_MIN = 1
        const val PORT_MAX = 65_535
    }
}
