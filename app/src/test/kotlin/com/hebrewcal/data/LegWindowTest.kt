package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LegWindowTest {
    private val ka = "Tanya, Part V; Kuntres Acharon"

    /** A text of whole or partial chapters: each pair is a chapter number and its verse numbers. */
    private fun text(vararg chapters: Pair<Int, IntRange>, next: String? = null, prev: String? = null, heRef: String = "he") =
        SefariaText(heRef, chapters.map { (c, vs) -> Chapter(c, vs.map { Verse(it, "h$c:$it", "e$c:$it") }) },
            next = next, prev = prev)

    private fun LegWindow.verses() = blocks.flatMap { b -> b.verses.map { it.chapter to it.verse.num } }
    private fun LegBlock.nums() = verses.map { it.verse.num }

    // ---- Tehillim, whole chapters ----

    @Test fun wholeChapterTehillimDownGoesStraightToNextChapterWithHeading() {
        val w = LegWindow.start("Psalms.44-48", text(44 to 1..27, 45 to 1..18, 48 to 1..15, next = "Psalms 49", prev = "Psalms 43"))
        assertEquals(1, w.blocks.size)
        assertNull(w.blocks[0].heading)
        assertTrue(w.canExtendDown)
        assertEquals("Psalms 49", w.nextFetch(down = true))
        val g = w.applied(true, "Psalms 49", text(49 to 1..21, next = "Psalms 50", prev = "Psalms 48", heRef = "תהילים מ״ט"))
        assertEquals(1, g.blocksAdded(w))
        assertEquals("Psalms 49", g.blocks.last().heading)
        assertEquals("תהילים מ״ט", g.blocks.last().headingHe)
        assertEquals(List(21) { 49 }, g.blocks.last().verses.map { it.chapter })
        assertEquals((1..21).toList(), g.blocks.last().nums())
        assertEquals("Psalms 50", g.nextFetch(true))
    }

    @Test fun wholeChapterTehillimUpPrependsPreviousChapter() {
        val w = LegWindow.start("Psalms.44-48", text(44 to 1..27, 48 to 1..15, next = "Psalms 49", prev = "Psalms 43"))
        assertTrue(w.canExtendUp)
        assertEquals("Psalms 43", w.nextFetch(down = false))
        val g = w.applied(false, "Psalms 43", text(43 to 1..5, prev = "Psalms 42", next = "Psalms 44"))
        assertEquals(2, g.blocks.size)
        assertEquals("Psalms 43", g.blocks.first().heading)
        assertEquals((1..5).toList(), g.blocks.first().nums())
        assertNull(g.blocks[1].heading) // the original slice stays put, below
        assertEquals("Psalms 42", g.nextFetch(false))
        assertEquals("Psalms 49", g.nextFetch(true)) // the other edge is untouched
    }

    // ---- Tehillim, verse-bounded Psalm 119 ----

    @Test fun psalm119FirstHalfDownCompletesChapterThenPsalm120() {
        // next/prev from a verse-bounded fetch are not trusted.
        val w = LegWindow.start("Psalms.119.1-96", text(119 to 1..96, next = "Psalms 120", prev = "Psalms 118"))
        assertEquals("Psalms 119", w.nextFetch(true))
        val g = w.applied(true, "Psalms 119", text(119 to 1..176, next = "Psalms 120", prev = "Psalms 118"))
        assertEquals(1, g.blocksAdded(w))
        assertNull(g.blocks.last().heading) // continues the same chapter
        assertEquals((97..176).toList(), g.blocks.last().nums())
        assertTrue(g.blocks.last().verses.all { it.chapter == 119 })
        assertEquals("Psalms 120", g.nextFetch(true))
        val h = g.applied(true, "Psalms 120", text(120 to 1..7, next = "Psalms 121", prev = "Psalms 119"))
        assertEquals("Psalms 120", h.blocks.last().heading)
        assertEquals((1..7).toList(), h.blocks.last().nums())
    }

    @Test fun psalm119SecondHalfStartsMidChapter() {
        val w = LegWindow.start("Psalms.119.97-176", text(119 to 97..176, next = "Psalms 120", prev = "Psalms 118"))
        assertEquals((97..176).toList(), w.blocks.single().nums())
        // Up: the rest of 119 first, without a heading, then 118.
        assertEquals("Psalms 119", w.nextFetch(false))
        val up = w.applied(false, "Psalms 119", text(119 to 1..176, next = "Psalms 120", prev = "Psalms 118"))
        assertEquals((1..96).toList(), up.blocks.first().nums())
        assertNull(up.blocks.first().heading)
        assertEquals("Psalms 118", up.nextFetch(false))
        // Down: 119 already ends at 176, so the completion adds nothing and moves on to 120.
        val down = w.applied(true, "Psalms 119", text(119 to 1..176, next = "Psalms 120", prev = "Psalms 118"))
        assertEquals(0, down.blocksAdded(w))
        assertTrue(down.canExtendDown)
        assertEquals("Psalms 120", down.nextFetch(true))
    }

    // ---- Chumash ----

    @Test fun chumashAliyahDownCompletesChapterThenNextChapter() {
        val w = LegWindow.start("Genesis 30:14-30:27", text(30 to 14..27, next = "Genesis 31", prev = "Genesis 29"))
        assertEquals("Genesis 30", w.nextFetch(true))
        val g = w.applied(true, "Genesis 30", text(30 to 1..43, next = "Genesis 31", prev = "Genesis 29"))
        assertEquals((28..43).toList(), g.blocks.last().nums())
        assertNull(g.blocks.last().heading)
        assertEquals("Genesis 31", g.nextFetch(true))
        val h = g.applied(true, "Genesis 31", text(31 to 1..54, next = "Genesis 32", prev = "Genesis 30"))
        assertEquals("Genesis 31", h.blocks.last().heading)
        assertEquals(listOf(30 to 14, 30 to 27, 30 to 28, 30 to 43, 31 to 1, 31 to 54),
            h.verses().let { v -> listOf(v.first(), v[13], v[14], v[29], v[30], v.last()) })
    }

    @Test fun chumashAliyahUpCompletesChapterThenPreviousChapter() {
        val w = LegWindow.start("Genesis 30:14-30:27", text(30 to 14..27, next = "Genesis 31", prev = "Genesis 29"))
        assertEquals("Genesis 30", w.nextFetch(false))
        val g = w.applied(false, "Genesis 30", text(30 to 1..43, next = "Genesis 31", prev = "Genesis 29"))
        assertEquals((1..13).toList(), g.blocks.first().nums())
        assertNull(g.blocks.first().heading)
        assertEquals("Genesis 29", g.nextFetch(false))
        val h = g.applied(false, "Genesis 29", text(29 to 1..35, next = "Genesis 30", prev = "Genesis 28"))
        assertEquals("Genesis 29", h.blocks.first().heading)
        assertEquals((1..35).toList(), h.blocks.first().nums())
        assertEquals((1..27).toList(), h.blocks.drop(1).flatMap { it.nums() }) // 30:1-27, numbers true
        assertEquals("Genesis 28", h.nextFetch(false))
    }

    // ---- Review Focus 3: a "complete the chapter" fetch that adds nothing ----

    @Test fun edgeChapterAlreadyCompleteSignalsNoGrowthAndPointsAtNeighbour() {
        val w = LegWindow.start("Genesis 11:1-11:32", text(11 to 1..32, next = "Genesis 12", prev = "Genesis 10"))
        assertEquals("Genesis 11", w.nextFetch(true))
        val g = w.applied(true, "Genesis 11", text(11 to 1..32, next = "Genesis 12", prev = "Genesis 10"))
        assertEquals(0, g.blocksAdded(w))
        assertEquals(w.blocks, g.blocks)
        // Not dead: the very next step is the neighbouring chapter, not the same refetch.
        assertTrue(g.canExtendDown)
        assertEquals("Genesis 12", g.nextFetch(true))
        assertEquals(1, g.applied(true, "Genesis 12", text(12 to 1..20, next = "Genesis 13")).blocksAdded(g))
    }

    // ---- Tanya ----

    @Test fun trimmedTanyaRangeDownCompletesChapterThenNextChapter() {
        // "…4:35-4:40" fetched, verse 40 (tomorrow's start) dropped: 35–39 shown.
        val w = LegWindow.start("$ka 4:35-4:40", text(4 to 35..39, next = "$ka 5", prev = "$ka 3"), trimmed = true)
        assertEquals((35..39).toList(), w.blocks.single().nums())
        assertEquals("$ka 4", w.nextFetch(true))
        val g = w.applied(true, "$ka 4", text(4 to 1..45, next = "$ka 5", prev = "$ka 3"))
        assertEquals((40..45).toList(), g.blocks.last().nums())
        assertNull(g.blocks.last().heading)
        assertEquals("$ka 5", g.nextFetch(true))
        val h = g.applied(true, "$ka 5", text(5 to 1..12, next = "$ka 6", prev = "$ka 4"))
        assertEquals("$ka 5", h.blocks.last().heading)
        assertEquals(List(12) { 5 }, h.blocks.last().verses.map { it.chapter })
    }

    @Test fun trimmedCrossChapterTanyaDoesNotTrustRangeNext() {
        // "4:50-5:1" trimmed to chapter 4 only; the range response says next = 6, which would skip 5.
        val w = LegWindow.start("$ka 4:50-5:1", text(4 to 50..52, next = "$ka 6", prev = "$ka 3"), trimmed = true)
        assertEquals("$ka 4", w.nextFetch(true))
        val g = w.applied(true, "$ka 4", text(4 to 1..52, next = "$ka 5", prev = "$ka 3"))
        assertEquals(0, g.blocksAdded(w))
        assertEquals("$ka 5", g.nextFetch(true))
        val h = g.applied(true, "$ka 5", text(5 to 1..9, next = "$ka 6", prev = "$ka 4"))
        assertEquals(5 to 1, h.verses()[3]) // 5:1, tomorrow's start, now reached by scrolling
    }

    @Test fun trimmedWholeChapterLookingRangeStillTreatsLastEdgeAsBounded() {
        // A trimmed leg's last edge is verse-bounded even if the ref's spec were chapter-level.
        val w = LegWindow.start("$ka 4-5", text(4 to 1..10, next = "$ka 6", prev = "$ka 3"), trimmed = true)
        assertEquals("$ka 4", w.nextFetch(true))
        assertEquals("$ka 3", w.nextFetch(false))
    }

    @Test fun untrimmedTanyaStartSegmentExtendsBothWaysWithinItsChapter() {
        val li = "Tanya, Part I; Likkutei Amarim"
        val w = LegWindow.start("$li 12:3", text(12 to 3..3, next = "$li 13", prev = "$li 11"))
        assertEquals("$li 12", w.nextFetch(true))
        assertEquals("$li 12", w.nextFetch(false))
        val whole = text(12 to 1..8, next = "$li 13", prev = "$li 11")
        assertEquals((4..8).toList(), w.applied(true, "$li 12", whole).blocks.last().nums())
        assertEquals((1..2).toList(), w.applied(false, "$li 12", whole).blocks.first().nums())
    }

    // ---- Exhaustion ----

    @Test fun wholeChapterRefAtBookEndCannotExtendDown() {
        val w = LegWindow.start("Psalms.140-150", text(140 to 1..14, 150 to 1..6, next = null, prev = "Psalms 139"))
        assertFalse(w.canExtendDown)
        assertNull(w.nextFetch(true))
        assertTrue(w.canExtendUp)
    }

    @Test fun wholeChapterRefAtBookStartCannotExtendUp() {
        val w = LegWindow.start("Psalms.1-9", text(1 to 1..6, 9 to 1..21, next = "Psalms 10", prev = null))
        assertFalse(w.canExtendUp)
        assertNull(w.nextFetch(false))
        assertTrue(w.canExtendDown)
    }

    @Test fun verseBoundedEdgeAtBookEndBecomesExhaustedAfterCompletion() {
        val w = LegWindow.start("Deuteronomy 34:1-34:12", text(34 to 1..12, next = null, prev = "Deuteronomy 33"))
        assertTrue(w.canExtendDown) // optimistic while the neighbour is unknown
        val g = w.applied(true, "Deuteronomy 34", text(34 to 1..12, next = null, prev = "Deuteronomy 33"))
        assertEquals(0, g.blocksAdded(w))
        assertFalse(g.canExtendDown)
        assertNull(g.nextFetch(true))
    }

    @Test fun genesisOneOneUpFindsNothingAndNoPrevious() {
        val w = LegWindow.start("Genesis 1:1-2:3", text(1 to 1..31, 2 to 1..3, next = "Genesis 3", prev = null))
        assertTrue(w.canExtendUp) // a verse-bounded edge: unknown until fetched
        assertEquals("Genesis 1", w.nextFetch(false))
        val g = w.applied(false, "Genesis 1", text(1 to 1..31, next = "Genesis 2", prev = null))
        assertEquals(0, g.blocksAdded(w))
        assertFalse(g.canExtendUp)
        assertNull(g.nextFetch(false))
        assertEquals("Genesis 2", g.nextFetch(true)) // down edge (2:3) still needs completing
    }

    // ---- Robustness ----

    @Test fun aFetchThatIsNotTheExpectedRefChangesNothing() {
        val w = LegWindow.start("Genesis 30:14-30:27", text(30 to 14..27))
        assertSame(w, w.applied(true, "Genesis 31", text(31 to 1..54, next = "Genesis 32")))
    }

    @Test fun blockKeysStayUniqueAcrossBothDirections() {
        var w = LegWindow.start("Genesis 30:14-30:27", text(30 to 14..27))
        val whole30 = text(30 to 1..43, next = "Genesis 31", prev = "Genesis 29")
        w = w.applied(true, "Genesis 30", whole30)
        w = w.applied(false, "Genesis 30", whole30)
        w = w.applied(true, "Genesis 31", text(31 to 1..54, next = "Genesis 32", prev = "Genesis 30"))
        w = w.applied(false, "Genesis 29", text(29 to 1..35, next = "Genesis 30", prev = "Genesis 28"))
        assertEquals(5, w.blocks.size)
        assertEquals(w.blocks.size, w.blocks.map { it.key }.toSet().size)
        assertEquals(listOf("Genesis 29", null, null, null, "Genesis 31"), w.blocks.map { it.heading })
    }

    @Test fun unparseableRefCannotExtend() {
        val w = LegWindow.start("Something odd", text(1 to 1..3, next = "x", prev = "y"))
        assertFalse(w.canExtendUp)
        assertFalse(w.canExtendDown)
        assertEquals(1, w.blocks.size)
    }
}
