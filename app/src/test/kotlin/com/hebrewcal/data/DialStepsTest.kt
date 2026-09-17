package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DialStepsTest {
    @Test fun tehillimRangeTurnsFasterThanTheOldFixedRate() {
        assertEquals(100, TorahNavigation.dialStepsPerTurn(TorahNavigation.TEHILLIM_CHAPTERS))
    }

    @Test fun mediumRangesTrackTheirOwnSize() {
        assertEquals(50, TorahNavigation.dialStepsPerTurn(50))
    }

    @Test fun smallRangesKeepTheFortyStepFloor() {
        assertEquals(40, TorahNavigation.dialStepsPerTurn(27))
    }
}
