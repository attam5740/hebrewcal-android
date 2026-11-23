package com.hebrewcal.data

/**
 * One run of a Chitas leg's verses. [heading] is null for the leg's original slice and for the
 * rest of a chapter the leg had already started; a newly reached chapter carries its Sefaria ref
 * ("Genesis 31") as [heading] and its Hebrew ref as [headingHe]. [key] is unique within the leg
 * and stable across extensions, for keyed list rows.
 */
data class LegBlock(
    val key: String,
    val heading: String?,
    val verses: List<AliyotPartitioner.TaggedVerse>,
    val headingHe: String? = null
)

/**
 * One Chitas leg as a verse-bounded slice of a book that grows chapter by chapter, up or down.
 * Pure and immutable: the caller fetches [nextFetch] and folds the result in with [applied].
 *
 * Each end of the window is an [Edge]: the chapter it sits in, the outermost verse shown, whether
 * that chapter is known to be shown out to that end ([Edge.complete]), and the neighbouring
 * chapter's ref once known. Sefaria's `next`/`prev` are relative to the requested range, not to
 * the verses shown (a trimmed lesson `4:50-5:1` shows only chapter 4 yet reports next = 6), so a
 * neighbour is read only from a whole-chapter response: the leg's own text when its ref is
 * whole-chapter at that end, otherwise the response for the edge chapter fetched to complete it.
 */
class LegWindow private constructor(
    val blocks: List<LegBlock>,
    private val up: Edge?,
    private val down: Edge?
) {
    private data class Edge(
        /** Ref of the edge chapter as a whole ("Genesis 30"), for completing it. */
        val chapterRef: String,
        val chapter: Int,
        /** First shown verse (up edge) or last shown verse (down edge). */
        val verse: Int,
        /** True once the edge chapter is known to be shown out to this end. */
        val complete: Boolean,
        /** True once [neighbour] was read from a trustworthy (whole-chapter) response. */
        val neighbourKnown: Boolean,
        /** The adjacent chapter's ref; null with [neighbourKnown] means the book ends here. */
        val neighbour: String?
    )

    val canExtendUp: Boolean get() = nextFetch(false) != null
    val canExtendDown: Boolean get() = nextFetch(true) != null

    /**
     * The chapter ref to fetch next in that direction: the edge chapter while it may be
     * incomplete or its neighbour is still unknown, else the neighbouring chapter; null when
     * the book ends there (or the leg's ref could not be understood).
     */
    fun nextFetch(down: Boolean): String? {
        val e = edge(down) ?: return null
        return if (!e.complete || !e.neighbourKnown) e.chapterRef else e.neighbour
    }

    /**
     * Folds in [fetched], the response for [fetchedRef], which must be what [nextFetch] returned
     * for this direction (anything else returns this window unchanged, the same instance).
     * Completing the edge chapter adds only the verses beyond the shown edge, as a block without a
     * heading — possibly none, when the chapter was already complete; the caller then sees
     * [blocksAdded] == 0 and goes on to the neighbour. A neighbouring chapter is added whole, with
     * a heading. Verse and chapter numbers are kept as Sefaria gives them.
     */
    fun applied(down: Boolean, fetchedRef: String, fetched: SefariaText): LegWindow {
        val e = edge(down) ?: return this
        val expected = nextFetch(down) ?: return this
        if (fetchedRef != expected) return this
        val neighbour = if (down) fetched.next else fetched.prev
        if (fetchedRef == e.chapterRef && (!e.complete || !e.neighbourKnown)) {
            val ch = fetched.chapters.firstOrNull { it.number == e.chapter } ?: return this
            val extra = ch.verses.filter { if (down) it.num > e.verse else it.num < e.verse }
            val outer = if (down) ch.verses.maxOfOrNull { it.num } else ch.verses.minOfOrNull { it.num }
            val newEdge = e.copy(
                verse = if (down) maxOf(e.verse, outer ?: e.verse) else minOf(e.verse, outer ?: e.verse),
                complete = true, neighbourKnown = true, neighbour = neighbour
            )
            if (extra.isEmpty()) return with(down, newEdge, null)
            val block = LegBlock(key(down, fetchedRef, extra.first().num), null, extra.map { AliyotPartitioner.TaggedVerse(ch.number, it) })
            return with(down, newEdge, block)
        }
        // The neighbouring chapter, fetched whole: its own next/prev are trustworthy.
        val chapters = fetched.chapters.filter { it.verses.isNotEmpty() }
        if (chapters.isEmpty()) return this
        val tagged = chapters.flatMap { c -> c.verses.map { AliyotPartitioner.TaggedVerse(c.number, it) } }
        val edgeChapter = if (down) chapters.last() else chapters.first()
        val newEdge = Edge(
            chapterRef = fetchedRef, chapter = edgeChapter.number,
            verse = if (down) edgeChapter.verses.last().num else edgeChapter.verses.first().num,
            complete = true, neighbourKnown = true, neighbour = neighbour
        )
        val block = LegBlock(key(down, fetchedRef, tagged.first().verse.num), fetchedRef, tagged,
            fetched.heRef.ifBlank { null })
        return with(down, newEdge, block)
    }

    /** How many blocks this window has gained over [before] (0 when an extension step added nothing). */
    fun blocksAdded(before: LegWindow): Int = blocks.size - before.blocks.size

    private fun edge(down: Boolean) = if (down) this.down else up

    private fun with(down: Boolean, e: Edge, block: LegBlock?): LegWindow {
        val b = when {
            block == null -> blocks
            down -> blocks + block
            else -> listOf(block) + blocks
        }
        return if (down) LegWindow(b, up, e) else LegWindow(b, e, this.down)
    }

    private fun key(down: Boolean, ref: String, firstVerse: Int) = (if (down) "d|" else "u|") + "$ref|$firstVerse"

    companion object {
        // "<prefix><space or dot><spec>": "Psalms.119.1-96", "Genesis 30:14-30:27", "Tanya, Part V; Kuntres Acharon 4:35-4:40".
        private val REF = Regex("^(.+?)[ .](\\d+(?:[.:]\\d+)?(?:-\\d+(?:[.:]\\d+)?)?)$")
        private val POINT = Regex("^(\\d+)(?:[.:](\\d+))?$")

        /**
         * The window for a leg fetched with [ref], showing [text] (after any trim). An end of the
         * ref that names a verse — or, when [trimmed], the last end, whose final segment was
         * dropped — may stop mid-chapter, so it starts incomplete with its neighbour unknown. A
         * whole-chapter end (`Psalms.44-48`) starts complete with its neighbour from [text].
         * An unrecognised ref gives a window that shows [text] but cannot extend.
         */
        fun start(ref: String, text: SefariaText, trimmed: Boolean = false): LegWindow {
            val chapters = text.chapters.filter { it.verses.isNotEmpty() }
            val original = LegBlock("leg", null,
                chapters.flatMap { c -> c.verses.map { AliyotPartitioner.TaggedVerse(c.number, it) } })
            val m = REF.matchEntire(ref.trim())
            if (m == null || chapters.isEmpty()) return LegWindow(listOf(original), null, null)
            val prefix = m.groupValues[1].trim()
            val spec = m.groupValues[2]
            val startSpec = spec.substringBefore('-')
            val endSpec = spec.substringAfter('-', startSpec)
            val startPoint = POINT.matchEntire(startSpec) ?: return LegWindow(listOf(original), null, null)
            val endPoint = POINT.matchEntire(endSpec) ?: return LegWindow(listOf(original), null, null)
            val startHasVerse = startPoint.groupValues[2].isNotEmpty()
            // "119.1-96": a bare end after a verse-bearing start is a verse in the same chapter
            // (and a single-segment ref, "12:3", is its own end).
            val endHasVerse = endPoint.groupValues[2].isNotEmpty() || startHasVerse
            val upBounded = startHasVerse
            val downBounded = endHasVerse || trimmed

            val first = chapters.first()
            val last = chapters.last()
            val upEdge = Edge(
                chapterRef = "$prefix ${first.number}", chapter = first.number, verse = first.verses.first().num,
                complete = !upBounded, neighbourKnown = !upBounded, neighbour = if (upBounded) null else text.prev
            )
            val downEdge = Edge(
                chapterRef = "$prefix ${last.number}", chapter = last.number, verse = last.verses.last().num,
                complete = !downBounded, neighbourKnown = !downBounded, neighbour = if (downBounded) null else text.next
            )
            return LegWindow(listOf(original), upEdge, downEdge)
        }
    }
}
