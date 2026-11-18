package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.ChainKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the feedback-path design's hard constraint: every chain log step
 * must have an on-screen name, so a screenshot's red dot always lines up
 * with the log number. Add a chain step without adding its name here and
 * this test fails.
 */
class ChainStageNamesTest {

    @Test fun wiredTableCoversAllSixteenSteps() {
        assertEquals((1..16).toSet(), ChainStageNames.coveredSteps(ChainKind.WIRED))
    }

    @Test fun wirelessTableCoversAllTwelveSteps() {
        assertEquals((1..12).toSet(), ChainStageNames.coveredSteps(ChainKind.WIRELESS))
    }

    @Test fun everyCoveredStepResolvesToANonZeroResource() {
        for (kind in ChainKind.entries) {
            for (step in 1..kind.totalSteps) {
                val res = ChainStageNames.nameRes(kind, step)
                assertNotNull("$kind step $step has no name", res)
                assertTrue("$kind step $step resolved to 0", res != 0)
            }
        }
        assertEquals(null, ChainStageNames.nameRes(ChainKind.WIRED, 0))
        assertEquals(null, ChainStageNames.nameRes(ChainKind.WIRED, 17))
        assertEquals(null, ChainStageNames.nameRes(ChainKind.WIRELESS, 13))
    }

    @Test fun namesAreDistinctWithinEachChain() {
        for (kind in ChainKind.entries) {
            val ids = (1..kind.totalSteps).map { ChainStageNames.nameRes(kind, it) }
            assertEquals(kind.totalSteps, ids.toSet().size)
        }
    }
}
