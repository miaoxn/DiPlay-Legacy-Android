package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.network.CarHotspotStatus
import com.shilapi.xcertplay.network.CarHotspotTethering
import com.shilapi.xcertplay.network.WirelessStartupPolicy

/** Starts the CarPlay host after boot when the user has enabled the startup option. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val launchEnabled = AirPlayPersistence.loadAutoStartOnBoot(context)
        StartupDiagnosticSnapshot.received(context, launchEnabled)
        if (launchEnabled) {
            // REORDER_TO_FRONT: on some OEM launchers the started activity stays
            // covered by the desktop after boot. Bringing an existing instance
            // forward covers that case; it is a no-op when there is none.
            val launch = Intent(context, DiPlayActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                )
            }
            try {
                context.startActivity(launch)
                StartupDiagnosticSnapshot.launchResult(context)
                startBootFrontingWatchdog(context.applicationContext)
            } catch (error: RuntimeException) {
                StartupDiagnosticSnapshot.launchResult(context, error)
                Log.w(TAG, "Boot auto-start could not launch DiPlayActivity", error)
            }
            // With the Bluetooth-music switch on, open the car's Bluetooth
            // music app so the audio source is already on Bluetooth when
            // CarPlay connects; DiPlay is brought back to the front after
            // a short delay. Only when DiPlay itself auto-starts at boot.
            BluetoothMusicOpener.openIfConfigured(
                context,
                { line ->
                    Log.i(TAG, "Boot $line")
                    StartupDiagnosticSnapshot.bluetoothMusic(context, line)
                },
            ) {
                runCatching { context.startActivity(launch) }
            }
        }

        healHotspotIfOptedIn(context)
    }

    /**
     * Issue #315 follow-up: boot-time hotspot self-healing. Some head units
     * (e.g. Zhangxun PF8227) never bring their own hotspot up after boot, so
     * the phone has no AP to join and wireless CarPlay cannot start until
     * the driver opens system settings by hand. When the user has opted into
     * automatic hotspot startup (the same gate as the launch-time path —
     * auto_enable on, wireless on, manual mode, valid saved SSID/passphrase;
     * there is no session at boot), bring the AP up here, independent of
     * whether the DiPlay UI itself auto-launches. The enable call blocks
     * (up to the boot hotspot budget) so it runs on a worker thread behind
     * goAsync; failures only log — the per-connection auto-enable path
     * remains the fallback, and nothing is ever prompted for at boot.
     */
    private fun healHotspotIfOptedIn(context: Context) {
        if (!CarHotspotSetup.shouldStartOnLaunch(context, hasSession = false)) return
        if (CarHotspotStatus.isEnabled(context) == true) {
            Log.i(TAG, "Boot hotspot self-heal: hotspot already on, nothing to do")
            return
        }
        val pending = goAsync()
        Thread({
            try {
                val result = CarHotspotTethering.enable(
                    context,
                    isCancelled = { !CarHotspotSettings.enabled(context) },
                    timeoutMillis = WirelessStartupPolicy.BOOT_HOTSPOT_READY_MILLIS,
                    log = { Log.i(TAG, "Boot hotspot self-heal: $it") },
                )
                Log.i(TAG, "Boot hotspot self-heal finished: $result")
            } catch (error: Throwable) {
                Log.w(TAG, "Boot hotspot self-heal failed", error)
            } finally {
                pending.finish()
            }
        }, "boot-hotspot-heal").start()
    }

    /**
     * Boot fronting watchdog (IKTCRS §1): BOOT_COMPLETED fires before some OEM
     * desktops finish loading, so the first launch can end up covered by the
     * desktop even though startActivity returned. Re-check a few times after
     * boot; each check only re-pulls when the foreground is a home screen
     * (or unknown on API 21+), never over another app, and stops as soon as
     * DiPlay itself is in front. Every decision is logged, and the last
     * outcome lands in the boot diagnostics snapshot.
     */
    private fun startBootFrontingWatchdog(context: Context) {
        val appContext = context.applicationContext
        Thread({
            val checkDelays = longArrayOf(6_000L, 8_000L, 10_000L)
            val homePackages = HomeScreenMonitor.queryHomePackages(appContext)
            var outcome = "retries-exhausted"
            for (delay in checkDelays) {
                try {
                    Thread.sleep(delay)
                } catch (_: InterruptedException) {
                    break
                }
                if (!AirPlayPersistence.loadAutoStartOnBoot(appContext)) {
                    outcome = "disabled"
                    break
                }
                val foreground = HomeScreenMonitor.foregroundPackage(appContext)
                val decision = BootFrontingPolicy.decide(foreground, appContext.packageName, homePackages)
                Log.i(TAG, "Boot fronting watchdog: foreground=${foreground ?: "unknown"} decision=$decision")
                when (decision) {
                    BootFrontingPolicy.Decision.STOP -> {
                        outcome = "already-front"
                        break
                    }
                    BootFrontingPolicy.Decision.SKIP -> {
                        outcome = "skipped:other-app"
                    }
                    BootFrontingPolicy.Decision.RETRY -> {
                        WifiForegroundTrigger.bringToForeground(appContext)
                        outcome = "re-pulled"
                    }
                }
            }
            StartupDiagnosticSnapshot.bootWatchdog(appContext, outcome)
        }, "boot-fronting-watchdog").apply { isDaemon = true; start() }
    }

    private companion object {
        const val TAG = "xcertplay-boot"
    }
}
