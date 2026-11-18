package com.shilapi.xcertplay.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MeasuredDecodeStatsTest {

    @Before fun clear() {
        MeasuredDecodeStats.reset()
    }

    private fun window(rxFps: Double, p90Ms: Int? = null, maxMs: Int? = null, recoveries: Int = 0) =
        VideoWindowStats(rxFps, rxFps, null, p90Ms, maxMs, recoveries)

    @Test fun emptySnapshotIsNull() {
        assertNull(MeasuredDecodeStats.snapshot())
    }

    @Test fun medianOverOddAndEvenWindowCounts() {
        MeasuredDecodeStats.record(window(10.0))
        MeasuredDecodeStats.record(window(30.0))
        MeasuredDecodeStats.record(window(20.0))
        assertEquals(20.0, MeasuredDecodeStats.snapshot()!!.rxFpsMedian, 0.001)
        MeasuredDecodeStats.record(window(40.0))
        assertEquals(25.0, MeasuredDecodeStats.snapshot()!!.rxFpsMedian, 0.001)
    }

    @Test fun ringKeepsOnlyTheMostRecentWindows() {
        repeat(120) { MeasuredDecodeStats.record(window(10.0)) }
        repeat(120) { MeasuredDecodeStats.record(window(50.0)) }
        val snapshot = MeasuredDecodeStats.snapshot()!!
        assertEquals(240, snapshot.windowCount)
        assertEquals(50.0, snapshot.rxFpsMedian, 0.001)
    }

    @Test fun p90MedianIgnoresWindowsWithoutDecodeSamples() {
        MeasuredDecodeStats.record(window(30.0))
        MeasuredDecodeStats.record(window(30.0, p90Ms = 10))
        MeasuredDecodeStats.record(window(30.0, p90Ms = 30))
        assertEquals(20, MeasuredDecodeStats.snapshot()!!.decodeP90MedianMs)
    }

    @Test fun p90MedianIsNullWhenNothingWasDecoded() {
        MeasuredDecodeStats.record(window(30.0))
        assertNull(MeasuredDecodeStats.snapshot()!!.decodeP90MedianMs)
        assertNull(MeasuredDecodeStats.snapshot()!!.decodeMaxMs)
    }

    @Test fun decodeMaxAndRecoveriesAccumulate() {
        MeasuredDecodeStats.record(window(30.0, p90Ms = 10, maxMs = 100, recoveries = 1))
        MeasuredDecodeStats.record(window(30.0, p90Ms = 20, maxMs = 60, recoveries = 2))
        val snapshot = MeasuredDecodeStats.snapshot()!!
        assertEquals(100, snapshot.decodeMaxMs)
        assertEquals(3, snapshot.recoveriesTotal)
    }

    @Test fun resetClearsEverything() {
        MeasuredDecodeStats.record(window(30.0, p90Ms = 10, maxMs = 100, recoveries = 1))
        MeasuredDecodeStats.reset()
        assertNull(MeasuredDecodeStats.snapshot())
    }
}
