package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Test

class BootFrontingPolicyTest {

    private val home = setOf("com.android.launcher3", "com.example.desktop")

    @Test fun ownPackageStopsTheWatchdog() {
        assertEquals(
            BootFrontingPolicy.Decision.STOP,
            BootFrontingPolicy.decide("com.shihab.diplay", "com.shihab.diplay", home),
        )
    }

    @Test fun launcherPackageRetries() {
        assertEquals(
            BootFrontingPolicy.Decision.RETRY,
            BootFrontingPolicy.decide("com.android.launcher3", "com.shihab.diplay", home),
        )
    }

    @Test fun otherAppIsSkipped() {
        assertEquals(
            BootFrontingPolicy.Decision.SKIP,
            BootFrontingPolicy.decide("com.example.music", "com.shihab.diplay", home),
        )
    }

    @Test fun unknownForegroundRetries() {
        assertEquals(
            BootFrontingPolicy.Decision.RETRY,
            BootFrontingPolicy.decide(null, "com.shihab.diplay", home),
        )
    }

    @Test fun ownPackageWinsOverHomeSet() {
        assertEquals(
            BootFrontingPolicy.Decision.STOP,
            BootFrontingPolicy.decide(
                "com.shihab.diplay",
                "com.shihab.diplay",
                home + "com.shihab.diplay",
            ),
        )
    }
}
