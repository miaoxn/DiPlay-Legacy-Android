package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class WifiForegroundTriggerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("airplay_preferences", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testDisabledDoesNotLaunchActivity() {
        AirPlayPersistence.saveWifiForegroundTrigger(context, false)
        WifiForegroundTrigger.onWifiConnected(context, "test")

        val app = shadowOf(context as android.app.Application)
        assertEquals(null, app.nextStartedActivity)
    }

    @Test
    fun testEnabledLaunchesActivityAndDebounces() {
        AirPlayPersistence.saveWifiForegroundTrigger(context, true)
        WifiForegroundTrigger.onWifiConnected(context, "test")

        val app = shadowOf(context as android.app.Application)
        val startedIntent = app.nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(DiPlayActivity::class.java.name, startedIntent.component?.className)
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK,
            startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK,
        )
        assertEquals(
            0,
            startedIntent.flags and (Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )

        // Second call right away should be debounced
        WifiForegroundTrigger.onWifiConnected(context, "immediate second call")
        assertEquals(null, app.nextStartedActivity)
    }
}
