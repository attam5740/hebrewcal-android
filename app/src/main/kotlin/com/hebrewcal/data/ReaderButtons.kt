package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter

/**
 * Shared label builders for the Daily Tehillim / Weekly Parsha reader buttons,
 * used by both the home-screen widget pills and the notification actions so the
 * two surfaces always agree.
 */
object ReaderButtons {

    fun tehillimLabel(portion: TehillimPortion, hebrew: Boolean): String {
        val title = if (hebrew) "תהילים יומי" else "Daily Tehillim"
        return "$title · ${rangeLabel(portion.firstChapter, portion.lastChapter, hebrew)}"
    }

    fun parshaLabel(parshaName: String, hebrew: Boolean): String {
        val title = if (hebrew) "פרשת השבוע" else "Weekly Parsha"
        return "$title · $parshaName"
    }

    private fun rangeLabel(first: Int, last: Int, hebrew: Boolean): String {
        val fmt = HebrewDateFormatter()
        fun n(x: Int) = if (hebrew) fmt.formatHebrewNumber(x) else x.toString()
        return if (first == last) n(first) else "${n(first)}–${n(last)}"
    }
}
