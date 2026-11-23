package com.hebrewcal.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FitFontSpTest {
    // Fake row: total width grows linearly with font size.
    private val total = { sp: Float -> sp * 6f }

    @Test fun usesCeilingWhenItFits() = assertEquals(15f, fitFontSp(100f, total)!!, 0f)

    @Test fun scalesDownUntilItFits() = assertEquals(12f, fitFontSp(72f, total)!!, 0f)

    @Test fun usesFloorWhenExactlyFits() = assertEquals(11f, fitFontSp(66f, total)!!, 0f)

    @Test fun nullWhenNothingFits() = assertNull(fitFontSp(60f, total))

    @Test fun halfStepGranularity() = assertEquals(12.5f, fitFontSp(75f, total)!!, 0f)

    @Test fun customMaxAndMin() {
        assertEquals(20f, fitFontSp(1000f, total, maxSp = 20f, minSp = 8f)!!, 0f)
        assertEquals(8f, fitFontSp(48f, total, maxSp = 20f, minSp = 8f)!!, 0f)
        assertNull(fitFontSp(47f, total, maxSp = 20f, minSp = 8f))
    }

    @Test fun proportionalRowFitsWhereEqualWeightsDoNot() {
        // Four short labels (4 units/sp) and one long one (11 units/sp), 8 padding, row = 400px.
        val units = listOf(4f, 4f, 4f, 4f, 11f)
        val proportional = { sp: Float -> units.sumOf { (it * sp + 8f).toDouble() }.toFloat() }
        val equal = { sp: Float -> 5f * (units.max() * sp + 8f) }
        assertNotNull(fitFontSp(400f, proportional))
        assertNull(fitFontSp(400f, equal))
    }
}
