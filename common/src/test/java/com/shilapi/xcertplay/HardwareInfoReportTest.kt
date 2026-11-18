package com.shilapi.xcertplay

import com.shilapi.xcertplay.media.MeasuredDecodeStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareInfoReportTest {
    @Test fun lowTierOnSmallMemoryOrFewCores() {
        assertEquals("low", HardwareInfoReport.tierFor(ramTotalMb = 512, cpuCores = 8, cpuMaxMhz = 2000))
        assertEquals("low", HardwareInfoReport.tierFor(ramTotalMb = 1024, cpuCores = 2, cpuMaxMhz = 2000))
        assertEquals("low", HardwareInfoReport.tierFor(ramTotalMb = 256, cpuCores = 1, cpuMaxMhz = null))
    }

    @Test fun ntda3ReferenceUnitIsMedium() {
        // The IKT54J reporter's own worked example: NTDA3, 1 GB RAM,
        // quad-core i.MX6 at 996 MHz -> medium / balanced.
        assertEquals("medium", HardwareInfoReport.tierFor(ramTotalMb = 1024, cpuCores = 4, cpuMaxMhz = 996))
        assertEquals("balanced", HardwareInfoReport.recommendFor("medium"))
    }

    @Test fun highTierNeedsMemoryCoresAndClock() {
        assertEquals("high", HardwareInfoReport.tierFor(ramTotalMb = 2048, cpuCores = 4, cpuMaxMhz = 1500))
        assertEquals("high", HardwareInfoReport.tierFor(ramTotalMb = 4096, cpuCores = 8, cpuMaxMhz = null))
        assertEquals("medium", HardwareInfoReport.tierFor(ramTotalMb = 2048, cpuCores = 4, cpuMaxMhz = 1200))
        assertEquals("medium", HardwareInfoReport.tierFor(ramTotalMb = 1024, cpuCores = 4, cpuMaxMhz = null))
    }

    @Test fun recommendationsFollowTheTier() {
        assertEquals("legacy-rescue", HardwareInfoReport.recommendFor("low"))
        assertEquals("quality", HardwareInfoReport.recommendFor("high"))
        assertEquals("balanced", HardwareInfoReport.recommendFor("anything-else"))
    }

    private fun measured(rxFps: Double, p90Ms: Int?) = MeasuredDecodeStats.Snapshot(
        windowCount = 10,
        rxFpsMedian = rxFps,
        decodeP90MedianMs = p90Ms,
        decodeMaxMs = p90Ms,
        recoveriesTotal = 0,
    )

    @Test fun measuredLowOnPoorFpsAttainment() {
        // Strong static spec, but the unit only sustains 44% of the 45 fps target.
        val decision = HardwareInfoReport.decideTier(2048, 4, 2000, measured(20.0, 20), targetFps = 45)
        assertEquals("low", decision.tier)
        assertEquals("static+measured", decision.basis)
        assertTrue(decision.reasons.any { it.startsWith("fps_attainment=") })
    }

    @Test fun measuredLowOnSlowDecode() {
        val decision = HardwareInfoReport.decideTier(1024, 4, 1200, measured(40.0, 72), targetFps = 45)
        assertEquals("low", decision.tier)
        assertEquals(listOf("decode_p90=72ms>60ms"), decision.reasons)
    }

    @Test fun staticLowRulesStillFireWithGoodMeasuredNumbers() {
        val decision = HardwareInfoReport.decideTier(512, 8, 2000, measured(44.0, 20), targetFps = 45)
        assertEquals("low", decision.tier)
        assertEquals(listOf("ram_total_mb=512<=512"), decision.reasons)
    }

    @Test fun highTierNeedsGoodMeasuredNumbersWhenPresent() {
        val good = HardwareInfoReport.decideTier(2048, 4, 2000, measured(44.0, 20), targetFps = 45)
        assertEquals("high", good.tier)
        val blocked = HardwareInfoReport.decideTier(2048, 4, 2000, measured(40.0, 40), targetFps = 45)
        assertEquals("medium", blocked.tier)
        assertTrue(blocked.reasons.single().startsWith("high-blocked-by-measured:"))
    }

    @Test fun staticOnlyDecisionMatchesTierFor() {
        val decision = HardwareInfoReport.decideTier(1024, 4, 996, measured = null, targetFps = 0)
        assertEquals("medium", decision.tier)
        assertEquals("static-specs-only", decision.basis)
        assertEquals("medium", HardwareInfoReport.tierFor(1024, 4, 996))
    }

    @Test fun presetValuesFollowTheTier() {
        assertEquals("30fps / 2.5Mbps / audio queue 20 / minimal logging", HardwareInfoReport.presetFor("low"))
        assertEquals("45fps / 3Mbps / audio queue 30", HardwareInfoReport.presetFor("medium"))
        assertEquals("45-60fps / 4Mbps / audio queue 40", HardwareInfoReport.presetFor("high"))
    }
}
