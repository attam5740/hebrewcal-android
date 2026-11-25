package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderWindowTanyaTest {
    private val part1 = "Tanya, Part I; Likkutei Amarim"
    private val part2 = "Tanya, Part II; Shaar HaYichud VehaEmunah"

    private fun window(book: String, first: Int, last: Int = first) = ReaderWindow(
        bookEn = book, chapters = (first..last).map { Chapter(it, listOf(Verse(1, "א", "a"))) },
        firstChapter = first, lastChapter = last, firstPartial = false, lastPartial = false
    )

    @Test fun refForUsesASpaceForTanyaAndADotForTheTorah() {
        assertEquals("$part1 4", ReaderWindows.refFor(part1, 4))
        assertEquals("Genesis.4", ReaderWindows.refFor("Genesis", 4))
    }

    @Test fun fromRefReadsATanyaChapter() {
        val text = SefariaText("", listOf(Chapter(4, listOf(Verse(1, "א", "a")))))
        val w = ReaderWindows.fromRef("$part1 4", text)
        assertEquals(part1, w.bookEn)
        assertEquals(4, w.firstChapter)
        assertEquals(4, w.lastChapter)
        assertEquals(false, w.lastPartial)
    }

    @Test fun nextAndPreviousChapterWithinAPart() {
        val w = window(part1, 4)
        assertEquals(part1 to 5, ReaderWindows.nextFetch(w))
        assertEquals(part1 to 3, ReaderWindows.prevFetch(w))
    }

    @Test fun nextCrossesFromOnePartToTheNext() {
        assertEquals(part2 to 1, ReaderWindows.nextFetch(window(part1, 53)))
    }

    @Test fun previousCrossesBackToTheLastChapterOfThePartBefore() {
        assertEquals(part1 to 53, ReaderWindows.prevFetch(window(part2, 1)))
    }

    @Test fun theBooksEndStopsTheWindow() {
        assertNull(ReaderWindows.prevFetch(window(part1, 1)))
        assertNull(ReaderWindows.nextFetch(window("Tanya, Part V; Kuntres Acharon", 9)))
    }

    @Test fun theTorahStillCrossesBooks() {
        assertEquals("Exodus" to 1, ReaderWindows.nextFetch(window("Genesis", 50)))
    }
}
