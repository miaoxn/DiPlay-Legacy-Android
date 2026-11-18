package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import android.util.Log

object WifiForegroundTrigger {
    private const val TAG = "WifiForegroundTrigger"
    private const val DEBOUNCE_MILLIS = 20_000L
    @Volatile private var lastTriggerMillis = 0L

    fun onWifiConnected(context: Context, reason: String) {
        if (!AirPlayPersistence.loadWifiForegroundTrigger(context)) return
        val now = System.currentTimeMillis()
        if (now - lastTriggerMillis < DEBOUNCE_MILLIS) {
            Log.d(TAG, "Wi-Fi foreground trigger debounced ($reason)")
            return
        }
        lastTriggerMillis = now
        Log.i(TAG, "Wi-Fi connected trigger: bringing DiPlay to front ($reason)")
        bringToForeground(context)
    }

    /**
     * Brings DiPlay forward through the launcher activity. CarPlayHostActivity
     * is singleTask in its own task: aiming REORDER_TO_FRONT at the host
     * itself is a no-op (only onNewIntent fires, the window never moves —
     * proven on-device in the v0.3.15 retest and again by the Bluetooth-music
     * re-front in v0.3.31). DiPlayActivity routes back to the host while a
     * session is active, so the launcher with NEW_TASK is the only target
     * that works whether or not a session is running.
     */
    fun bringToForeground(context: Context) {
        val intent = Intent(context, DiPlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Could not bring DiPlay to front", it) }
    }
}
