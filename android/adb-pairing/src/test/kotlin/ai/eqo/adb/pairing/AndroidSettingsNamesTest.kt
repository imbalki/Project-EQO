package ai.eqo.adb.pairing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guard test: the guide must show Android-owned settings names verbatim. Each literal
 * here is asserted against the exact string Android renders, so a paraphrase fails the
 * build (acceptance criterion: "Android-owned settings names shown verbatim").
 */
class AndroidSettingsNamesTest {
    @Test
    fun developerOptionsIsVerbatim() {
        assertEquals("Developer options", AndroidSettingsNames.DEVELOPER_OPTIONS)
    }

    @Test
    fun buildNumberIsVerbatim() {
        assertEquals("Build number", AndroidSettingsNames.BUILD_NUMBER)
    }

    @Test
    fun networkAndInternetIsVerbatim() {
        assertEquals("Network & internet", AndroidSettingsNames.NETWORK_AND_INTERNET)
    }

    @Test
    fun wifiIsVerbatim() {
        assertEquals("Wi-Fi", AndroidSettingsNames.WIFI)
    }

    @Test
    fun wirelessDebuggingIsVerbatim() {
        assertEquals("Wireless debugging", AndroidSettingsNames.WIRELESS_DEBUGGING)
    }

    @Test
    fun pairDeviceWithPairingCodeIsVerbatim() {
        assertEquals("Pair device with pairing code", AndroidSettingsNames.PAIR_DEVICE_WITH_PAIRING_CODE)
    }

    @Test
    fun pairingCodeLabelIsVerbatim() {
        assertEquals("Pairing code", AndroidSettingsNames.PAIRING_CODE)
    }

    @Test
    fun ipAddressAndPortIsVerbatim() {
        assertEquals("IP address & Port", AndroidSettingsNames.IP_ADDRESS_AND_PORT)
    }

    @Test
    fun revokeUsbDebuggingAuthorizationsIsVerbatim() {
        assertEquals(
            "Revoke USB debugging authorizations",
            AndroidSettingsNames.REVOKE_USB_DEBUGGING_AUTHORIZATIONS,
        )
    }
}
