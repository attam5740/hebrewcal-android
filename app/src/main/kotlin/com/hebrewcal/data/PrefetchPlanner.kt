package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Calendar

/**
 * Pure planning for the offline-download job: which Sefaria refs the reader
 * would need over the coming days/weeks.
 */
object PrefetchPlanner {

    /**
     * Daily Tehillim refs for [days] consecutive Hebrew days starting at [start],
     * followed by the Elul-season supplement refs for the same days when
     * [includeElul] is set. Deduplicated, in reading order.
     */
    fun tehillimRefs(start: JewishDate, days: Int, includeElul: Boolean): List<String> {
        val cal = start.clone() as JewishDate
        val daily = mutableListOf<String>()
        val elul = mutableListOf<String>()
        repeat(days) {
            daily += TehillimSchedule.portionFor(cal.jewishDayOfMonth, cal.daysInJewishMonth).sefariaRef
            if (includeElul) ElulTehillim.portionFor(cal.jewishMonth, cal.jewishDayOfMonth)?.let { elul += it.sefariaRef }
            cal.forward(Calendar.DATE, 1)
        }
        return (daily + elul).distinct()
    }

    /** Gregorian dates of the next [weeks] Shabbatot, counting [start] itself if it is one. */
    fun shabbatDates(start: LocalDate, weeks: Int): List<LocalDate> {
        val first = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        return (0 until weeks).map { first.plusWeeks(it.toLong()) }
    }
}
