package com.shilapi.xcertplay.settings

import android.content.Context
import android.graphics.Color

/**
 * The full DiPlay palette, shared by the settings screens. Two surfaces (card pages and
 * overlay menus) times two modes (dark and light). CARD and OVERLAY keep their historic
 * names and mean the dark variants; [forSurface] resolves the entry for the app theme.
 * The light palette mirrors the dark one: the waiting screen's original day colors anchor
 * the background and text, and the accent is the same hue darkened for contrast on white.
 */
enum class SettingsTheme(
    val isOverlay: Boolean,
    val night: Boolean,
    val background: Int,
    val surface: Int,
    val button: Int,
    val border: Int,
    val railSelected: Int,
    val railSelectedBorder: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val accent: Int,
    val accentTrack: Int,
    val trackOff: Int,
    val buttonText: Int,
    val warning: Int,
    val ready: Int,
) {
    CARD(
        isOverlay = false,
        night = true,
        background = Color.rgb(12, 17, 27),
        surface = Color.rgb(21, 30, 44),
        button = Color.rgb(31, 43, 61),
        border = Color.rgb(42, 56, 75),
        railSelected = Color.rgb(24, 54, 92),
        railSelectedBorder = Color.rgb(42, 82, 130),
        textPrimary = Color.rgb(241, 245, 252),
        textSecondary = Color.rgb(168, 182, 202),
        accent = Color.rgb(166, 200, 255),
        accentTrack = Color.rgb(0x32, 0x58, 0x8c),
        trackOff = Color.rgb(42, 56, 75),
        buttonText = Color.rgb(12, 17, 27),
        warning = Color.rgb(255, 196, 128),
        ready = Color.rgb(127, 205, 154),
    ),
    CARD_LIGHT(
        isOverlay = false,
        night = false,
        background = Color.rgb(233, 238, 246),
        surface = Color.rgb(255, 255, 255),
        button = Color.rgb(241, 245, 249),
        border = Color.rgb(203, 213, 225),
        railSelected = Color.rgb(219, 234, 254),
        railSelectedBorder = Color.rgb(147, 197, 253),
        textPrimary = Color.rgb(28, 28, 30),
        textSecondary = Color.rgb(90, 100, 116),
        accent = Color.rgb(37, 99, 235),
        accentTrack = Color.rgb(191, 219, 254),
        trackOff = Color.rgb(203, 213, 225),
        buttonText = Color.rgb(255, 255, 255),
        warning = Color.rgb(176, 116, 8),
        ready = Color.rgb(22, 132, 80),
    ),
    // The in-session (three-finger) menu shares the main settings palette: it used
    // to carry its own green accent, which made the two settings surfaces look like
    // different products. Layout differences stay (isOverlay), colors do not.
    OVERLAY(
        isOverlay = true,
        night = true,
        background = Color.rgb(12, 17, 27),
        surface = Color.rgb(21, 30, 44),
        button = Color.rgb(31, 43, 61),
        border = Color.rgb(42, 56, 75),
        railSelected = Color.rgb(24, 54, 92),
        railSelectedBorder = Color.rgb(42, 82, 130),
        textPrimary = Color.rgb(241, 245, 252),
        textSecondary = Color.rgb(168, 182, 202),
        accent = Color.rgb(166, 200, 255),
        accentTrack = Color.rgb(0x32, 0x58, 0x8c),
        trackOff = Color.rgb(42, 56, 75),
        buttonText = Color.rgb(12, 17, 27),
        warning = Color.rgb(255, 196, 128),
        ready = Color.rgb(127, 205, 154),
    ),
    OVERLAY_LIGHT(
        isOverlay = true,
        night = false,
        background = Color.rgb(233, 238, 246),
        surface = Color.rgb(255, 255, 255),
        button = Color.rgb(241, 245, 249),
        border = Color.rgb(203, 213, 225),
        railSelected = Color.rgb(219, 234, 254),
        railSelectedBorder = Color.rgb(147, 197, 253),
        textPrimary = Color.rgb(28, 28, 30),
        textSecondary = Color.rgb(90, 100, 116),
        accent = Color.rgb(37, 99, 235),
        accentTrack = Color.rgb(191, 219, 254),
        trackOff = Color.rgb(203, 213, 225),
        buttonText = Color.rgb(255, 255, 255),
        warning = Color.rgb(176, 116, 8),
        ready = Color.rgb(22, 132, 80),
    );

    companion object {
        fun forSurface(isOverlay: Boolean, night: Boolean): SettingsTheme = when {
            isOverlay && night -> OVERLAY
            isOverlay -> OVERLAY_LIGHT
            night -> CARD
            else -> CARD_LIGHT
        }
    }

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
