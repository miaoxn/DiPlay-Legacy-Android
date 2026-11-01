package com.shilapi.xcertplay.ecarx

import com.shilapi.xcertplay.bluetooth.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class GocRouteTest {
    @Test fun selectedButUnsupportedGocDoesNotFallBackToAndroid() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("goc449", 0)
        prefs.edit().putBoolean("enabled", true).commit()
        try {
            val goc = EcarxGocBluetoothAdapter(context)
            val hybrid = HybridBluetoothAdapter(HsaeBluetoothAdapter(context), AndroidBluetoothAdapter(context), goc = goc)
            assertFalse(hybrid.isSupported())
            assertFalse(hybrid.enable())
            assertEquals("ECARX 449 GOC", hybrid.getAdapterName())
            assertNull(hybrid.getConnectedDevice())
            assertThrows(java.io.IOException::class.java) { hybrid.openRfcommStream("AA:BB:CC:DD:EE:FF", GocFirmware.UUID) }
            hybrid.disable()
            assertTrue(goc.selected()) // Stopping CarPlay must not rewrite the chosen route.
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun factoryListNormalizesDeduplicatesAndRejectsInvalidEntries() {
        val list = FactoryDevices.normalize(5, arrayOf("aabbccddeeff", "AA:BB:CC:DD:EE:FF", "020000000000", "bad"), arrayOf("first", "iPhone"))
        assertEquals(1, list.size)
        assertEquals("AA:BB:CC:DD:EE:FF", list.single().address)
        assertEquals("iPhone", list.single().name)
    }
}
