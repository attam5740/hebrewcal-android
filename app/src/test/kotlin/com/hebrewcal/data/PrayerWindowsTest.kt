package com.hebrewcal.data

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class PrayerWindowsTest {
    private val tz = TimeZone.getTimeZone("America/New_York")
    private val lat = 40.7128
    private val lng = -74.0060
    private val all = PrayerButton.values().toSet()

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Date =
        Calendar.getInstance(tz).apply { clear(); set(y, m, d, h, min) }.time

    private fun status(now: Date, enabled: Set<PrayerButton> = all) =
        PrayerWindows.status(now, lat, lng, tz, false, 18, HavdalahOpinion.STANDARD_8_5, enabled)

    private fun activeAt(now: Date) = status(now).active.map { it.button }.toSet()

    private fun cal(day: Date) =
        ComplexZmanimCalendar(GeoLocation("t", lat, lng, 0.0, tz)).apply { calendar.time = day }

    @Test
    fun havdalahOpensAtHavdalahAndClosesThreeHoursLater() {
        // Motzei Shabbat Oct 24, 2026
        val havdalah = ShabbatTimes.calculate(at(2026, Calendar.OCTOBER, 24, 12), lat, lng, timeZone = tz).havdalah!!
        assertFalse(PrayerButton.HAVDALAH in activeAt(Date(havdalah.time - 60_000)))
        assertEquals(havdalah, status(Date(havdalah.time - 60_000), setOf(PrayerButton.HAVDALAH)).nextChange)
        assertTrue(PrayerButton.HAVDALAH in activeAt(Date(havdalah.time + 3_600_000)))
        assertFalse(PrayerButton.HAVDALAH in activeAt(Date(havdalah.time + PrayerWindows.HAVDALAH_LENGTH_MS)))
        assertFalse(PrayerButton.HAVDALAH in activeAt(at(2026, Calendar.OCTOBER, 26, 20)))   // Monday
    }

    @Test
    fun havdalahSkipsShabbatFollowedByYomTov() {
        val day = Calendar.getInstance(tz).apply { time = at(2026, Calendar.OCTOBER, 24, 12) }
        var saturday: Date? = null
        repeat(160) {
            if (saturday == null && JewishCalendar(day.time).apply { inIsrael = false }.isTomorrowShabbosOrYomTov) saturday = day.time
            day.add(Calendar.DATE, 7)
        }
        val evening = Calendar.getInstance(tz).apply { time = saturday!!; set(Calendar.HOUR_OF_DAY, 21) }.time
        assertFalse(PrayerButton.HAVDALAH in activeAt(evening))
    }

    @Test
    fun candleLightingIsOneHourEitherSide() {
        val friday = at(2026, Calendar.OCTOBER, 23, 12)
        val cl = ShabbatTimes.calculate(friday, lat, lng, timeZone = tz).candleLighting!!
        assertTrue(PrayerButton.CANDLE_LIGHTING in activeAt(Date(cl.time - 59 * 60_000)))
        assertTrue(PrayerButton.CANDLE_LIGHTING in activeAt(Date(cl.time + 59 * 60_000)))
        assertFalse(PrayerButton.CANDLE_LIGHTING in activeAt(Date(cl.time - 61 * 60_000)))
        assertFalse(PrayerButton.CANDLE_LIGHTING in activeAt(Date(cl.time + 61 * 60_000)))
        val w = status(Date(cl.time)).active.first { it.button == PrayerButton.CANDLE_LIGHTING }
        assertEquals(CandleKind.SHABBAT, w.candleKind)
        assertFalse(PrayerButton.CANDLE_LIGHTING in activeAt(at(2026, Calendar.OCTOBER, 21, 17)))  // Wednesday
    }

    @Test
    fun birchotHashacharRunsFromDawnToSofZmanShema() {
        val day = at(2026, Calendar.OCTOBER, 21, 12)
        val c = cal(day)
        assertTrue(PrayerButton.BIRCHOT_HASHACHAR in activeAt(Date(c.alosHashachar.time + 60_000)))
        assertTrue(PrayerButton.BIRCHOT_HASHACHAR in activeAt(Date(c.sofZmanShmaGRA.time - 60_000)))
        assertFalse(PrayerButton.BIRCHOT_HASHACHAR in activeAt(Date(c.alosHashachar.time - 60_000)))
        assertFalse(PrayerButton.BIRCHOT_HASHACHAR in activeAt(Date(c.sofZmanShmaGRA.time + 60_000)))
    }

    @Test
    fun shemaAlHamitaRunsFromNightfallPastMidnightToDawn() {
        val c = cal(at(2026, Calendar.OCTOBER, 21, 12))
        val nextDawn = cal(at(2026, Calendar.OCTOBER, 22, 12)).alosHashachar
        assertFalse(PrayerButton.SHEMA_AL_HAMITA in activeAt(Date(c.tzais.time - 60_000)))
        assertTrue(PrayerButton.SHEMA_AL_HAMITA in activeAt(Date(c.tzais.time + 60_000)))
        assertTrue("after midnight", PrayerButton.SHEMA_AL_HAMITA in activeAt(at(2026, Calendar.OCTOBER, 22, 2)))
        assertFalse(PrayerButton.SHEMA_AL_HAMITA in activeAt(Date(nextDawn.time + 60_000)))
    }

    @Test
    fun disabledButtonsNeverShow() {
        val night = at(2026, Calendar.OCTOBER, 21, 23)
        assertTrue(PrayerButton.SHEMA_AL_HAMITA in activeAt(night))
        assertTrue(status(night, all - PrayerButton.SHEMA_AL_HAMITA).active.isEmpty())
        assertTrue(status(night, emptySet()).active.isEmpty())
        assertNotNull(status(night).nextChange)
    }

    @Test
    fun everyButtonHasRefsFromItsOwnCustom() {
        for (n in Nusach.values()) for (b in PrayerButton.values()) for (k in CandleKind.values()) {
            val refs = Tefillot.refsFor(b, n, k)
            assertTrue("$n $b", refs.isNotEmpty() && refs.none { it.contains(Tefillot.REF_SEPARATOR) })
        }
        assertTrue(Tefillot.refsFor(PrayerButton.HAVDALAH, Nusach.SEFARD, null).all { it.startsWith("Siddur Sefard") })
        assertTrue(Tefillot.refsFor(PrayerButton.SHEMA_AL_HAMITA, Nusach.EDOT_HAMIZRACH, null).all { it.startsWith("Siddur Edot HaMizrach") })
    }
}
