package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

data class BirkatHaLevanaInfo(
    val isOpenNow: Boolean,
    val earliestStart: Date?,
    val latestEnd: Date?,
    val statusText: String
)

data class MoonPhaseInfo(
    val phaseFraction: Float,       // 0.0 = new moon, 0.5 = full moon, 1.0 = cycle complete
    val illuminationPercent: Int,   // 0% to 100%
    val phaseName: String
)

object LunarCalculator {

    /**
     * Calculates the Birkat HaLevana (Kiddush Levana) window for the given date and location.
     */
    fun calculateBirkatHaLevana(
        date: Date,
        latitude: Double,
        longitude: Double,
        elevation: Double = 0.0,
        timeZone: TimeZone = TimeZone.getDefault(),
        isHebrew: Boolean = false
    ): BirkatHaLevanaInfo {
        if (latitude == 0.0 && longitude == 0.0) {
            return BirkatHaLevanaInfo(false, null, null, "")
        }

        val jcal = JewishCalendar(date)
        val earliest = jcal.tchilasZmanKidushLevana7Days ?: jcal.tchilasZmanKidushLevana3Days
        val latest = jcal.sofZmanKidushLevana15Days ?: jcal.sofZmanKidushLevanaBetweenMoldos

        val isOpen = if (earliest != null && latest != null) {
            !date.before(earliest) && !date.after(latest)
        } else false

        val statusText = when {
            isOpen -> if (isHebrew) "זמן ברכת הלבנה" else "Birkat HaLevana Open"
            earliest != null && date.before(earliest) -> {
                val diffDays = ((earliest.time - date.time) / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(1)
                if (isHebrew) "ברכת הלבנה בעוד $diffDays ימים" else "Birkat HaLevana in $diffDays days"
            }
            else -> if (isHebrew) "עבר זמן קידוש לבנה" else "Birkat HaLevana Passed"
        }

        return BirkatHaLevanaInfo(
            isOpenNow = isOpen,
            earliestStart = earliest,
            latestEnd = latest,
            statusText = statusText
        )
    }

    private const val SYNODIC_DAYS = 29.530588853
    /** A known new moon, 2000-01-06 18:14 UTC; lunar months are counted from it. */
    private const val NEW_MOON_EPOCH_MS = 947_182_440_000L
    private const val DAY_MS = 86_400_000.0

    /**
     * The moon's phase at [at], from its age in an average-length lunar month. The real moon
     * runs up to about 14 hours ahead of or behind the average, which shifts the percentage
     * by at most about 6 points: close enough for the phase picture and its name.
     */
    fun getMoonPhase(at: Date, isHebrew: Boolean = false): MoonPhaseInfo {
        val days = (at.time - NEW_MOON_EPOCH_MS) / DAY_MS
        val age = ((days % SYNODIC_DAYS) + SYNODIC_DAYS) % SYNODIC_DAYS
        val fraction = (age / SYNODIC_DAYS).toFloat()
        val illumination = ((1 - cos(2 * PI * fraction)) / 2 * 100).roundToInt()

        // New, quarter and full moons are named for a day either side of the exact moment.
        val day = (1 / SYNODIC_DAYS).toFloat()
        val name = when {
            fraction < day || fraction > 1 - day -> if (isHebrew) "מולד (לבנה חדשה)" else "New Moon"
            fraction < 0.25f - day -> if (isHebrew) "חרמש ראשון" else "Waxing Crescent"
            fraction < 0.25f + day -> if (isHebrew) "רבע ראשון" else "First Quarter"
            fraction < 0.5f - day -> if (isHebrew) "ירח מתמלא" else "Waxing Gibbous"
            fraction < 0.5f + day -> if (isHebrew) "ירח מלא" else "Full Moon"
            fraction < 0.75f - day -> if (isHebrew) "ירח מתמעט" else "Waning Gibbous"
            fraction < 0.75f + day -> if (isHebrew) "רבע אחרון" else "Third Quarter"
            else -> if (isHebrew) "חרמש אחרון" else "Waning Crescent"
        }

        return MoonPhaseInfo(
            phaseFraction = fraction,
            illuminationPercent = illumination,
            phaseName = name
        )
    }
}
