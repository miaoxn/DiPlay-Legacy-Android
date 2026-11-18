package com.shilapi.xcertplay.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.shilapi.xcertplay.compat.realSdkInt
import android.os.Looper
import com.shilapi.xcertplay.orchestration.ManualHotspotValidation
import com.shilapi.xcertplay.transport.Iap2WirelessSecurity
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean

/** Attaches to an existing station network. Never creates an AP, joins Wi-Fi or changes routing.
 * When no Wi-Fi network is present, a USB-shared network (portable Wi-Fi over RNDIS/ECM) or a
 * wired Ethernet link on the same LAN is accepted instead; the saved Wi-Fi credentials still
 * reach the iPhone over Bluetooth, and the channel stays unknown (0/Auto). */
class ExistingWifiManager(
    context: Context,
    private val ssid: String,
    private val passphrase: String,
    private val onDiagnostic: (String) -> Unit = {},
    private val onNetworkChanged: () -> Unit = {},
    private val lostGraceMillis: Long = LOST_GRACE_MILLIS,
    private val lostPollMillis: Long = LOST_POLL_MILLIS,
) : WirelessHotspotManager {
    companion object {
        /** How long a lost network may come back on the same link before the session restarts. */
        const val LOST_GRACE_MILLIS = 3_000L
        /** Poll interval while waiting for the lost network to come back. */
        const val LOST_POLL_MILLIS = 200L
    }
    private val connectivity = if (realSdkInt() >= Build.VERSION_CODES.M) {
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    } else {
        @Suppress("DEPRECATION")
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    } ?: throw IllegalStateException("ConnectivityManager is unavailable")
    private val wifi = if (realSdkInt() >= Build.VERSION_CODES.M) {
        context.applicationContext.getSystemService(WifiManager::class.java)
    } else {
        @Suppress("DEPRECATION")
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    } ?: throw IllegalStateException("WifiManager is unavailable")
    private val lock = Any()
    private val invalidated = AtomicBoolean()
    private val lossGracePending = AtomicBoolean()
    @Volatile private var attachedViaUsbNetwork = false
    @Volatile private var closed = false
    @Volatile private var selected: Network? = null
    @Volatile private var host: InetAddress? = null
    @Volatile private var hosts: List<InetAddress> = emptyList()
    @Volatile private var interfaceIndex = 0
    @Volatile private var interfaceName: String? = null
    private var callbackRegistered = false
    private var legacy: LegacyExistingWifi? = null

    private val callback by lazy { if (realSdkInt() >= 21) modernCallback() else null }

    @androidx.annotation.RequiresApi(21)
    private fun modernCallback() = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            if (network == selected) onSelectedNetworkLost()
        }

        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
            if (network == selected && !sameLink(properties)) invalidate("interface or address changed")
        }
    }

    init {
        require(ManualHotspotValidation.error(ssid, passphrase) == null) {
            "Invalid existing Wi-Fi credentials"
        }
    }

    override fun start(timeoutMillis: Long): WirelessHotspotInfo {
        if (realSdkInt() < 21) {
            val adapter = synchronized(lock) {
                check(!closed) { "Existing Wi-Fi attachment was cancelled" }
                LegacyExistingWifi(connectivity, wifi, ssid, passphrase,
                    onDiagnostic, onNetworkChanged).also { legacy = it }
            }
            return adapter.start(timeoutMillis)
        }
        return startModern(timeoutMillis)
    }

    @androidx.annotation.RequiresApi(21)
    private fun startModern(timeoutMillis: Long): WirelessHotspotInfo {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "ExistingWifiManager.start must not run on the main thread"
        }
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
        check(selected == null) { "ExistingWifiManager has already started" }
        val started = System.nanoTime()
        while (!closed) {
            // INTERNET/VALIDATED and the default route do not identify the CarPlay LAN.
            val allNetworks = connectivity.allNetworks
            val wifiNetworks = allNetworks.filter { network ->
                connectivity.getNetworkCapabilities(network)?.let {
                    it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                        !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                } == true
            }
            if (wifiNetworks.size > 1) throw IOException("Multiple Wi-Fi networks are active; keep one existing Wi-Fi connection")
            val wifiCandidate = wifiNetworks.singleOrNull()?.let { network ->
                linkDetails(network)?.let { SelectedLink(network, it, viaUsbNetwork = false) }
            }
            // A USB-shared network is the fallback only when no Wi-Fi network is present at
            // all: a live Wi-Fi connection always wins, so existing setups never flip
            // transports while Wi-Fi is still associating.
            val usbCandidate = if (wifiNetworks.isEmpty()) {
                val usbNetworks = allNetworks.filter { network ->
                    connectivity.getNetworkCapabilities(network)?.let {
                        it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) &&
                            !it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                            !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    } == true
                }
                if (usbNetworks.size > 1) throw IOException("Multiple USB-shared networks are active; keep one USB network connection")
                usbNetworks.singleOrNull()?.let { network ->
                    linkDetails(network)?.let { SelectedLink(network, it, viaUsbNetwork = true) }
                }
            } else null
            val candidate = wifiCandidate ?: usbCandidate
            if (candidate != null) {
                val network = candidate.network
                val name = candidate.link.name
                val iface = candidate.link.iface
                val address = candidate.link.address
                val addresses = candidate.link.addresses
                val viaUsbNetwork = candidate.viaUsbNetwork
                // A USB-shared network has no station identity: no SSID to match, no channel
                // to observe, no AP address, and no station snapshot worth consulting. The
                // saved credentials still go to the iPhone over Bluetooth.
                val capabilities = connectivity.getNetworkCapabilities(network)
                val networkInfo = if (!viaUsbNetwork && Build.VERSION.SDK_INT >= 31) capabilities?.transportInfo as? WifiInfo else null
                // On-demand capabilities can redact SSID/BSSID even when the legacy station
                // snapshot can supply them. A non-null redacted object must not hide that input.
                @Suppress("DEPRECATION")
                val stationInfo = if (viaUsbNetwork) null else try { wifi.connectionInfo } catch (_: SecurityException) { null }
                val info = if (viaUsbNetwork) null else listOfNotNull(networkInfo, stationInfo).firstOrNull { readableSsid(it) != null }
                    ?: networkInfo ?: stationInfo
                val liveSsid = readableSsid(info)
                if (liveSsid != null && liveSsid != ssid) {
                    throw IOException("Configured Wi-Fi does not match the connected network; check Wi-Fi settings and saved details")
                }
                val frequency = info?.frequency?.takeIf { it > 0 }
                val channel = frequency?.let(::wifiFrequencyMhzToChannel) ?: 0
                val apBssid = accessPointAddress(info?.bssid)
                val observedSecurity = if (Build.VERSION.SDK_INT >= 31 && info != null) info.currentSecurityType else -1
                if (!viaUsbNetwork && Build.VERSION.SDK_INT >= 31) {
                    when (observedSecurity) {
                        WifiInfo.SECURITY_TYPE_OPEN -> if (passphrase.isNotEmpty()) {
                            throw IOException("Connected Wi-Fi is open; clear the saved Wi-Fi password")
                        }
                        WifiInfo.SECURITY_TYPE_PSK, WifiInfo.SECURITY_TYPE_SAE -> if (passphrase.isEmpty()) {
                            throw IOException("Connected Wi-Fi is secured; enter its WPA2 password")
                        }
                        WifiInfo.SECURITY_TYPE_UNKNOWN -> Unit
                        else -> throw IOException("Existing Wi-Fi requires an open or WPA2-Personal network")
                    }
                }
                synchronized(lock) {
                    check(!closed) { "ExistingWifiManager is closed" }
                    selected = network
                    host = address
                    hosts = addresses
                    interfaceIndex = iface.index
                    interfaceName = name
                    attachedViaUsbNetwork = viaUsbNetwork
                    connectivity.registerNetworkCallback(NetworkRequest.Builder().apply {
                        if (Build.VERSION.SDK_INT >= 30) clearCapabilities()
                        else {
                            removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                            removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                        }
                    }
                        .addTransportType(if (viaUsbNetwork) NetworkCapabilities.TRANSPORT_ETHERNET else NetworkCapabilities.TRANSPORT_WIFI)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), checkNotNull(callback))
                    callbackRegistered = true
                }
                // Close the gap between reading the link and registering the callback.
                val current = connectivity.getLinkProperties(network)
                if (current == null || !sameLink(current) || invalidated.get()) {
                    throw IOException("Existing Wi-Fi changed during startup; connect again")
                }
                if (viaUsbNetwork) {
                    onDiagnostic("Existing Wi-Fi attached iface=$name via=usb-network host=${address.hostAddress} " +
                        "networkNameReadable=false channel=auto security=${security()} receiverIdentity=saved")
                } else {
                    onDiagnostic("Existing Wi-Fi attached iface=$name host=${address.hostAddress} " +
                        "networkNameReadable=${liveSsid != null} channel=$channel security=${security()} " +
                        "receiverIdentity=saved")
                }
                return WirelessHotspotInfo(ssid, passphrase, security(), channel, frequency,
                    // The router's BSSID is not this receiver's AirPlay device identity.
                    null, name, address, when {
                        viaUsbNetwork || frequency == null -> "Auto"
                        frequency < 2500 -> "2.4 GHz"
                        frequency < 5955 -> "5 GHz"
                        else -> "6 GHz"
                    }, WirelessHotspotBackend.EXISTING_WIFI, hostAddresses = addresses, accessPointBssid = apBssid)
            }
            if ((System.nanoTime() - started) / 1_000_000 >= timeoutMillis) {
                throw IOException("Existing Wi-Fi is not connected or has no usable address. Connect both devices to the same Wi-Fi in system settings, or use a USB-shared network with Wi-Fi off")
            }
            Thread.sleep(200)
        }
        throw IOException("Existing Wi-Fi attachment was cancelled")
    }

    /** Link properties plus a usable host address, or null when the network cannot serve the LAN yet. */
    @androidx.annotation.RequiresApi(21)
    private fun linkDetails(network: Network): LinkDetails? {
        val properties = connectivity.getLinkProperties(network) ?: return null
        val name = properties.interfaceName ?: return null
        val iface = runCatching { NetworkInterface.getByName(name) }.getOrNull() ?: return null
        val linkAddresses = properties.linkAddresses.map { link -> link.address }
        val address = wirelessHostAddress(linkAddresses, iface.index) ?: return null
        return LinkDetails(name, iface, address, existingWifiHostAddresses(linkAddresses, iface.index))
    }

    private data class LinkDetails(
        val name: String,
        val iface: NetworkInterface,
        val address: InetAddress,
        val addresses: List<InetAddress>,
    )

    private data class SelectedLink(
        val network: Network,
        val link: LinkDetails,
        val viaUsbNetwork: Boolean,
    )

    @androidx.annotation.RequiresApi(21)
    private fun sameLink(properties: LinkProperties): Boolean =
        properties.interfaceName == interfaceName && properties.linkAddresses.any { it.address == host } &&
            existingWifiHostAddresses(properties.linkAddresses.map { it.address }, interfaceIndex).toSet() == hosts.toSet()

    private fun security(): Iap2WirelessSecurity =
        if (passphrase.isEmpty()) Iap2WirelessSecurity.NONE else Iap2WirelessSecurity.WPA_WPA2

    private fun readableSsid(info: WifiInfo?): String? = info?.ssid?.removeSurrounding("\"")
        ?.takeUnless { it == WifiManager.UNKNOWN_SSID || it.isEmpty() }

    private fun accessPointAddress(text: String?): ByteArray? = parseAccessPointAddress(text)

    /**
     * A lost network is not always a dead LAN. USB-shared networks (a
     * portable hotspot over RNDIS/ECM) can drop and recreate their
     * [Network] object transiently while the iPhone never leaves the
     * LAN and the head unit keeps the same address, so restarting the
     * session on the first loss turns a sub-second blip into a visible
     * CarPlay drop and a reconnect loop. Wait out a short grace period
     * and re-check: when the same link (interface, host address and
     * address set) is back, adopt the replacement network and keep the
     * session; only a loss that outlasts the grace restarts it. A
     * genuine disconnect stays gone, so its restart is delayed by the
     * grace at most.
     */
    private fun onSelectedNetworkLost() {
        if (closed || invalidated.get() || !lossGracePending.compareAndSet(false, true)) return
        Thread({
            var recovered = false
            try {
                val deadlineNs = System.nanoTime() + lostGraceMillis * 1_000_000L
                while (!closed && !invalidated.get() && !recovered) {
                    val replacement = findEquivalentLink()
                    if (replacement != null) {
                        synchronized(lock) {
                            if (!closed && !invalidated.get()) {
                                selected = replacement.network
                                host = replacement.link.address
                                hosts = replacement.link.addresses
                                interfaceIndex = replacement.link.iface.index
                                interfaceName = replacement.link.name
                                recovered = true
                            }
                        }
                        if (recovered) {
                            onDiagnostic("Existing Wi-Fi transient loss recovered iface=${replacement.link.name}; same link returned, session kept")
                        }
                    } else if (System.nanoTime() >= deadlineNs) {
                        break
                    } else {
                        Thread.sleep(lostPollMillis)
                    }
                }
            } catch (_: InterruptedException) {
                // Fall through: an interrupted wait restarts like a sustained loss.
            } finally {
                lossGracePending.set(false)
            }
            if (!recovered) invalidate("network lost")
        }, "diplay-existing-wifi-loss-grace").apply { isDaemon = true; start() }
    }

    /** A live network on the same transport whose link matches the attached one, if any. */
    @androidx.annotation.RequiresApi(21)
    private fun findEquivalentLink(): SelectedLink? {
        val viaUsbNetwork = attachedViaUsbNetwork
        return try {
            for (network in connectivity.allNetworks) {
                val capabilities = connectivity.getNetworkCapabilities(network) ?: continue
                val transportMatches = if (viaUsbNetwork) {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) &&
                        !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                        !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                } else {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                        !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                }
                if (!transportMatches) continue
                val properties = connectivity.getLinkProperties(network) ?: continue
                if (!sameLink(properties)) continue
                val details = linkDetails(network) ?: continue
                return SelectedLink(network, details, viaUsbNetwork)
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun invalidate(reason: String) {
        if (!closed && invalidated.compareAndSet(false, true)) {
            onDiagnostic("Existing Wi-Fi $reason; restarting wireless session")
            onNetworkChanged()
        }
    }

    override fun connectionDiagnosticSnapshot(): String =
        "existingWifiLink=${if (closed || invalidated.get()) "lost" else "attached"}"

    override fun close() {
        synchronized(lock) {
            closed = true
            legacy?.close()
            if (realSdkInt() >= 21 && callbackRegistered) {
                connectivity.unregisterNetworkCallback(checkNotNull(callback))
                callbackRegistered = false
            }
        }
    }
}
