package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ChitasScheduleTest {
    // Vayetzei 5787: aliyot as the live API returns them, 7 + maftir.
    private val vayetzei = ParshaRef(
        sefariaRef = "Genesis.28.10-32.3", heRef = "", nameEn = "Vayetzei", nameHe = "ויצא",
        aliyot = listOf(
            "Genesis 28:10-28:22", "Genesis 29:1-29:17", "Genesis 29:18-30:13",
            "Genesis 30:14-30:27", "Genesis 30:28-31:16", "Genesis 31:17-31:42",
            "Genesis 31:43-32:3", "Genesis 32:1-32:3"
        ),
        tanyaRef = "Tanya, Part V; Kuntres Acharon 4:35", tanyaLabel = "8 Kislev", tanyaLabelHe = "ח כסלו"
    )

    @Test fun wednesdayOfVayetzeiIsTheFourthAliyah() {
        val p = ChitasSchedule.portionFor(LocalDate.of(2026, 11, 18), 8, 30, vayetzei)
        assertEquals("Genesis 30:14-30:27", p.chumashRef)
        assertEquals(4, p.aliyahIndex)
    }

    @Test fun sundayIsTheFirstAliyahAndShabbatIsTheSeventh() {
        assertEquals(1, ChitasSchedule.portionFor(LocalDate.of(2026, 11, 15), 5, 30, vayetzei).aliyahIndex)
        assertEquals(7, ChitasSchedule.portionFor(LocalDate.of(2026, 11, 21), 11, 30, vayetzei).aliyahIndex)
    }

    @Test fun maftirIsNeverSelected() {
        // Every day of the week maps into the first seven entries only.
        val refs = (15..21).map {
            ChitasSchedule.portionFor(LocalDate.of(2026, 11, it), 5, 30, vayetzei).chumashRef
        }
        assertEquals(7, refs.distinct().size)
        assertEquals(false, refs.contains("Genesis 32:1-32:3"))
    }

    // Review Focus 1
    @Test fun theParshaDateAdvancesToTheComingShabbat() {
        // Saturday 21 Nov is the Shabbat of Vayetzei. The advanced date on that
        // evening is Sunday 22 Nov, which belongs to the NEXT Shabbat, 28 Nov.
        assertEquals(LocalDate.of(2026, 11, 21), ChitasSchedule.parshaDateFor(LocalDate.of(2026, 11, 21)))
        assertEquals(LocalDate.of(2026, 11, 28), ChitasSchedule.parshaDateFor(LocalDate.of(2026, 11, 22)))
    }

    // Review Focus 3
    @Test fun aCombinedParshaStillIndexesByDayOfWeek() {
        val double = vayetzei.copy(nameEn = "Vayakhel-Pekudei", aliyot = vayetzei.aliyot.take(7))
        assertEquals(3, ChitasSchedule.portionFor(LocalDate.of(2026, 11, 17), 7, 30, double).aliyahIndex)
    }

    // Review Focus 4
    @Test fun aParshaWithNoAliyotYieldsABlankChumashRefNotACrash() {
        val p = ChitasSchedule.portionFor(LocalDate.of(2026, 11, 18), 8, 30, vayetzei.copy(aliyot = emptyList()))
        assertEquals("", p.chumashRef)
    }

    @Test fun tehillimAndTanyaComeThrough() {
        val p = ChitasSchedule.portionFor(LocalDate.of(2026, 11, 18), 8, 30, vayetzei)
        assertEquals(TehillimSchedule.portionFor(8, 30).sefariaRef, p.tehillimRef)
        assertEquals("Tanya, Part V; Kuntres Acharon 4:35", p.tanyaRef)
        assertEquals("8 Kislev", p.tanyaLabel)
    }

    @Test fun legTitlesAreThreeAndNameTheDaysPortions() {
        val t = ChitasSchedule.legTitles(ChitasSchedule.portionFor(LocalDate.of(2026, 11, 18), 8, 30, vayetzei), hebrew = false)
        assertEquals(3, t.size)
        assertEquals(true, t[0].startsWith("Chumash — Vayetzei"))
        assertEquals(true, t[2].contains("8 Kislev"))
    }

    @Test fun hebrewLegTitlesUseHebrewTanyaLabelAndPleneTehillim() {
        val t = ChitasSchedule.legTitles(ChitasSchedule.portionFor(LocalDate.of(2026, 11, 18), 8, 30, vayetzei), hebrew = true)
        assertEquals(3, t.size)
        assertEquals(true, t[1].startsWith("תהילים"))
        assertEquals(true, t[2].contains("ח כסלו"))
        assertEquals(false, t[2].contains("Kislev"))
    }

    @Test fun hebrewTanyaTitleFallsBackWhenHebrewLabelIsEmpty() {
        val p = ChitasSchedule.portionFor(LocalDate.of(2026, 11, 18), 8, 30, vayetzei.copy(tanyaLabelHe = ""))
        assertEquals("תניא — 8 Kislev", ChitasSchedule.legTitles(p, hebrew = true)[2])
    }

    @Test fun dailyTanyaReplacesTheShabbatsButKeepsItsAliyot() {
        val daily = ParshaRef("", "", "", "", tanyaRef = "Tanya, X 1", tanyaLabel = "L", tanyaLabelHe = "ל")
        val merged = ChitasSchedule.withDailyTanya(vayetzei, daily)
        assertEquals("Tanya, X 1", merged.tanyaRef)
        assertEquals("L", merged.tanyaLabel)
        assertEquals("ל", merged.tanyaLabelHe)
        assertEquals(vayetzei.aliyot, merged.aliyot)
        assertEquals("Vayetzei", merged.nameEn)
    }
}
