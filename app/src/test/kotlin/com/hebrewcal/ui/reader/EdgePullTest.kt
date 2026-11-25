package com.hebrewcal.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class EdgePullTest {
    private fun pull() = EdgePull(thresholdPx = 100f)

    @Test fun aSmallNudgeAtTheBottomDoesNothing() {
        val p = pull()
        assertEquals(0, p.onDrag(0f, -30f, atTop = false, atBottom = true))
    }

    @Test fun aDeliberatePullAtTheBottomExtendsDown() {
        val p = pull()
        var r = 0
        repeat(6) { r = p.onDrag(0f, -20f, atTop = false, atBottom = true).takeIf { it != 0 } ?: r }
        assertEquals(1, r)
    }

    @Test fun aDeliberatePullAtTheTopExtendsUp() {
        val p = pull()
        assertEquals(0, p.onDrag(0f, 60f, atTop = true, atBottom = false))
        assertEquals(-1, p.onDrag(0f, 60f, atTop = true, atBottom = false))
    }

    @Test fun pullingAwayFromTheEdgeDoesNotCount() {
        val p = pull()
        // Dragging down at the bottom is a scroll back into the text, not a pull on the bottom edge.
        assertEquals(0, p.onDrag(0f, 300f, atTop = false, atBottom = true))
    }

    @Test fun oneGestureLoadsOnlyOnce() {
        val p = pull()
        assertEquals(1, p.onDrag(0f, -150f, atTop = false, atBottom = true))
        assertEquals(0, p.onDrag(0f, -150f, atTop = false, atBottom = true))
        p.release()
        assertEquals(1, p.onDrag(0f, -150f, atTop = false, atBottom = true))
    }

    @Test fun theListStillMovingResetsThePull() {
        val p = pull()
        assertEquals(0, p.onDrag(0f, -80f, atTop = false, atBottom = true))
        assertEquals(0, p.onDrag(-40f, 0f, atTop = false, atBottom = false)) // list scrolled: not at the edge
        assertEquals(0, p.onDrag(0f, -80f, atTop = false, atBottom = true))  // needs the full distance again
    }

    @Test fun reversingDirectionStartsOver() {
        val p = pull()
        assertEquals(0, p.onDrag(0f, -80f, atTop = true, atBottom = true))
        assertEquals(0, p.onDrag(0f, 80f, atTop = true, atBottom = true))
        assertEquals(-1, p.onDrag(0f, 30f, atTop = true, atBottom = true))
    }

    @Test fun releasingMidPullForgetsIt() {
        val p = pull()
        assertEquals(0, p.onDrag(0f, -80f, atTop = false, atBottom = true))
        p.release()
        assertEquals(0, p.onDrag(0f, -80f, atTop = false, atBottom = true))
    }
}
