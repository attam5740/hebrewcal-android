package com.hebrewcal.data

/** Sefaria refs for the prayer services the app shows, per the user's custom. */
object Tefillot {

    /**
     * The moon blessing through its concluding Aleinu, in reading order. Verified against
     * Sefaria 2026-10: the Ashkenaz and Edot HaMizrach services end before Aleinu, so it is
     * appended from its own section; the Sefard Kiddush Levanah already ends with Aleinu
     * and Kaddish, so it stands alone.
     */
    fun kiddushLevanaRefs(nusach: Nusach): List<String> = when (nusach) {
        Nusach.ASHKENAZ -> listOf(
            "Siddur Ashkenaz, Weekday, Maariv, Birkat HaLevana",
            "Siddur Ashkenaz, Shabbat, Maariv, Aleinu"
        )
        Nusach.SEFARD -> listOf(
            "Siddur Sefard, Kiddush Levanah"
        )
        Nusach.EDOT_HAMIZRACH -> listOf(
            "Siddur Edot HaMizrach, Blessing of the Moon",
            "Siddur Edot HaMizrach, Weekday Arvit, Alenu"
        )
    }

    /** The Motzei Shabbat Havdalah service. Verified against Sefaria 2026-10 (the Sefard
     *  title really has a space before its comma; Sefard's text is Hebrew-only). */
    fun havdalahRefs(nusach: Nusach): List<String> = when (nusach) {
        Nusach.ASHKENAZ -> listOf("Siddur Ashkenaz, Shabbat, Havdalah")
        Nusach.SEFARD -> listOf("Siddur Sefard, Motzaei Shabbat , Havdala")
        Nusach.EDOT_HAMIZRACH -> listOf("Siddur Edot HaMizrach, Havdalah, Havdala")
    }

    /** Several refs joined for one reader screen; [ReaderViewModel] splits on this. */
    const val REF_SEPARATOR = "||"
}
