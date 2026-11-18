package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import com.shilapi.xcertplay.usbbt.UsbBtDongleDetector

/** A launcher app that could be the head unit's own Bluetooth music app. */
internal data class BluetoothMusicCandidate(
    val packageName: String,
    val label: String,
)

private val BLUETOOTH_LABEL_TOKENS = listOf("bluetooth", "蓝牙", "بلوتوث")
private val MUSIC_LABEL_STEMS = listOf("musi", "音乐", "музык", "музик", "موسيق")

/**
 * Picks the head unit's Bluetooth music app out of its launcher apps.
 *
 * There is no standard intent or package name for it: every vendor ships
 * its own. The match is deliberately strict — the visible label must carry
 * BOTH a Bluetooth token and a music stem, so a plain music player (which
 * would play local files, not switch the audio source) and a Bluetooth
 * settings page never match. A bare "bt" only counts as its own word, so
 * labels like "Subtle Music" do not match either. Among matches the
 * alphabetically first package wins, so the pick is deterministic and the
 * diagnostic log can show exactly which app was chosen.
 */
internal fun selectBluetoothMusicApp(
    candidates: List<BluetoothMusicCandidate>,
): BluetoothMusicCandidate? {
    val matches = candidates.filter { candidate ->
        val label = candidate.label.lowercase()
        val hasBluetooth = BLUETOOTH_LABEL_TOKENS.any { label.contains(it) } ||
            label.split(Regex("[^a-z0-9]+")).any { it == "bt" }
        val hasMusic = MUSIC_LABEL_STEMS.any { label.contains(it) }
        hasBluetooth && hasMusic
    }
    return matches.sortedBy { it.packageName }.firstOrNull()
}

/**
 * Opens the head unit's own Bluetooth music app when CarPlay audio runs
 * over the car's Bluetooth.
 *
 * With "CarPlay audio over the car's Bluetooth" on, the iPhone sends sound
 * over its Bluetooth connection with the car — but on many head units
 * nothing is heard until the driver opens the Bluetooth music app and the
 * unit switches its audio source to Bluetooth. This automates that tap:
 * when the user enabled the companion switch, the music app is opened at
 * boot (after DiPlay auto-starts) and when a CarPlay session activates,
 * then DiPlay is brought back to the front after a short delay.
 *
 * Whether the audio source STAYS on Bluetooth once DiPlay returns to the
 * front depends on the head unit's MCU: units whose source follows the
 * foreground app may switch back. That is a property of the unit, not
 * something the app can detect, so the feature is opt-in and experimental,
 * every outcome is logged (including the resolved package, or that none
 * was found), and it never fires while a USB Bluetooth adapter is plugged
 * in — with an adapter the car's own Bluetooth cannot play audio at all.
 */
internal object BluetoothMusicOpener {
    /** How long the music app stays in front before DiPlay returns. */
    const val REFRONT_DELAY_MILLIS = 3_000L

    /**
     * Runs the gates, resolves and opens the music app, and schedules
     * [refront]. All outcomes go through [log]; a disabled feature stays
     * silent. [refront] runs on the main thread after the delay and must
     * guard its own target's lifecycle.
     */
    fun openIfConfigured(
        context: Context,
        log: (String) -> Unit,
        refront: () -> Unit,
    ) {
        if (!AirPlayPersistence.loadBluetoothMusicAutoOpen(context)) return
        if (!AirPlayPersistence.loadCarBluetoothAudio(context)) {
            log("bluetooth music auto-open skipped: car Bluetooth audio is off")
            return
        }
        if (usbDonglePresent(context)) {
            log("bluetooth music auto-open skipped: USB Bluetooth adapter present")
            return
        }
        val candidates = queryLauncherApps(context)
        val pick = selectBluetoothMusicApp(candidates)
        if (pick == null) {
            log("bluetooth music auto-open: no Bluetooth music app found among ${candidates.size} launcher apps")
            return
        }
        val launch = context.packageManager.getLaunchIntentForPackage(pick.packageName)
        if (launch == null) {
            log("bluetooth music auto-open: no launch intent for ${pick.packageName}")
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launch)
        } catch (error: RuntimeException) {
            log("bluetooth music auto-open: launch failed for ${pick.packageName}: ${error.message}")
            return
        }
        log("bluetooth music auto-open: opened ${pick.label} (${pick.packageName}); returning to DiPlay in ${REFRONT_DELAY_MILLIS}ms")
        Handler(Looper.getMainLooper()).postDelayed(
            { runCatching { refront() } },
            REFRONT_DELAY_MILLIS,
        )
    }

    private fun queryLauncherApps(context: Context): List<BluetoothMusicCandidate> =
        runCatching {
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            context.packageManager.queryIntentActivities(launcher, 0).mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName) return@mapNotNull null
                val label = runCatching { info.loadLabel(context.packageManager).toString() }
                    .getOrDefault("")
                BluetoothMusicCandidate(packageName, label)
            }
        }.getOrDefault(emptyList())

    private fun usbDonglePresent(context: Context): Boolean = runCatching {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return false
        UsbBtDongleDetector(context.applicationContext).findDongles(usbManager).isNotEmpty()
    }.getOrDefault(false)
}
