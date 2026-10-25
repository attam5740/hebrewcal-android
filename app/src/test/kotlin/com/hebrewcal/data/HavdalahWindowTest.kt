package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class HavdalahWindowTest {
    private val tz = TimeZone.getTimeZone("America/New_York")
    private val lat = 40.7128
    private val lng = -74.0060

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Date =
        Calendar.getInstance(tz).apply { clear(); set(y, m, d, h, min) }.time

    private fun status(now: Date) =
        HavdalahWindow.status(now, lat, lng, tz, israel = false, opinion = HavdalahOpinion.STANDARD_8_5)

    @Test
    fun opensAtHavdalahAndClosesThreeHoursLater() {
        // Motzei Shabbat Oct 24, 2026 (Lech Lecha); Havdalah 8.5 degrees is about 18:45 in NYC.
        val havdalah = ShabbatTimes.calculate(at(2026, Calendar.OCTOBER, 24, 12), lat, lng, timeZone = tz).havdalah!!

        val before = status(Date(havdalah.time - 60_000))
        assertFalse(before.active)
        assertEquals("next change is the window opening", havdalah, before.nextChange)

        val during = status(Date(havdalah.time + 60 * 60_000))
        assertTrue(during.active)
        assertEquals(Date(havdalah.time + HavdalahWindow.LENGTH_MS), during.nextChange)

        assertFalse(status(Date(havdalah.time + HavdalahWindow.LENGTH_MS)).active)
    }

    @Test
    fun closedOnOtherDays() {
        assertFalse(status(at(2026, Calendar.OCTOBER, 23, 21)).active)   // Friday night
        assertFalse(status(at(2026, Calendar.OCTOBER, 26, 20)).active)   // Monday night
        // Looks one day ahead: from Friday it already knows when Saturday's window opens
        // (the nightly refresh carries it there from earlier in the week).
        assertNotNull("Friday knows Saturday's opening", status(at(2026, Calendar.OCTOBER, 23, 21)).nextChange)
    }

    @Test
    fun skipsShabbatFollowedByYomTov() {
        // Find a Saturday in the next three years where Yom Tov begins that night.
        val day = Calendar.getInstance(tz).apply { time = at(2026, Calendar.OCTOBER, 24, 12) }
        var found: Date? = null
        repeat(160) {
            val jc = JewishCalendar(day.time).apply { inIsrael = false }
            if (jc.isTomorrowShabbosOrYomTov) { found = day.time; return@repeat }
            day.add(Calendar.DATE, 7)
        }
        val saturday = found!!
        val evening = Calendar.getInstance(tz).apply { time = saturday; set(Calendar.HOUR_OF_DAY, 21) }.time
        assertFalse("Havdalah is said in Kiddush that night", status(evening).active)
    }
}
