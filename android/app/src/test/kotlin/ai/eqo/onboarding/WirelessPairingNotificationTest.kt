package ai.eqo.onboarding

import android.app.Notification
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkInfo
import java.net.InetAddress
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 33])
@Suppress("DEPRECATION") // Shadows configure API 30 Wi-Fi and the legacy NSD callbacks.
class WirelessPairingNotificationTest {
    @After
    fun reset() {
        WirelessPairingSession.state = WirelessDiscoveryState()
        WirelessPairingSession.discovering = false
        WirelessPairingSession.busy = false
        WirelessPairingSession.message = null
        WirelessPairingSession.cancelPendingReply = null
        WirelessPairingSession.observer = null
    }

    @Test
    fun replyIsConsumedWithoutKeepingCodeInIntent() {
        val intent = reply("001234")
        assertNotNull(intent.clipData)
        assertEquals("001234", consumeNotificationCode(intent)?.digits)
        assertNull(intent.clipData)
        assertNull(consumeNotificationCode(intent))
    }

    @Test
    fun malformedReplyIsAlsoConsumedAndDoesNotPair() {
        val intent = reply("12345")
        assertNull(consumeNotificationCode(intent))
        assertNull(intent.clipData)
    }

    @Test
    fun pairingAdvertisementOffersExplicitRemoteInputWithoutLaunchingSettingsOrConsent() {
        val controller = Robolectric.buildService(WirelessPairingService::class.java).create()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            val service = controller.get()
            val state = WirelessPairingSession.state
            state.networkChanged("fake-wifi", setOf("192.0.2.1"))
            state.found("pair", WirelessDiscoveryState.PAIRING, "192.0.2.1", 30_001, "fake-wifi")
            service.onStartCommand(Intent(service, WirelessPairingService::class.java), 0, 1)
            val manager = service.getSystemService(NotificationManager::class.java)
            val notification = shadowOf(manager).getNotification(80)
            assertEquals(Notification.VISIBILITY_SECRET, notification.visibility)
            val action = notification.actions.single { !it.remoteInputs.isNullOrEmpty() }
            assertFalse(action.allowGeneratedReplies)
            assertEquals(WirelessPairingService.CODE, action.remoteInputs.single().resultKey)
            val target = shadowOf(action.actionIntent).savedIntent
            assertEquals(WirelessPairingService::class.java.name, target.component?.className)
            assertNotNull(target.getStringExtra("session"))
            assertTrue(target.data.toString().startsWith("eqo-pairing://reply/"))
            // A stale notification cannot start a pairing run, even with valid-looking input.
            target.putExtra("session", "stale-session")
            RemoteInput.addResultsToIntent(
                action.remoteInputs,
                target,
                Bundle().apply { putCharSequence(WirelessPairingService.CODE, "001234") },
            )
            service.onStartCommand(target, 0, 2)
            assertFalse(WirelessPairingSession.busy)
            assertNull(target.clipData)
            assertNull(shadowOf(service).nextStartedActivity)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun noPairingAdvertisementStillOffersManualReplyAction() {
        val controller = Robolectric.buildService(WirelessPairingService::class.java).create()
        try {
            val manager = controller.get().getSystemService(NotificationManager::class.java)
            val notification = shadowOf(manager).getNotification(80)
            assertEquals(1, notification.actions.count { !it.remoteInputs.isNullOrEmpty() })
            assertFalse(WirelessPairingSession.busy)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun nsdCallbacksFindOnlyThisPhoneAndServiceLossInvalidatesPorts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val network = fakeWifi(context)
        val state = WirelessDiscoveryState()
        var changes = 0
        val discovery = WirelessAdbDiscovery(context, state) { changes++ }
        discovery.start()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            announce(context, network, WirelessDiscoveryState.PAIRING, 30_001)
            announce(context, network, WirelessDiscoveryState.CONNECT, 30_002)
            assertEquals(30_001, state.endpoints()?.pairingPort)
            assertEquals(30_002, state.endpoints()?.connectionPort)
            assertTrue(changes > 0)
            val nsd = shadowOf(context.getSystemService(NsdManager::class.java))
            nsd.getDiscoveryListeners(WirelessDiscoveryState.PAIRING).orEmpty().single().onServiceLost(
                NsdServiceInfo().apply {
                    serviceName = "synthetic-local"
                    serviceType = WirelessDiscoveryState.PAIRING
                },
            )
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(state.pairingPort)
        } finally {
            discovery.close()
        }
        assertNull(state.networkId)
        assertTrue(shadowOf(context.getSystemService(ConnectivityManager::class.java)).networkCallbacks.isEmpty())
    }

    @Test
    fun nsdTimeoutOffersFallbackAndLateLocalResultsRecover() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val network = fakeWifi(context)
        val state = WirelessDiscoveryState()
        val discovery = WirelessAdbDiscovery(context, state) {}
        discovery.start()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            announce(context, network, WirelessDiscoveryState.PAIRING, 30_001, "192.0.2.2")
            assertNull(state.pairingPort)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9))
            assertFalse(state.manualFallback)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertTrue(state.manualFallback)
            announce(context, network, WirelessDiscoveryState.PAIRING, 30_001)
            announce(context, network, WirelessDiscoveryState.CONNECT, 30_002)
            assertFalse(state.manualFallback)
            assertNotNull(state.endpoints())
        } finally {
            discovery.close()
        }
    }

    @Test
    fun lateResolutionFromLostWifiCannotRestoreEndpoints() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val network = fakeWifi(context)
        val state = WirelessDiscoveryState()
        val discovery = WirelessAdbDiscovery(context, state) {}
        discovery.start()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            val nsd = shadowOf(context.getSystemService(NsdManager::class.java))
            val info =
                NsdServiceInfo().apply {
                    serviceName = "synthetic-local"
                    serviceType = WirelessDiscoveryState.PAIRING
                    host = InetAddress.getByName("192.0.2.1")
                    port = 30_001
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) this.network = network
                }
            nsd
                .getDiscoveryListeners(WirelessDiscoveryState.PAIRING)
                .orEmpty()
                .single()
                .onServiceFound(info)
            shadowOf(Looper.getMainLooper()).idle()
            val callback = nsd.getResolveListeners(info).orEmpty().single()
            val connectivity = shadowOf(context.getSystemService(ConnectivityManager::class.java))
            connectivity.removeNetwork(network)
            connectivity.networkCallbacks.toList().forEach { it.onLost(network) }
            shadowOf(Looper.getMainLooper()).idle()
            callback.onServiceResolved(info)
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(state.networkId)
            assertNull(state.pairingPort)
        } finally {
            discovery.close()
        }
    }

    @Test
    fun failedFrameworkResolutionUsesInjectedMdnsAndIgnoresLateNsdSuccess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val network = fakeWifi(context)
        val state = WirelessDiscoveryState()
        val queried = CountDownLatch(1)
        val discovery =
            WirelessAdbDiscovery(context, state, mdnsLookup = { scoped, _, instance, addresses ->
                assertEquals(network, scoped)
                assertTrue(instance.endsWith("._adb-tls-pairing._tcp.local."))
                assertTrue(InetAddress.getByName("192.0.2.1") in addresses)
                queried.countDown()
                InetAddress.getByName("192.0.2.1") to 30_001
            }) {}
        discovery.start()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            val nsd = shadowOf(context.getSystemService(NsdManager::class.java))
            val info =
                NsdServiceInfo().apply {
                    serviceName = "fixture"
                    serviceType = WirelessDiscoveryState.PAIRING
                }
            nsd.getDiscoveryListeners(info.serviceType)!!.single().onServiceFound(info)
            shadowOf(Looper.getMainLooper()).idle()
            val callback = nsd.getResolveListeners(info)!!.single()
            callback.onResolveFailed(info, 0)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(queried.await(5, TimeUnit.SECONDS))
            awaitPairingPort(state, 30_001)
            info.host = InetAddress.getByName("192.0.2.1")
            info.port = 30_003
            if (Build.VERSION.SDK_INT >= 33) info.network = network
            callback.onServiceResolved(info)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(30_001, state.pairingPort)
        } finally {
            discovery.close()
        }
    }

    @Test
    fun missingFrameworkCallbackStartsMdnsAfterBoundedTimeout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fakeWifi(context)
        val state = WirelessDiscoveryState()
        val queried = CountDownLatch(1)
        val discovery =
            WirelessAdbDiscovery(context, state, mdnsLookup = { _, _, _, _ ->
                queried.countDown()
                InetAddress.getByName("192.0.2.1") to 30_001
            }) {}
        discovery.start()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            val nsd = shadowOf(context.getSystemService(NsdManager::class.java))
            val info =
                NsdServiceInfo().apply {
                    serviceName = "fixture"
                    serviceType = WirelessDiscoveryState.PAIRING
                }
            nsd.getDiscoveryListeners(info.serviceType)!!.single().onServiceFound(info)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1L, queried.count)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_500))
            assertTrue(queried.await(5, TimeUnit.SECONDS))
            awaitPairingPort(state, 30_001)
        } finally {
            discovery.close()
        }
    }

    @Test
    fun threeFieldRemoteInputIsConsumedWithoutDependingOnDiscovery() {
        val intent = reply("001234,30001,30002")
        val request = consumeNotificationReply(intent)!!.request(null, null)!!
        assertEquals(30_001, request.endpoints.pairingPort)
        assertEquals(30_002, request.endpoints.connectionPort)
        assertNull(intent.clipData)
    }

    private fun awaitPairingPort(
        state: WirelessDiscoveryState,
        expected: Int,
    ) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (state.pairingPort == null && System.nanoTime() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertEquals(expected, state.pairingPort)
    }

    private fun fakeWifi(context: Context): Network {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val connectivity = shadowOf(manager)
        connectivity.clearAllNetworks()
        connectivity.setDefaultNetworkActive(true)
        connectivity.setActiveNetworkInfo(
            ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI,
                0,
                true,
                true,
            ),
        )
        val network = manager.activeNetwork!!
        val capabilities = shadowOf(NetworkCapabilities()).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        connectivity.setNetworkCapabilities(network, capabilities)
        // Fixture-only hidden setters exist in Android's full runtime, not its public SDK stubs.
        val address =
            LinkAddress::class.java
                .getConstructor(InetAddress::class.java, Int::class.javaPrimitiveType)
                .newInstance(InetAddress.getByName("192.0.2.1"), 24)
        val properties = LinkProperties()
        LinkProperties::class.java.getMethod("addLinkAddress", LinkAddress::class.java).invoke(properties, address)
        connectivity.setLinkProperties(network, properties)
        return network
    }

    private fun announce(
        context: Context,
        network: Network,
        type: String,
        port: Int,
        address: String = "192.0.2.1",
    ) {
        val nsd = shadowOf(context.getSystemService(NsdManager::class.java))
        val info =
            NsdServiceInfo().apply {
                serviceName = "synthetic-local"
                serviceType = type
                host = InetAddress.getByName(address)
                this.port = port
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) this.network = network
            }
        nsd
            .getDiscoveryListeners(type)
            .orEmpty()
            .single()
            .onServiceFound(info)
        shadowOf(Looper.getMainLooper()).idle()
        // Manually invoking a shadow callback does not remove its completed listener.
        nsd
            .getResolveListeners(info)
            .orEmpty()
            .last()
            .onServiceResolved(info)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun reply(text: String): Intent {
        val input = RemoteInput.Builder(WirelessPairingService.CODE).build()
        return Intent().also {
            RemoteInput.addResultsToIntent(
                arrayOf(input),
                it,
                Bundle().apply { putCharSequence(WirelessPairingService.CODE, text) },
            )
        }
    }
}
