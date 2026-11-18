package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeModeTest {
    @Test fun fromKeyMapsStableValuesAndDefaultsToSystem() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromKey("system"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromKey("dark"))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromKey("light"))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromKey("bogus"))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromKey(null))
    }

    @Test fun systemModeFollowsTheReportedNightState() {
        assertEquals(true, resolveAppNight(AppThemeMode.SYSTEM, true, false))
        assertEquals(false, resolveAppNight(AppThemeMode.SYSTEM, false, true))
    }

    @Test fun systemModeKeepsThePreviousThemeWhenTheUnitReportsUndefined() {
        assertEquals(true, resolveAppNight(AppThemeMode.SYSTEM, null, true))
        assertEquals(false, resolveAppNight(AppThemeMode.SYSTEM, null, false))
    }

    @Test fun manualModesIgnoreTheSystemState() {
        for (systemNight in listOf(true, false, null)) {
            assertEquals(true, resolveAppNight(AppThemeMode.DARK, systemNight, false))
            assertEquals(false, resolveAppNight(AppThemeMode.LIGHT, systemNight, true))
        }
    }

    @Test fun timeWindowNightRunsFrom1830To0600() {
        assertEquals(true, isNightByTimeWindow(0))
        assertEquals(true, isNightByTimeWindow(359))
        assertEquals(false, isNightByTimeWindow(360))
        assertEquals(false, isNightByTimeWindow(720))
        assertEquals(false, isNightByTimeWindow(1109))
        assertEquals(true, isNightByTimeWindow(1110))
        assertEquals(true, isNightByTimeWindow(1439))
    }

    @Test fun timeWindowOverridesTheReportedStateWhenEnabled() {
        // A unit stuck reporting night must not keep the window from engaging.
        assertEquals(false, effectiveSystemNight(true, true, 720))
        assertEquals(true, effectiveSystemNight(false, true, 0))
        assertEquals(true, effectiveSystemNight(null, true, 0))
        assertEquals(false, effectiveSystemNight(null, true, 720))
    }

    @Test fun reportedStatePassesThroughWhenTheWindowIsOff() {
        assertEquals(true, effectiveSystemNight(true, false, 720))
        assertEquals(false, effectiveSystemNight(false, false, 0))
        assertEquals(null, effectiveSystemNight(null, false, 0))
    }

    @Test fun customWindowWrapsPastMidnight() {
        val window = AppThemeNightWindow(startMinute = 22 * 60, endMinute = 5 * 60)
        assertEquals(true, isNightByTimeWindow(0, window))
        assertEquals(true, isNightByTimeWindow(299, window))
        assertEquals(false, isNightByTimeWindow(300, window))
        assertEquals(false, isNightByTimeWindow(1319, window))
        assertEquals(true, isNightByTimeWindow(1320, window))
        assertEquals(true, isNightByTimeWindow(1439, window))
    }

    @Test fun customWindowWithoutWrapping() {
        val window = AppThemeNightWindow(startMinute = 9 * 60, endMinute = 17 * 60)
        assertEquals(false, isNightByTimeWindow(0, window))
        assertEquals(false, isNightByTimeWindow(539, window))
        assertEquals(true, isNightByTimeWindow(540, window))
        assertEquals(true, isNightByTimeWindow(1019, window))
        assertEquals(false, isNightByTimeWindow(1020, window))
        assertEquals(false, isNightByTimeWindow(1439, window))
    }

    @Test fun equalStartAndEndMeansAlwaysDay() {
        val window = AppThemeNightWindow(startMinute = 600, endMinute = 600)
        assertEquals(false, isNightByTimeWindow(0, window))
        assertEquals(false, isNightByTimeWindow(600, window))
        assertEquals(false, isNightByTimeWindow(1439, window))
    }

    @Test fun effectiveSystemNightUsesTheCustomWindow() {
        val window = AppThemeNightWindow(startMinute = 22 * 60, endMinute = 5 * 60)
        assertEquals(true, effectiveSystemNight(null, true, 0, window))
        assertEquals(false, effectiveSystemNight(null, true, 720, window))
        assertEquals(true, effectiveSystemNight(false, true, 0, window))
    }

    @Test fun boundaryDelayUsesTheCustomWindow() {
        val window = AppThemeNightWindow(startMinute = 22 * 60, endMinute = 5 * 60)
        assertEquals(1_001L, millisUntilNextThemeBoundary(1319, 59, 999, window))
        assertEquals(25_201_000L, millisUntilNextThemeBoundary(1320, 0, 0, window))
        assertEquals(2_000L, millisUntilNextThemeBoundary(299, 59, 0, window))
        assertEquals(61_201_000L, millisUntilNextThemeBoundary(300, 0, 0, window))
    }

    @Test fun boundaryDelayLandsOneSecondPastTheNextBoundary() {
        assertEquals(45_001_000L, millisUntilNextThemeBoundary(360, 0, 0))
        assertEquals(1_001L, millisUntilNextThemeBoundary(1109, 59, 999))
        assertEquals(41_401_000L, millisUntilNextThemeBoundary(1110, 0, 0))
        assertEquals(2_000L, millisUntilNextThemeBoundary(359, 59, 0))
        assertEquals(21_602_000L, millisUntilNextThemeBoundary(1439, 59, 0))
    }
}
