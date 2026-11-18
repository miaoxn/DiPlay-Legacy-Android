package com.shilapi.xcertplay.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class SettingsThemePaletteTest {
    @Test fun forSurfaceResolvesAllFourCombinations() {
        assertEquals(SettingsTheme.CARD, SettingsTheme.forSurface(isOverlay = false, night = true))
        assertEquals(SettingsTheme.CARD_LIGHT, SettingsTheme.forSurface(isOverlay = false, night = false))
        assertEquals(SettingsTheme.OVERLAY, SettingsTheme.forSurface(isOverlay = true, night = true))
        assertEquals(SettingsTheme.OVERLAY_LIGHT, SettingsTheme.forSurface(isOverlay = true, night = false))
    }

    @Test fun nightFlagsMatchTheEntries() {
        assertTrue(SettingsTheme.CARD.night)
        assertFalse(SettingsTheme.CARD_LIGHT.night)
        assertTrue(SettingsTheme.OVERLAY.night)
        assertFalse(SettingsTheme.OVERLAY_LIGHT.night)
    }

    @Test fun overlaySharesTheCardPalette() {
        assertEquals(palette(SettingsTheme.CARD), palette(SettingsTheme.OVERLAY))
        assertEquals(palette(SettingsTheme.CARD_LIGHT), palette(SettingsTheme.OVERLAY_LIGHT))
    }

    @Test fun lightPaletteIsLightAndDarkPaletteIsDark() {
        assertTrue(luminance(SettingsTheme.CARD_LIGHT.background) > 200)
        assertTrue(luminance(SettingsTheme.CARD.background) < 40)
        assertTrue(luminance(SettingsTheme.CARD_LIGHT.textPrimary) < 60)
        assertTrue(luminance(SettingsTheme.CARD.textPrimary) > 200)
    }

    @Test fun everyLightColorDiffersFromItsDarkCounterpart() {
        val dark = palette(SettingsTheme.CARD)
        val light = palette(SettingsTheme.CARD_LIGHT)
        assertEquals(dark.size, light.size)
        dark.zip(light).forEach { (d, l) -> assertNotEquals(d, l) }
    }

    private fun palette(theme: SettingsTheme): List<Int> = listOf(
        theme.background, theme.surface, theme.button, theme.border,
        theme.railSelected, theme.railSelectedBorder,
        theme.textPrimary, theme.textSecondary, theme.accent, theme.accentTrack,
        theme.trackOff, theme.buttonText, theme.warning, theme.ready,
    )

    private fun luminance(color: Int): Double {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
