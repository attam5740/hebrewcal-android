package com.hebrewcal.data

import kotlin.math.*

data class MizrachResult(
    val bearingDegrees: Float,       // 0 to 360 degrees forward azimuth towards Jerusalem
    val distanceKm: Double,          // distance along great circle in kilometers
    val distanceMiles: Double,       // distance along great circle in miles
    val cardinalDirection: String,  // e.g. "ENE" or "מזרח"
    val isFacingJerusalem: Boolean   // true when current heading aligns within threshold
)

object MizrachCalculator {

    // Har HaBayit (Temple Mount / Holy of Holies in Jerusalem)
    const val JERUSALEM_LAT = 31.7780
    const val JERUSALEM_LNG = 35.2354
    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Calculates great circle initial bearing from [fromLat], [fromLng] towards Jerusalem.
     */
    fun calculate(
        fromLat: Double,
        fromLng: Double,
        currentDeviceHeading: Float? = null,
        alignmentThresholdDegrees: Float = 4.0f,
        isHebrew: Boolean = false
    ): MizrachResult {
        if (fromLat == 0.0 && fromLng == 0.0) {
            return MizrachResult(0f, 0.0, 0.0, if (isHebrew) "מזרח" else "East", false)
        }

        val lat1 = Math.toRadians(fromLat)
        val lon1 = Math.toRadians(fromLng)
        val lat2 = Math.toRadians(JERUSALEM_LAT)
        val lon2 = Math.toRadians(JERUSALEM_LNG)

        val dLon = lon2 - lon1

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

        var initialBearing = Math.toDegrees(atan2(y, x))
        initialBearing = (initialBearing + 360.0) % 360.0

        // Haversine distance
        val dLat = lat2 - lat1
        val a = sin(dLat / 2.0).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2.0).pow(2)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        val distanceKm = EARTH_RADIUS_KM * c
        val distanceMiles = distanceKm * 0.621371

        val bearingFloat = initialBearing.toFloat()

        val isFacing = if (currentDeviceHeading != null) {
            val diff = abs((currentDeviceHeading - bearingFloat + 540) % 360 - 180)
            diff <= alignmentThresholdDegrees
        } else {
            false
        }

        val cardinal = getCardinal(bearingFloat, isHebrew)

        return MizrachResult(
            bearingDegrees = bearingFloat,
            distanceKm = distanceKm,
            distanceMiles = distanceMiles,
            cardinalDirection = cardinal,
            isFacingJerusalem = isFacing
        )
    }

    private fun getCardinal(bearing: Float, isHebrew: Boolean): String {
        val directionsEn = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val directionsHe = arrayOf("צפון", "צפון-מזרח", "מזרח", "דרום-מזרח", "דרום", "דרום-מערב", "מערב", "צפון-מערב")

        val index16 = ((bearing + 11.25) / 22.5).toInt() % 16
        if (!isHebrew) return directionsEn[index16]

        val index8 = ((bearing + 22.5) / 45.0).toInt() % 8
        return directionsHe[index8]
    }
}
