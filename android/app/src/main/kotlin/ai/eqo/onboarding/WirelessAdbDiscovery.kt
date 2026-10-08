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

/** Discover while Settings is foreground; never select another device on the same LAN. */
@Suppress("DEPRECATION") // API 30 devices require the listener-based resolve API.
internal class WirelessAdbDiscovery(
    context: Context,
    private val state: WirelessDiscoveryState,
    private val changed: () -> Unit,
) : AutoCloseable {
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
                        if (!closed && epoch == generation) {
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
        val (info, epoch) = pending.removeFirst()
        val key = info.serviceType + info.serviceName
        lostServices.remove(key)
        resolving = true
        val listener =
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int,
                ) = finished(null)

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) = finished(serviceInfo)

                private fun finished(resolved: NsdServiceInfo?) {
                    main.post {
                        resolving = false
                        val eligible = !closed && epoch == generation && key !in lostServices
                        if (eligible && resolved != null) {
                            val scopedNetwork =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) resolved.network else wifi
                            state.found(
                                key,
                                resolved.serviceType,
                                resolved.host?.hostAddress,
                                resolved.port,
                                scopedNetwork?.toString(),
                            )
                            changed()
                        }
                        resolveNext()
                    }
                }
            }
        runCatching { nsd.resolveService(info, listener) }.onFailure {
            resolving = false
            resolveNext()
        }
    }

    private fun stopDiscovery() {
        main.removeCallbacks(timeout)
        listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        listeners.clear()
        pending.clear()
        lostServices.clear()
    }

    override fun close() {
        closed = true
        generation++
        stopDiscovery()
        runCatching { connectivity.unregisterNetworkCallback(callback) }
        runCatching { connectivity.unregisterNetworkCallback(defaultCallback) }
        state.networkChanged(null, emptySet())
        changed()
    }
}
