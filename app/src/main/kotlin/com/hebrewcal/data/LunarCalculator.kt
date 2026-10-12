package com.hebrewcal.data

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.kosherjava.zmanim.util.GeoLocation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class MoladInfo(
    val monthName: String,
    val dayOfWeekName: String,
    val hours: Int,
    val minutes: Int,
    val chalakim: Int,
    val formattedString: String
)

data class BirkatHaLevanaInfo(
    val isOpenNow: Boolean,
    val earliestStart: Date?,
    val latestEnd: Date?,
    val formattedWindow: String,
    val statusText: String
)

data class MoonPhaseInfo(
    val phaseFraction: Float,       // 0.0 = new moon, 0.5 = full moon, 1.0 = cycle complete
    val illuminationPercent: Int,   // 0% to 100%
    val phaseName: String
)

object LunarCalculator {

    /**
     * Calculates the exact traditional Molad for the given Hebrew month and year.
     */
    fun calculateMolad(
        jewishMonth: Int,
        jewishYear: Int,
        isHebrew: Boolean = false
    ): MoladInfo {
        val jd = JewishDate(jewishYear, jewishMonth, 1)
        val molad = jd.molad

        val hours = molad.moladHours
        val minutes = molad.moladMinutes
        val chalakim = molad.moladChalakim

        // Calculate civil day of week for the molad
        // KosherJava chalakimSinceMoladTohu: 1 day = 24 hours = 25920 chalakim
        val totalChalakim = molad.chalakimSinceMoladTohu
        val dayOfWeekIndex = ((totalChalakim / 25920L) % 7L).toInt() + 1 // 1 = Sunday, 7 = Shabbat

        val formatter = HebrewDateFormatter().apply { isHebrewFormat = isHebrew }
        val monthName = formatter.formatMonth(jd)

        val dayNamesEn = arrayOf("", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Shabbat")
        val dayNamesHe = arrayOf("", "ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")
        val dayName = if (isHebrew) dayNamesHe.getOrElse(dayOfWeekIndex) { "" } else dayNamesEn.getOrElse(dayOfWeekIndex) { "" }

        val formattedString = if (isHebrew) {
            "מולד $monthName: יום $dayName, בשעה $hours:$minutes ו-$chalakim חלקים"
        } else {
            "Molad $monthName: $dayName, $hours:${minutes.toString().padStart(2, '0')} and $chalakim chalakim"
        }

        return MoladInfo(
            monthName = monthName,
            dayOfWeekName = dayName,
            hours = hours,
            minutes = minutes,
            chalakim = chalakim,
            formattedString = formattedString
        )
    }

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
            return BirkatHaLevanaInfo(false, null, null, "", "")
        }

        val jcal = JewishCalendar(date)
        val earliest = jcal.tchilasZmanKidushLevana7Days ?: jcal.tchilasZmanKidushLevana3Days
        val latest = jcal.sofZmanKidushLevana15Days ?: jcal.sofZmanKidushLevanaBetweenMoldos

        val isOpen = if (earliest != null && latest != null) {
            !date.before(earliest) && !date.after(latest)
        } else false

        val df = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        val formattedWindow = if (earliest != null && latest != null) {
            "${df.format(earliest)} – ${df.format(latest)}"
        } else ""

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
            formattedWindow = formattedWindow,
            statusText = statusText
        )
    }

    /**
     * Computes the moon illumination and phase for the given Hebrew day of month (1 to 29/30).
     */
    fun getMoonPhase(dayOfMonth: Int, daysInMonth: Int, isHebrew: Boolean = false): MoonPhaseInfo {
        val fraction = (dayOfMonth.toFloat() - 1f) / daysInMonth.toFloat()
        // Day 1 = ~0%, Day 15 = 100%, Day 30 = ~0%
        val illumination = if (fraction <= 0.5f) {
            (fraction * 2f * 100f).toInt()
        } else {
            ((1f - fraction) * 2f * 100f).toInt()
        }.coerceIn(0, 100)

        val name = when {
            dayOfMonth in 1..2 -> if (isHebrew) "מולד (לבנה חדשה)" else "New Moon"
            dayOfMonth in 3..6 -> if (isHebrew) "חרמש ראשון" else "Waxing Crescent"
            dayOfMonth in 7..8 -> if (isHebrew) "רבע ראשון" else "First Quarter"
            dayOfMonth in 9..13 -> if (isHebrew) "ירח מתמלא" else "Waxing Gibbous"
            dayOfMonth in 14..16 -> if (isHebrew) "ירח מלא" else "Full Moon"
            dayOfMonth in 17..21 -> if (isHebrew) "ירח מתמעט" else "Waning Gibbous"
            dayOfMonth in 22..23 -> if (isHebrew) "רבע אחרון" else "Third Quarter"
            else -> if (isHebrew) "חרמש אחרון" else "Waning Crescent"
        }

        return MoonPhaseInfo(
            phaseFraction = fraction,
            illuminationPercent = illumination,
            phaseName = name
        )
    }
}
