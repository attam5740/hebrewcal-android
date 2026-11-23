package com.hebrewcal.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FitFontSpTest {
    // Fake measurer: width grows linearly with font size.
    private val width = { sp: Float -> sp * 6f }

    @Test fun usesMaxWhenItFits() = assertEquals(15f, fitFontSp(100f, width)!!, 0f)

    @Test fun scalesDownUntilItFits() = assertEquals(12f, fitFontSp(72f, width)!!, 0f)

    @Test fun usesFloorWhenExactlyFits() = assertEquals(11f, fitFontSp(66f, width)!!, 0f)

    @Test fun nullWhenNothingFitsSoCallerUsesTwoRows() = assertNull(fitFontSp(60f, width))
}
