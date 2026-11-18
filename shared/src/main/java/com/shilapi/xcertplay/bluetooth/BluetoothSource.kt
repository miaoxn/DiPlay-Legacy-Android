package com.shilapi.xcertplay.bluetooth

enum class BluetoothSource(val key: String) {
    AUTO("auto"),
    BUILTIN("builtin"),
    USB_DONGLE("usb_dongle");

    companion object {
        fun fromKey(key: String?): BluetoothSource =
            entries.firstOrNull { it.key == key } ?: AUTO
    }
}
