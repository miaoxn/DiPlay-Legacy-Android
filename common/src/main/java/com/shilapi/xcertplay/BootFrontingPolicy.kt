package com.shilapi.xcertplay

/**
 * Pure decision for the boot fronting watchdog (IKTCRS §1): after boot, DiPlay
 * may only re-pull itself forward when the foreground is a home screen — never
 * over another app the driver opened, and never once it is already in front.
 *
 * The foreground package is only knowable on pre-Lollipop units (see
 * [HomeScreenMonitor.foregroundPackage]); on API 21+ it is unknown, and the
 * watchdog keeps retrying as before rather than guessing.
 */
internal object BootFrontingPolicy {

    enum class Decision {
        /** Foreground is a home screen (or unknown): pull DiPlay forward. */
        RETRY,

        /** Foreground is another app: leave it alone this round. */
        SKIP,

        /** DiPlay itself is in front: stop watching. */
        STOP,
    }

    fun decide(foregroundPackage: String?, ownPackage: String, homePackages: Set<String>): Decision =
        when {
            foregroundPackage == ownPackage -> Decision.STOP
            foregroundPackage == null -> Decision.RETRY
            foregroundPackage in homePackages -> Decision.RETRY
            else -> Decision.SKIP
        }
}
