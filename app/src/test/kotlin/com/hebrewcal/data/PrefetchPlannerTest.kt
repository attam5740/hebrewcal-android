package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PrefetchPlannerTest {

    @Test fun tehillimRefsRollAcrossA29DayMonthWithoutElul() {
        // 27 Elul 5786 — Elul always has 29 days, so day 29 combines 140-150,
        // then the cycle restarts on 1 Tishrei.
        val refs = PrefetchPlanner.tehillimRefs(JewishDate(5786, JewishDate.ELUL, 27), days = 7, includeElul = false)
        assertEquals(
            listOf(
                "Psalms.120-134", "Psalms.135-139", "Psalms.140-150",
                "Psalms.1-9", "Psalms.10-17", "Psalms.18-22", "Psalms.23-28"
            ),
            refs
        )
    }

    @Test fun tehillimRefsAppendElulSupplementWhenEnabled() {
        val refs = PrefetchPlanner.tehillimRefs(JewishDate(5786, JewishDate.ELUL, 28), days = 3, includeElul = true)
        assertEquals(
            listOf(
                "Psalms.135-139", "Psalms.140-150", "Psalms.1-9",   // daily cycle
                "Psalms.82-84", "Psalms.85-87", "Psalms.88-90"      // Elul 28, 29, 1 Tishrei
            ),
            refs
        )
    }

    @Test fun tehillimRefsSkipElulSupplementOutsideTheSeason() {
        val refs = PrefetchPlanner.tehillimRefs(JewishDate(5786, JewishDate.KISLEV, 5), days = 2, includeElul = true)
        assertEquals(listOf(Psalms(5), Psalms(6)), refs)
    }

    @Test fun shabbatDatesFromAWeekdayStartWithTheComingShabbat() {
        val dates = PrefetchPlanner.shabbatDates(LocalDate.of(2026, 9, 8), weeks = 3) // a Tuesday
        assertEquals(
            listOf(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 26)),
            dates
        )
    }

    @Test fun shabbatDatesFromAShabbatIncludeThatDay() {
        val dates = PrefetchPlanner.shabbatDates(LocalDate.of(2026, 9, 12), weeks = 2)
        assertEquals(listOf(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 19)), dates)
    }

    private fun Psalms(day: Int) = TehillimSchedule.portionFor(day, 30).sefariaRef
}
