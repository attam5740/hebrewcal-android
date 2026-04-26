package com.hebrewcal.data

data class CityData(
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double
) {
    val displayName: String get() = "$name, $country"
}

object CityDatabase {

    val cities: List<CityData> = listOf(
        // United States
        CityData("New York City",    "USA",           40.7128,  -74.0060),
        CityData("Los Angeles",      "USA",           34.0522, -118.2437),
        CityData("Miami",            "USA",           25.7617,  -80.1918),
        CityData("Chicago",          "USA",           41.8781,  -87.6298),
        CityData("Philadelphia",     "USA",           39.9526,  -75.1652),
        CityData("Boston",           "USA",           42.3601,  -71.0589),
        CityData("Washington DC",    "USA",           38.9072,  -77.0369),
        CityData("San Francisco",    "USA",           37.7749, -122.4194),
        CityData("Baltimore",        "USA",           39.2904,  -76.6122),
        CityData("Boca Raton",       "USA",           26.3683,  -80.1289),
        CityData("Fort Lauderdale",  "USA",           26.1224,  -80.1373),
        CityData("West Palm Beach",  "USA",           26.7153,  -80.0534),
        CityData("Houston",          "USA",           29.7604,  -95.3698),
        CityData("Dallas",           "USA",           32.7767,  -96.7970),
        CityData("Atlanta",          "USA",           33.7490,  -84.3880),
        CityData("Phoenix",          "USA",           33.4484, -112.0740),
        CityData("Denver",           "USA",           39.7392, -104.9903),
        CityData("Seattle",          "USA",           47.6062, -122.3321),
        CityData("San Diego",        "USA",           32.7157, -117.1611),
        CityData("Portland",         "USA",           45.5051, -122.6750),
        CityData("Las Vegas",        "USA",           36.1699, -115.1398),
        CityData("Detroit",          "USA",           42.3314,  -83.0458),
        CityData("Cleveland",        "USA",           41.4993,  -81.6944),
        CityData("Minneapolis",      "USA",           44.9778,  -93.2650),
        CityData("St. Louis",        "USA",           38.6270,  -90.1994),
        CityData("Pittsburgh",       "USA",           40.4406,  -79.9959),
        CityData("Cincinnati",       "USA",           39.1031,  -84.5120),
        CityData("Columbus",         "USA",           39.9612,  -82.9988),
        CityData("Hartford",         "USA",           41.7658,  -72.6851),
        CityData("New Haven",        "USA",           41.3083,  -72.9279),
        CityData("Providence",       "USA",           41.8240,  -71.4128),
        CityData("Rochester",        "USA",           43.1566,  -77.6088),
        CityData("Buffalo",          "USA",           42.8864,  -78.8784),
        CityData("Richmond",         "USA",           37.5407,  -77.4360),
        CityData("Orlando",          "USA",           28.5383,  -81.3792),
        CityData("Tampa",            "USA",           27.9506,  -82.4572),
        CityData("Albany",           "USA",           42.6526,  -73.7562),
        CityData("Nashville",        "USA",           36.1627,  -86.7816),

        // Israel
        CityData("Jerusalem",        "Israel",        31.7683,   35.2137),
        CityData("Tel Aviv",         "Israel",        32.0853,   34.7818),
        CityData("Haifa",            "Israel",        32.7940,   35.0176),
        CityData("Bnei Brak",        "Israel",        32.0833,   34.8333),
        CityData("Beersheba",        "Israel",        31.2518,   34.7915),
        CityData("Netanya",          "Israel",        32.3286,   34.8597),
        CityData("Ashdod",           "Israel",        31.8044,   34.6553),
        CityData("Petah Tikva",      "Israel",        32.0870,   34.8878),
        CityData("Rishon LeZion",    "Israel",        31.9642,   34.8008),
        CityData("Rehovot",          "Israel",        31.8969,   34.8097),
        CityData("Holon",            "Israel",        32.0111,   34.7792),
        CityData("Bat Yam",          "Israel",        32.0172,   34.7506),
        CityData("Ramat Gan",        "Israel",        32.0833,   34.8167),
        CityData("Ashkelon",         "Israel",        31.6659,   34.5710),
        CityData("Herzliya",         "Israel",        32.1644,   34.8432),
        CityData("Kfar Saba",        "Israel",        32.1790,   34.9075),
        CityData("Modiin",           "Israel",        31.8993,   35.0108),
        CityData("Nahariya",         "Israel",        33.0060,   35.0981),
        CityData("Tiberias",         "Israel",        32.7940,   35.5300),
        CityData("Tzfat",            "Israel",        32.9658,   35.4960),

        // Canada
        CityData("Toronto",          "Canada",        43.6532,  -79.3832),
        CityData("Montreal",         "Canada",        45.5017,  -73.5673),
        CityData("Vancouver",        "Canada",        49.2827, -123.1207),
        CityData("Ottawa",           "Canada",        45.4215,  -75.6919),
        CityData("Winnipeg",         "Canada",        49.8951,  -97.1384),

        // United Kingdom
        CityData("London",           "UK",            51.5074,   -0.1278),
        CityData("Manchester",       "UK",            53.4808,   -2.2426),
        CityData("Leeds",            "UK",            53.8008,   -1.5491),
        CityData("Glasgow",          "UK",            55.8642,   -4.2518),

        // Western Europe
        CityData("Paris",            "France",        48.8566,    2.3522),
        CityData("Strasbourg",       "France",        48.5734,    7.7521),
        CityData("Lyon",             "France",        45.7640,    4.8357),
        CityData("Antwerp",          "Belgium",       51.2194,    4.4025),
        CityData("Brussels",         "Belgium",       50.8503,    4.3517),
        CityData("Amsterdam",        "Netherlands",   52.3676,    4.9041),
        CityData("Berlin",           "Germany",       52.5200,   13.4050),
        CityData("Frankfurt",        "Germany",       50.1109,    8.6821),
        CityData("Munich",           "Germany",       48.1351,   11.5820),
        CityData("Vienna",           "Austria",       48.2082,   16.3738),
        CityData("Zurich",           "Switzerland",   47.3769,    8.5417),
        CityData("Geneva",           "Switzerland",   46.2044,    6.1432),
        CityData("Rome",             "Italy",         41.9028,   12.4964),
        CityData("Milan",            "Italy",         45.4642,    9.1900),
        CityData("Stockholm",        "Sweden",        59.3293,   18.0686),
        CityData("Copenhagen",       "Denmark",       55.6761,   12.5683),

        // Eastern Europe
        CityData("Budapest",         "Hungary",       47.4979,   19.0402),
        CityData("Prague",           "Czech Republic",50.0755,   14.4378),
        CityData("Warsaw",           "Poland",        52.2297,   21.0122),
        CityData("Krakow",           "Poland",        50.0647,   19.9450),
        CityData("Kiev",             "Ukraine",       50.4501,   30.5234),
        CityData("Odessa",           "Ukraine",       46.4825,   30.7233),
        CityData("Moscow",           "Russia",        55.7558,   37.6173),
        CityData("St. Petersburg",   "Russia",        59.9343,   30.3351),

        // Latin America
        CityData("Buenos Aires",     "Argentina",    -34.6037,  -58.3816),
        CityData("Sao Paulo",        "Brazil",       -23.5505,  -46.6333),
        CityData("Rio de Janeiro",   "Brazil",       -22.9068,  -43.1729),
        CityData("Mexico City",      "Mexico",        19.4326,  -99.1332),
        CityData("Guadalajara",      "Mexico",        20.6597, -103.3496),
        CityData("Montevideo",       "Uruguay",      -34.9011,  -56.1645),
        CityData("Santiago",         "Chile",        -33.4489,  -70.6693),
        CityData("Lima",             "Peru",         -12.0464,  -77.0428),
        CityData("Bogota",           "Colombia",       4.7110,  -74.0721),
        CityData("Panama City",      "Panama",         8.9936,  -79.5197),
        CityData("Caracas",          "Venezuela",     10.4806,  -66.9036),

        // Australia & Pacific
        CityData("Melbourne",        "Australia",    -37.8136,  144.9631),
        CityData("Sydney",           "Australia",    -33.8688,  151.2093),

        // Africa & Middle East
        CityData("Johannesburg",     "South Africa", -26.2041,   28.0473),
        CityData("Cape Town",        "South Africa", -33.9249,   18.4241),
        CityData("Casablanca",       "Morocco",       33.5731,   -7.5898),
        CityData("Baku",             "Azerbaijan",    40.4093,   49.8671)
    )

    fun search(query: String): List<CityData> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        return cities.filter { city ->
            city.name.lowercase().contains(q) || city.country.lowercase().contains(q)
        }.take(8)
    }

    fun findByDisplayName(displayName: String): CityData? =
        cities.firstOrNull { it.displayName == displayName }

    fun findNearest(latitude: Double, longitude: Double): CityData =
        cities.minByOrNull { city ->
            val dLat = city.latitude - latitude
            val dLng = city.longitude - longitude
            dLat * dLat + dLng * dLng
        } ?: cities.first()
}
