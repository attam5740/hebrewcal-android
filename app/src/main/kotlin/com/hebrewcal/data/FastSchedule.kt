package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar

enum class FastKind { MAJOR, MINOR }

/**
 * Major fasts (Yom Kippur, Tisha B'Av) run from sunset the evening before to
 * tzeit hakochavim; minor fasts run from alot hashachar to tzeit.
 */
object FastSchedule {
    fun kindFor(yomTovIndex: Int): FastKind? = when (yomTovIndex) {
        JewishCalendar.YOM_KIPPUR, JewishCalendar.TISHA_BEAV -> FastKind.MAJOR
        JewishCalendar.FAST_OF_GEDALYAH, JewishCalendar.TENTH_OF_TEVES,
        JewishCalendar.FAST_OF_ESTHER, JewishCalendar.SEVENTEEN_OF_TAMMUZ -> FastKind.MINOR
        else -> null
    }

    fun label(begin: String, end: String) = "($begin–$end)"
}
