package com.shilapi.xcertplay.network

import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Looper
import com.shilapi.xcertplay.transport.Iap2WirelessSecurity
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

/** KitKat station-network adapter; it never changes Wi-Fi or the default route.
 * Falls back to a USB-shared network (or wired Ethernet) when Wi-Fi is not connected. */
internal class LegacyExistingWifi(
    private val connectivity: ConnectivityManager,
    private val wifi: WifiManager,
    private val ssid: String,
    private val passphrase: String,
    private val log: (String) -> Unit,
    private val onChanged: () -> Unit,
) : Closeable {
    @Volatile private var closed = false
    private var monitor: Thread? = null

    private fun stationLink(): StationLink? {
        if (connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI)?.isConnected == true) {
            val info = wifi.connectionInfo ?: return null
            val live = info.ssid?.removeSurrounding("\"")
            if (!live.isNullOrEmpty() && live != WifiManager.UNKNOWN_SSID && live != ssid) {
                throw IOException("Saved Wi-Fi name does not match the connected Wi-Fi")
            }
            val ip = info.ipAddress
            if (ip == 0) return null
            return StationLink(
                InetAddress.getByAddress(ByteArray(4) { (ip ushr (8 * it)).toByte() }),
                viaUsbNetwork = false,
            )
        }
        // No Wi-Fi connection: fall back to a USB-shared network, which the framework
        // never reports as a Wi-Fi station. A live Wi-Fi connection always wins above.
        val usbAddress = usbSharedAddress() ?: return null
        return StationLink(usbAddress, viaUsbNetwork = true)
    }

    private fun usbSharedAddress(): InetAddress? {
        val snapshot = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }
            .getOrDefault(emptyList())
            .mapNotNull { iface ->
                runCatching {
                    UsbSharedInterface(
                        iface.name,
                        iface.isUp,
                        iface.isLoopback,
                        Collections.list(iface.inetAddresses),
                    )
                }.getOrNull()
            }
        return selectUsbSharedAddress(snapshot)
    }

    private data class StationLink(val address: InetAddress, val viaUsbNetwork: Boolean)

    fun start(timeoutMillis: Long): WirelessHotspotInfo {
        check(Looper.myLooper() != Looper.getMainLooper())
        require(timeoutMillis > 0)
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (!closed && System.nanoTime() < deadline) {
            val station = stationLink()
            val iface = station?.let { link -> Collections.list(NetworkInterface.getNetworkInterfaces())
                .firstOrNull { it.isUp && !it.isLoopback && Collections.list(it.inetAddresses).contains(link.address) } }
            if (station != null && iface != null) {
                val address = station.address
                val viaUsbNetwork = station.viaUsbNetwork
                val addresses = existingWifiHostAddresses(Collections.list(iface.inetAddresses), iface.index)
                val info = if (viaUsbNetwork) null else wifi.connectionInfo
                val bssid = info?.bssid
                val apBssid = parseAccessPointAddress(bssid)
                val frequency = if (viaUsbNetwork) null else runCatching {
                    wifi.scanResults?.firstOrNull { it.BSSID.equals(bssid, ignoreCase = true) }?.frequency
                }.getOrNull()
                val channel = frequency?.let(::wifiFrequencyMhzToChannel) ?: 0
                val band = when {
                    viaUsbNetwork -> "Auto"
                    frequency != null && frequency in 5150..5895 -> "5 GHz"
                    frequency != null && frequency in 2412..2484 -> "2.4 GHz"
                    else -> "Auto"
                }
                if (viaUsbNetwork) {
                    log("Existing Wi-Fi attached legacy iface=${iface.name} via=usb-network host=${address.hostAddress} channel=auto")
                } else {
                    log("Existing Wi-Fi attached legacy iface=${iface.name} host=${address.hostAddress} channel=${if (channel > 0) channel else "auto"}")
                }
                if (RootShell.isRootAvailable()) {
                    BluetoothStackReset.reset("legacy-wifi", isCancelled = { closed }, onDiagnostic = log)
                }
                monitor = Thread({
                    try {
                        while (!closed) {
                            Thread.sleep(1000)
                            if (!closed && runCatching { stationLink() }.getOrNull()?.address != address) {
                                onChanged()
                                return@Thread
                            }
                        }
                    } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                }, "diplay-legacy-wifi").apply { isDaemon = true; start() }
                return WirelessHotspotInfo(ssid, passphrase,
                    if (passphrase.isEmpty()) Iap2WirelessSecurity.NONE else Iap2WirelessSecurity.WPA_WPA2,
                    channel, frequency, bssid, iface.name, address, band, WirelessHotspotBackend.EXISTING_WIFI,
                    hostAddresses = addresses, accessPointBssid = apBssid)
            }
            Thread.sleep(200)
        }
        throw IOException("Existing Wi-Fi is unavailable or startup was cancelled")
    }

    override fun close() { closed = true; monitor?.interrupt() }
}
