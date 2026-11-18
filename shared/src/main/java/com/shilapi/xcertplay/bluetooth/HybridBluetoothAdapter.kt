package com.shilapi.xcertplay.bluetooth

import android.content.Context
import android.util.Log
import com.shilapi.xcertplay.transport.BlockingDuplexByteStream
import com.shilapi.xcertplay.usbbt.UsbBtBluetoothAdapter

class HybridBluetoothAdapter(
    val hsae: HsaeBluetoothAdapter,
    val android: AndroidBluetoothAdapter,
    /** USB dongle stack; preferred for data channels when plugged in. */
    val usbDongle: UsbBtBluetoothAdapter? = null,
    var sourceProvider: () -> BluetoothSource = { BluetoothSource.AUTO },
) : CarPlayBluetooth {

    companion object {
        private const val TAG = "HybridBluetoothAdapter"
    }

    private val currentSource get() = sourceProvider()

    override fun isSupported(): Boolean = when (currentSource) {
        BluetoothSource.BUILTIN -> hsae.isSupported() || android.isSupported()
        BluetoothSource.USB_DONGLE -> usbDongle?.isSupported() == true
        BluetoothSource.AUTO -> hsae.isSupported() || android.isSupported() || usbDongle?.isSupported() == true
    }

    override fun isEnabled(): Boolean = when (currentSource) {
        BluetoothSource.BUILTIN -> {
            if (hsae.isSupported() && hsae.isEnabled()) true
            else android.isEnabled()
        }
        BluetoothSource.USB_DONGLE -> usbDongle?.isSupported() == true && usbDongle.isEnabled()
        BluetoothSource.AUTO -> {
            if (usbDongle?.isSupported() == true) usbDongle.isEnabled()
            else if (hsae.isSupported() && hsae.isEnabled()) true
            else android.isEnabled()
        }
    }

    override fun enable(): Boolean {
        when (currentSource) {
            BluetoothSource.BUILTIN -> {
                var ok = false
                if (hsae.isSupported()) ok = hsae.enable()
                if (android.enable()) ok = true
                return ok
            }
            BluetoothSource.USB_DONGLE -> {
                return usbDongle?.isSupported() == true && usbDongle.enable()
            }
            BluetoothSource.AUTO -> {
                var ok = false
                if (hsae.isSupported()) ok = hsae.enable()
                if (android.enable()) ok = true
                if (usbDongle?.isSupported() == true) {
                    return usbDongle.enable()
                }
                return ok
            }
        }
    }

    override fun disable() {
        if (currentSource != BluetoothSource.BUILTIN) {
            usbDongle?.disable()
        }
    }

    override fun getAdapterName(): String {
        val parts = mutableListOf<String>()
        if (currentSource != BluetoothSource.USB_DONGLE) {
            if (hsae.hasHsaeService()) parts.add("HSAE")
            if (hsae.hasAnwPhoneLink()) parts.add("AnwPhoneLink")
            if (android.isSupported()) parts.add("Android")
        }
        if (currentSource != BluetoothSource.BUILTIN && usbDongle?.isSupported() == true) {
            parts.add("USB")
        }
        return if (parts.isEmpty()) android.getAdapterName()
        else if (parts.size == 1 && parts[0] == "USB") "USB"
        else "Hybrid(${parts.joinToString("+")})"
    }

    override fun getPairedDevices(): List<CarPlayBluetoothDevice> {
        val seen = HashSet<String>()
        val merged = ArrayList<CarPlayBluetoothDevice>()
        fun addAll(devices: List<CarPlayBluetoothDevice>) {
            for (device in devices) {
                val key = device.address.lowercase()
                if (seen.add(key)) merged.add(device)
            }
        }
        if (currentSource != BluetoothSource.USB_DONGLE) {
            if (hsae.isSupported()) runCatching { addAll(hsae.getPairedDevices()) }
            runCatching { addAll(android.getPairedDevices()) }
        }
        if (currentSource != BluetoothSource.BUILTIN) {
            usbDongle?.let { runCatching { addAll(it.getPairedDevices()) } }
        }
        return merged
    }

    override fun getConnectedDevice(): CarPlayBluetoothDevice? {
        if (currentSource != BluetoothSource.BUILTIN) {
            usbDongle?.let { runCatching { it.getConnectedDevice() } }?.getOrNull()?.let { return it }
        }
        if (currentSource != BluetoothSource.USB_DONGLE) {
            android.getConnectedDevice()?.let { return it }
            if (hsae.isSupported()) {
                runCatching { hsae.getConnectedDevice() }.getOrNull()?.let { return it }
            }
        }
        return null
    }

    override fun localAddress(context: Context): String? {
        if (currentSource != BluetoothSource.BUILTIN) {
            usbDongle?.let { runCatching { it.localAddress(context) } }?.getOrNull()
                ?.takeIf { !it.isNullOrBlank() }?.let { return it }
        }
        if (currentSource != BluetoothSource.USB_DONGLE) {
            if (hsae.isSupported()) {
                val hsaeAddr = hsae.localAddress(context)
                if (!hsaeAddr.isNullOrBlank()) return hsaeAddr
            }
            android.localAddress(context)?.let { return it }
        }
        return null
    }

    override fun openRfcommStream(deviceAddress: String, uuid: String): BlockingDuplexByteStream {
        val dongle = usbDongle
        if (currentSource != BluetoothSource.BUILTIN && dongle?.isSupported() == true) {
            if (!dongle.isEnabled()) throw java.io.IOException("USB Bluetooth not enabled")
            return dongle.openRfcommStream(deviceAddress, uuid)
        }
        if (currentSource == BluetoothSource.USB_DONGLE) {
            throw java.io.IOException("USB Bluetooth dongle required but not available or supported")
        }
        var failure: Exception? = null
        if (android.isSupported() && android.isEnabled()) {
            try {
                return android.openRfcommStream(deviceAddress, uuid)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw interrupted
            } catch (error: Exception) {
                failure = error
            }
        }
        if (hsae.isSupported()) return hsae.openRfcommStream(deviceAddress, uuid)
        throw java.io.IOException("Bluetooth RFCOMM unavailable: ${failure?.message ?: "no Bluetooth stack available"}", failure)
    }
}
