package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import java.util.Calendar
import java.util.Date

data class HebrewDateInfo(
    val hebrewDateString: String,       // e.g. "כ״ב ניסן תשפ״ו" or "22 Nisan 5786"
    val gregorianDateString: String,    // e.g. "April 22, 2026"
    val holidayName: String?,           // e.g. "Pesach VII", null if regular day
    val parshaName: String?,            // e.g. "Parashat Tazria-Metzora", null on Yom Tov/Shabbat
    val isShabbat: Boolean,
    val hebrewDayNumber: Int,
    val hebrewMonthName: String,
    val hebrewYear: Int,
    val omerDay: Int = 0,               // 1-49 during the Omer, 0 otherwise
    val omerText: String? = null,       // formatted display string, e.g. "Day 3 of the Omer"
    val daysInMonth: Int = 30,
    val jewishMonth: Int = 0
)

data class HolidayInfo(
    val name: String,
    val priority: Int               // lower = higher priority
)

class HebrewCalendarRepository {

    fun getDateInfo(
        date: Date = Date(),
        language: CalendarLanguage,
        location: CalendarLocation,
        showParshaOnWeekdays: Boolean = false,
        advanceAfter: Date? = null
    ): HebrewDateInfo {
        // KosherJava's JewishCalendar rolls at civil midnight, but the halachic day
        // changes at tzet hakochavim. When [advanceAfter] is non-null (typically today's
        // tzet) and [date] is at or past it, advance the Hebrew calendar one day so the
        // displayed Hebrew date, holiday, parsha, and omer reflect the actual halachic
        // day between tzet and civil midnight. The Gregorian sub-line still uses the
        // raw [date], so it correctly shows the civil day until civil midnight.
        val jewishCalendar = JewishCalendar(date).apply {
            inIsrael = (location == CalendarLocation.ISRAEL)
            if (advanceAfter != null && !date.before(advanceAfter)) {
                forward(java.util.Calendar.DATE, 1)
            }
        }
        val formatter = HebrewDateFormatter().apply {
            isHebrewFormat = (language == CalendarLanguage.HEBREW)
            isUseGershGershayim = true
            isLongWeekFormat = false
        }

        val hebrewDateStr = when (language) {
            CalendarLanguage.HEBREW  -> formatter.format(jewishCalendar)
            CalendarLanguage.ENGLISH -> formatEnglishDate(jewishCalendar)
        }

        val gregorianStr = formatGregorianDate(date)
        val holidayInfo = getHolidayInfo(jewishCalendar, language)
        val parsha = getParshaName(jewishCalendar, formatter, language, showParshaOnWeekdays)
        val omerDay = jewishCalendar.dayOfOmer.coerceAtLeast(0)
        val omerText = if (omerDay > 0) formatOmer(omerDay, language) else null

        return HebrewDateInfo(
            hebrewDateString  = hebrewDateStr,
            gregorianDateString = gregorianStr,
            holidayName       = holidayInfo?.name,
            parshaName        = if (holidayInfo == null || holidayInfo.priority > 5) parsha else null,
            isShabbat         = jewishCalendar.dayOfWeek == 7,
            hebrewDayNumber   = jewishCalendar.jewishDayOfMonth,
            hebrewMonthName   = formatter.formatMonth(jewishCalendar),
            hebrewYear        = jewishCalendar.jewishYear,
            omerDay           = omerDay,
            omerText          = omerText,
            daysInMonth       = jewishCalendar.daysInJewishMonth,
            jewishMonth       = jewishCalendar.jewishMonth
        )
    }

    /**
     * Name of the parsha read on the upcoming Shabbat (or today if today is Shabbat),
     * independent of the weekday-display preference. Returns null if none can be found
     * (e.g. a run of festival Shabbatot), which the caller can treat as "no parsha".
     */
    fun upcomingParshaName(
        date: Date,
        location: CalendarLocation,
        language: CalendarLanguage
    ): String? {
        val cal = JewishCalendar(date).apply { inIsrael = (location == CalendarLocation.ISRAEL) }
        while (cal.dayOfWeek != Calendar.SATURDAY) cal.forward(Calendar.DATE, 1)
        var guard = 0
        while (cal.parshah == JewishCalendar.Parsha.NONE && guard < 8) {
            cal.forward(Calendar.DATE, 7); guard++
        }
        if (cal.parshah == JewishCalendar.Parsha.NONE) return null
        val formatter = HebrewDateFormatter().apply { isHebrewFormat = (language == CalendarLanguage.HEBREW) }
        return formatter.formatParsha(cal).takeIf { it.isNotBlank() }
    }

    private fun formatEnglishDate(cal: JewishCalendar): String {
        val formatter = HebrewDateFormatter().apply { isHebrewFormat = false }
        return "${cal.jewishDayOfMonth} ${formatter.formatMonth(cal)} ${cal.jewishYear}"
    }

    private fun formatGregorianDate(date: Date): String {
        val sdf = java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.ENGLISH)
        return sdf.format(date)
    }

    private fun formatOmer(day: Int, language: CalendarLanguage): String =
        if (language == CalendarLanguage.HEBREW) "יום $day בעומר"
        else "Day $day of the Omer"

    private fun getHolidayInfo(cal: JewishCalendar, language: CalendarLanguage): HolidayInfo? {
        val index = cal.yomTovIndex
        if (index == -1) {
            return if (cal.dayOfWeek == 7) HolidayInfo(
                if (language == CalendarLanguage.HEBREW) "שַׁבָּת" else "Shabbat", 6
            ) else null
        }

        val (name, priority) = when (index) {
            // Major Yom Tov - priority 1
            JewishCalendar.ROSH_HASHANA        -> Pair("Rosh Hashana", 1)
            JewishCalendar.YOM_KIPPUR          -> Pair("Yom Kippur", 1)
            JewishCalendar.SUCCOS          -> {
                if (cal.jewishDayOfMonth == 15) Pair("Sukkot I", 1) else Pair("Sukkot II", 1)
            }
            JewishCalendar.SHEMINI_ATZERES -> Pair("Shemini Atzeret", 1)
            JewishCalendar.SIMCHAS_TORAH   -> Pair("Simchat Torah", 1)
            JewishCalendar.PESACH          -> {
                when (cal.jewishDayOfMonth) {
                    15 -> Pair("Pesach I", 1)
                    16 -> Pair("Pesach II", 1)
                    21 -> Pair("Pesach VII", 1)
                    else -> Pair("Pesach VIII", 1)
                }
            }
            JewishCalendar.SHAVUOS         -> {
                if (cal.jewishDayOfMonth == 6) Pair("Shavuot I", 1) else Pair("Shavuot II", 1)
            }
            // Chol HaMoed - priority 2
            JewishCalendar.CHOL_HAMOED_SUCCOS  -> {
                // Sukkot: 15 Tishrei = day 1 (Yom Tov), Chol HaMoed = days 2-6 (16-20 Tishrei)
                val day = cal.jewishDayOfMonth - 15
                Pair("Chol HaMoed Sukkot Day $day", 2)
            }
            JewishCalendar.HOSHANA_RABBA       -> Pair("Hoshana Raba", 2)
            JewishCalendar.CHOL_HAMOED_PESACH  -> {
                // Pesach: 15 Nisan = day 1 (Yom Tov), Chol HaMoed = days 2-6 (16-20 Nisan)
                val day = cal.jewishDayOfMonth - 15
                Pair("Chol HaMoed Pesach Day $day", 2)
            }
            // Rosh Chodesh - priority 3
            JewishCalendar.ROSH_CHODESH        -> {
                val rcFormatter = HebrewDateFormatter().apply {
                    isHebrewFormat = (language == CalendarLanguage.HEBREW)
                }
                // When it's the 30th, we're in the first day of Rosh Chodesh (month not yet changed).
                // We want to display the month that is *beginning*, i.e. next month.
                val rcCal = JewishCalendar(cal.localDate).apply { inIsrael = cal.inIsrael }
                if (rcCal.jewishDayOfMonth == 30) {
                    // Move to next Jewish month
                    var nextMonth = rcCal.jewishMonth + 1
                    var nextYear  = rcCal.jewishYear
                    val isLeapYear = ((7L * nextYear) + 1) % 19 < 7
                    if (nextMonth > (if (isLeapYear) JewishDate.ADAR_II else JewishDate.ADAR)) {
                        nextMonth = JewishDate.NISSAN
                        nextYear++
                    }
                    rcCal.setJewishDate(nextYear, nextMonth, 1)
                }
                Pair("Rosh Chodesh ${rcFormatter.formatMonth(rcCal)}", 3)
            }
            // Fast days - priority 4
            JewishCalendar.FAST_OF_GEDALYAH    -> Pair("Tzom Gedalyah", 4)
            JewishCalendar.TENTH_OF_TEVES      -> Pair("Asara B'Tevet", 4)
            JewishCalendar.FAST_OF_ESTHER      -> Pair("Ta'anit Esther", 4)
            JewishCalendar.SEVENTEEN_OF_TAMMUZ -> Pair("Shiva Asar B'Tammuz", 4)
            JewishCalendar.TISHA_BEAV          -> Pair("Tisha B'Av", 4)
            // Minor holidays - priority 5
            JewishCalendar.CHANUKAH            -> {
                val day = cal.dayOfChanukah
                Pair("Chanukah Day $day", 5)
            }
            JewishCalendar.PURIM               -> Pair("Purim", 5)
            JewishCalendar.SHUSHAN_PURIM       -> Pair("Shushan Purim", 5)
            JewishCalendar.LAG_BAOMER          -> Pair("Lag B'Omer", 5)
            JewishCalendar.TU_BESHVAT          -> Pair("Tu B'Shvat", 5)
            JewishCalendar.TU_BEAV             -> Pair("Tu B'Av", 5)
            JewishCalendar.YOM_HAATZMAUT       -> Pair("Yom Ha'Atzmaut", 5)
            JewishCalendar.YOM_HAZIKARON       -> Pair("Yom HaZikaron", 5)
            JewishCalendar.YOM_YERUSHALAYIM    -> Pair("Yom Yerushalayim", 5)
            JewishCalendar.PESACH_SHENI        -> Pair("Pesach Sheni", 5)
            JewishCalendar.EREV_PESACH         -> Pair("Erev Pesach", 5)
            JewishCalendar.EREV_SUCCOS         -> Pair("Erev Sukkot", 5)
            JewishCalendar.EREV_ROSH_HASHANA   -> Pair("Erev Rosh Hashana", 5)
            JewishCalendar.EREV_YOM_KIPPUR     -> Pair("Erev Yom Kippur", 5)
            JewishCalendar.EREV_SHAVUOS        -> Pair("Erev Shavuot", 5)
            else -> Pair("Special Day", 7)
        }

        // Translate to Hebrew if needed
        val displayName = if (language == CalendarLanguage.HEBREW) {
            translateHolidayToHebrew(name) ?: name
        } else name

        return HolidayInfo(displayName, priority)
    }

    private fun getParshaName(
        cal: JewishCalendar,
        formatter: HebrewDateFormatter,
        language: CalendarLanguage,
        showOnWeekdays: Boolean = false
    ): String? {
        val targetCal = when {
            cal.dayOfWeek == 7 -> cal
            showOnWeekdays -> {
                // Advance to the next Shabbat (dayOfWeek 1=Sun … 6=Fri → 7=Sat)
                val daysAhead = 7 - cal.dayOfWeek
                JewishCalendar(cal.localDate.plusDays(daysAhead.toLong())).apply {
                    inIsrael = cal.inIsrael
                }
            }
            else -> return null
        }
        return try {
            val parsha = targetCal.parshah
            if (parsha == JewishCalendar.Parsha.NONE) null
            else {
                val parshaStr = formatter.formatParsha(targetCal)
                if (parshaStr.isBlank()) null
                else if (language == CalendarLanguage.HEBREW) parshaStr
                else "Parashat $parshaStr"
            }
        } catch (e: Exception) { null }
    }

    private fun translateHolidayToHebrew(name: String): String? = when {
        name.startsWith("Rosh Hashana")   -> "ראש השנה"
        name == "Yom Kippur"              -> "יום כיפור"
        name.startsWith("Sukkot")         -> "סוכות"
        name == "Shemini Atzeret"         -> "שמיני עצרת"
        name == "Simchat Torah"           -> "שמחת תורה"
        name.startsWith("Pesach")         -> "פסח"
        name.startsWith("Shavuot")        -> "שבועות"
        name.startsWith("Chol HaMoed Sukkot") -> "חול המועד סוכות"
        name.startsWith("Chol HaMoed Pesach") -> "חול המועד פסח"
        name == "Hoshana Raba"            -> "הושענא רבה"
        name.startsWith("Rosh Chodesh")   -> name.replace("Rosh Chodesh", "ראש חודש")
        name == "Chanukah Day 1"          -> "חנוכה א׳"
        name.startsWith("Chanukah")       -> "חנוכה"
        name == "Purim"                   -> "פורים"
        name == "Shushan Purim"           -> "פורים דשושן"
        name == "Lag B'Omer"              -> "ל\"ג בעומר"
        name == "Tu B'Shvat"             -> "ט\"ו בשבט"
        name == "Tu B'Av"                -> "ט\"ו באב"
        name == "Yom Ha'Atzmaut"         -> "יום העצמאות"
        name == "Yom HaZikaron"          -> "יום הזכרון"
        name == "Yom Yerushalayim"       -> "יום ירושלים"
        name == "Tisha B'Av"             -> "תשעה באב"
        name == "Asara B'Tevet"          -> "עשרה בטבת"
        name == "Tzom Gedalyah"          -> "צום גדליה"
        name == "Shiva Asar B'Tammuz"   -> "י\"ז בתמוז"
        name == "Ta'anit Esther"         -> "תענית אסתר"
        name == "Erev Pesach"            -> "ערב פסח"
        name == "Erev Sukkot"            -> "ערב סוכות"
        name == "Erev Rosh Hashana"      -> "ערב ראש השנה"
        name == "Erev Yom Kippur"        -> "ערב יום כיפור"
        name == "Erev Shavuot"           -> "ערב שבועות"
        name == "Pesach Sheni"           -> "פסח שני"
        else -> null
    }
}
