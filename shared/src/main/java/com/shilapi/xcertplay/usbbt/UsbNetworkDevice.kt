package com.shilapi.xcertplay.usbbt

import android.hardware.usb.UsbDevice

/**
 * Snapshot of a USB device's class descriptors, decoupled from [UsbDevice] so the
 * network-adapter match stays unit-testable on the JVM without Android stubs.
 */
data class UsbDeviceSignature(
    val vendorId: Int,
    val productId: Int,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val deviceProtocol: Int,
    val interfaces: List<UsbInterfaceSignature>,
)

data class UsbInterfaceSignature(
    val interfaceClass: Int,
    val interfaceSubclass: Int,
    val interfaceProtocol: Int,
)

/** Reads the class descriptors of [this] device into a [UsbDeviceSignature]. Never throws. */
fun UsbDevice.toSignature(): UsbDeviceSignature {
    val interfaces = ArrayList<UsbInterfaceSignature>()
    runCatching {
        for (i in 0 until interfaceCount) {
            val intf = getInterface(i) ?: continue
            interfaces += UsbInterfaceSignature(intf.interfaceClass, intf.interfaceSubclass, intf.interfaceProtocol)
        }
    }
    return UsbDeviceSignature(
        vendorId = vendorId,
        productId = productId,
        deviceClass = deviceClass,
        deviceSubclass = deviceSubclass,
        deviceProtocol = deviceProtocol,
        interfaces = interfaces,
    )
}

/**
 * True when the device looks like a USB network adapter: a phone tether or a USB
 * sharing-network stick speaking RNDIS, CDC ECM, CDC NCM or CDC EEM.
 *
 * Matching is by USB-IF class code only, at the device level (RNDIS declares its
 * 0xEF/0x04/0x01 triple on the device descriptor) or on any interface (CDC ECM/NCM/EEM
 * declare theirs on the control interface). A bare CDC Data interface (class 0x0A) is
 * deliberately NOT enough: it also shows up on composite devices that are not network
 * adapters. Vendor-specific USB Ethernet adapters (ASIX/Realtek class 0xFF parts) are
 * out of scope: they carry no networking class code to match on.
 *
 * Apple devices are excluded: an attached iPhone is owned by the wired path and must
 * never trigger the USB-device auto-launch.
 */
fun isUsbNetworkAdapter(signature: UsbDeviceSignature): Boolean {
    if (signature.vendorId == APPLE_VENDOR_ID) return false
    if (isRndisTriple(signature.deviceClass, signature.deviceSubclass, signature.deviceProtocol)) return true
    if (isCdcNetworkingTriple(signature.deviceClass, signature.deviceSubclass)) return true
    return signature.interfaces.any { intf ->
        isRndisTriple(intf.interfaceClass, intf.interfaceSubclass, intf.interfaceProtocol) ||
            isCdcNetworkingTriple(intf.interfaceClass, intf.interfaceSubclass)
    }
}

private const val APPLE_VENDOR_ID = 0x05AC

/** RNDIS: Miscellaneous device class, RNDIS subclass, RNDIS protocol. */
private fun isRndisTriple(cls: Int, sub: Int, protocol: Int): Boolean =
    cls == 0xEF && sub == 0x04 && protocol == 0x01

/** CDC Communications class with an ECM (6), EEM (12) or NCM (13) subclass. Protocol is not constrained. */
private fun isCdcNetworkingTriple(cls: Int, sub: Int): Boolean =
    cls == 0x02 && (sub == 0x06 || sub == 0x0C || sub == 0x0D)
