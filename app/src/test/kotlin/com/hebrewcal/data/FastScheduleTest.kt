package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FastScheduleTest {
    @Test fun majorFasts() {
        assertEquals(FastKind.MAJOR, FastSchedule.kindFor(JewishCalendar.YOM_KIPPUR))
        assertEquals(FastKind.MAJOR, FastSchedule.kindFor(JewishCalendar.TISHA_BEAV))
    }
    @Test fun minorFasts() {
        for (i in listOf(JewishCalendar.FAST_OF_GEDALYAH, JewishCalendar.TENTH_OF_TEVES,
                         JewishCalendar.FAST_OF_ESTHER, JewishCalendar.SEVENTEEN_OF_TAMMUZ))
            assertEquals(FastKind.MINOR, FastSchedule.kindFor(i))
    }
    @Test fun nonFastsHaveNoKind() {
        assertNull(FastSchedule.kindFor(JewishCalendar.PURIM))
        assertNull(FastSchedule.kindFor(-1))
    }
    @Test fun labelIsParenthesizedRange() {
        assertEquals("(5:41 AM–5:12 PM)", FastSchedule.label("5:41 AM", "5:12 PM"))
    }
    @Test fun fastTimesAppendToHolidayName() {
        // 10 Tevet 5787 = 2026-12-20 (Sunday).
        val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse("2026-12-20")!!
        val info = HebrewCalendarRepository().getDateInfo(
            d, CalendarLanguage.ENGLISH, CalendarLocation.DIASPORA,
            fastTimes = { kind, _ -> if (kind == FastKind.MINOR) "(5:41 AM–5:12 PM)" else null }
        )
        assertEquals("Asara B'Tevet (5:41 AM–5:12 PM)", info.holidayName)
    }
}
