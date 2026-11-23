package com.hebrewcal.ui.reader

import com.hebrewcal.data.LegBlock

/**
 * Pure shaping of the Chitas reader's list rows, one leg at a time, and the decisions behind its
 * per-leg scrolling: when an edge may ask for more text, and how far to scroll back when text
 * lands above what is being read. Kept out of Compose so it can be unit-tested; the reader maps
 * each [Row] to a keyed `LazyColumn` item.
 *
 * Every row key carries the leg index, and a block's key carries its [LegBlock.key], so the keys
 * are unique across the list and stable as blocks are added at either end of a leg.
 */
internal object LegRows {

    sealed class Row {
        abstract val key: String

        /** The leg's foldable heading. */
        data class Head(val leg: Int) : Row() { override val key = "leg$leg-head" }

        /** "Not available for this day" / "Needs a connection to load": the leg has no text. */
        data class Empty(val leg: Int) : Row() { override val key = "leg$leg-empty" }

        /**
         * A fixed-height row at one end of an expanded leg. It is always there, whatever the
         * edge's [EdgeState] (it shows the spinner or the retry line inside itself), so no row
         * is ever inserted or removed at an edge and nothing next to it jumps.
         */
        data class Edge(val leg: Int, val down: Boolean) : Row() {
            override val key = if (down) "leg$leg-bottom" else "leg$leg-top"
        }

        /**
         * One run of verses, shown as one paragraph under its optional chapter heading.
         * [prevChapter] is the last chapter of the leg's previous block (-1 for the first), so a
         * block without a heading that starts a new chapter still gets its chapter mark.
         */
        data class Block(val leg: Int, val block: LegBlock, val prevChapter: Int) : Row() {
            override val key = blockRowKey(leg, block.key)
        }
    }

    fun blockRowKey(leg: Int, blockKey: String) = "leg$leg-blk-$blockKey"

    /**
     * The rows for leg [leg]. Folded, it is the heading alone. Expanded with no [blocks] (no
     * window: a blank ref or a fetch that failed at load) or no verses, it is the heading and the
     * empty-leg line, with no edges. Otherwise: heading, top edge, every block with verses,
     * bottom edge.
     */
    fun rows(leg: Int, folded: Boolean, blocks: List<LegBlock>?): List<Row> {
        val head = Row.Head(leg)
        if (folded) return listOf(head)
        val shown = blocks.orEmpty().filter { it.verses.isNotEmpty() }
        if (shown.isEmpty()) return listOf(head, Row.Empty(leg))
        val out = mutableListOf<Row>(head, Row.Edge(leg, down = false))
        var prev = -1
        shown.forEach { b ->
            out += Row.Block(leg, b, prev)
            prev = b.verses.last().chapter
        }
        out += Row.Edge(leg, down = true)
        return out
    }

    enum class EdgeState { TRIGGER, LOADING, RETRY, END }

    /** What an edge row shows: a spinner while loading, the retry line after a failure, else an
     * idle trigger while the leg can extend that way, else nothing (the book ends there). */
    fun edgeState(canExtend: Boolean, loading: Boolean, failed: Boolean): EdgeState = when {
        loading -> EdgeState.LOADING
        failed -> EdgeState.RETRY
        canExtend -> EdgeState.TRIGGER
        else -> EdgeState.END
    }

    /**
     * Whether to draw a chapter mark (‹Ch. N›) before a verse of [chapter], given the chapter of
     * the leg's previous shown verse ([prevChapter], -1 at the leg's start). The first verse of a
     * block with a heading needs none: the heading names the chapter.
     */
    fun markBefore(prevChapter: Int, chapter: Int, firstOfHeadedBlock: Boolean): Boolean =
        prevChapter != -1 && chapter != prevChapter && !firstOfHeadedBlock

    /**
     * Whether a leg's edge should ask to extend now. Only a real user scroll may fire it, never
     * its mere presence on screen: it must be [visible], a scroll must be in progress, that scroll
     * must be heading toward the edge ([scrolledForward] for the bottom, [scrolledBackward] for
     * the top), and the scroll must have moved since this check began ([movedSinceStart]), so a
     * finger held still after a drag does not fire it. The end must still be extendable, must not
     * have failed (the retry line handles that), and the leg must not be [busy] in either
     * direction, since the ViewModel ignores any request for a leg that is already extending.
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
        failed: Boolean,
        movedSinceStart: Boolean
    ): Boolean =
        visible && scrollInProgress && movedSinceStart && (if (down) scrolledForward else scrolledBackward) &&
            canExtend && !busy && !failed

    /**
     * The "visible" input of the top edge's [shouldFire]: its edge row is on screen, or the leg's
     * first block is the first row on screen and its top is less than a screen away. The second
     * case asks for the previous text while the block being read is still the list's anchor, so
     * the new block lands above the screen and nothing visible moves.
     */
    fun inTopZone(
        edgeVisible: Boolean,
        firstVisibleKey: Any?,
        firstVisibleOffset: Int,
        viewportHeight: Int,
        firstBlockRowKey: String?
    ): Boolean =
        edgeVisible || (firstBlockRowKey != null && firstVisibleKey == firstBlockRowKey &&
            firstVisibleOffset < viewportHeight)

    /**
     * The row keys of the blocks that were prepended to leg [leg], given the key of its first block
     * before ([oldFirstKey]) and its blocks now: those before the old first block. Null when it was
     * not a prepend (no previous window, the old first block is gone, or it is still first).
     */
    fun prependedKeys(leg: Int, oldFirstKey: String?, blocks: List<LegBlock>): List<String>? {
        if (oldFirstKey == null) return null
        val shown = blocks.filter { it.verses.isNotEmpty() }
        val at = shown.indexOfFirst { it.key == oldFirstKey }
        if (at <= 0) return null
        return shown.subList(0, at).map { blockRowKey(leg, it.key) }
    }

    /**
     * How far to scroll forward so that blocks just prepended to a leg do not push the text being
     * read down the screen, and which of [newKeys] that covers. [visible] is the laid-out rows on
     * screen (key to height). A new block on screen was inserted below the list's anchor (the
     * leg's heading or top edge), so it pushed everything below it down by its height; one off
     * screen was inserted above the anchor (or below the screen) and moved nothing. Keys already
     * in [done] are not counted twice.
     */
    fun prependShift(newKeys: Collection<String>, done: Set<String>, visible: List<Pair<Any, Int>>): Pair<Int, Set<String>> {
        val hit = visible.filter { (k, _) -> k is String && k in newKeys && k !in done }
        return hit.sumOf { it.second } to hit.map { it.first as String }.toSet()
    }

    /**
     * Tap-to-flip key for one verse of a Chitas leg. Chapter:verse alone can repeat within one
     * leg (Sefaria's next/prev can cross Tanya parts, whose numbering restarts), so the leg and the
     * block are part of it. The block key is followed by exactly two numbers, so distinct
     * (leg, block, chapter, verse) tuples always give distinct keys.
     */
    fun verseKey(leg: Int, blockKey: String, chapter: Int, verse: Int): String =
        "${blockKeyPrefix(leg, blockKey)}$chapter:$verse"

    /** The start every [verseKey] of that block shares, and no other block's does. */
    fun blockKeyPrefix(leg: Int, blockKey: String): String = "c:$leg:$blockKey:"
}
