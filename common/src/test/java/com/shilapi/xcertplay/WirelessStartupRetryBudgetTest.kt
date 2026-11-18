package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class WirelessStartupRetryBudgetTest {
    @Test fun stopsAfterFiveRetriesWithExistingBackoff() {
        val budget = WirelessStartupRetryBudget()
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L), List(5) { budget.nextDelayMillis() })
        assertNull(budget.nextDelayMillis())
        assertEquals(5, budget.retries)
        budget.manualRetry()
        assertEquals(2_000L, budget.nextDelayMillis())
    }

    @Test fun onlyContinuousVideoSessionResetsBudget() {
        val budget = WirelessStartupRetryBudget()
        budget.nextDelayMillis()
        val one = Any()
        assertFalse(budget.resetIfStable(one, 120_000))
        assertTrue(budget.firstFrame(one, 100))
        assertFalse(budget.firstFrame(one, 200))
        assertFalse(budget.resetIfStable(one, 60_099))
        assertFalse(budget.disconnected(60_099))
        assertFalse(budget.resetIfStable(one, 60_100))
        val two = Any()
        assertTrue(budget.firstFrame(two, 70_000))
        assertFalse(budget.resetIfStable(one, 130_000))
        assertTrue(budget.resetIfStable(two, 130_000))
        assertEquals(0, budget.retries)
    }

    @Test fun stableSessionEndingResetsBudgetAtDisconnect() {
        // IKTCRS: the host's one-shot +60s check can miss its window; the
        // disconnect itself must settle a session that had been stable.
        val budget = WirelessStartupRetryBudget()
        budget.nextDelayMillis()
        budget.nextDelayMillis()
        assertEquals(2, budget.retries)
        val short = Any()
        assertTrue(budget.firstFrame(short, 1_000))
        // Ends before the stability window: nothing is earned back.
        assertFalse(budget.disconnected(30_000))
        assertEquals(2, budget.retries)
        val stable = Any()
        assertTrue(budget.firstFrame(stable, 100_000))
        // Ends after 60s of frames: budget restored without the timer.
        assertTrue(budget.disconnected(160_000))
        assertEquals(0, budget.retries)
        assertEquals(2_000L, budget.nextDelayMillis())
    }
}
