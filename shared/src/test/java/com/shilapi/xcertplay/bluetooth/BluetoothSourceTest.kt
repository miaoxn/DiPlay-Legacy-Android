package com.shilapi.xcertplay.bluetooth

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BluetoothSourceTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun testFromKeyParsesCorrectly() {
        assertEquals(BluetoothSource.AUTO, BluetoothSource.fromKey("auto"))
        assertEquals(BluetoothSource.BUILTIN, BluetoothSource.fromKey("builtin"))
        assertEquals(BluetoothSource.USB_DONGLE, BluetoothSource.fromKey("usb_dongle"))
        assertEquals(BluetoothSource.AUTO, BluetoothSource.fromKey("invalid"))
        assertEquals(BluetoothSource.AUTO, BluetoothSource.fromKey(null))
    }

    @Test
    fun testHybridAdapterSourceRouting() {
        val hsae = HsaeBluetoothAdapter(context)
        val android = AndroidBluetoothAdapter(context)

        var selectedSource = BluetoothSource.AUTO
        val hybrid = HybridBluetoothAdapter(
            hsae = hsae,
            android = android,
            usbDongle = null,
            sourceProvider = { selectedSource },
        )

        // When USB_DONGLE is selected but usbDongle is null, isSupported should be false
        selectedSource = BluetoothSource.USB_DONGLE
        assertFalse(hybrid.isSupported())
        assertFalse(hybrid.isEnabled())

        // When BUILTIN is selected, isSupported uses native
        selectedSource = BluetoothSource.BUILTIN
        assertEquals(android.isSupported(), hybrid.isSupported())

        // When AUTO is selected and usbDongle is null, isSupported falls back to native
        selectedSource = BluetoothSource.AUTO
        assertEquals(android.isSupported(), hybrid.isSupported())
    }
}
