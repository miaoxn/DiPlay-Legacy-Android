package com.shilapi.xcertplay.network

import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Snapshot of one host network interface, decoupled from [java.net.NetworkInterface] so the
 * USB-shared selection below stays testable on the JVM without Android.
 */
internal data class UsbSharedInterface(
    val name: String,
    val isUp: Boolean,
    val isLoopback: Boolean,
    val addresses: List<InetAddress>,
)

/**
 * Kernel interface names a USB-shared network (RNDIS/ECM gadget) or a wired Ethernet link
 * shows up under. Wi-Fi (`wlan*`, `p2p*`), loopback and virtual interfaces never match.
 */
internal fun isUsbSharedInterfaceName(name: String): Boolean =
    name.startsWith("eth") || name.startsWith("usb") || name.startsWith("rndis")

/**
 * Picks the usable IPv4 address of the single USB-shared / wired-Ethernet interface on the
 * same LAN. Returns null when no interface qualifies yet (the caller keeps polling), and
 * throws when more than one qualifies, because the CarPlay LAN would then be ambiguous.
 * IPv6-only interfaces do not qualify: discovery and TCP on this path are IPv4-first.
 */
internal fun selectUsbSharedAddress(interfaces: List<UsbSharedInterface>): InetAddress? {
    val usable = interfaces
        .filter { it.isUp && !it.isLoopback && isUsbSharedInterfaceName(it.name) }
        .mapNotNull { iface ->
            existingWifiHostAddresses(iface.addresses, 0).firstOrNull { it is Inet4Address }
        }
    if (usable.size > 1) {
        throw IOException("Multiple USB-shared networks are active; keep one USB network connection")
    }
    return usable.singleOrNull()
}
