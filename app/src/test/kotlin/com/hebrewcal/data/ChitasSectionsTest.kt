package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChitasSectionsTest {
    private fun text(vararg chapters: Pair<Int, IntRange>) = SefariaText(
        heRef = "",
        chapters = chapters.map { (num, vs) -> Chapter(num, vs.map { Verse(it, "ה$it", "e$it") }) }
    )

    @Test fun threeLegsInOrder() {
        val legs = ChitasSections.build(
            listOf(text(30 to 14..27), text(44 to 1..5, 45 to 1..3), text(1 to 1..1)),
            listOf("Chumash", "Tehillim", "Tanya")
        )
        assertEquals(3, legs.size)
        assertEquals(listOf("Chumash", "Tehillim", "Tanya"), legs.map { it.first })
    }

    @Test fun verseAndChapterNumbersSurviveMultiChapterLegs() {
        val legs = ChitasSections.build(
            listOf(text(30 to 14..15), text(44 to 1..2, 45 to 1..2), text(1 to 1..1)),
            listOf("Chumash", "Tehillim", "Tanya")
        )
        // Tehillim leg keeps real psalm numbers and real verse numbers.
        assertEquals(listOf(44, 44, 45, 45), legs[1].second.map { it.chapter })
        assertEquals(listOf(1, 2, 1, 2), legs[1].second.map { it.verse.num })
        // Chumash keeps its true verse numbers, not 1..N.
        assertEquals(listOf(14, 15), legs[0].second.map { it.verse.num })
    }

    // Review Focus 5
    @Test fun aMissingLegKeepsTheOthers() {
        val legs = ChitasSections.build(
            listOf(text(30 to 14..15), text(44 to 1..2), null),
            listOf("Chumash", "Tehillim", "Tanya")
        )
        assertEquals(3, legs.size)
        assertEquals(2, legs[0].second.size)
        assertEquals(0, legs[2].second.size)
    }

    @Test fun blankChumashRefKeepsPositions() {
        assertEquals(listOf("", "Psalms.44-48", "Tanya, Part V; Kuntres Acharon 4:35"),
            ChitasSections.splitRefs("||Psalms.44-48||Tanya, Part V; Kuntres Acharon 4:35"))
    }

    @Test fun blankTanyaRefKeepsPositions() {
        assertEquals(listOf("Genesis.44.18-30", "Psalms.44-48", ""),
            ChitasSections.splitRefs("Genesis.44.18-30||Psalms.44-48||"))
    }

    @Test fun allBlankRefsStayThreeBlanks() {
        assertEquals(listOf("", "", ""), ChitasSections.splitRefs("||||"))
    }
}
