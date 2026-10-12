package com.hebrewcal.data

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class CelestialCalculatorTest {

    private val lat = 31.7780 // Jerusalem
    private val lng = 35.2354
    private val tz = TimeZone.getTimeZone("Asia/Jerusalem")

    @Test
    fun testDaytimeSolarArcProgress() {
        val cal = Calendar.getInstance(tz).apply {
            set(2026, Calendar.OCTOBER, 5, 12, 0, 0)
        }
        val targetDate = cal.time

        // Midday (around noon)
        val arcData = CelestialCalculator.calculate(
            latitude = lat,
            longitude = lng,
            timeZone = tz,
            targetDate = targetDate,
            currentRealTime = targetDate
        )

        assertNotNull("Arc data should be calculated", arcData)
        assertTrue("Noon should be daytime", arcData!!.isDaytime)
        assertTrue("Day progress at noon should be between 0.3 and 0.7", arcData.dayProgress in 0.3f..0.7f)
        assertTrue("Sha'ah duration should be roughly 45-75 minutes", arcData.shaahZmanitMinutes in 45..75)
        assertTrue("Sha'ah index should be between 0 and 12", arcData.currentShaahIndex in 0f..12f)
        assertTrue("Formatted shaah should contain hour number", arcData.formattedShaah.contains("Hour"))
    }

    @Test
    fun testNighttimeSolarArc() {
        val cal = Calendar.getInstance(tz).apply {
            set(2026, Calendar.OCTOBER, 5, 23, 30, 0) // 11:30 PM
        }
        val targetDate = cal.time

        val arcData = CelestialCalculator.calculate(
            latitude = lat,
            longitude = lng,
            timeZone = tz,
            targetDate = targetDate,
            currentRealTime = targetDate
        )

        assertNotNull(arcData)
        assertFalse("11:30 PM should be nighttime", arcData!!.isDaytime)
        assertEquals("Nighttime label expected", "Nighttime", arcData.formattedShaah)
    }
}
