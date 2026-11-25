package com.hebrewcal.ui.reader

import kotlin.math.sign

/**
 * Decides when dragging past the end of the reader's text means "load the next/previous chapter".
 * A flick or a stray touch at the edge must not: the finger has to be pulled [thresholdPx] against the
 * edge, in one direction, and one pull loads one chapter (the next needs the finger lifted first).
 *
 * Fed from the list's nested-scroll drag deltas: [onDrag] for each, [release] when the finger lifts.
 */
internal class EdgePull(private val thresholdPx: Float) {
    private var pull = 0f
    private var armed = true

    /** +1: extend down (drag up at the bottom), -1: extend up (drag down at the top), 0: nothing yet. */
    fun onDrag(consumedY: Float, availableY: Float, atTop: Boolean, atBottom: Boolean): Int {
        // The list is still moving under the finger: it has not reached the edge, so nothing is pulled.
        if (consumedY != 0f) { pull = 0f; return 0 }
        if (availableY == 0f) return 0
        if (pull != 0f && sign(availableY) != sign(pull)) pull = 0f
        pull += availableY
        val result = when {
            pull <= -thresholdPx && atBottom -> 1
            pull >= thresholdPx && atTop -> -1
            else -> 0
        }
        if (result == 0) return 0
        pull = 0f
        if (!armed) return 0
        armed = false
        return result
    }

    /** The finger lifted: the next pull starts from nothing and may load again. */
    fun release() { pull = 0f; armed = true }
}
