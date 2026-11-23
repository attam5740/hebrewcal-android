package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter

/**
 * Shared label builders for the Daily Tehillim / Weekly Parsha reader buttons,
 * used by both the home-screen widget pills and the notification actions so the
 * two surfaces always agree.
 */
object ReaderButtons {

    fun tehillimSpec(portion: TehillimPortion, hebrew: Boolean): PillSpec =
        PillSpec(
            if (hebrew) "תהילים יומי" else "Daily Tehillim",
            rangeLabel(portion.firstChapter, portion.lastChapter, hebrew)
        )

    fun parshaSpec(parshaName: String, hebrew: Boolean): PillSpec =
        PillSpec(if (hebrew) "פרשת השבוע" else "Weekly Parsha", parshaName)

    fun tehillimLabel(portion: TehillimPortion, hebrew: Boolean): String =
        tehillimSpec(portion, hebrew).full

    fun parshaLabel(parshaName: String, hebrew: Boolean): String =
        parshaSpec(parshaName, hebrew).full

    private fun rangeLabel(first: Int, last: Int, hebrew: Boolean): String {
        val fmt = HebrewDateFormatter()
        fun n(x: Int) = if (hebrew) fmt.formatHebrewNumber(x) else x.toString()
        return if (first == last) n(first) else "${n(first)}–${n(last)}"
    }
}
