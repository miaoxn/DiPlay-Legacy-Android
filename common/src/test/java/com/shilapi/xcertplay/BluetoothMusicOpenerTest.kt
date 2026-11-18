package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothMusicOpenerTest {
    private fun candidate(packageName: String, label: String) =
        BluetoothMusicCandidate(packageName, label)

    @Test fun matchesBluetoothMusicLabels() {
        assertEquals(
            candidate("com.vendor.btmusic", "蓝牙音乐"),
            selectBluetoothMusicApp(listOf(candidate("com.vendor.btmusic", "蓝牙音乐"))),
        )
        assertEquals(
            candidate("com.vendor.bt", "BT Music"),
            selectBluetoothMusicApp(listOf(candidate("com.vendor.bt", "BT Music"))),
        )
        assertEquals(
            candidate("com.vendor.bt", "Bluetooth Music"),
            selectBluetoothMusicApp(listOf(candidate("com.vendor.bt", "Bluetooth Music"))),
        )
        assertEquals(
            candidate("com.vendor.bt", "BLUETOOTH MUSIC"),
            selectBluetoothMusicApp(listOf(candidate("com.vendor.bt", "BLUETOOTH MUSIC"))),
        )
    }

    @Test fun requiresBothBluetoothAndMusic() {
        assertNull(selectBluetoothMusicApp(listOf(candidate("com.vendor.bt", "蓝牙设置"))))
        assertNull(selectBluetoothMusicApp(listOf(candidate("com.vendor.music", "音乐"))))
        assertNull(selectBluetoothMusicApp(listOf(candidate("com.vendor.music", "Musicolet"))))
        assertNull(selectBluetoothMusicApp(listOf(candidate("com.vendor.bt", "BT File Transfer"))))
    }

    @Test fun bareBtSubstringDoesNotMatch() {
        assertNull(selectBluetoothMusicApp(listOf(candidate("com.vendor.music", "Subtle Music"))))
    }

    @Test fun pickIsDeterministicAcrossCandidates() {
        val a = candidate("com.vendor.a", "BT Music")
        val b = candidate("com.vendor.b", "蓝牙音乐")
        assertEquals(a, selectBluetoothMusicApp(listOf(b, a)))
        assertEquals(a, selectBluetoothMusicApp(listOf(a, b)))
    }

    @Test fun emptyListMatchesNothing() {
        assertNull(selectBluetoothMusicApp(emptyList()))
    }
}
