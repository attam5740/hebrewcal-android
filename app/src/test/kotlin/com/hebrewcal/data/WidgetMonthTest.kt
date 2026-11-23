package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WidgetMonthTest {
    @Test fun gregorianStepsAcrossYearEnd() {
        assertEquals(MonthAnchor(false, 2027, 1), WidgetMonth.step(MonthAnchor(false, 2026, 12), 1))
        assertEquals(MonthAnchor(false, 2025, 12), WidgetMonth.step(MonthAnchor(false, 2026, 1), -1))
    }

    @Test fun hebrewStepIsReversible() {
        val a = WidgetMonth.current(true, LocalDate.of(2026, 11, 22))
        assertEquals(a, WidgetMonth.step(WidgetMonth.step(a, 1), -1))
        assertEquals(a, WidgetMonth.step(WidgetMonth.step(a, -1), 1))
    }

    @Test fun hebrewStepCrossesRoshHashanah() {
        // Elul 5786 -> Tishrei 5787 (the year number changes at Tishrei, not at Nisan).
        val elul = WidgetMonth.current(true, LocalDate.of(2026, 9, 5))
        val next = WidgetMonth.step(elul, 1)
        assertEquals(elul.year + 1, next.year)
    }

    @Test fun currentHebrewMonthOfKnownDate() {
        // 22 Nov 2026 is 13 Kislev 5787; Kislev is month 9 (KosherJava counts from Nisan = 1).
        assertEquals(MonthAnchor(true, 5787, 9), WidgetMonth.current(true, LocalDate.of(2026, 11, 22)))
    }

    @Test fun toggleLandsOnTheMonthHoldingDayOne() {
        // Kislev 5787 begins 11 Nov 2026.
        assertEquals(MonthAnchor(false, 2026, 11), WidgetMonth.toggled(MonthAnchor(true, 5787, 9)))
    }
}
