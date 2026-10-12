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
    val yomTovName: String?
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

        // Candle lighting: applicable on Friday or Erev Yom Tov
        val candleLighting: Date? = if (isFriday || isErevYomTov) {
            cal.candleLightingOffset = candleLightingOffsetMinutes.toDouble()
            cal.candleLighting
        } else {
            null
        }

        // Havdalah: applicable on Saturday evening or Motzei Yom Tov
        val havdalah: Date? = if (isShabbat || (isYomTov && !jcal.isTomorrowShabbosOrYomTov)) {
            when (havdalahOpinion) {
                HavdalahOpinion.STANDARD_8_5 -> cal.tzaisGeonim8Point5Degrees ?: cal.tzais
                HavdalahOpinion.MINUTES_42   -> cal.sunset?.let { Date(it.time + 42L * 60 * 1000) }
                HavdalahOpinion.MINUTES_50   -> cal.tzais50 ?: cal.sunset?.let { Date(it.time + 50L * 60 * 1000) }
                HavdalahOpinion.MINUTES_72_RT -> cal.tzais72 ?: cal.sunset?.let { Date(it.time + 72L * 60 * 1000) }
            }
        } else {
            null
        }

        // Rabbeinu Tam havdalah (always calculated for reference if Shabbat/Yom Tov)
        val havdalahRt: Date? = if (isShabbat || (isYomTov && !jcal.isTomorrowShabbosOrYomTov)) {
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
            yomTovName      = null
        )
    }

    fun formatCandleLighting(isHebrew: Boolean, timeStr: String): String {
        return if (isHebrew) "הדלקת נרות: $timeStr" else "Candle Lighting: $timeStr"
    }

    /**
     * Formats Havdalah time string for display (e.g. "Havdalah: 6:52 PM").
     */
    fun formatHavdalah(isHebrew: Boolean, timeStr: String, rtStr: String? = null): String {
        val base = if (isHebrew) "הבדלה: $timeStr" else "Havdalah: $timeStr"
        return if (rtStr != null) {
            if (isHebrew) "$base (ר\"ת: $rtStr)" else "$base (RT: $rtStr)"
        } else {
            base
        }
    }
}
