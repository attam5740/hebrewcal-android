package com.hebrewcal.ui.reader

import com.hebrewcal.data.AliyotPartitioner.TaggedVerse
import com.hebrewcal.data.LegBlock
import com.hebrewcal.data.Verse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegRowsTest {
    private fun v(ch: Int, n: Int) = TaggedVerse(ch, Verse(n, "he$ch:$n", "en$ch:$n"))
    private val leg = LegBlock("leg", null, listOf(v(30, 14), v(30, 15)))
    private val next = LegBlock("d|Genesis 31|1", "Genesis 31", listOf(v(31, 1)), "בראשית לא")

    private fun keys(rows: List<LegRows.Row>) = rows.map { it.key }

    // --- rows ---

    @Test fun foldedLegIsItsHeadingAlone() =
        assertEquals(listOf("leg1-head"), keys(LegRows.rows(1, folded = true, blocks = listOf(leg))))

    @Test fun expandedLegHasEdgeRowsAroundItsBlocks() = assertEquals(
        listOf("leg0-head", "leg0-top", "leg0-blk-leg", "leg0-blk-d|Genesis 31|1", "leg0-bottom"),
        keys(LegRows.rows(0, false, listOf(leg, next)))
    )

    /** Edge rows never come and go (loading, failure, book end), so nothing at an edge shifts. */
    @Test fun edgeRowsArePresentWhateverTheEdgeState() {
        val rows = LegRows.rows(2, false, listOf(leg))
        assertEquals(listOf("leg2-head", "leg2-top", "leg2-blk-leg", "leg2-bottom"), keys(rows))
        assertEquals(LegRows.Row.Edge(2, down = false), rows[1])
        assertEquals(LegRows.Row.Edge(2, down = true), rows[3])
    }

    @Test fun noWindowShowsTheEmptyLineAndNoEdges() =
        assertEquals(listOf("leg1-head", "leg1-empty"), keys(LegRows.rows(1, false, null)))

    @Test fun windowWithoutVersesCountsAsEmpty() = assertEquals(
        listOf("leg1-head", "leg1-empty"),
        keys(LegRows.rows(1, false, listOf(LegBlock("leg", null, emptyList()))))
    )

    @Test fun keysAreUniqueAcrossLegs() {
        val all = (0..2).flatMap { LegRows.rows(it, false, listOf(leg, next)) }
        assertEquals(all.size, keys(all).toSet().size)
    }

    @Test fun blockKeysStayStableWhenABlockIsPrepended() {
        val before = keys(LegRows.rows(0, false, listOf(leg)))
        val up = LegBlock("u|Genesis 29|1", "Genesis 29", listOf(v(29, 1)))
        val after = keys(LegRows.rows(0, false, listOf(up, leg)))
        assertEquals(before.size + 1, after.size)
        assertTrue(after.containsAll(before))
        assertEquals("leg0-blk-u|Genesis 29|1", after[2])
    }

    @Test fun blockRowCarriesThePreviousBlocksLastChapter() {
        val headed = LegBlock("u|Genesis 29|1", "Genesis 29", listOf(v(29, 34), v(29, 35)))
        val rest = LegBlock("u|Genesis 30|1", null, listOf(v(30, 1), v(30, 13)))
        val blocks = LegRows.rows(0, false, listOf(headed, rest, leg)).filterIsInstance<LegRows.Row.Block>()
        assertEquals(listOf(-1, 29, 30), blocks.map { it.prevChapter })
    }

    // --- edge state ---

    @Test fun edgeStates() {
        assertEquals(LegRows.EdgeState.TRIGGER, LegRows.edgeState(canExtend = true, loading = false, failed = false))
        assertEquals(LegRows.EdgeState.LOADING, LegRows.edgeState(canExtend = true, loading = true, failed = false))
        assertEquals(LegRows.EdgeState.RETRY, LegRows.edgeState(canExtend = true, loading = false, failed = true))
        assertEquals(LegRows.EdgeState.END, LegRows.edgeState(canExtend = false, loading = false, failed = false))
        // A retry starts a load, which clears the failure; loading wins if both are ever seen.
        assertEquals(LegRows.EdgeState.LOADING, LegRows.edgeState(canExtend = true, loading = true, failed = true))
    }

    // --- chapter marks across blocks ---

    /** Genesis 29 (headed), then 30:1-13 without a heading, then the original 30:14-27. */
    @Test fun headlessBlockInANewChapterGetsAChapterMark() {
        assertTrue(LegRows.markBefore(prevChapter = 29, chapter = 30, firstOfHeadedBlock = false))
        assertFalse(LegRows.markBefore(prevChapter = 30, chapter = 30, firstOfHeadedBlock = false))
    }

    @Test fun headedBlockNeedsNoMarkAtItsStart() =
        assertFalse(LegRows.markBefore(prevChapter = 30, chapter = 31, firstOfHeadedBlock = true))

    @Test fun firstVerseOfTheLegIsNeverMarked() =
        assertFalse(LegRows.markBefore(prevChapter = -1, chapter = 44, firstOfHeadedBlock = false))

    @Test fun chapterChangeInsideABlockIsMarked() =
        assertTrue(LegRows.markBefore(prevChapter = 44, chapter = 45, firstOfHeadedBlock = false))

    // --- shouldFire ---

    private fun fire(
        visible: Boolean = true, scrolling: Boolean = true, fwd: Boolean = false, back: Boolean = false,
        down: Boolean = true, can: Boolean = true, busy: Boolean = false, failed: Boolean = false,
        moved: Boolean = true
    ) = LegRows.shouldFire(visible, scrolling, fwd, back, down, can, busy, failed, moved)

    @Test fun bottomFiresOnForwardScroll() = assertTrue(fire(fwd = true, down = true))
    @Test fun topFiresOnBackwardScroll() = assertTrue(fire(back = true, down = false))

    /** Review Focus 4: unfolding a short leg puts both edges on screen with no scroll running. */
    @Test fun visibleWithoutAScrollNeverFires() {
        assertFalse(fire(scrolling = false, fwd = true, down = true))
        assertFalse(fire(scrolling = false, back = true, down = false))
        assertFalse(fire(scrolling = false, fwd = true, back = true, down = true))
    }

    @Test fun wrongDirectionDoesNotFire() {
        assertFalse(fire(fwd = true, down = false))
        assertFalse(fire(back = true, down = true))
    }

    @Test fun scrollWithNoDirectionDoesNotFire() = assertFalse(fire(fwd = false, back = false))
    @Test fun offScreenDoesNotFire() = assertFalse(fire(visible = false, fwd = true))
    @Test fun endOfBookDoesNotFire() = assertFalse(fire(fwd = true, can = false))
    @Test fun failedEdgeWaitsForTheRetryTap() = assertFalse(fire(fwd = true, failed = true))

    @Test fun busyLegWaitsThenFires() {
        assertFalse(fire(fwd = true, busy = true))
        assertFalse(fire(back = true, down = false, busy = true))
        assertTrue(fire(fwd = true, busy = false))
    }

    /** A finger held still after a drag keeps the scroll "in progress" with its old direction. */
    @Test fun heldFingerWithNoNewMovementDoesNotFire() {
        assertFalse(fire(fwd = true, moved = false))
        assertFalse(fire(back = true, down = false, moved = false))
    }

    // --- early top zone ---

    @Test fun topZoneWhenTheEdgeRowIsOnScreen() =
        assertTrue(LegRows.inTopZone(edgeVisible = true, firstVisibleKey = "leg0-head", firstVisibleOffset = 0, viewportHeight = 2000, firstBlockRowKey = "leg0-blk-leg"))

    /** Reading the leg's first block near its top: extend up now, so the block lands above the anchor. */
    @Test fun topZoneWhenTheFirstBlockIsAtTheTopOfTheScreen() =
        assertTrue(LegRows.inTopZone(false, "leg0-blk-leg", firstVisibleOffset = 1500, viewportHeight = 2000, firstBlockRowKey = "leg0-blk-leg"))

    @Test fun noTopZoneDeepInsideTheFirstBlock() =
        assertFalse(LegRows.inTopZone(false, "leg0-blk-leg", firstVisibleOffset = 2000, viewportHeight = 2000, firstBlockRowKey = "leg0-blk-leg"))

    @Test fun noTopZoneInALaterBlockOrAnotherLeg() {
        assertFalse(LegRows.inTopZone(false, "leg0-blk-d|Genesis 31|1", 10, 2000, "leg0-blk-leg"))
        assertFalse(LegRows.inTopZone(false, "leg1-blk-leg", 10, 2000, "leg0-blk-leg"))
        assertFalse(LegRows.inTopZone(false, null, 0, 2000, null))
    }

    // --- keeping the text still when blocks land above it ---

    @Test fun prependedKeysAreTheBlocksBeforeTheOldFirst() {
        val up1 = LegBlock("u|Genesis 30|1", null, listOf(v(30, 1)))
        val up2 = LegBlock("u|Genesis 29|1", "Genesis 29", listOf(v(29, 1)))
        assertEquals(listOf("leg0-blk-u|Genesis 29|1", "leg0-blk-u|Genesis 30|1"),
            LegRows.prependedKeys(0, "leg", listOf(up2, up1, leg)))
    }

    @Test fun appendIsNotAPrepend() = assertNull(LegRows.prependedKeys(0, "leg", listOf(leg, next)))
    @Test fun replacedWindowIsNotAPrepend() = assertNull(LegRows.prependedKeys(0, "gone", listOf(leg)))
    @Test fun firstWindowIsNotAPrepend() = assertNull(LegRows.prependedKeys(0, null, listOf(leg)))

    @Test fun shiftIsTheHeightOfTheNewBlocksOnScreen() {
        val newKeys = listOf("leg0-blk-u|29", "leg0-blk-u|30")
        val visible = listOf<Pair<Any, Int>>("leg0-head" to 80, "leg0-top" to 60, "leg0-blk-u|29" to 900, "leg0-blk-u|30" to 300)
        assertEquals(1200 to newKeys.toSet(), LegRows.prependShift(newKeys, emptySet(), visible))
    }

    /** Landed above the screen (the user scrolled on, or the early zone fired): nothing moved. */
    @Test fun noShiftWhenTheNewBlocksAreOffScreen() =
        assertEquals(0 to emptySet<String>(),
            LegRows.prependShift(listOf("leg0-blk-u|29"), emptySet(), listOf<Pair<Any, Int>>("leg0-blk-leg" to 900)))

    @Test fun eachBlockIsShiftedForOnce() {
        val visible = listOf<Pair<Any, Int>>("leg0-blk-u|29" to 900, "leg0-blk-u|30" to 300)
        assertEquals(300 to setOf("leg0-blk-u|30"),
            LegRows.prependShift(listOf("leg0-blk-u|29", "leg0-blk-u|30"), setOf("leg0-blk-u|29"), visible))
    }

    // --- verseKey ---

    @Test fun verseKeyIncludesLegAndBlock() {
        assertNotEquals(LegRows.verseKey(2, "leg", 4, 1), LegRows.verseKey(2, "d|Tanya, Part V 4|1", 4, 1))
        assertNotEquals(LegRows.verseKey(0, "leg", 44, 19), LegRows.verseKey(1, "leg", 44, 19))
        assertEquals(LegRows.verseKey(0, "leg", 44, 19), LegRows.verseKey(0, "leg", 44, 19))
    }

    @Test fun verseKeyDoesNotCollideWhenTheBlockKeyHasColons() =
        assertNotEquals(LegRows.verseKey(0, "a:1", 2, 3), LegRows.verseKey(0, "a", 1, 2))

    @Test fun verseKeyPrefixSelectsOneBlock() {
        assertTrue(LegRows.verseKey(0, "leg", 30, 14).startsWith(LegRows.blockKeyPrefix(0, "leg")))
        assertFalse(LegRows.verseKey(0, "leg2", 30, 14).startsWith(LegRows.blockKeyPrefix(0, "leg")))
    }
}
