package com.shilapi.xcertplay

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], qualifiers = "en", manifest = Config.NONE)
class UsbDeviceAutoLaunchReceiverTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val receiver = UsbDeviceAutoLaunchReceiver()

    @Before fun clearPreferences() {
        app.getSharedPreferences("diplay", 0).edit().clear().commit()
    }

    @Test fun toggleOffIgnoresDongleAttach() {
        receive(attachIntent(dongleDevice()))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun toggleOnOpensDiPlayForDongleAttach() {
        DiPlayPreferences.saveUsbDeviceAutoLaunch(app, true)
        receive(attachIntent(dongleDevice()))
        val started = shadowOf(app).nextStartedActivity
        assertEquals(DiPlayActivity::class.java.name, started.component?.className)
    }

    @Test fun toggleOnOpensDiPlayForRndisAttach() {
        DiPlayPreferences.saveUsbDeviceAutoLaunch(app, true)
        receive(attachIntent(rndisDevice()))
        val started = shadowOf(app).nextStartedActivity
        assertEquals(DiPlayActivity::class.java.name, started.component?.className)
    }

    @Test fun toggleOnOpensDiPlayForEcmInterfaceAttach() {
        DiPlayPreferences.saveUsbDeviceAutoLaunch(app, true)
        receive(attachIntent(ecmDevice()))
        val started = shadowOf(app).nextStartedActivity
        assertEquals(DiPlayActivity::class.java.name, started.component?.className)
    }

    @Test fun appleDeviceNeverTriggersAutoLaunch() {
        DiPlayPreferences.saveUsbDeviceAutoLaunch(app, true)
        val device = mock(UsbDevice::class.java)
        `when`(device.vendorId).thenReturn(0x05AC)
        receive(attachIntent(device))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun massStorageAttachDoesNotTriggerAutoLaunch() {
        DiPlayPreferences.saveUsbDeviceAutoLaunch(app, true)
        val device = mock(UsbDevice::class.java)
        `when`(device.vendorId).thenReturn(0x0781)
        `when`(device.deviceClass).thenReturn(0x08)
        receive(attachIntent(device))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun detachIntentIsIgnored() {
        DiPlayPreferences.saveUsbDeviceAutoLaunch(app, true)
        val intent = Intent(UsbManager.ACTION_USB_DEVICE_DETACHED).putExtra(UsbManager.EXTRA_DEVICE, dongleDevice())
        receive(intent)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    private fun receive(intent: Intent) = receiver.onReceive(app, intent)

    private fun attachIntent(device: UsbDevice): Intent =
        Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED).putExtra(UsbManager.EXTRA_DEVICE, device)

    /** UGREEN CM591 (Actions ATS2851): a known (VID, PID) dongle pair, no interfaces stubbed. */
    private fun dongleDevice(): UsbDevice {
        val device = mock(UsbDevice::class.java)
        `when`(device.vendorId).thenReturn(0x10D7)
        `when`(device.productId).thenReturn(0xB012)
        return device
    }

    /** RNDIS stick: the 0xEF/0x04/0x01 triple on the device descriptor. */
    private fun rndisDevice(): UsbDevice {
        val device = mock(UsbDevice::class.java)
        `when`(device.vendorId).thenReturn(0x0B95)
        `when`(device.productId).thenReturn(0x1790)
        `when`(device.deviceClass).thenReturn(0xEF)
        `when`(device.deviceSubclass).thenReturn(0x04)
        `when`(device.deviceProtocol).thenReturn(0x01)
        return device
    }

    /** CDC ECM adapter: the networking class code on its control interface. */
    private fun ecmDevice(): UsbDevice {
        val intf = mock(UsbInterface::class.java)
        `when`(intf.interfaceClass).thenReturn(0x02)
        `when`(intf.interfaceSubclass).thenReturn(0x06)
        `when`(intf.interfaceProtocol).thenReturn(0x00)
        val device = mock(UsbDevice::class.java)
        `when`(device.vendorId).thenReturn(0x0B95)
        `when`(device.productId).thenReturn(0x1790)
        `when`(device.interfaceCount).thenReturn(1)
        `when`(device.getInterface(0)).thenReturn(intf)
        return device
    }
}
