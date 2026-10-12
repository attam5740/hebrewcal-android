package com.hebrewcal.data

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CelestialArcData(
    val sunrise: Date?,
    val sunset: Date?,
    val chatzot: Date?,
    val alos: Date?,
    val tzais: Date?,
    val shaahZmanitMs: Long,          // duration of 1 sha'ah in milliseconds
    val shaahZmanitMinutes: Int,      // duration in rounded minutes
    val currentShaahIndex: Float,     // e.g. 4.25 (0.0 to 12.0)
    val isDaytime: Boolean,
    val dayProgress: Float,           // 0.0 at sunrise, 0.5 at chatzot, 1.0 at sunset
    val arcProgress: Float,           // normalized 0.0 (alos) to 1.0 (tzais) or wrapped for night
    val formattedShaah: String        // e.g. "Hour 5 of 12 · 1 Sha'ah = 54m"
)

object CelestialCalculator {

    fun calculate(
        latitude: Double,
        longitude: Double,
        elevation: Double = 0.0,
        timeZone: TimeZone = TimeZone.getDefault(),
        targetDate: Date = Date(),
        currentRealTime: Date = Date(),
        isHebrew: Boolean = false
    ): CelestialArcData? {
        if (latitude == 0.0 && longitude == 0.0) return null

        val geo = GeoLocation("User Location", latitude, longitude, elevation, timeZone)
        val cal = ComplexZmanimCalendar(geo).apply { calendar.time = targetDate }

        val sunrise = cal.sunrise ?: return null
        val sunset = cal.sunset ?: return null
        val chatzot = cal.chatzos ?: Date((sunrise.time + sunset.time) / 2)
        val alos = cal.alosHashachar ?: Date(sunrise.time - 72L * 60 * 1000)
        val tzais = cal.tzais ?: Date(sunset.time + 42L * 60 * 1000)

        val totalDaylightMs = sunset.time - sunrise.time
        if (totalDaylightMs <= 0) return null

        val shaahMs = totalDaylightMs / 12L
        val shaahMinutes = (shaahMs / (60L * 1000L)).toInt()

        val nowMs = currentRealTime.time
        val isDaytime = nowMs in sunrise.time..sunset.time

        val dayProgress: Float = when {
            nowMs <= sunrise.time -> 0f
            nowMs >= sunset.time -> 1f
            else -> ((nowMs - sunrise.time).toDouble() / totalDaylightMs.toDouble()).toFloat()
        }

        val currentShaahIndex: Float = if (isDaytime) {
            ((nowMs - sunrise.time).toDouble() / shaahMs.toDouble()).toFloat().coerceIn(0f, 12f)
        } else {
            // Nighttime sha'ah relative to nightfall
            0f
        }

        val totalArcMs = tzais.time - alos.time
        val arcProgress: Float = if (totalArcMs > 0) {
            when {
                nowMs <= alos.time -> 0f
                nowMs >= tzais.time -> 1f
                else -> ((nowMs - alos.time).toDouble() / totalArcMs.toDouble()).toFloat()
            }
        } else {
            dayProgress
        }

        val hourNum = currentShaahIndex.toInt() + 1
        val formattedShaah = if (isDaytime) {
            if (isHebrew) {
                "שעה $hourNum מתוך 12 · שעה זמנית = $shaahMinutes דק׳"
            } else {
                "Hour $hourNum of 12 · 1 Sha'ah = ${shaahMinutes}m"
            }
        } else {
            if (isHebrew) "לילה" else "Nighttime"
        }

        return CelestialArcData(
            sunrise = sunrise,
            sunset = sunset,
            chatzot = chatzot,
            alos = alos,
            tzais = tzais,
            shaahZmanitMs = shaahMs,
            shaahZmanitMinutes = shaahMinutes,
            currentShaahIndex = currentShaahIndex,
            isDaytime = isDaytime,
            dayProgress = dayProgress,
            arcProgress = arcProgress,
            formattedShaah = formattedShaah
        )
    }
}
