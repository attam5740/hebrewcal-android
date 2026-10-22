package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CalendarMonthTest {
    private val today = LocalDate.of(2026, 9, 17)

    @Test fun tishrei5787StartsOnSaturdayAndPadsToSunday() {
        val g = CalendarMonths.hebrewMonth(5787, JewishDate.TISHREI, inIsrael = false, hebrewLabels = false, today = today)
        assertEquals(0, g.cells.size % 7)
        assertEquals(6, g.cells.indexOfFirst { it.inMonth })          // 1 Tishrei 5787 = Sat 2026-09-12
        assertEquals(LocalDate.of(2026, 9, 12), g.cells[6].date)
        assertEquals(30, g.cells.count { it.inMonth })
        assertTrue(g.cells[6].holidayIndex == JewishCalendar.ROSH_HASHANA)
        assertTrue(g.title.contains("Tishrei") && g.title.contains("5787"))
    }

    @Test fun todayIsMarkedOnceInItsOwnMonth() {
        val g = CalendarMonths.hebrewMonth(5787, JewishDate.TISHREI, false, true, today)
        assertEquals(1, g.cells.count { it.isToday })
        val t = g.cells.first { it.isToday }
        assertEquals(today, t.date); assertTrue(t.inMonth); assertEquals(6, t.hebrewDay)
    }

    @Test fun todayIsNotMarkedInADistantMonth() {
        val g = CalendarMonths.hebrewMonth(5786, JewishDate.AV, false, true, today)
        assertEquals(0, g.cells.count { it.isToday })
    }

    @Test fun gregorianMonthCarriesHebrewDays() {
        val g = CalendarMonths.gregorianMonth(2026, 9, false, false, today)
        val sep12 = g.cells.first { it.date == LocalDate.of(2026, 9, 12) }
        assertEquals(1, sep12.hebrewDay); assertEquals(JewishDate.TISHREI, sep12.hebrewMonth); assertTrue(sep12.isShabbat)
    }

    @Test fun monthStepping() {
        assertEquals(5787 to JewishDate.TISHREI, CalendarMonths.nextHebrewMonth(5786, JewishDate.ELUL))
        assertEquals(5786 to JewishDate.ELUL, CalendarMonths.prevHebrewMonth(5787, JewishDate.TISHREI))
        assertEquals(5787 to JewishDate.ADAR, CalendarMonths.nextHebrewMonth(5787, JewishDate.SHEVAT)) // 5787 is not leap
    }

    @Test fun leapYearAdarStepping() {
        // 5790 is a leap year (19-year cycle: years 3,6,8,11,14,17,19 → 5790 % 19 == 14).
        assertEquals(5790 to JewishDate.ADAR_II, CalendarMonths.nextHebrewMonth(5790, JewishDate.ADAR))
        assertEquals(5790 to JewishDate.NISSAN, CalendarMonths.nextHebrewMonth(5790, JewishDate.ADAR_II))
        assertEquals(5790 to JewishDate.ADAR, CalendarMonths.prevHebrewMonth(5790, JewishDate.ADAR_II))
        assertEquals(5790 to JewishDate.ADAR_II, CalendarMonths.prevHebrewMonth(5790, JewishDate.NISSAN))
    }

    @Test fun anchorsLandInTheRightCalendarMonth() {
        // 1 Tishrei 5787 is September 12, 2026.
        assertEquals(2026 to 9, CalendarMonths.gregorianAnchor(5787, 7))
        assertEquals(5787 to 7, CalendarMonths.hebrewAnchor(2026, 10))
        // 5787 is a leap year; Adar II begins in March 2027.
        assertEquals(2027 to 3, CalendarMonths.gregorianAnchor(5787, 13))
        // A month's anchor must contain its own first day: mapping back gives the same
        // Hebrew month or the one before (when the Gregorian month starts mid-month).
        for (m in listOf(7, 10, 12, 13, 1, 6)) {
            val (gy, gm) = CalendarMonths.gregorianAnchor(5787, m)
            val back = CalendarMonths.hebrewAnchor(gy, gm)
            assertTrue(
                "month $m anchored to $gy-$gm which maps back to $back",
                back == 5787 to m || CalendarMonths.nextHebrewMonth(back.first, back.second) == 5787 to m
            )
        }
    }
}
