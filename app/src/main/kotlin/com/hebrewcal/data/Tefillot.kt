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
     * Birchot HaShachar: from Modeh Ani through the end of Birkat Kohanim, in each siddur's own
     * order. Sefaria splits that span across sections and sometimes mid-section (a trailing
     * number range selects paragraphs); Shacharit then starts right after it.
     */
    fun birchotHashacharRefs(nusach: Nusach): List<String> = when (nusach) {
        Nusach.ASHKENAZ -> listOf(
            "Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Modeh Ani",
            "Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Netilat Yadayim",
            "Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Asher Yatzar",
            "Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Elokai Neshama",
            "Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Tzitzit",
            "Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Torah Blessings",
            "Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Torah Study 1"   // Birkat Kohanim
        )
        Nusach.SEFARD -> listOf(
            all("Siddur Sefard, Upon Arising"),                                // Modeh Ani, tallit, tefillin, ...
            "Siddur Sefard, Weekday Shacharit, Morning Blessings",
            "Siddur Sefard, Weekday Shacharit, Blessings on Torah 1-6"         // through Birkat Kohanim
        )
        Nusach.EDOT_HAMIZRACH -> listOf(all("Siddur Edot HaMizrach, Preparatory Prayers"))  // Modeh Ani .. Torah Blessings
    }

    private fun all(path: String) = SiddurIndex.ALL_UNDER + path
    private fun after(path: String, leaf: String) = SiddurIndex.ALL_UNDER + path + SiddurIndex.AFTER + leaf

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

    /**
     * A full service: every section of the siddur under these paths ([SiddurIndex.ALL_UNDER]),
     * in order. Shabbat swaps in the Shabbat services (with Musaf, and Kabbalat Shabbat
     * before Friday-night Arvit); Nusach Sefard also has its own Friday Mincha.
     */
    fun serviceRefs(button: PrayerButton, nusach: Nusach, day: ServiceDay): List<String> {
        val shabbat = day == ServiceDay.SHABBAT
        return when (nusach) {
            Nusach.ASHKENAZ -> when (button) {
                // Weekday Shacharit begins at Eilu Devarim (Torah Study 2-3); everything before it
                // is the Birchot HaShachar button. On Shabbat the same opening sections are skipped.
                PrayerButton.SHACHARIT -> if (shabbat) listOf(
                    after("Siddur Ashkenaz, Shabbat, Shacharit", "Siddur Ashkenaz, Shabbat, Shacharit, Preparatory Prayers, Tzitzit"),
                    all("Siddur Ashkenaz, Shabbat, Musaf LeShabbat")
                ) else listOf(
                    "Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Torah Study 2-3",
                    after("Siddur Ashkenaz, Weekday, Shacharit", "Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Torah Study")
                )
                PrayerButton.MINCHA -> listOf(all(if (shabbat) "Siddur Ashkenaz, Shabbat, Minchah" else "Siddur Ashkenaz, Weekday, Minchah"))
                else -> if (shabbat) listOf(all("Siddur Ashkenaz, Shabbat, Kabbalat Shabbat"), all("Siddur Ashkenaz, Shabbat, Maariv"))
                    else listOf(all("Siddur Ashkenaz, Weekday, Maariv"))
            }
            Nusach.SEFARD -> when (button) {
                PrayerButton.SHACHARIT -> if (shabbat) listOf(all("Siddur Sefard, Shabbat Morning Services")) else listOf(
                    "Siddur Sefard, Weekday Shacharit, Blessings on Torah 7-26",   // from Eilu Devarim
                    after("Siddur Sefard, Weekday Shacharit", "Siddur Sefard, Weekday Shacharit, Blessings on Torah")
                )
                PrayerButton.MINCHA -> listOf(all(when (day) {
                    ServiceDay.SHABBAT -> "Siddur Sefard, Shabbat Mincha"
                    ServiceDay.EREV_SHABBAT -> "Siddur Sefard, Shabbat Eve Mincha"
                    ServiceDay.WEEKDAY -> "Siddur Sefard, Weekday Mincha"
                }))
                else -> if (shabbat) listOf(all("Siddur Sefard, Kabbalat Shabbat"), all("Siddur Sefard, Shabbat Eve Maariv"))
                    else listOf(all("Siddur Sefard, Weekday Maariv"))
            }
            Nusach.EDOT_HAMIZRACH -> when (button) {
                PrayerButton.SHACHARIT -> if (shabbat) listOf(all("Siddur Edot HaMizrach, Shabbat Shacharit"), all("Siddur Edot HaMizrach, Shabbat Mussaf"))
                    else listOf(all("Siddur Edot HaMizrach, Weekday Shacharit"))
                PrayerButton.MINCHA -> listOf(all(if (shabbat) "Siddur Edot HaMizrach, Shabbat Mincha" else "Siddur Edot HaMizrach, Weekday Mincha"))
                else -> if (shabbat) listOf(all("Siddur Edot HaMizrach, Kabbalat Shabbat"), all("Siddur Edot HaMizrach, Shabbat Arvit"))
                    else listOf(all("Siddur Edot HaMizrach, Weekday Arvit"))
            }
        }
    }

    /** Every prayer spec a user of [nusach] can open, for the offline prefetch. */
    fun allPrayerSpecs(nusach: Nusach): List<String> = buildList {
        for (b in PrayerButton.values()) when (b) {
            PrayerButton.CANDLE_LIGHTING -> CandleKind.values().forEach { addAll(refsFor(b, nusach, it)) }
            PrayerButton.SHACHARIT, PrayerButton.MINCHA, PrayerButton.ARVIT ->
                ServiceDay.values().forEach { addAll(refsFor(b, nusach, null, it)) }
            else -> addAll(refsFor(b, nusach, null))
        }
    }.distinct()

    fun refsFor(
        button: PrayerButton, nusach: Nusach, candleKind: CandleKind?, serviceDay: ServiceDay? = null
    ): List<String> = when (button) {
        PrayerButton.SHACHARIT, PrayerButton.MINCHA, PrayerButton.ARVIT ->
            serviceRefs(button, nusach, serviceDay ?: ServiceDay.WEEKDAY)
        PrayerButton.BIRCHOT_HASHACHAR -> birchotHashacharRefs(nusach)
        PrayerButton.CANDLE_LIGHTING -> candleLightingRefs(nusach, candleKind ?: CandleKind.SHABBAT)
        PrayerButton.HAVDALAH -> havdalahRefs(nusach)
        PrayerButton.SHEMA_AL_HAMITA -> shemaAlHamitaRefs(nusach)
    }

    /** Several refs joined for one reader screen; [ReaderViewModel] splits on this. */
    const val REF_SEPARATOR = "||"
}
