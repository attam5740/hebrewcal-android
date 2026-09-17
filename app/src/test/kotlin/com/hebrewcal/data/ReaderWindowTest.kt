package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderWindowTest {
    private fun ch(n: Int, verses: Int, from: Int = 1) =
        Chapter(n, (from until from + verses).map { Verse(it, "he$it", "en$it") })

    @Test fun parshaRefWithPartialEdges() {
        val w = ReaderWindows.fromRef("Genesis.1.1-6.8", SefariaText("x", listOf(ch(1, 31), ch(2, 25), ch(3, 24), ch(4, 26), ch(5, 32), ch(6, 8))))
        assertEquals("Genesis", w.bookEn); assertEquals(1, w.firstChapter); assertEquals(6, w.lastChapter)
        assertEquals(false, w.firstPartial)   // starts at 1:1
        assertEquals(true, w.lastPartial)     // 6:8 is not the end of chapter 6 (22 verses)
    }

    @Test fun wholeChapterRangeIsNotPartial() {
        val w = ReaderWindows.fromRef("Psalms.1-9", SefariaText("x", (1..9).map { ch(it, 5) }))
        assertEquals(false, w.firstPartial); assertEquals(false, w.lastPartial)
    }

    @Test fun partialTailRefetchesItself() {
        val w = ReaderWindows.fromRef("Genesis.1.1-6.8", SefariaText("x", listOf(ch(6, 8))))
        assertEquals("Genesis" to 6, ReaderWindows.nextFetch(w))
        val w2 = ReaderWindows.appended(w, "Genesis", ch(6, 22))
        assertEquals(false, w2.lastPartial); assertEquals(22, w2.chapters.last().verses.size)
        assertEquals("Genesis" to 7, ReaderWindows.nextFetch(w2))
    }

    @Test fun torahChainsAcrossBooksAndEndsAtDeuteronomy() {
        val gen50 = ReaderWindows.fromRef("Genesis.50", SefariaText("x", listOf(ch(50, 26))))
        assertEquals("Exodus" to 1, ReaderWindows.nextFetch(gen50))
        val deut34 = ReaderWindows.fromRef("Deuteronomy.34", SefariaText("x", listOf(ch(34, 12))))
        assertNull(ReaderWindows.nextFetch(deut34))
        val ex1 = ReaderWindows.fromRef("Exodus.1", SefariaText("x", listOf(ch(1, 22))))
        assertEquals("Genesis" to 50, ReaderWindows.prevFetch(ex1))
    }

    @Test fun psalmsStopAtBothEnds() {
        assertNull(ReaderWindows.nextFetch(ReaderWindows.fromRef("Psalms.150", SefariaText("x", listOf(ch(150, 6))))))
        assertNull(ReaderWindows.prevFetch(ReaderWindows.fromRef("Psalms.1", SefariaText("x", listOf(ch(1, 6))))))
    }

    @Test fun prependKeepsOrderAndFirstBook() {
        val ex1 = ReaderWindows.fromRef("Exodus.1", SefariaText("x", listOf(ch(1, 22))))
        val w = ReaderWindows.prepended(ex1, "Genesis", ch(50, 26))
        assertEquals(listOf(50, 1), w.chapters.map { it.number })
        assertEquals("Genesis", w.firstBookEn); assertEquals("Exodus", w.bookEn)
        assertEquals("Genesis" to 49, ReaderWindows.prevFetch(w))
    }

    @Test fun tikkunRangeCoversTheWindow() {
        val w = ReaderWindows.fromRef("Genesis.1.1-6.8", SefariaText("x", listOf(ch(5, 32), ch(6, 8))))
        assertEquals("Genesis.5.1-6.8", ReaderWindows.torahRange(w))
        assertNull(ReaderWindows.torahRange(ReaderWindows.fromRef("Psalms.3", SefariaText("x", listOf(ch(3, 9))))))
    }
}
