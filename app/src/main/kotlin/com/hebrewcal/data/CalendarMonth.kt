package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class DayCell(
    val date: LocalDate, val hebrewDay: Int, val hebrewMonth: Int, val hebrewYear: Int,
    val inMonth: Boolean, val isShabbat: Boolean, val holidayIndex: Int, val isRoshChodesh: Boolean, val isToday: Boolean
)
data class MonthGrid(val title: String, val subtitle: String, val cells: List<DayCell>)

object CalendarMonths {
    private fun cell(date: LocalDate, inMonth: Boolean, inIsrael: Boolean, today: LocalDate): DayCell {
        val jc = JewishCalendar(date).apply { this.inIsrael = inIsrael }
        return DayCell(date, jc.jewishDayOfMonth, jc.jewishMonth, jc.jewishYear, inMonth,
            date.dayOfWeek == DayOfWeek.SATURDAY, jc.yomTovIndex, jc.isRoshChodesh, date == today)
    }

    private fun grid(first: LocalDate, last: LocalDate, inIsrael: Boolean, today: LocalDate): List<DayCell> {
        val start = first.minusDays((first.dayOfWeek.value % 7).toLong())          // back to Sunday
        // Fixed 6 rows (42 cells): the largest month (31 days) starting on Saturday
        // needs exactly 6 front-pad + 31 + 5 back-pad = 42 cells, so this always covers
        // the whole month plus lead/trail days from adjacent months, with no overflow.
        return (0 until 42L).asSequence().map { start.plusDays(it) }
            .map { cell(it, !it.isBefore(first) && !it.isAfter(last), inIsrael, today) }.toList()
    }

    private val greg = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    private val gregYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    fun hebrewMonth(year: Int, month: Int, inIsrael: Boolean, hebrewLabels: Boolean, today: LocalDate): MonthGrid {
        val jd = JewishDate(year, month, 1)
        val first = jd.localDate
        val last = first.plusDays((jd.daysInJewishMonth - 1).toLong())
        val f = HebrewDateFormatter().apply { isHebrewFormat = hebrewLabels; isUseGershGershayim = true }
        val title = if (hebrewLabels) "${f.formatMonth(jd)} ${f.formatHebrewNumber(year)}" else "${f.formatMonth(jd)} $year"
        return MonthGrid(title, "${first.format(greg)} – ${last.format(gregYear)}", grid(first, last, inIsrael, today))
    }

    fun gregorianMonth(year: Int, month: Int, inIsrael: Boolean, hebrewLabels: Boolean, today: LocalDate): MonthGrid {
        val first = LocalDate.of(year, month, 1); val last = first.withDayOfMonth(first.lengthOfMonth())
        val f = HebrewDateFormatter().apply { isHebrewFormat = hebrewLabels; isUseGershGershayim = true }
        val a = JewishDate(first); val b = JewishDate(last)
        val sub = if (a.jewishMonth == b.jewishMonth) "${f.formatMonth(a)} ${if (hebrewLabels) f.formatHebrewNumber(a.jewishYear) else a.jewishYear}"
                  else "${f.formatMonth(a)} – ${f.formatMonth(b)} ${if (hebrewLabels) f.formatHebrewNumber(b.jewishYear) else b.jewishYear}"
        val title = "${first.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} $year"
        return MonthGrid(title, sub, grid(first, last, inIsrael, today))
    }

    fun nextHebrewMonth(year: Int, month: Int): Pair<Int, Int> {
        val jd = JewishDate(year, month, 1); jd.forward(java.util.Calendar.MONTH, 1); return jd.jewishYear to jd.jewishMonth
    }
    fun prevHebrewMonth(year: Int, month: Int): Pair<Int, Int> {
        val jd = JewishDate(year, month, 1); jd.back(); jd.setJewishDayOfMonth(1); return jd.jewishYear to jd.jewishMonth
    }
}
