package com.shilapi.xcertplay

/** DiPlay's own UI theme mode. Stable preference values; never persist enum ordinals. */
enum class AppThemeMode(val key: String) {
    SYSTEM("system"),
    DARK("dark"),
    LIGHT("light");

    companion object {
        fun fromKey(key: String?): AppThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * Resolves whether DiPlay's own UI renders dark. SYSTEM follows the head unit's reported
 * night state; when the unit reports undefined, the previously applied theme is kept
 * instead of flipping (see [nightModeOrNull]). DARK and LIGHT ignore the system state.
 */
internal fun resolveAppNight(mode: AppThemeMode, systemNight: Boolean?, fallbackNight: Boolean): Boolean =
    when (mode) {
        AppThemeMode.SYSTEM -> systemNight ?: fallbackNight
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

/** Default local-time night window for the app-theme fallback, in minutes after midnight. */
internal const val APP_THEME_TIME_WINDOW_NIGHT_START_MINUTES = 18 * 60 + 30
internal const val APP_THEME_TIME_WINDOW_NIGHT_END_MINUTES = 6 * 60
private const val MINUTES_PER_DAY = 24 * 60

/**
 * The user-adjustable night window for the app-theme fallback. Semantics
 * mirror [CarPlayNightSchedule]: a window whose start equals its end is
 * empty (always day); otherwise night runs from start (inclusive) to end
 * (exclusive), wrapping past midnight when start is after end.
 */
data class AppThemeNightWindow(
    val startMinute: Int = APP_THEME_TIME_WINDOW_NIGHT_START_MINUTES,
    val endMinute: Int = APP_THEME_TIME_WINDOW_NIGHT_END_MINUTES,
) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY && endMinute in 0 until MINUTES_PER_DAY)
    }

    fun isNight(minuteOfDay: Int): Boolean = when {
        startMinute == endMinute -> false
        startMinute < endMinute -> minuteOfDay in startMinute until endMinute
        else -> minuteOfDay >= startMinute || minuteOfDay < endMinute
    }
}

/** Pure window check: [minuteOfDay] is local minutes after midnight, 0..1439. */
internal fun isNightByTimeWindow(
    minuteOfDay: Int,
    window: AppThemeNightWindow = AppThemeNightWindow(),
): Boolean = window.isNight(minuteOfDay)

internal fun currentMinuteOfDay(): Int {
    val calendar = java.util.Calendar.getInstance()
    return calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
        calendar.get(java.util.Calendar.MINUTE)
}

/**
 * Decides the night state for Follow-system mode. With the time window
 * enabled, the window always wins — the head unit's reported state is
 * ignored, because some units report a stuck value (constant night)
 * that would otherwise keep the fallback from ever engaging. With the
 * window off, the reported state passes through untouched, and null
 * stays null so the caller keeps the previously applied theme.
 */
internal fun effectiveSystemNight(
    systemNight: Boolean?,
    useTimeWindow: Boolean,
    minuteOfDay: Int = currentMinuteOfDay(),
    window: AppThemeNightWindow = AppThemeNightWindow(),
): Boolean? = if (useTimeWindow) isNightByTimeWindow(minuteOfDay, window) else systemNight

/**
 * Milliseconds from the given local wall-clock reading until the next
 * window boundary (the window's start or end, whichever comes first),
 * landing one second past it so the minute has actually turned over.
 * Always positive.
 */
internal fun millisUntilNextThemeBoundary(
    minuteOfDay: Int,
    secondOfMinute: Int,
    millisecondOfSecond: Int,
    window: AppThemeNightWindow = AppThemeNightWindow(),
): Long {
    val first = minOf(window.startMinute, window.endMinute)
    val second = maxOf(window.startMinute, window.endMinute)
    val nextBoundary = when {
        minuteOfDay < first -> first
        minuteOfDay < second -> second
        else -> first + MINUTES_PER_DAY
    }
    return (nextBoundary - minuteOfDay) * 60_000L -
        secondOfMinute * 1_000L - millisecondOfSecond + 1_000L
}
