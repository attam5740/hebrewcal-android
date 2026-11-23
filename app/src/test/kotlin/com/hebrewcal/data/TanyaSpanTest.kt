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

    @Test fun chapterAndSegmentCompareNumerically() {
        assertEquals("$acharon 4:9-4:10", TanyaSpan.spanRef("$acharon 4:9", "$acharon 4:10"))
        assertNull(TanyaSpan.spanRef("$acharon 4:10", "$acharon 4:9"))
        assertEquals("$acharon 9:1-10:1", TanyaSpan.spanRef("$acharon 9:1", "$acharon 10:1"))
    }

    @Test fun apostropheInPrefixParses() =
        assertEquals("Tanya, Part V; Compiler's Foreword 1:1-1:3",
            TanyaSpan.spanRef("Tanya, Part V; Compiler's Foreword 1:1", "Tanya, Part V; Compiler's Foreword 1:3"))

    // --- depth-1 nodes (Tanya Yomi's first days, real refs from the live calendar) ---
    private val foreword = "Tanya, Part I; Likkutei Amarim, Compiler's Foreword"

    @Test fun depthOneNodeParagraphSpan() {
        assertEquals("$foreword 1-9", TanyaSpan.spanRef("$foreword 1", "$foreword 9"))
        assertEquals("$foreword 9-13", TanyaSpan.spanRef("$foreword 9", "$foreword 13")) // numeric, not lexical
    }

    @Test fun depthOneNodeSpanReversedOrEqualIsNull() {
        assertNull(TanyaSpan.spanRef("$foreword 13", "$foreword 9"))
        assertNull(TanyaSpan.spanRef("$foreword 9", "$foreword 9"))
    }

    @Test fun depthOneNodeAcrossNodesIsNull() {
        assertNull(TanyaSpan.spanRef("$foreword 13", "Tanya, Part I; Likkutei Amarim 1:1"))
        assertNull(TanyaSpan.spanRef("Tanya, Part I; Likkutei Amarim, Title Page 1", "Tanya, Part I; Likkutei Amarim, Approbation 3:1"))
    }

    @Test fun bareNumberNeverPairsWithChapterSegment() {
        assertNull(TanyaSpan.spanRef("$foreword 1", "$foreword 9:1"))
        assertNull(TanyaSpan.spanRef("$foreword 1:1", "$foreword 9"))
    }

    @Test fun depthOneNodeLegIsTrimmed() {
        // The last paragraph of 1-9 (9) is tomorrow's start, so the leg is marked for trimming.
        assertEquals(TanyaLeg("$foreword 1-9", true), ChitasSchedule.tanyaLegRef(day("$foreword 1"), day("$foreword 9")))
        val r = ChitasSections.legsAfterTrim(listOf(null, null, text(1 to 1..9)), true)
        assertEquals((1..8).toList(), r[2]!!.chapters.single().verses.map { it.num })
    }

    // --- legsAfterTrim ---
    @Test fun trimsOnlyTanyaLegWhenFlagged() {
        val legs = listOf(text(1 to 1..3), text(2 to 1..3), text(4 to 35..40))
        val r = ChitasSections.legsAfterTrim(legs, true)
        assertEquals(3, r[0]!!.chapters.single().verses.size)
        assertEquals(3, r[1]!!.chapters.single().verses.size)
        assertEquals(listOf(35, 36, 37, 38, 39), r[2]!!.chapters.single().verses.map { it.num })
    }

    @Test fun noTrimWhenNotFlagged() {
        val legs = listOf(text(1 to 1..3), text(2 to 1..3), text(4 to 35..40))
        assertEquals(legs, ChitasSections.legsAfterTrim(legs, false))
    }

    @Test fun singleVerseLegNeverTrimmed() {
        val legs = listOf(null, null, text(4 to 35..35))
        assertEquals(legs, ChitasSections.legsAfterTrim(legs, true))
    }

    @Test fun nullLegsLeftAlone() {
        val legs = listOf<SefariaText?>(null, null, null)
        assertEquals(legs, ChitasSections.legsAfterTrim(legs, true))
    }
}
