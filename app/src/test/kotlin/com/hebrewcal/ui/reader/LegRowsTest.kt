package com.hebrewcal.ui.reader

import com.hebrewcal.data.AliyotPartitioner.TaggedVerse
import com.hebrewcal.data.LegBlock
import com.hebrewcal.data.Verse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegRowsTest {
    private fun v(ch: Int, n: Int) = TaggedVerse(ch, Verse(n, "he$ch:$n", "en$ch:$n"))
    private val leg = LegBlock("leg", null, listOf(v(30, 14), v(30, 15)))
    private val next = LegBlock("d|Genesis 31|1", "Genesis 31", listOf(v(31, 1)), "בראשית לא")
    private val none = false to false

    private fun keys(rows: List<LegRows.Row>) = rows.map { it.key }

    // --- rows ---

    @Test fun foldedLegIsItsHeadingAlone() = assertEquals(
        listOf("leg1-head"),
        keys(LegRows.rows(1, folded = true, blocks = listOf(leg), canExtendUp = true, canExtendDown = true, extending = none, failed = none))
    )

    @Test fun expandedLegHasTriggersAroundItsBlocks() = assertEquals(
        listOf("leg0-head", "leg0-top", "leg0-blk-leg", "leg0-blk-d|Genesis 31|1", "leg0-bottom"),
        keys(LegRows.rows(0, false, listOf(leg, next), canExtendUp = true, canExtendDown = true, extending = none, failed = none))
    )

    @Test fun noTriggerWhereTheBookEnds() = assertEquals(
        listOf("leg2-head", "leg2-blk-leg"),
        keys(LegRows.rows(2, false, listOf(leg), canExtendUp = false, canExtendDown = false, extending = none, failed = none))
    )

    @Test fun noWindowShowsTheEmptyLineAndNoTriggers() = assertEquals(
        listOf("leg1-head", "leg1-empty"),
        keys(LegRows.rows(1, false, null, canExtendUp = false, canExtendDown = false, extending = none, failed = none))
    )

    @Test fun windowWithoutVersesCountsAsEmpty() = assertEquals(
        listOf("leg1-head", "leg1-empty"),
        keys(LegRows.rows(1, false, listOf(LegBlock("leg", null, emptyList())), true, true, none, none))
    )

    @Test fun spinnersSitBetweenTriggerAndText() = assertEquals(
        listOf("leg0-head", "leg0-top", "leg0-top-loading", "leg0-blk-leg", "leg0-bottom-loading", "leg0-bottom"),
        keys(LegRows.rows(0, false, listOf(leg), true, true, extending = true to true, failed = none))
    )

    @Test fun failureReplacesTheTriggerWithRetry() {
        val rows = LegRows.rows(0, false, listOf(leg), true, true, extending = none, failed = true to false)
        assertEquals(listOf("leg0-head", "leg0-top-retry", "leg0-blk-leg", "leg0-bottom"), keys(rows))
        assertEquals(LegRows.Row.Retry(0, down = false), rows[1])
        val down = LegRows.rows(0, false, listOf(leg), true, true, extending = none, failed = false to true)
        assertEquals(LegRows.Row.Retry(0, down = true), down.last())
    }

    @Test fun keysAreUniqueAcrossLegs() {
        val all = (0..2).flatMap { LegRows.rows(it, false, listOf(leg, next), true, true, true to true, none) }
        assertEquals(all.size, keys(all).toSet().size)
    }

    @Test fun blockKeysStayStableWhenABlockIsPrepended() {
        val before = keys(LegRows.rows(0, false, listOf(leg), true, true, none, none))
        val up = LegBlock("u|Genesis 29|1", "Genesis 29", listOf(v(29, 1)))
        val after = keys(LegRows.rows(0, false, listOf(up, leg), true, true, none, none))
        assertTrue(after.containsAll(before))
        assertEquals("leg0-blk-u|Genesis 29|1", after[2])
    }

    // --- shouldFire ---

    private fun fire(
        visible: Boolean = true, scrolling: Boolean = true, fwd: Boolean = false, back: Boolean = false,
        down: Boolean = true, can: Boolean = true, busy: Boolean = false, failed: Boolean = false
    ) = LegRows.shouldFire(visible, scrolling, fwd, back, down, can, busy, failed)

    @Test fun bottomFiresOnForwardScroll() = assertTrue(fire(fwd = true, down = true))
    @Test fun topFiresOnBackwardScroll() = assertTrue(fire(back = true, down = false))

    /** Review Focus 4: unfolding a short leg puts both triggers on screen with no scroll running. */
    @Test fun visibleWithoutAScrollNeverFires() {
        assertFalse(fire(scrolling = false, fwd = true, down = true))
        assertFalse(fire(scrolling = false, back = true, down = false))
        // The flags remember the last scroll after it ends; without a scroll in progress they are ignored.
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

    /** A busy leg is dropped by the ViewModel either way; once free, the same inputs fire. */
    @Test fun busyLegWaitsThenFires() {
        assertFalse(fire(fwd = true, busy = true))
        assertFalse(fire(back = true, down = false, busy = true))
        assertTrue(fire(fwd = true, busy = false))
    }

    // --- verseKey ---

    @Test fun verseKeyIncludesLegAndBlock() {
        // Same chapter:verse in two blocks of one leg (numbering restarts across Tanya parts).
        assertNotEquals(LegRows.verseKey(2, "leg", 4, 1), LegRows.verseKey(2, "d|Tanya, Part V 4|1", 4, 1))
        // Same chapter:verse in two legs (Genesis 44:19 vs Psalm 44:19).
        assertNotEquals(LegRows.verseKey(0, "leg", 44, 19), LegRows.verseKey(1, "leg", 44, 19))
        assertEquals(LegRows.verseKey(0, "leg", 44, 19), LegRows.verseKey(0, "leg", 44, 19))
    }

    @Test fun verseKeyDoesNotCollideWhenTheBlockKeyHasColons() =
        assertNotEquals(LegRows.verseKey(0, "a:1", 2, 3), LegRows.verseKey(0, "a", 1, 2))
}
