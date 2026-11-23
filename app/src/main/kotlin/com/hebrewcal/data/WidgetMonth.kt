package com.hebrewcal.data

import java.time.LocalDate
import com.kosherjava.zmanim.hebrewcalendar.JewishDate

/** Which month the month widget shows: a Hebrew (year, month) or a Gregorian one. */
data class MonthAnchor(val hebrew: Boolean, val year: Int, val month: Int)

/** Pure month-navigation rules for the month widget, kept out of Glance so they can be tested. */
object WidgetMonth {
    fun current(hebrew: Boolean, today: LocalDate): MonthAnchor =
        if (hebrew) JewishDate().apply { setGregorianDate(today.year, today.monthValue - 1, today.dayOfMonth) }
            .let { MonthAnchor(true, it.jewishYear, it.jewishMonth) }
        else MonthAnchor(false, today.year, today.monthValue)

    fun step(a: MonthAnchor, delta: Int): MonthAnchor =
        if (a.hebrew) {
            var (y, m) = a.year to a.month
            repeat(kotlin.math.abs(delta)) {
                val n = if (delta > 0) CalendarMonths.nextHebrewMonth(y, m) else CalendarMonths.prevHebrewMonth(y, m)
                y = n.first; m = n.second
            }
            MonthAnchor(true, y, m)
        } else LocalDate.of(a.year, a.month, 1).plusMonths(delta.toLong()).let { MonthAnchor(false, it.year, it.monthValue) }

    /** The other calendar's month that holds day 1 of [a]'s month. */
    fun toggled(a: MonthAnchor): MonthAnchor =
        if (a.hebrew) CalendarMonths.gregorianAnchor(a.year, a.month).let { MonthAnchor(false, it.first, it.second) }
        else CalendarMonths.hebrewAnchor(a.year, a.month).let { MonthAnchor(true, it.first, it.second) }
}
