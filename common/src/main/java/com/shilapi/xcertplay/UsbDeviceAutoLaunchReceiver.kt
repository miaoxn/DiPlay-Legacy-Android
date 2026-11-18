package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.shilapi.xcertplay.usbbt.UsbBtDongleDetector
import com.shilapi.xcertplay.usbbt.isUsbNetworkAdapter
import com.shilapi.xcertplay.usbbt.toSignature

/**
 * Opens DiPlay when a USB Bluetooth dongle or USB network sharing device is plugged in,
 * mirroring the wired path where inserting the iPhone brings the connection screen up
 * without tapping the launcher icon (IKUZ2R follow-up request).
 *
 * This is a manifest receiver with a bare USB_DEVICE_ATTACHED filter and NO device-filter
 * meta-data: it fires for every attach while the app is dead or alive, and it never takes
 * part in USB default-handler resolution, so the wired flow and the permission-persistence
 * trampoline are untouched. The device-kind filtering happens here in code, and the whole
 * behavior is gated by the opt-in "Open DiPlay when a USB device is plugged in" setting
 * (off by default). iPhones are excluded: the wired path owns them.
 *
 * On Android 10+ a background receiver may not start an activity; the launch is wrapped
 * and only logged when the platform refuses it. The head-unit fleet this targets predates
 * that restriction.
 */
class UsbDeviceAutoLaunchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return
        if (!DiPlayPreferences.usbDeviceAutoLaunch(context)) return
        val device = intent.attachedDevice() ?: return
        if (device.vendorId == APPLE_VENDOR_ID) return
        val viaDongle = runCatching {
            UsbBtDongleDetector(context.applicationContext).identify(device) != null
        }.getOrDefault(false)
        val viaNetwork = runCatching { isUsbNetworkAdapter(device.toSignature()) }.getOrDefault(false)
        if (!viaDongle && !viaNetwork) return
        Log.i(
            TAG,
            "USB device attached: vid=0x${device.vendorId.toString(16)} pid=0x${device.productId.toString(16)} " +
                "dongle=$viaDongle network=$viaNetwork, opening DiPlay",
        )
        val launch = Intent(context, DiPlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        try {
            context.startActivity(launch)
        } catch (error: RuntimeException) {
            Log.w(TAG, "USB auto-launch could not open DiPlay", error)
        }
    }

    private fun Intent.attachedDevice(): UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
    }

    private companion object {
        const val TAG = "UsbDeviceAutoLaunch"
        const val APPLE_VENDOR_ID = 0x05AC
    }
}
