package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TehillimScheduleTest {
    @Test fun day1IsChapters1to9() {
        val p = TehillimSchedule.portionFor(1, 30)
        assertEquals("Psalms.1-9", p.sefariaRef)
        assertEquals(1, p.firstChapter); assertEquals(9, p.lastChapter)
    }

    @Test fun day25IsChapter119FirstHalf() {
        val p = TehillimSchedule.portionFor(25, 30)
        assertEquals("Psalms.119.1-96", p.sefariaRef)
        assertEquals(119, p.firstChapter); assertEquals(119, p.lastChapter)
    }

    @Test fun day26IsChapter119SecondHalf() {
        assertEquals("Psalms.119.97-176", TehillimSchedule.portionFor(26, 30).sefariaRef)
    }

    @Test fun day29In30DayMonthStopsAt144() {
        val p = TehillimSchedule.portionFor(29, 30)
        assertEquals("Psalms.140-144", p.sefariaRef)
        assertEquals(140, p.firstChapter); assertEquals(144, p.lastChapter)
    }

    @Test fun day30In30DayMonthIs145to150() {
        assertEquals("Psalms.145-150", TehillimSchedule.portionFor(30, 30).sefariaRef)
    }

    @Test fun day29In29DayMonthCombinesTo150() {
        val p = TehillimSchedule.portionFor(29, 29)
        assertEquals("Psalms.140-150", p.sefariaRef)
        assertEquals(140, p.firstChapter); assertEquals(150, p.lastChapter)
    }
}
