package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * When the notification offers the Havdalah text: from Havdalah time on Motzei Shabbat for
 * [LENGTH_MS]. Only a Saturday night that is not followed by Yom Tov qualifies; then
 * Havdalah is said inside Kiddush with a different text.
 */
object HavdalahWindow {
    const val LENGTH_MS = 3 * 60 * 60 * 1000L

    data class Status(val active: Boolean, val nextChange: Date?)

    fun status(
        now: Date, latitude: Double, longitude: Double, timeZone: TimeZone,
        israel: Boolean, opinion: HavdalahOpinion
    ): Status {
        if (latitude == 0.0 && longitude == 0.0) return Status(false, null)
        var active = false
        var next: Date? = null
        // Yesterday covers a window that runs past midnight; tomorrow finds the next start.
        for (offset in -1..1) {
            val day = Calendar.getInstance(timeZone).apply { time = now; add(Calendar.DATE, offset) }.time
            val start = motzeiShabbatHavdalah(day, latitude, longitude, timeZone, israel, opinion) ?: continue
            val end = Date(start.time + LENGTH_MS)
            if (!now.before(start) && now.before(end)) active = true
            for (edge in listOf(start, end)) if (edge.after(now) && (next == null || edge.before(next))) next = edge
        }
        return Status(active, next)
    }

    private fun motzeiShabbatHavdalah(
        day: Date, latitude: Double, longitude: Double, timeZone: TimeZone,
        israel: Boolean, opinion: HavdalahOpinion
    ): Date? {
        val jcal = JewishCalendar(day).apply { inIsrael = israel }
        if (jcal.dayOfWeek != Calendar.SATURDAY || jcal.isTomorrowShabbosOrYomTov) return null
        return ShabbatTimes.calculate(
            date = day, latitude = latitude, longitude = longitude, timeZone = timeZone,
            israel = israel, havdalahOpinion = opinion
        ).havdalah
    }
}
