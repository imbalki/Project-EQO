package ai.eqo.onboarding

import android.content.Context
import android.net.Network
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/** Bounded, interface-bound DNS-SD fallback. No names, ports, codes or addresses are logged. */
@Suppress("DEPRECATION") // MulticastSocket is required on API 30; Network scopes its underlying socket.
internal class WifiMdnsResolver(
    context: Context,
) : AutoCloseable {
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    @Volatile
    private var active: MulticastSocket? = null
    private var closed = false

    fun resolve(
        network: Network,
        interfaceName: String?,
        instance: String,
        addresses: Set<InetAddress>,
    ): Pair<InetAddress, Int>? =
        runCatching {
            synchronized(this) { if (closed) return@runCatching null }
            val iface = interfaceName?.let { NetworkInterface.getByName(it) } ?: return@runCatching null
            val lock = wifi.createMulticastLock("eqo-mdns").apply { setReferenceCounted(false) }
            lock.acquire()
            try {
                MulticastSocket(null).use { socket ->
                    synchronized(this) {
                        if (closed) return@runCatching null
                        active = socket
                    }
                    socket.reuseAddress = true
                    network.bindSocket(socket)
                    socket.bind(InetSocketAddress(MDNS_PORT))
                    socket.networkInterface = iface
                    socket.timeToLive = MDNS_TTL
                    val group = InetSocketAddress(InetAddress.getByName("224.0.0.251"), MDNS_PORT)
                    socket.joinGroup(group, iface)
                    socket.soTimeout = RECEIVE_TIMEOUT_MS
                    receive(socket, group, instance, addresses)
                }
            } finally {
                active = null
                if (lock.isHeld) lock.release()
            }
        }.getOrNull()

    private fun receive(
        socket: MulticastSocket,
        group: InetSocketAddress,
        instance: String,
        addresses: Set<InetAddress>,
    ): Pair<InetAddress, Int>? {
        val records = mutableListOf<MdnsMessage.Record>()
        val end = System.nanoTime() + RESOLVE_NS
        var nextQuery = 0L
        while (System.nanoTime() < end && !Thread.currentThread().isInterrupted) {
            if (System.nanoTime() >= nextQuery) {
                val targets = records.filter { it.name == MdnsMessage.canonical(instance) }.mapNotNull { it.target }
                val questions =
                    listOf(instance to MdnsMessage.SRV) +
                        targets.distinct().take(1).flatMap { listOf(it to MdnsMessage.A, it to MdnsMessage.AAAA) }
                val query = MdnsMessage.query(questions)
                socket.send(DatagramPacket(query, query.size, group))
                nextQuery = System.nanoTime() + QUERY_NS
            }
            val packet = DatagramPacket(ByteArray(MdnsMessage.MAX_PACKET + 1), MdnsMessage.MAX_PACKET + 1)
            try {
                socket.receive(packet)
                if (packet.port != MDNS_PORT) continue
                val incoming = MdnsMessage.parse(packet.data.copyOf(packet.length))
                merge(records, incoming)
                MdnsMessage.ownEndpoint(records, instance, addresses)?.let { return it }
            } catch (_: SocketTimeoutException) {
                // Retry SRV and target address questions within the fixed deadline.
            }
        }
        return null
    }

    private fun merge(
        records: MutableList<MdnsMessage.Record>,
        incoming: List<MdnsMessage.Record>,
    ) {
        incoming.forEach { record ->
            records.removeAll {
                it.name == record.name &&
                    it.type == record.type &&
                    (record.type == MdnsMessage.SRV || it.address == record.address)
            }
            if (record.ttl > 0 && records.size < MAX_CACHED_RECORDS) records.add(record)
        }
    }

    override fun close() {
        synchronized(this) {
            closed = true
            active?.close()
        }
    }

    private companion object {
        const val MDNS_PORT = 5353
        const val MDNS_TTL = 255
        const val RECEIVE_TIMEOUT_MS = 250
        const val MAX_CACHED_RECORDS = 256
        const val RESOLVE_NS = 4_000_000_000L
        const val QUERY_NS = 500_000_000L
    }
}

/** One winner per service. A framework failure/timeout falls back exactly once; late callbacks are ignored. */
internal class WirelessResolveAttempt<T>(
    private val fallback: () -> Unit,
    private val complete: (T?) -> Unit,
) {
    private var fallbackStarted = false
    private var completed = false

    fun nsd(value: T?) {
        if (completed || fallbackStarted) return
        if (value != null) finish(value) else startFallback()
    }

    fun startFallback() {
        if (completed || fallbackStarted) return
        fallbackStarted = true
        fallback()
    }

    fun mdns(value: T?) {
        if (fallbackStarted) finish(value)
    }

    private fun finish(value: T?) {
        if (completed) return
        completed = true
        complete(value)
    }
}
