package com.hebrewcal.data

import org.junit.Assert.*
import org.junit.Test

class MizrachCalculatorTest {

    @Test
    fun testNewYorkToJerusalemBearing() {
        // NYC: 40.7128° N, 74.0060° W
        // Great-circle initial bearing from NYC to Jerusalem (31.7780° N, 35.2354° E) is 54.06° (NE),
        // computed independently with the standard forward-azimuth formula.
        val result = MizrachCalculator.calculate(
            fromLat = 40.7128,
            fromLng = -74.0060,
            currentDeviceHeading = 54.0f
        )

        assertEquals("Bearing from NYC to Jerusalem should be ~54°", 54.06f, result.bearingDegrees, 0.5f)
        assertTrue("Distance should be around 5600-5700 miles", result.distanceMiles in 5600.0..5750.0)
        assertTrue("Device heading matches bearing, so should be facing Jerusalem", result.isFacingJerusalem)
    }

    @Test
    fun testAlignmentThreshold() {
        val resultMisaligned = MizrachCalculator.calculate(
            fromLat = 40.7128,
            fromLng = -74.0060,
            currentDeviceHeading = 180.0f // Facing South
        )
        assertFalse("Facing south should not be aligned", resultMisaligned.isFacingJerusalem)
    }

    @Test
    fun testZeroLocation() {
        val result = MizrachCalculator.calculate(0.0, 0.0)
        assertEquals(0f, result.bearingDegrees, 0.01f)
        assertFalse(result.isFacingJerusalem)
    }
}
