package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HebrewVocalizationTest {
    // "בְּרֵאשִׁית" with nikkud, plus a te'am (etnahta ֑) after the bet.
    private val withAll = "בְּ֑רֵאשִׁית"

    @Test fun keepsEverythingWhenBothShown() {
        assertEquals(withAll, HebrewVocalization.strip(withAll, showNikkud = true, showTeamim = true))
    }

    @Test fun removesTeamimKeepsNikkud() {
        val out = HebrewVocalization.strip(withAll, showNikkud = true, showTeamim = false)
        assertEquals("בְּרֵאשִׁית", out)
        assertTrue(out.none { it in '֑'..'֯' })
    }

    @Test fun removesAllMarksWhenNikkudOff() {
        val out = HebrewVocalization.strip(withAll, showNikkud = false, showTeamim = false)
        assertEquals("בראשית", out)
    }

    @Test fun leavesLatinTextUntouched() {
        assertEquals("Happy is the one", HebrewVocalization.strip("Happy is the one", false, false))
    }

    private fun assertTrue(b: Boolean) = assertEquals(true, b)
}
