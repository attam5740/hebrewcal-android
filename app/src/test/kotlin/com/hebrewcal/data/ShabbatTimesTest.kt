package com.hebrewcal.data

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ShabbatTimesTest {

    // NYC coordinates
    private val nycLat = 40.7128
    private val nycLng = -74.0060
    private val nycTz = TimeZone.getTimeZone("America/New_York")

    @Test
    fun testFridayCandleLightingCalculated() {
        // Construct a known Friday: Oct 9, 2026
        val cal = Calendar.getInstance(nycTz).apply {
            set(2026, Calendar.OCTOBER, 9, 12, 0, 0)
        }
        val result = ShabbatTimes.calculate(
            date = cal.time,
            latitude = nycLat,
            longitude = nycLng,
            timeZone = nycTz,
            israel = false,
            candleLightingOffsetMinutes = 18
        )

        assertTrue("Should be recognized as Erev Shabbat", result.isErevShabbat)
        assertFalse("Friday afternoon is not Shabbat", result.isShabbat)
        assertNotNull("Candle lighting should be calculated on Friday", result.candleLighting)
        assertNull("Havdalah should not be on Friday", result.havdalah)
    }

    @Test
    fun testCandleLightingOffsetChanges() {
        val cal = Calendar.getInstance(nycTz).apply {
            set(2026, Calendar.OCTOBER, 9, 12, 0, 0)
        }
        val result18 = ShabbatTimes.calculate(
            date = cal.time, latitude = nycLat, longitude = nycLng, timeZone = nycTz,
            candleLightingOffsetMinutes = 18
        )
        val result40 = ShabbatTimes.calculate(
            date = cal.time, latitude = nycLat, longitude = nycLng, timeZone = nycTz,
            candleLightingOffsetMinutes = 40
        )

        assertNotNull(result18.candleLighting)
        assertNotNull(result40.candleLighting)

        // 40m offset should be 22 minutes earlier than 18m offset
        val diffMs = result18.candleLighting!!.time - result40.candleLighting!!.time
        assertEquals("Offset diff should be 22 minutes", 22 * 60 * 1000L, diffMs)
    }

    @Test
    fun testSaturdayHavdalahCalculated() {
        // Construct a known Saturday: Oct 10, 2026
        val cal = Calendar.getInstance(nycTz).apply {
            set(2026, Calendar.OCTOBER, 10, 14, 0, 0)
        }
        val result = ShabbatTimes.calculate(
            date = cal.time,
            latitude = nycLat,
            longitude = nycLng,
            timeZone = nycTz,
            havdalahOpinion = HavdalahOpinion.MINUTES_50
        )

        assertTrue("Should be recognized as Shabbat", result.isShabbat)
        assertFalse("Saturday is not Erev Shabbat", result.isErevShabbat)
        assertNull("Candle lighting should be null on regular Saturday", result.candleLighting)
        assertNotNull("Havdalah should be calculated on Saturday", result.havdalah)
        assertNotNull("Rabbeinu Tam havdalah should be calculated on Saturday", result.havdalahRt)

        // RT (72m) should be after 50m havdalah
        assertTrue("RT havdalah should be after 50m havdalah", result.havdalahRt!!.after(result.havdalah))
    }

    @Test
    fun testWeekdayHasNoShabbatTimes() {
        // Tuesday: Oct 6, 2026
        val cal = Calendar.getInstance(nycTz).apply {
            set(2026, Calendar.OCTOBER, 6, 12, 0, 0)
        }
        val result = ShabbatTimes.calculate(
            date = cal.time,
            latitude = nycLat,
            longitude = nycLng,
            timeZone = nycTz
        )

        assertFalse(result.isErevShabbat)
        assertFalse(result.isShabbat)
        assertNull(result.candleLighting)
        assertNull(result.havdalah)
    }

    @Test
    fun candleLightingOnShabbatBeforeYomTovWaitsForNightfall() {
        // Sat Sep 12, 2026: Shabbat and Rosh Hashana day 1; day 2 follows.
        val day = Calendar.getInstance(nycTz).apply { clear(); set(2026, Calendar.SEPTEMBER, 12, 12, 0) }.time
        val result = ShabbatTimes.calculate(date = day, latitude = nycLat, longitude = nycLng, timeZone = nycTz)
        val cal = ComplexZmanimCalendar(GeoLocation("t", nycLat, nycLng, 0.0, nycTz)).apply { calendar.time = day }

        assertEquals(cal.tzaisGeonim8Point5Degrees, result.candleLighting)
        assertNull("No Havdalah when Yom Tov follows Shabbat", result.havdalah)
        assertFalse(result.fromExistingFlame)
    }

    @Test
    fun candleLightingOnYomTovFridayIsFromExistingFlame() {
        // Fri Apr 23, 2027: second day of Pesach outside Israel.
        val day = Calendar.getInstance(nycTz).apply { clear(); set(2027, Calendar.APRIL, 23, 12, 0) }.time
        assertTrue(JewishCalendar(day).apply { inIsrael = false }.isYomTovAssurBemelacha)
        val result = ShabbatTimes.calculate(date = day, latitude = nycLat, longitude = nycLng, timeZone = nycTz)
        val cal = ComplexZmanimCalendar(GeoLocation("t", nycLat, nycLng, 0.0, nycTz)).apply { calendar.time = day }

        assertEquals((cal.sunset.time - 18 * 60_000L).toDouble(), result.candleLighting!!.time.toDouble(), 1000.0)
        assertTrue(result.fromExistingFlame)
        assertEquals("🕯️←🕯️", ShabbatTimes.candles(true, result.fromExistingFlame))
        assertEquals("🕯️→🕯️", ShabbatTimes.candles(false, result.fromExistingFlame))
        assertEquals(ShabbatTimes.CANDLES, ShabbatTimes.candles(true, false))
    }

    @Test
    fun timesStayLeftToRightInsideHebrewText() {
        // "6:12 PM" must be one isolated LTR run, or RTL layout shows "PM 6:12".
        val line = ShabbatTimes.formatCandleLighting(true, "6:12 PM")
        org.junit.Assert.assertTrue(line.endsWith("\u20666:12 PM\u2069"))
        val hav = ShabbatTimes.formatHavdalah(true, "7:05 PM", "7:36 PM")
        org.junit.Assert.assertTrue(hav.contains("\u20667:05 PM\u2069") && hav.contains("\u20667:36 PM\u2069"))
    }
}
