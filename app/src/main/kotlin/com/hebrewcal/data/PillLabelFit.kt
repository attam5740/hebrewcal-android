package com.hebrewcal.data

/** A reader pill's text: a [title] and an optional [detail], rendered `"title · detail"`. */
data class PillSpec(val title: String, val detail: String?) {
    /** The full, unshortened label. */
    val full: String
        get() = if (detail.isNullOrBlank()) title else "$title${PillLabelFit.SEPARATOR}$detail"
}

/**
 * Fits a row of reader pills into a pixel budget without clipping the last pill.
 *
 * Pure (text measurement is injected) so it is unit-testable. The rule: if every
 * full label fits, nothing changes. Otherwise the currently widest label is
 * shortened a character at a time, first by cutting its *title* from the end
 * (the detail, e.g. the chapter range, stays intact), and only when the title is
 * down to one character plus an ellipsis by middle-ellipsizing the whole string.
 * A pill without a detail (Chitas) is only touched when no other pill can give.
 * It always prefers returning too little text over overflowing.
 */
object PillLabelFit {
    const val SEPARATOR = " · "
    private const val ELLIPSIS = "…"

    /**
     * @param budgetPx total horizontal room for the whole pill row
     * @param perPillChromePx horizontal padding of one pill (left + right)
     * @param gapPx width of one pill's leading margin
     * @param gapCount how many pills actually carry a leading margin (see [leadingMarginCount]);
     *   defaults to pills-1, which is only right when the first pill has no margin
     * @param measure width in px of a string in the pill's font
     * @return one display string per pill, same order
     */
    fun fit(
        pills: List<PillSpec>,
        budgetPx: Float,
        perPillChromePx: Float,
        gapPx: Float,
        gapCount: Int = pills.size - 1,
        measure: (String) -> Float
    ): List<String> {
        if (pills.isEmpty()) return emptyList()
        val textBudget = budgetPx - pills.size * perPillChromePx - gapCount.coerceAtLeast(0) * gapPx

        // Per pill, every candidate from the full label down to a lone ellipsis.
        val candidates = pills.map { candidatesFor(it) }
        val step = IntArray(pills.size)
        val widths = FloatArray(pills.size) { measure(candidates[it][0]) }

        while (widths.sum() > textBudget) {
            val shrinkable = pills.indices.filter { step[it] < candidates[it].lastIndex }
            if (shrinkable.isEmpty()) break
            val withDetail = shrinkable.filter { !pills[it].detail.isNullOrBlank() }
            val pool = withDetail.ifEmpty { shrinkable }
            val i = pool.maxByOrNull { widths[it] }!!
            step[i]++
            widths[i] = measure(candidates[i][step[i]])
        }
        return pills.indices.map { candidates[it][step[it]] }
    }

    /**
     * The notification layouts put `marginStart` on the Parsha and Chitas pills,
     * never on Tehillim, so a margin belongs to a specific pill, not a position.
     */
    fun leadingMarginCount(hasTehillim: Boolean, hasParsha: Boolean, hasChitas: Boolean): Int =
        (if (hasParsha) 1 else 0) + (if (hasChitas) 1 else 0)

    private fun candidatesFor(pill: PillSpec): List<String> {
        val full = pill.full
        val out = mutableListOf(full)
        val detail = pill.detail
        if (!detail.isNullOrBlank()) {
            // Stage 1: cut the title from its end, keep " · detail" intact.
            for (keep in pill.title.length - 1 downTo 1) {
                out += pill.title.take(keep).trimEnd() + ELLIPSIS + SEPARATOR + detail
            }
        }
        // Stage 2: middle-ellipsize the whole original string (keeps both ends).
        for (n in out.last().length - 1 downTo 1) out += middleEllipsize(full, n)
        return out
    }

    /** [s] reduced to [n] chars with the ellipsis in the middle; n >= 1. */
    private fun middleEllipsize(s: String, n: Int): String {
        if (n >= s.length) return s
        if (n <= 1) return ELLIPSIS
        val tail = (n - 1) / 2
        val head = n - 1 - tail
        return s.take(head) + ELLIPSIS + s.takeLast(tail)
    }
}
