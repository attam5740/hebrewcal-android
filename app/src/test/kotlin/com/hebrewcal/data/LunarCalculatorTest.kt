package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class LunarCalculatorTest {

    @Test
    fun testMoonPhaseProgression() {
        // The molad is the traditional mean new moon, so it is an independent check on the epoch.
        for (month in listOf(JewishDate.TISHREI, JewishDate.CHESHVAN, JewishDate.NISSAN, JewishDate.AV)) {
            val molad = com.kosherjava.zmanim.hebrewcalendar.JewishCalendar(5787, month, 1).moladAsDate
            val atMolad = LunarCalculator.getMoonPhase(molad, false)
            val offset = minOf(atMolad.phaseFraction, 1 - atMolad.phaseFraction)
            assertTrue("month $month: molad should be within a day of new moon, was $offset", offset < 0.034f)
            assertEquals("New Moon", atMolad.phaseName)

            val dayMs = 86_400_000L
            val full = LunarCalculator.getMoonPhase(java.util.Date(molad.time + (14.77 * dayMs).toLong()), false)
            assertEquals("Full Moon", full.phaseName)
            assertTrue(full.illuminationPercent >= 98)

            val quarter = LunarCalculator.getMoonPhase(java.util.Date(molad.time + (7.38 * dayMs).toLong()), false)
            assertEquals("First Quarter", quarter.phaseName)
            assertTrue(quarter.illuminationPercent in 45..55)

            val third = LunarCalculator.getMoonPhase(java.util.Date(molad.time + (22.15 * dayMs).toLong()), false)
            assertEquals("Third Quarter", third.phaseName)
        }
    }

    @Test
    fun testBirkatHaLevanaWindow() {
        val tz = TimeZone.getTimeZone("America/New_York")
        val cal = Calendar.getInstance(tz).apply {
            set(2026, Calendar.OCTOBER, 18, 20, 0, 0)
        }
        val info = LunarCalculator.calculateBirkatHaLevana(
            date = cal.time,
            latitude = 40.7128,
            longitude = -74.0060,
            timeZone = tz,
            isHebrew = false
        )

        assertNotNull("Birkat HaLevana earliest should be non-null", info.earliestStart)
        assertNotNull("Birkat HaLevana latest should be non-null", info.latestEnd)
        assertTrue("Latest should be after earliest", info.latestEnd!!.after(info.earliestStart))
    }
}
