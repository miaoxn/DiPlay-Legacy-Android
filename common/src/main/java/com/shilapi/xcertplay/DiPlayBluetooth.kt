package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.bluetooth.BluetoothAddressResolver

internal object DiPlayBluetooth {
    @android.annotation.SuppressLint("MissingPermission")
    fun localAddress(context: Context): String? {
        val goc = com.shilapi.xcertplay.bluetooth.CarPlayBluetoothManager.get(context).goc
        if (goc?.selected() == true) return runCatching { goc.localAddress(context) }.getOrNull()
        val custom = AirPlayPersistence.loadCustomBluetoothMac(context)
        return BluetoothAddressResolver.resolve(context, custom)
    }
}
