package com.hebrewcal.data

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Verifies [ZmanimRepository.getFastTimes] against KosherJava's [ComplexZmanimCalendar]
 * computed independently for Jerusalem, covering the halachic rule: MAJOR fast begin is
 * the previous civil day's sunset, MINOR begin is alot hashachar, and both end at tzais.
 */
class FastTimesTest {

    private val jerusalemTz = TimeZone.getTimeZone("Asia/Jerusalem")
    private val lat = 31.778
    private val lng = 35.235

    private fun dateOf(iso: String) =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = jerusalemTz }.parse(iso)!!

    private fun calendarFor(date: java.util.Date) =
        ComplexZmanimCalendar(GeoLocation("Jerusalem", lat, lng, 0.0, jerusalemTz)).apply {
            calendar.time = date
        }

    @Test
    fun minorFastRunsFromAlotToTzeit() {
        // 10 Tevet 5787 = 2026-12-20.
        val date = dateOf("2026-12-20")
        val cal = calendarFor(date)

        val fastTimes = ZmanimRepository().getFastTimes(FastKind.MINOR, lat, lng, jerusalemTz, date)

        assertEquals(cal.alosHashachar, fastTimes?.begin)
        assertEquals(cal.tzais, fastTimes?.end)
    }

    @Test
    fun majorFastBeginsAtPreviousSunset() {
        // 9 Av 5786 = 2026-07-23.
        val date = dateOf("2026-07-23")
        val cal = calendarFor(date)
        val previousDay = Calendar.getInstance(jerusalemTz).apply {
            time = date
            add(Calendar.DATE, -1)
        }.time
        val previousDayCal = calendarFor(previousDay)

        val fastTimes = ZmanimRepository().getFastTimes(FastKind.MAJOR, lat, lng, jerusalemTz, date)

        assertEquals(previousDayCal.sunset, fastTimes?.begin)
        assertEquals(cal.tzais, fastTimes?.end)
        assertTrue(fastTimes!!.begin.before(fastTimes.end))

        val beginCivilDay = Calendar.getInstance(jerusalemTz).apply { time = fastTimes.begin }
        assertEquals(22, beginCivilDay.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.JULY, beginCivilDay.get(Calendar.MONTH))
        assertEquals(2026, beginCivilDay.get(Calendar.YEAR))
    }

    @Test
    fun noLocationGivesNull() {
        val date = dateOf("2026-07-23")

        val fastTimes = ZmanimRepository().getFastTimes(FastKind.MAJOR, 0.0, 0.0, jerusalemTz, date)

        assertNull(fastTimes)
    }
}
