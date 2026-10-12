package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class LunarCalculatorTest {

    @Test
    fun testMoladCalculation() {
        // Year 5787, Month Tishrei (JewishDate.TISHREI = 7)
        val moladInfo = LunarCalculator.calculateMolad(
            jewishMonth = JewishDate.TISHREI,
            jewishYear = 5787,
            isHebrew = false
        )

        assertNotNull(moladInfo)
        assertTrue("Hours should be 0-23", moladInfo.hours in 0..23)
        assertTrue("Minutes should be 0-59", moladInfo.minutes in 0..59)
        assertTrue("Chalakim should be 0-1079", moladInfo.chalakim in 0..1079)
        assertTrue("Formatted string should contain Molad and chalakim", moladInfo.formattedString.contains("Molad") && moladInfo.formattedString.contains("chalakim"))
    }

    @Test
    fun testMoonPhaseProgression() {
        val newMoon = LunarCalculator.getMoonPhase(1, 30, false)
        assertEquals("New Moon", newMoon.phaseName)
        assertEquals(0, newMoon.illuminationPercent)

        val fullMoon = LunarCalculator.getMoonPhase(15, 30, false)
        assertEquals("Full Moon", fullMoon.phaseName)
        assertTrue("Full Moon should be near 90-100% illumination", fullMoon.illuminationPercent >= 90)

        val thirdQuarter = LunarCalculator.getMoonPhase(22, 30, false)
        assertEquals("Third Quarter", thirdQuarter.phaseName)
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
