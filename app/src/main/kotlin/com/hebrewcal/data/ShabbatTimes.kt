package com.hebrewcal.data

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

enum class HavdalahOpinion {
    STANDARD_8_5,   // 8.5 degrees (standard geonim/shulchan aruch tzais)
    MINUTES_42,     // 42 minutes after sunset
    MINUTES_50,     // 50 minutes after sunset
    MINUTES_72_RT   // 72 minutes (Rabbeinu Tam)
}

data class ShabbatTimesResult(
    val isErevShabbat: Boolean,
    val isShabbat: Boolean,
    val isErevYomTov: Boolean,
    val isYomTov: Boolean,
    val candleLighting: Date?,
    val havdalah: Date?,
    val havdalahRt: Date?,
    val parshaName: String?,
    val yomTovName: String?,
    /** Candles lit on Yom Tov for Shabbat, from an existing flame (Yom Tov on Friday). */
    val fromExistingFlame: Boolean = false
)

object ShabbatTimes {

    /**
     * Calculates candle lighting for Friday or Erev Yom Tov, and Havdalah for Motzei Shabbat
     * or Motzei Yom Tov at the given coordinates.
     */
    fun calculate(
        date: Date,
        latitude: Double,
        longitude: Double,
        elevation: Double = 0.0,
        timeZone: TimeZone = TimeZone.getDefault(),
        israel: Boolean = false,
        candleLightingOffsetMinutes: Int = 18,
        havdalahOpinion: HavdalahOpinion = HavdalahOpinion.STANDARD_8_5
    ): ShabbatTimesResult {
        if (latitude == 0.0 && longitude == 0.0) {
            return ShabbatTimesResult(
                isErevShabbat = false, isShabbat = false,
                isErevYomTov = false, isYomTov = false,
                candleLighting = null, havdalah = null, havdalahRt = null,
                parshaName = null, yomTovName = null
            )
        }

        val geo = GeoLocation("User Location", latitude, longitude, elevation, timeZone)
        val cal = ComplexZmanimCalendar(geo).apply { calendar.time = date }
        val jcal = JewishCalendar(date).apply { inIsrael = israel }

        val dayOfWeek = jcal.dayOfWeek
        val isFriday = (dayOfWeek == Calendar.FRIDAY)
        val isShabbat = (dayOfWeek == Calendar.SATURDAY)
        val isErevYomTov = jcal.isErevYomTov || (jcal.hasCandleLighting() && !isFriday)
        val isYomTov = jcal.isYomTovAssurBemelacha

        val nightfall: Date? = when (havdalahOpinion) {
            HavdalahOpinion.STANDARD_8_5 -> cal.tzaisGeonim8Point5Degrees ?: cal.tzais
            HavdalahOpinion.MINUTES_42   -> cal.sunset?.let { Date(it.time + 42L * 60 * 1000) }
            HavdalahOpinion.MINUTES_50   -> cal.tzais50 ?: cal.sunset?.let { Date(it.time + 50L * 60 * 1000) }
            HavdalahOpinion.MINUTES_72_RT -> cal.tzais72 ?: cal.sunset?.let { Date(it.time + 72L * 60 * 1000) }
        }
        val holyToday = isShabbat || isYomTov
        val endsTonight = holyToday && !jcal.isTomorrowShabbosOrYomTov

        // Candle lighting: applicable on Friday or Erev Yom Tov. When today is itself
        // Shabbat or Yom Tov (and not Friday), lighting waits until nightfall.
        val candleLighting: Date? = when {
            !isFriday && !isErevYomTov -> null
            holyToday && !isFriday -> nightfall
            else -> {
                cal.candleLightingOffset = candleLightingOffsetMinutes.toDouble()
                cal.candleLighting
            }
        }

        // Havdalah: Motzei Shabbat or Motzei Yom Tov; none when Shabbat or Yom Tov follows
        val havdalah: Date? = if (endsTonight) nightfall else null

        // Rabbeinu Tam havdalah (always calculated for reference if Shabbat/Yom Tov)
        val havdalahRt: Date? = if (endsTonight) {
            cal.tzais72 ?: cal.sunset?.let { Date(it.time + 72L * 60 * 1000) }
        } else {
            null
        }

        return ShabbatTimesResult(
            isErevShabbat   = isFriday,
            isShabbat       = isShabbat,
            isErevYomTov    = isErevYomTov,
            isYomTov        = isYomTov,
            candleLighting  = candleLighting,
            havdalah        = havdalah,
            havdalahRt      = havdalahRt,
            parshaName      = null,
            yomTovName      = null,
            fromExistingFlame = isFriday && isYomTov
        )
    }

    /** Two candles, as lit for Shabbat and Yom Tov. */
    const val CANDLES = "🕯️🕯️"

    /** [CANDLES], or with an arrow (in reading direction) when lit from an existing flame. */
    fun candles(hebrew: Boolean, fromExistingFlame: Boolean): String = when {
        !fromExistingFlame -> CANDLES
        hebrew -> "🕯️←🕯️"
        else -> "🕯️→🕯️"
    }

    /**
     * Wraps a clock time in a left-to-right isolate. Inside Hebrew (right-to-left) text the
     * bidi algorithm otherwise splits "6:12 PM" into two runs and shows "PM 6:12".
     */
    fun ltr(time: String): String = "\u2066$time\u2069"

    fun formatCandleLighting(isHebrew: Boolean, timeStr: String): String {
        return if (isHebrew) "הדלקת נרות: ${ltr(timeStr)}" else "Candle Lighting: ${ltr(timeStr)}"
    }

    /**
     * Formats Havdalah time string for display (e.g. "Havdalah: 6:52 PM").
     */
    fun formatHavdalah(isHebrew: Boolean, timeStr: String, rtStr: String? = null): String {
        val base = if (isHebrew) "הבדלה: ${ltr(timeStr)}" else "Havdalah: ${ltr(timeStr)}"
        return if (rtStr != null) {
            if (isHebrew) "$base (ר\"ת: ${ltr(rtStr)})" else "$base (RT: ${ltr(rtStr)})"
        } else {
            base
        }
    }
}
