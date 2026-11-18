package com.shilapi.xcertplay.bluetooth

import android.content.Context
import android.content.Intent

/** ANW phone-link service backing the Hangsheng built-in Bluetooth data channel. */
internal const val ANW_PHONE_LINK_ACTION = "com.anwsdk.service.AnwPhoneLink"
internal const val ANW_PHONE_LINK_PACKAGE = "com.anwsdk.service"

/**
 * Whether the ANW phone-link service resolves on this head unit. This is a read-only
 * PackageManager query: it never binds the service. Some Hangsheng units (e.g. NTDA3)
 * expose Bluetooth only through ANW while the `com.hsae.bluetoothservice` package is
 * absent, so the HSAE package check alone would wrongly judge the built-in channel dead.
 */
internal fun isAnwPhoneLinkAvailable(context: Context): Boolean = runCatching {
    val intent = Intent(ANW_PHONE_LINK_ACTION).apply { setPackage(ANW_PHONE_LINK_PACKAGE) }
    context.packageManager.queryIntentServices(intent, 0).isNotEmpty()
}.getOrDefault(false)
