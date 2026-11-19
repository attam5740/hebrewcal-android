package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EnumOrTest {

    @Test
    fun knownNameIsParsed() {
        assertEquals(Nusach.SEFARD, enumOr("SEFARD", Nusach.ASHKENAZ))
        assertEquals(CalendarLanguage.HEBREW, enumOr("HEBREW", CalendarLanguage.ENGLISH))
    }

    @Test
    fun unknownOrGarbledNameFallsBackToDefault() {
        // Values a hand-edited or newer-version backup might restore.
        assertEquals(Nusach.ASHKENAZ, enumOr("SEFARD_LINEAR", Nusach.ASHKENAZ))
        assertEquals(CalendarLanguage.ENGLISH, enumOr("hebrew", CalendarLanguage.ENGLISH))
        assertEquals(PrefetchNetwork.WIFI, enumOr("", PrefetchNetwork.WIFI))
    }

    @Test
    fun missingNameFallsBackToDefault() {
        assertEquals(ZmanimLocationSource.GPS, enumOr(null, ZmanimLocationSource.GPS))
    }
}
