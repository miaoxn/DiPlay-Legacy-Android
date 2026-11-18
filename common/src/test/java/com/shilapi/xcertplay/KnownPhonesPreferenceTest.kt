package com.shilapi.xcertplay

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class KnownPhonesPreferenceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("airplay_preferences", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val diplayPrefs = context.getSharedPreferences("diplay", Context.MODE_PRIVATE)
        diplayPrefs.edit().clear().commit()
    }

    @Test
    fun testLegacyMigration() {
        val diplayPrefs = context.getSharedPreferences("diplay", Context.MODE_PRIVATE)
        diplayPrefs.edit().putString("phone_address", "AA:BB:CC:DD:EE:FF").commit()

        val list = DiPlayPreferences.knownPhones(context)
        assertEquals(1, list.size)
        assertEquals("AA:BB:CC:DD:EE:FF", list[0].address)
    }

    @Test
    fun testRecordPhoneConnectedBumpsToHeadAndInserts() {
        DiPlayPreferences.recordPhoneConnected(context, "11:22:33:44:55:66", "Phone 1")
        DiPlayPreferences.recordPhoneConnected(context, "22:33:44:55:66:77", "Phone 2")

        var list = DiPlayPreferences.knownPhones(context)
        assertEquals(2, list.size)
        assertEquals("22:33:44:55:66:77", list[0].address)
        assertEquals("11:22:33:44:55:66", list[1].address)

        // Connect Phone 1 again -> should bump to top
        DiPlayPreferences.recordPhoneConnected(context, "11:22:33:44:55:66", "Phone 1 Updated")
        list = DiPlayPreferences.knownPhones(context)
        assertEquals(2, list.size)
        assertEquals("11:22:33:44:55:66", list[0].address)
        assertEquals("Phone 1 Updated", list[0].name)
    }

    @Test
    fun testUpdateAliasAndRemove() {
        DiPlayPreferences.recordPhoneConnected(context, "AA:BB:CC:DD:EE:FF", "My iPhone")
        DiPlayPreferences.updatePhoneAlias(context, "AA:BB:CC:DD:EE:FF", "Work Phone")

        var list = DiPlayPreferences.knownPhones(context)
        assertEquals("Work Phone", list[0].alias)
        assertEquals("Work Phone", list[0].displayName())

        DiPlayPreferences.removeKnownPhone(context, "AA:BB:CC:DD:EE:FF")
        list = DiPlayPreferences.knownPhones(context)
        assertTrue(list.isEmpty())
    }
}
