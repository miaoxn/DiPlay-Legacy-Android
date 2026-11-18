package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.ChainKind
import com.shilapi.xcertplay.airplay.ChainStageEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainStageTrackerTest {

    private fun event(kind: ChainKind, step: Int, at: Long = 1000L + step): ChainStageEvent =
        ChainStageEvent(kind, step, at, "CHAIN [$step] test")

    @Test fun arrivalsMarkEarlierStepsPassedAndLastCurrent() {
        val tracker = ChainStageTracker()
        tracker.onEvent(event(ChainKind.WIRELESS, 1, 1000L))
        tracker.onEvent(event(ChainKind.WIRELESS, 2, 2000L))
        tracker.onEvent(event(ChainKind.WIRELESS, 3, 3000L))
        val snapshot = tracker.snapshot()
        assertTrue(tracker.isActive)
        assertEquals(ChainKind.WIRELESS, snapshot.kind)
        assertEquals(12, snapshot.totalSteps)
        assertEquals(3, snapshot.currentStep)
        assertEquals(3000L, snapshot.currentStepArrivedAt)
        assertEquals(1000L, snapshot.attemptStartedAt)
        assertEquals(
            listOf(
                ChainStageTracker.Dot.PASSED, ChainStageTracker.Dot.PASSED,
                ChainStageTracker.Dot.CURRENT,
            ) + List(9) { ChainStageTracker.Dot.PENDING },
            snapshot.dots,
        )
    }

    @Test fun outOfOrderStepsDoNotRestartTheAttempt() {
        // Wired step 13 is emitted by the VPN attach, which the controller
        // awaits before logging step 11 — so 13 legitimately arrives first.
        val tracker = ChainStageTracker()
        tracker.onEvent(event(ChainKind.WIRED, 10, 1000L))
        tracker.onEvent(event(ChainKind.WIRED, 13, 2000L))
        tracker.onEvent(event(ChainKind.WIRED, 11, 3000L))
        val snapshot = tracker.snapshot()
        assertEquals(13, snapshot.currentStep)
        assertEquals(1000L, snapshot.attemptStartedAt)
        assertEquals(ChainStageTracker.Dot.PASSED, snapshot.dots[10])
        assertEquals(ChainStageTracker.Dot.CURRENT, snapshot.dots[12])
    }

    @Test fun stepOneAfterALaterStepRestartsTheAttempt() {
        val tracker = ChainStageTracker()
        tracker.onEvent(event(ChainKind.WIRELESS, 1, 1000L))
        tracker.onEvent(event(ChainKind.WIRELESS, 4, 2000L))
        tracker.onEvent(event(ChainKind.WIRELESS, 1, 9000L))
        val snapshot = tracker.snapshot()
        assertEquals(1, snapshot.currentStep)
        assertEquals(9000L, snapshot.attemptStartedAt)
        assertEquals(9000L, snapshot.currentStepArrivedAt)
        assertEquals(
            listOf(ChainStageTracker.Dot.CURRENT) + List(11) { ChainStageTracker.Dot.PENDING },
            snapshot.dots,
        )
    }

    @Test fun kindChangeRestartsTheAttempt() {
        val tracker = ChainStageTracker()
        tracker.onEvent(event(ChainKind.WIRED, 5, 1000L))
        tracker.onEvent(event(ChainKind.WIRELESS, 1, 2000L))
        val snapshot = tracker.snapshot()
        assertEquals(ChainKind.WIRELESS, snapshot.kind)
        assertEquals(12, snapshot.totalSteps)
        assertEquals(1, snapshot.currentStep)
    }

    @Test fun repeatOfTheSameStepKeepsTheFirstArrival() {
        val tracker = ChainStageTracker()
        tracker.onEvent(event(ChainKind.WIRED, 3, 1000L))
        tracker.onEvent(event(ChainKind.WIRED, 3, 5000L))
        assertEquals(1000L, tracker.snapshot().currentStepArrivedAt)
    }

    @Test fun failureFreezesTheCurrentStepUntilTheNextAttempt() {
        val tracker = ChainStageTracker()
        tracker.onEvent(event(ChainKind.WIRELESS, 5, 1000L))
        tracker.onEvent(event(ChainKind.WIRELESS, 6, 2000L))
        tracker.markFailed()
        tracker.onEvent(event(ChainKind.WIRELESS, 7, 3000L))
        val frozen = tracker.snapshot()
        assertTrue(tracker.hasFailed)
        assertEquals(6, frozen.failedStep)
        assertEquals(ChainStageTracker.Dot.FAILED, frozen.dots[5])
        assertEquals(ChainStageTracker.Dot.PASSED, frozen.dots[4])
        assertEquals(ChainStageTracker.Dot.PENDING, frozen.dots[6])
        tracker.onEvent(event(ChainKind.WIRELESS, 1, 9000L))
        assertFalse(tracker.hasFailed)
        assertEquals(1, tracker.snapshot().currentStep)
    }

    @Test fun markFailedWithoutAnAttemptIsANoOp() {
        val tracker = ChainStageTracker()
        tracker.markFailed()
        assertFalse(tracker.isActive)
        assertFalse(tracker.hasFailed)
    }

    @Test fun outOfRangeStepsAreIgnored() {
        val tracker = ChainStageTracker()
        tracker.onEvent(event(ChainKind.WIRED, 0))
        tracker.onEvent(event(ChainKind.WIRED, 17))
        tracker.onEvent(event(ChainKind.WIRELESS, 13))
        assertFalse(tracker.isActive)
    }

    @Test fun resetClearsEverything() {
        val tracker = ChainStageTracker()
        tracker.onEvent(event(ChainKind.WIRED, 8, 1000L))
        tracker.markFailed()
        tracker.reset()
        assertFalse(tracker.isActive)
        assertFalse(tracker.hasFailed)
        val snapshot = tracker.snapshot()
        assertEquals(null, snapshot.kind)
        assertEquals(0, snapshot.totalSteps)
        assertEquals(0, snapshot.currentStep)
        assertTrue(snapshot.dots.isEmpty())
    }
}
