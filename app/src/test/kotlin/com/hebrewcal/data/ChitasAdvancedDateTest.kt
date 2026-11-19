package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date
import java.util.TimeZone

class ChitasAdvancedDateTest {
    private val ny = TimeZone.getTimeZone("America/New_York")
    private val tokyo = TimeZone.getTimeZone("Asia/Tokyo")

    // 2026-11-18 17:00 EST == 22:00 UTC
    private fun nyAt(h: Int, m: Int): Date =
        Date(java.time.ZonedDateTime.of(2026, 11, 18, h, m, 0, 0, ny.toZoneId()).toInstant().toEpochMilli())

    @Test fun exactlyAtTzetAdvances() {
        val tzet = nyAt(17, 0)
        assertEquals("2026-11-19", chitasAdvancedDate(Date(tzet.time), ny, tzet))
    }

    @Test fun oneMinuteBeforeTzetDoesNotAdvance() {
        val tzet = nyAt(17, 0)
        assertEquals("2026-11-18", chitasAdvancedDate(nyAt(16, 59), ny, tzet))
    }

    @Test fun afterTzetAdvances() {
        assertEquals("2026-11-19", chitasAdvancedDate(nyAt(20, 0), ny, nyAt(17, 0)))
    }

    @Test fun nullTzetNeverAdvances() {
        assertEquals("2026-11-18", chitasAdvancedDate(nyAt(23, 59), ny, null))
    }

    @Test fun usesPassedTimeZoneForCivilDate() {
        // 2026-11-18 22:00 UTC is already 2026-11-19 07:00 in Tokyo.
        val now = Date(java.time.Instant.parse("2026-11-18T22:00:00Z").toEpochMilli())
        val tzet = Date(now.time + 60_000)
        assertEquals("2026-11-19", chitasAdvancedDate(now, tokyo, tzet))
        assertEquals("2026-11-18", chitasAdvancedDate(now, TimeZone.getTimeZone("UTC"), tzet))
    }
}
