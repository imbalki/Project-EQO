package ai.eqo.adb.pairing

import java.net.InetAddress

/** Numeric addresses only: never resolve DNS or allow a checked host to be re-resolved. */
internal object LoopbackAdbHost {
    private const val IPV4_PARTS = 4
    private const val LOOPBACK_PREFIX = 127
    private const val MAX_OCTET = 255
    private val ipv4Octet = Regex("0|[1-9][0-9]{0,2}")
    private val ipv6Literal = Regex("[0-9a-fA-F:]+")

    fun requireAddress(host: String): InetAddress {
        val parts = host.split('.')
        val address =
            if (parts.size == IPV4_PARTS && parts.all(::isOctet)) {
                require(parts.first().toInt() == LOOPBACK_PREFIX) { "ADB host must be a loopback address" }
                InetAddress.getByAddress(parts.map { it.toInt().toByte() }.toByteArray())
            } else {
                require(host.contains(':') && ipv6Literal.matches(host)) {
                    "ADB host must be a numeric loopback address"
                }
                InetAddress.getByName(host)
            }
        require(address.isLoopbackAddress) { "ADB host must be a loopback address" }
        return address
    }

    private fun isOctet(part: String): Boolean = ipv4Octet.matches(part) && part.toInt() <= MAX_OCTET
}
