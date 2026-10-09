package ai.eqo.onboarding

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.InetAddress
import java.util.concurrent.Executors

/** Discover while Settings is foreground; never select another device on the same LAN. */
@Suppress("DEPRECATION") // API 30 devices require the listener-based resolve API.
internal class WirelessAdbDiscovery(
    context: Context,
    private val state: WirelessDiscoveryState,
    private val changed: () -> Unit,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    private var mdns: WifiMdnsResolver? = null
    private var resolveDeadline: Runnable? = null
    private var attemptId = 0
    private val main = Handler(Looper.getMainLooper())
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val pending = ArrayDeque<Pair<NsdServiceInfo, Int>>()
    private var resolving = false
    private var generation = 0
    private var closed = false
    private var wifi: Network? = null
    private var defaultRoute: Network? = null
    private val timeout =
        Runnable {
            state.timeout()
            changed()
        }
    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()

            override fun onLost(network: Network) = refresh()

            override fun onLinkPropertiesChanged(
                network: Network,
                properties: android.net.LinkProperties,
            ) = refresh()
        }
    private val defaultCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()

            override fun onLost(network: Network) = refresh()

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) = refresh()
        }

    fun start() {
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            callback,
        )
        connectivity.registerDefaultNetworkCallback(defaultCallback)
        refresh()
    }

    private fun refresh() {
        main.post {
            if (closed) return@post
            val candidates =
                connectivity.allNetworks.filter {
                    val caps = connectivity.getNetworkCapabilities(it)
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                        !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                }
            // Ambiguous simultaneous Wi-Fi networks fail closed to manual loopback ports.
            val network = candidates.singleOrNull()
            // The old public NSD API cannot select a Network. Only start it when Wi-Fi
            // is also the default route; never discover through cellular or a VPN.
            val canScope = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            val route = connectivity.activeNetwork
            val addresses =
                network
                    ?.let { connectivity.getLinkProperties(it) }
                    ?.linkAddresses
                    ?.mapNotNull { it.address.hostAddress }
                    ?.toSet()
                    .orEmpty()
            val unchanged = wifi == network && addresses == currentAddresses && defaultRoute == route
            if (unchanged && state.networkId == network?.toString()) return@post
            stopDiscovery()
            generation++
            wifi = network
            defaultRoute = route
            currentAddresses = addresses
            state.networkChanged(network?.toString(), addresses, force = true)
            changed()
            if (network != null) {
                if (canScope || network == route) {
                    discover(WirelessDiscoveryState.PAIRING, network)
                    discover(WirelessDiscoveryState.CONNECT, network)
                }
                main.postDelayed(timeout, WirelessDiscoveryState.TIMEOUT_MS)
            }
        }
    }

    private var currentAddresses = emptySet<String>()

    private fun discover(
        type: String,
        network: Network,
    ) {
        val epoch = generation
        val listener =
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onStartDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) = Unit

                override fun onStopDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) = Unit

                override fun onServiceFound(info: NsdServiceInfo) {
                    main.post {
                        if (!closed && epoch == generation && pending.size < MAX_PENDING) {
                            pending.addLast(info to epoch)
                            resolveNext()
                        }
                    }
                }

                override fun onServiceLost(info: NsdServiceInfo) {
                    main.post {
                        if (!closed && epoch == generation) {
                            pending.removeAll {
                                it.first.serviceName == info.serviceName && it.first.serviceType == info.serviceType
                            }
                            state.lost(info.serviceType + info.serviceName)
                            lostServices.add(info.serviceType + info.serviceName)
                            changed()
                        }
                    }
                }
            }
        listeners.add(listener)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, network, contextExecutor, listener)
            } else {
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            }
        }
    }

    private val contextExecutor = java.util.concurrent.Executor { main.post(it) }
    private val lostServices = mutableSetOf<String>()

    private fun resolveNext() {
        if (closed || resolving || pending.isEmpty()) return
        val network = wifi ?: return
        val (info, epoch) = pending.removeFirst()
        val key = info.serviceType + info.serviceName
        lostServices.remove(key)
        resolving = true
        val id = ++attemptId
        val interfaceName = connectivity.getLinkProperties(network)?.interfaceName
        val addresses = currentAddresses.map { InetAddress.getByName(it) }.toSet()
        val resolver = WifiMdnsResolver(appContext)
        mdns = resolver
        lateinit var attempt: WirelessResolveAttempt<Pair<InetAddress, Int>>
        val deadline =
            Runnable {
                Log.d("EqoPairing", "resolve: nsd fail")
                attempt.startFallback()
            }
        resolveDeadline = deadline
        attempt =
            WirelessResolveAttempt(
                fallback = {
                    main.removeCallbacks(deadline)
                    worker.execute {
                        val instance = info.serviceName + "." + info.serviceType.trim('.') + ".local."
                        val result = resolver.resolve(network, interfaceName, instance, addresses)
                        main.post {
                            Log.d("EqoPairing", "resolve: mdns ${if (result != null) "ok" else "fail"}")
                            attempt.mdns(result)
                        }
                    }
                },
                complete = { result ->
                    main.removeCallbacks(deadline)
                    resolver.close()
                    val eligible = !closed && epoch == generation && key !in lostServices
                    if (eligible && result != null) {
                        state.found(key, info.serviceType, result.first.hostAddress, result.second, network.toString())
                        changed()
                    }
                    if (id == attemptId) {
                        resolving = false
                        resolveNext()
                    }
                },
            )
        val listener =
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int,
                ) = finished(null)

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) = finished(serviceInfo)

                private fun finished(resolved: NsdServiceInfo?) {
                    main.post {
                        if (closed || epoch != generation) return@post
                        val sameNetwork =
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                resolved?.network == network
                        val host = addresses.firstOrNull { it == resolved?.host }
                        val result =
                            if (sameNetwork && host != null && WirelessPairingReply.validPort(resolved?.port)) {
                                host!! to resolved!!.port
                            } else {
                                null
                            }
                        Log.d("EqoPairing", "resolve: nsd ${if (result != null) "ok" else "fail"}")
                        attempt.nsd(result)
                    }
                }
            }
        main.postDelayed(deadline, NSD_TIMEOUT_MS)
        runCatching { nsd.resolveService(info, listener) }.onFailure {
            Log.d("EqoPairing", "resolve: nsd fail")
            attempt.startFallback()
        }
    }

    private fun stopDiscovery() {
        attemptId++
        resolving = false
        resolveDeadline?.let { main.removeCallbacks(it) }
        mdns?.close()
        mdns = null
        main.removeCallbacks(timeout)
        listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        listeners.clear()
        pending.clear()
        lostServices.clear()
    }

    private companion object {
        const val MAX_PENDING = 32
        const val NSD_TIMEOUT_MS = 1_500L
    }

    override fun close() {
        closed = true
        generation++
        stopDiscovery()
        worker.shutdownNow()
        runCatching { connectivity.unregisterNetworkCallback(callback) }
        runCatching { connectivity.unregisterNetworkCallback(defaultCallback) }
        state.networkChanged(null, emptySet())
        changed()
    }
}
