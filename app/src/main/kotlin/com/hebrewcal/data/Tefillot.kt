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

    /**
     * Birchot HaShachar with Birchot HaTorah (asher bachar banu) and the Birkat Kohanim verses,
     * in each siddur's own order. Sefaria splits them across sections: Ashkenaz keeps the
     * Torah blessings and the Birkat Kohanim verses in two sections before the morning
     * blessings; Sefard's "Blessings on Torah" also holds its 15 morning blessings.
     */
    fun birchotHashacharRefs(nusach: Nusach): List<String> = when (nusach) {
        Nusach.ASHKENAZ -> listOf(
            "Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Torah Blessings",
            "Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Torah Study",
            "Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Morning Blessings"
        )
        Nusach.SEFARD -> listOf(
            "Siddur Sefard, Weekday Shacharit, Morning Blessings",
            "Siddur Sefard, Weekday Shacharit, Blessings on Torah"
        )
        Nusach.EDOT_HAMIZRACH -> listOf(
            "Siddur Edot HaMizrach, Preparatory Prayers, Morning Blessings",
            "Siddur Edot HaMizrach, Preparatory Prayers, Torah Blessings"
        )
    }

    fun shemaAlHamitaRefs(nusach: Nusach): List<String> = when (nusach) {
        Nusach.ASHKENAZ -> listOf("Siddur Ashkenaz, Weekday, Maariv, Keri'at Shema al Hamita")
        Nusach.SEFARD -> listOf("Siddur Sefard, Bedtime Shema")
        Nusach.EDOT_HAMIZRACH -> listOf("Siddur Edot HaMizrach, Bedtime Shema")
    }

    /**
     * Candle lighting. Siddur Ashkenaz on Sefaria has no candle-lighting section, so Ashkenaz
     * uses the Sefard linear siddur, whose blessings are worded the same.
     */
    fun candleLightingRefs(nusach: Nusach, kind: CandleKind): List<String> = when (nusach) {
        Nusach.EDOT_HAMIZRACH -> listOf("Siddur Edot HaMizrach, Shabbat Candle Lighting")
        else -> listOf(
            "Shabbat Siddur Sefard Linear, Candle Lighting, " + when (kind) {
                CandleKind.SHABBAT -> "Candle Lighting for Shabbos"
                CandleKind.YOM_TOV -> "Candle Lighting for Yom Tov"
                CandleKind.SHABBAT_YOM_TOV -> "Candle Lighting when Shabbos occurs on Yom Tov"
            }
        )
    }

    fun refsFor(button: PrayerButton, nusach: Nusach, candleKind: CandleKind?): List<String> = when (button) {
        PrayerButton.BIRCHOT_HASHACHAR -> birchotHashacharRefs(nusach)
        PrayerButton.CANDLE_LIGHTING -> candleLightingRefs(nusach, candleKind ?: CandleKind.SHABBAT)
        PrayerButton.HAVDALAH -> havdalahRefs(nusach)
        PrayerButton.SHEMA_AL_HAMITA -> shemaAlHamitaRefs(nusach)
    }

    /** Several refs joined for one reader screen; [ReaderViewModel] splits on this. */
    const val REF_SEPARATOR = "||"
}
