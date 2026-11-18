package com.shilapi.xcertplay.network

import android.content.Context

/**
 * Runs [WifiRadioHelperMain] as root through [RootShell] (issue #301(a)).
 * Only invoked by HostapdHotspotManager after `svc wifi enable` left wlan0
 * absent, or after `svc wifi disable` left the radio on in the failure-path
 * reload (IKVWMF): on some firmware the `svc` binary is a hollow stub, and
 * the helper toggles the radio through the framework API directly instead.
 * The helper only switches the radio — hostapd still owns the AP itself,
 * exactly as in the normal path.
 */
object WifiRadioHelper {
    internal const val HEADER = "DIPLAY_WIFI_RADIO_V1"

    /** The radio direction the privileged helper toggles. */
    enum class RadioAction(val arg: String) {
        ENABLE("enable"),
        DISABLE("disable"),
    }

    data class Result(val ok: Boolean, val raw: String)

    fun run(context: Context, action: RadioAction = RadioAction.ENABLE): Result {
        val output = RootShell.run(command(context.packageCodePath, context.packageName, action), 30_000).output
        return Result(ok = parse(output), raw = output.trim().replace('\n', ' ').take(200))
    }

    internal fun command(packageCodePath: String, packageName: String, action: RadioAction): String {
        val quoted = "'" + packageCodePath.replace("'", "'\"'\"'") + "'"
        return "CLASSPATH=$quoted app_process /system/bin " +
            "${WifiRadioHelperMain::class.java.name} $packageName ${action.arg}"
    }

    internal fun parse(output: String?): Boolean =
        output?.lines()?.any { it.trim() == "$HEADER|OK" } ?: false
}
