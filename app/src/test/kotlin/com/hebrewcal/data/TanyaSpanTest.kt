package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TanyaSpanTest {
    private val acharon = "Tanya, Part V; Kuntres Acharon"

    @Test fun sameChapterSpan() =
        assertEquals("$acharon 4:35-4:40", TanyaSpan.spanRef("$acharon 4:35", "$acharon 4:40"))

    @Test fun crossChapterSpan() =
        assertEquals("$acharon 4:50-5:1", TanyaSpan.spanRef("$acharon 4:50", "$acharon 5:1"))

    @Test fun differentPrefixIsNull() {
        assertNull(TanyaSpan.spanRef("$acharon 9:1", "Tanya, Part I; Likkutei Amarim, Title Page 1"))
        assertNull(TanyaSpan.spanRef("Tanya, Part I; Likkutei Amarim, Title Page 1", "Tanya, Part I; Likkutei Amarim, Approbation 1"))
        assertNull(TanyaSpan.spanRef("Tanya, Part I; Likkutei Amarim 53:1", "Tanya, Part II; Shaar HaYichud VeHaEmunah 1:1"))
    }

    @Test fun unparseableIsNull() {
        assertNull(TanyaSpan.spanRef("", "$acharon 4:40"))
        assertNull(TanyaSpan.spanRef("$acharon 4:35", ""))
        assertNull(TanyaSpan.spanRef("$acharon 4", "$acharon 5"))
    }

    @Test fun equalOrReversedIsNull() {
        assertNull(TanyaSpan.spanRef("$acharon 4:35", "$acharon 4:35"))
        assertNull(TanyaSpan.spanRef("$acharon 4:40", "$acharon 4:35"))
        assertNull(TanyaSpan.spanRef("$acharon 5:1", "$acharon 4:50"))
    }

    // --- dropLastVerse ---
    private fun text(vararg chapters: Pair<Int, IntRange>) = SefariaText(
        heRef = "x",
        chapters = chapters.map { (n, vs) -> Chapter(n, vs.map { Verse(it, "ה$it", "e$it") }) }
    )

    @Test fun dropLastVerseDropsOne() {
        val r = ChitasSections.dropLastVerse(text(4 to 35..40))
        assertEquals(listOf(35, 36, 37, 38, 39), r.chapters.single().verses.map { it.num })
    }

    @Test fun dropLastVerseRemovesEmptiedChapter() {
        val r = ChitasSections.dropLastVerse(text(4 to 50..58, 5 to 1..1))
        assertEquals(listOf(4), r.chapters.map { it.number })
        assertEquals(9, r.chapters.single().verses.size)
    }

    @Test fun dropLastVerseSingleVerseIsEmptyNotCrash() {
        val r = ChitasSections.dropLastVerse(text(1 to 1..1))
        assertTrue(r.chapters.isEmpty())
        assertTrue(ChitasSections.dropLastVerse(SefariaText("x", emptyList())).chapters.isEmpty())
    }

    // --- tanyaLegRef ---
    private fun day(tanya: String) = ParshaRef("", "", "", "", tanyaRef = tanya)

    @Test fun legRefSpan() {
        val l = ChitasSchedule.tanyaLegRef(day("$acharon 4:35"), day("$acharon 4:40"))
        assertEquals(TanyaLeg("$acharon 4:35-4:40", true), l)
    }

    @Test fun legRefBlankNextDay() {
        assertEquals(TanyaLeg("$acharon 4:35", false), ChitasSchedule.tanyaLegRef(day("$acharon 4:35"), day("")))
        assertEquals(TanyaLeg("$acharon 4:35", false), ChitasSchedule.tanyaLegRef(day("$acharon 4:35"), null))
    }

    @Test fun legRefImpossibleSpan() {
        val l = ChitasSchedule.tanyaLegRef(day("$acharon 9:1"), day("Tanya, Part I; Likkutei Amarim, Title Page 1"))
        assertEquals(TanyaLeg("$acharon 9:1", false), l)
        assertFalse(l.trimLast)
    }

    @Test fun legRefBlankStart() =
        assertEquals(TanyaLeg("", false), ChitasSchedule.tanyaLegRef(day(""), day("$acharon 4:40")))

    // --- Kehot pin survives a range ref ---
    @Test fun rangeRefStillPinsKehotWithoutMasorah() {
        val url = SefariaRefs.textUrl("$acharon 4:35-4:40")
        assertTrue(url, url.contains("vhe=Kehot"))
        assertTrue(url, url.contains("ven=Kehot"))
        assertFalse(url, url.contains("Masorah"))
    }
}
