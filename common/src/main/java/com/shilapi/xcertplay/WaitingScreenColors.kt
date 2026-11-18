package com.shilapi.xcertplay

import android.graphics.Color

/**
 * The screen before CarPlay's video is DiPlay's own chrome, not CarPlay content: it
 * follows the app theme (see [AppThemeMode]), so it renders light only when every other
 * DiPlay surface does too. Issue #297 made it always dark because a near-white
 * connecting screen glared against otherwise-dark surfaces; that inconsistency is gone
 * now that the whole app follows one theme, and the dark palette is unchanged for
 * night mode and for the always-dark theme choice. CarPlay's own day/night mode still
 * applies to the video once the session starts.
 */
internal data class WaitingScreenColors(val background: Int, val text: Int, val secondary: Int) {
    companion object {
        fun of(night: Boolean): WaitingScreenColors = if (night) {
            WaitingScreenColors(Color.rgb(12, 17, 27), Color.rgb(241, 245, 252), Color.rgb(168, 182, 202))
        } else {
            WaitingScreenColors(Color.rgb(233, 238, 246), Color.rgb(28, 28, 30), Color.rgb(90, 100, 116))
        }
    }
}
