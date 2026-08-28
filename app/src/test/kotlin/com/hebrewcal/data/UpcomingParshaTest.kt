package com.hebrewcal.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class UpcomingParshaTest {
    private val repo = HebrewCalendarRepository()
    private fun d(s: String) = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s)!!

    @Test fun returnsUpcomingParshaOnAWeekday() {
        // 2026-08-28 is during the week of Parashat Ki Tavo (Ashkenazi transliteration "Ki Savo").
        val name = repo.upcomingParshaName(d("2026-08-28"), CalendarLocation.DIASPORA, CalendarLanguage.ENGLISH)
        assertNotNull(name)
    }

    @Test fun hebrewNameIsHebrewScript() {
        val name = repo.upcomingParshaName(d("2026-08-28"), CalendarLocation.DIASPORA, CalendarLanguage.HEBREW)!!
        assertEquals(true, name.any { it in 'א'..'ת' })
    }
}
