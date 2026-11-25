package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavBooksTest {
    @Test fun prophetsAreTwentyOneBooks() {
        assertEquals(21, NavBooks.PROPHETS.sumOf { it.books.size })
        assertEquals(listOf(6, 3, 12), NavBooks.PROPHETS.map { it.books.size })
    }

    @Test fun refsAreBookDotChapterForProphetsAndSpacedForTanya() {
        val sam = NavBooks.PROPHETS[0].books[2]
        assertEquals("I Samuel.31", NavBooks.prophetRef(sam, 31))
        assertEquals("Tanya, Part I; Likkutei Amarim 4", NavBooks.tanyaRef(NavBooks.TANYA[0], 4))
    }

    @Test fun tanyaChapterCountsMatchSefaria() {
        assertEquals(listOf(53, 12, 12, 32, 9), NavBooks.TANYA.map { it.chapters })
    }

    @Test fun locateProphetFromHaftarahAndChapterRefs() {
        assertEquals(Triple(2, 3, 1), NavBooks.locateProphet("Obadiah.1.1-1.21"))
        assertEquals(Triple(0, 2, 1), NavBooks.locateProphet("I_Samuel.1"))
        assertEquals(Triple(0, 3, 12), NavBooks.locateProphet("II Samuel 12:1-25"))
        assertEquals(Triple(1, 0, 40), NavBooks.locateProphet("Isaiah.40.1-26"))
    }

    @Test fun locateProphetDoesNotConfuseOneAndTwoOfABook() {
        assertEquals(Triple(0, 4, 18), NavBooks.locateProphet("I Kings.18"))
        assertEquals(Triple(0, 5, 4), NavBooks.locateProphet("II Kings.4"))
    }

    @Test fun locateProphetRejectsOtherBooks() {
        assertNull(NavBooks.locateProphet("Genesis.1"))
        assertNull(NavBooks.locateProphet(""))
    }

    @Test fun locateTanyaReadsPartAndChapter() {
        assertEquals(0 to 4, NavBooks.locateTanya("Tanya, Part I; Likkutei Amarim 4:35-4:40"))
        assertEquals(4 to 4, NavBooks.locateTanya("Tanya, Part V; Kuntres Acharon 4:35"))
        assertEquals(1 to 12, NavBooks.locateTanya("Tanya, Part II; Shaar HaYichud VehaEmunah 12"))
    }

    @Test fun locateTanyaTitledNodeIsChapterOne() {
        assertEquals(0 to 1, NavBooks.locateTanya("Tanya, Part I; Likkutei Amarim, Compiler's Foreword 9"))
    }

    @Test fun locateTanyaRejectsOtherRefs() {
        assertNull(NavBooks.locateTanya("Psalms.1"))
    }
}
