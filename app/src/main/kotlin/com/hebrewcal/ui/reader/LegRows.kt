package com.hebrewcal.ui.reader

import com.hebrewcal.data.LegBlock

/**
 * Pure shaping of the Chitas reader's list rows, one leg at a time, and the decision of when a
 * leg's scroll trigger may ask for more text. Kept out of Compose so it can be unit-tested; the
 * reader only maps each [Row] to a keyed `LazyColumn` item.
 *
 * Every row key carries the leg index, and a block's key carries its [LegBlock.key], so the keys
 * are unique across the list and stable as blocks are added at either end of a leg. Stable keys
 * are what keep the viewport still when a block lands above the visible rows.
 */
internal object LegRows {

    sealed class Row {
        abstract val key: String

        /** The leg's foldable heading. */
        data class Head(val leg: Int) : Row() { override val key = "leg$leg-head" }

        /** "Not available for this day" / "Needs a connection to load": the leg has no text. */
        data class Empty(val leg: Int) : Row() { override val key = "leg$leg-empty" }

        /** Thin sentinel at one end of an expanded leg; a real scroll onto it extends that end. */
        data class Trigger(val leg: Int, val down: Boolean) : Row() {
            override val key = if (down) "leg$leg-bottom" else "leg$leg-top"
        }

        /** Spinner while that end of the leg is being fetched. */
        data class Loading(val leg: Int, val down: Boolean) : Row() {
            override val key = if (down) "leg$leg-bottom-loading" else "leg$leg-top-loading"
        }

        /** "Needs a connection — tap to retry", in place of the trigger after a failed attempt. */
        data class Retry(val leg: Int, val down: Boolean) : Row() {
            override val key = if (down) "leg$leg-bottom-retry" else "leg$leg-top-retry"
        }

        /** One run of verses, shown as one paragraph under its optional chapter heading. */
        data class Block(val leg: Int, val block: LegBlock) : Row() {
            override val key = "leg$leg-blk-${block.key}"
        }
    }

    /**
     * The rows for leg [leg]. Folded, it is the heading alone. Expanded with no [blocks] (no
     * window: a blank ref or a fetch that failed at load) or no verses, it is the heading and the
     * empty-leg line, with no triggers. Otherwise: heading, the top edge, every block, the bottom
     * edge. An edge is a retry line after a failure, else a trigger while that end can extend,
     * plus a spinner while it is extending (the spinner sits between the trigger and the text).
     * [extending] and [failed] are (up, down).
     */
    fun rows(
        leg: Int,
        folded: Boolean,
        blocks: List<LegBlock>?,
        canExtendUp: Boolean,
        canExtendDown: Boolean,
        extending: Pair<Boolean, Boolean>,
        failed: Pair<Boolean, Boolean>
    ): List<Row> {
        val head = Row.Head(leg)
        if (folded) return listOf(head)
        if (blocks == null || blocks.all { it.verses.isEmpty() }) return listOf(head, Row.Empty(leg))
        val out = mutableListOf<Row>(head)
        when {
            failed.first -> out += Row.Retry(leg, down = false)
            canExtendUp -> out += Row.Trigger(leg, down = false)
        }
        if (extending.first) out += Row.Loading(leg, down = false)
        blocks.forEach { if (it.verses.isNotEmpty()) out += Row.Block(leg, it) }
        if (extending.second) out += Row.Loading(leg, down = true)
        when {
            failed.second -> out += Row.Retry(leg, down = true)
            canExtendDown -> out += Row.Trigger(leg, down = true)
        }
        return out
    }

    /**
     * Whether a leg's trigger should ask to extend now. Only a real user scroll may fire it, never
     * its mere presence on screen: it must be [visible], a scroll must be in progress, and that
     * scroll must be heading toward the trigger's end ([scrolledForward] for the bottom trigger,
     * [scrolledBackward] for the top). The end must still be extendable, must not have failed (the
     * retry line handles that), and the leg must not be [busy] in either direction, since the
     * ViewModel ignores any request for a leg that is already extending.
     *
     * "Scrolled forward/backward" is the direction of the user's current drag or fling, counting
     * travel the list could not follow (all its rows already fit on screen, or it is at that end).
     */
    fun shouldFire(
        visible: Boolean,
        scrollInProgress: Boolean,
        scrolledForward: Boolean,
        scrolledBackward: Boolean,
        down: Boolean,
        canExtend: Boolean,
        busy: Boolean,
        failed: Boolean
    ): Boolean =
        visible && scrollInProgress && (if (down) scrolledForward else scrolledBackward) &&
            canExtend && !busy && !failed

    /**
     * Tap-to-flip key for one verse of a Chitas leg. Chapter:verse alone can repeat within one
     * leg (Sefaria's next/prev can cross Tanya parts, whose numbering restarts), so the leg and the
     * block are part of it. The block key is followed by exactly two numbers, so distinct
     * (leg, block, chapter, verse) tuples always give distinct keys.
     */
    fun verseKey(leg: Int, blockKey: String, chapter: Int, verse: Int): String =
        "c:$leg:$blockKey:$chapter:$verse"
}
