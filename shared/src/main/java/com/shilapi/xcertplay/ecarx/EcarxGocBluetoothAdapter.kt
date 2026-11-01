package com.shilapi.xcertplay.ecarx

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import com.shilapi.xcertplay.bluetooth.CarPlayBluetooth
import com.shilapi.xcertplay.bluetooth.CarPlayBluetoothDevice
import com.shilapi.xcertplay.transport.BlockingDuplexByteStream
import java.io.IOException

/** Explicit opt-in OEM route. No Android adapter power/profile operations. */
class EcarxGocBluetoothAdapter(context: Context) : CarPlayBluetooth {
    private val prefs = context.applicationContext.getSharedPreferences("goc449", Context.MODE_PRIVATE)
    fun selected() = prefs.getBoolean("enabled", false)
    override fun isSupported() = GocFirmware.supported(Build.VERSION.SDK_INT, Build.DEVICE)
    override fun isEnabled() = selected() && isSupported()
    override fun enable() = isEnabled() // The driver enables Bluetooth in the OEM settings.
    override fun disable() = Unit // Never disable OEM calls/music on app teardown.
    override fun getAdapterName() = "ECARX 449 GOC"
    override fun getPairedDevices() = runCatching { listOf(selectedPhone()) }.getOrDefault(emptyList())
    override fun getConnectedDevice(): CarPlayBluetoothDevice? = null // No invented connection state.
    override fun localAddress(context: Context): String = GocFirmware.mac(prefs.getString("local", "")!!)
    fun selectedPhone() = CarPlayBluetoothDevice(
        prefs.getString("name", "iPhone") ?: "iPhone", GocFirmware.mac(prefs.getString("phone", "")!!), false,
    )
    fun validate() {
        if (!isEnabled() || !GocFirmware.installed()) throw IOException("449 GOC patch is not verified; check factory Bluetooth settings")
        selectedPhone()
        GocFirmware.mac(prefs.getString("local", "")!!)
    }
    override fun openRfcommStream(deviceAddress: String, uuid: String): BlockingDuplexByteStream {
        validate()
        if (!uuid.equals(GocFirmware.UUID, true) || GocFirmware.mac(deviceAddress) != selectedPhone().address)
            throw IOException("GOC target/UUID does not match the selected phone")
        val socket = LocalSocket() // Default SOCK_STREAM; constructor also exists on legacy APIs.
        try {
            socket.connect(LocalSocketAddress("goc_spp", LocalSocketAddress.Namespace.RESERVED))
            val output = socket.outputStream
            output.write(GocFirmware.mac(deviceAddress).replace(":", "").toByteArray(Charsets.US_ASCII))
            output.flush()
            return BufferedDuplex(socket.inputStream, output, socket)
        } catch (error: Exception) {
            runCatching { socket.close() }
            throw IOException("Could not open factory GOC SPP", error)
        }
    }
}
