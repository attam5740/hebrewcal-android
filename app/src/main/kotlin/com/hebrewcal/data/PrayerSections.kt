package com.hebrewcal.data

/**
 * Sefaria splits a siddur into many small sections (Siddur Ashkenaz's weekday Shacharit
 * alone is ~100). For reading, consecutive sections are merged into the service's main
 * parts: each entry below starts a part, which runs until the next part starts.
 */
object PrayerSections {
    private const val CH = "Weekday Siddur Chabad"
    private fun torahOr(heading: String) = WikisourceText.ref(WikisourceText.TORAH_OR_SHABBAT_EVE, heading)

    private val STARTS: Map<String, SectionTitle> = mapOf(
        "Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Modeh Ani" to SectionTitle("Upon Arising", "השכמת הבוקר"),
        "Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Torah Blessings" to SectionTitle("Torah Blessings", "ברכות התורה"),
        "Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Torah Study 2-3" to SectionTitle("Preparation for Prayer", "הכנה לתפילה"),
        "Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Morning Blessings" to SectionTitle("Morning Blessings", "ברכות השחר"),
        "Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Korbanot, Kiyor" to SectionTitle("Korbanot", "קרבנות"),
        "Siddur Ashkenaz, Weekday, Shacharit, Pesukei Dezimra, Introductory Psalm" to SectionTitle("Pesukei Dezimra", "פסוקי דזמרה"),
        "Siddur Ashkenaz, Weekday, Shacharit, Blessings of the Shema, Barchu" to SectionTitle("Shema and Its Blessings", "קריאת שמע וברכותיה"),
        "Siddur Ashkenaz, Weekday, Shacharit, Amidah, Patriarchs" to SectionTitle("Amidah", "עמידה"),
        "Siddur Ashkenaz, Weekday, Shacharit, Post Amidah, Vidui and 13 Middot" to SectionTitle("Tachanun", "תחנון"),
        "Siddur Ashkenaz, Weekday, Shacharit, Torah Reading, Removing the Torah from Ark, El Erech Appayim" to SectionTitle("Torah Reading", "קריאת התורה"),
        "Siddur Ashkenaz, Weekday, Shacharit, Concluding Prayers, Ashrei" to SectionTitle("Ashrei, Uva Letzion, Aleinu", "אשרי · ובא לציון · עלינו"),
        "Siddur Ashkenaz, Weekday, Shacharit, Concluding Prayers, Song of the Day" to SectionTitle("Song of the Day", "שיר של יום"),
        "Siddur Ashkenaz, Weekday, Shacharit, Post Service, Six Remembrances" to SectionTitle("Six Remembrances", "שש זכירות"),
        "Siddur Ashkenaz, Weekday, Minchah, Ashrei" to SectionTitle("Ashrei", "אשרי"),
        "Siddur Ashkenaz, Weekday, Minchah, Amida, Patriarchs" to SectionTitle("Amidah", "עמידה"),
        "Siddur Ashkenaz, Weekday, Minchah, Post Amidah, Vidui and 13 Middot" to SectionTitle("Tachanun", "תחנון"),
        "Siddur Ashkenaz, Weekday, Minchah, Concluding Prayers, Alenu" to SectionTitle("Aleinu", "עלינו"),
        "Siddur Ashkenaz, Weekday, Maariv, Vehu Rachum" to SectionTitle("Shema and Its Blessings", "קריאת שמע וברכותיה"),
        "Siddur Ashkenaz, Weekday, Maariv, Amidah, Patriarchs" to SectionTitle("Amidah", "עמידה"),
        "Siddur Ashkenaz, Weekday, Maariv, Additions for Motza'ei Shabbat, Viyehi Noam" to SectionTitle("Motzaei Shabbat", "מוצאי שבת"),
        "Siddur Ashkenaz, Weekday, Maariv, Sefirat HaOmer" to SectionTitle("Counting the Omer", "ספירת העומר"),
        "Siddur Ashkenaz, Weekday, Maariv, Alenu" to SectionTitle("Aleinu", "עלינו"),
        "Siddur Ashkenaz, Shabbat, Kabbalat Shabbat, Yedid Nefesh" to SectionTitle("Lechu Neranenah", "לכו נרננה"),
        "Siddur Ashkenaz, Shabbat, Kabbalat Shabbat, Lekha Dodi" to SectionTitle("Lecha Dodi", "לכה דודי"),
        "Siddur Ashkenaz, Shabbat, Kabbalat Shabbat, Psalm 92" to SectionTitle("A Psalm for the Sabbath Day", "מזמור שיר ליום השבת"),
        "Siddur Ashkenaz, Shabbat, Kabbalat Shabbat, Bameh Madlikin" to SectionTitle("Bameh Madlikin", "במה מדליקין"),
        "Siddur Ashkenaz, Shabbat, Maariv, Barchu" to SectionTitle("Shema and Its Blessings", "קריאת שמע וברכותיה"),
        "Siddur Ashkenaz, Shabbat, Maariv, Amidah, Patriarchs" to SectionTitle("Amidah", "עמידה"),
        "Siddur Ashkenaz, Shabbat, Maariv, Vay'chulu" to SectionTitle("Vayechulu, Magen Avot, Aleinu", "ויכולו · מגן אבות · עלינו"),
        "Siddur Ashkenaz, Shabbat, Shacharit, Preparatory Prayers, Tallit" to SectionTitle("Preparation for Prayer", "הכנה לתפילה"),
        "Siddur Ashkenaz, Shabbat, Shacharit, Preparatory Prayers, Morning Blessings" to SectionTitle("Morning Blessings", "ברכות השחר"),
        "Siddur Ashkenaz, Shabbat, Shacharit, Preparatory Prayers, Korbanot, Kiyor" to SectionTitle("Korbanot", "קרבנות"),
        "Siddur Ashkenaz, Shabbat, Shacharit, Pesukei Dezimra, Mizmor Shir" to SectionTitle("Pesukei Dezimra", "פסוקי דזמרה"),
        "Siddur Ashkenaz, Shabbat, Shacharit, Pesukei Dezimra, Nishmat Kol Chai" to SectionTitle("Nishmat, Yishtabach", "נשמת · ישתבח"),
        "Siddur Ashkenaz, Shabbat, Shacharit, Blessings of the Shema, Barchu" to SectionTitle("Shema and Its Blessings", "קריאת שמע וברכותיה"),
        "Siddur Ashkenaz, Shabbat, Shacharit, Amidah, Patriarchs" to SectionTitle("Amidah", "עמידה"),
        "Siddur Ashkenaz, Shabbat, Shacharit, Torah Reading, Removing the Torah from the Ark, Ein Kamocha" to SectionTitle("Torah Reading", "קריאת התורה"),
        "Siddur Ashkenaz, Shabbat, Shacharit, Communal Prayers, Yekum Purkan" to SectionTitle("Communal Prayers, Returning the Torah", "תפילות לציבור והכנסת התורה"),
        "Siddur Ashkenaz, Shabbat, Musaf LeShabbat, Amidah, Patriarchs" to SectionTitle("Musaf", "מוסף"),
        "Siddur Ashkenaz, Shabbat, Musaf LeShabbat, Ein Keloheinu" to SectionTitle("Ein Keloheinu, Aleinu, Song of the Day", "אין כאלהינו · עלינו · שיר של יום"),
        "Siddur Ashkenaz, Shabbat, Minchah, Ashrei" to SectionTitle("Ashrei, Uva Letzion", "אשרי · ובא לציון"),
        "Siddur Ashkenaz, Shabbat, Minchah, Torah Reading, Removing the Torah from Ark, Va'ani Tefillati" to SectionTitle("Torah Reading", "קריאת התורה"),
        "Siddur Ashkenaz, Shabbat, Minchah, Amidah, Patriarchs" to SectionTitle("Amidah", "עמידה"),
        "Siddur Ashkenaz, Shabbat, Minchah, Tzidkatkhah Tzedek" to SectionTitle("Tzidkatcha, Aleinu", "צדקתך · עלינו"),
        // ── Chabad (Siddur Torah Or) ──
        "$CH, Shacharit, Upon Arising" to SectionTitle("Upon Arising", "השכמת הבוקר"),
        "$CH, Shacharit, Morning Blessings 32" to SectionTitle("Preparation for Prayer", "הכנה לתפילה"),
        "$CH, Shacharit, Hodu" to SectionTitle("Pesukei Dezimra", "פסוקי דזמרה"),
        "$CH, Shacharit, Blessings of the Shema" to SectionTitle("Shema and Its Blessings", "קריאת שמע וברכותיה"),
        "$CH, Shacharit, The Amidah" to SectionTitle("Amidah", "עמידה"),
        "$CH, Shacharit, Tachnun" to SectionTitle("Tachanun", "תחנון"),
        "$CH, Shacharit, Torah Reading" to SectionTitle("Torah Reading", "קריאת התורה"),
        "$CH, Shacharit, Ashrei Uva LeZion" to SectionTitle("Ashrei, Uva Letzion", "אשרי · ובא לציון"),
        "$CH, Shacharit, Song of the Day" to SectionTitle("Song of the Day", "שיר של יום"),
        "$CH, Shacharit, Kaveh" to SectionTitle("Kaveh, Aleinu", "קוה · עלינו"),
        "$CH, Shacharit, Rabbenu Tam" to SectionTitle("Rabbeinu Tam Tefillin, Six Remembrances", "תפילין דרבינו תם · שש זכירות"),
        "$CH, Mincha, Korbanot" to SectionTitle("Korbanot", "קרבנות"),
        "$CH, Mincha, Ashrei" to SectionTitle("Ashrei", "אשרי"),
        "$CH, Mincha, Amidah" to SectionTitle("Amidah", "עמידה"),
        "$CH, Mincha, Tachanun" to SectionTitle("Tachanun", "תחנון"),
        "$CH, Mincha, Aleinu" to SectionTitle("Aleinu", "עלינו"),
        "$CH, Maariv" to SectionTitle("Shema and Its Blessings", "קריאת שמע וברכותיה"),
        "$CH, Bedtime Shema" to SectionTitle("Shema", "קריאת שמע"),
        "$CH, Kiddush Levanah" to SectionTitle("Halleluyah", "הללויה"),
        torahOr("מנחה לערב שבת") to SectionTitle("Hodu, Patach Eliyahu", "הודו · פתח אליהו"),
        torahOr("סדר קבלת שבת") to SectionTitle("Lechu Neranenah", "לכו נרננה"),
        torahOr("מעריב לשבת ויום טוב") to SectionTitle("Shema and Its Blessings", "קריאת שמע וברכותיה")
    )

    /** One merged part: the [refs] indices it covers and its heading (null: keep the section's own). */
    data class Part(val indices: IntRange, val title: SectionTitle?)

    fun group(refs: List<String>): List<Part> {
        val parts = mutableListOf<Part>()
        var start = -1
        var title: SectionTitle? = null
        fun close(end: Int) { if (start >= 0) parts += Part(start..end, title) }
        refs.forEachIndexed { i, ref ->
            val t = STARTS[ref]
            when {
                t != null -> { close(i - 1); start = i; title = t }
                title != null -> {}                          // continues the open part
                else -> { close(i - 1); start = i; title = null; close(i); start = -1 }
            }
        }
        close(refs.lastIndex)
        return parts
    }

    /**
     * A point inside one long section where a new part starts: the paragraph whose text
     * (vowels and cantillation removed) begins with [marker]. Rules apply in order, and
     * [occurrence] picks the n-th match after the previous rule (ויכלו appears twice).
     */
    data class Split(val marker: String, val title: SectionTitle, val occurrence: Int = 1)

    private val SPLITS: Map<String, List<Split>> = mapOf(
        "$CH, Shacharit, Morning Blessings 1-31" to listOf(
            Split("כל הברכות הללו", SectionTitle("Morning Blessings", "ברכות השחר")),
            Split("ברכת התורה צריך", SectionTitle("Torah Blessings", "ברכות התורה"))
        ),
        "$CH, Shacharit, Morning Prayer" to listOf(
            Split("ביום שאין אומרים תחנון", SectionTitle("Akedah", "עקדה")),
            Split("נכון מאוד לומר בכל יום", SectionTitle("Korbanot", "קרבנות"))
        ),
        "$CH, Maariv" to listOf(
            Split("אדני, שפתי תפתח", SectionTitle("Amidah", "עמידה")),
            Split("למוצאי שבת", SectionTitle("Motzaei Shabbat", "מוצאי שבת")),
            Split("עלינו לשבח", SectionTitle("Aleinu", "עלינו"))
        ),
        "$CH, Bedtime Shema" to listOf(
            Split("יעלזו חסידים", SectionTitle("Yaalzu, Yoshev BeSeter", "יעלזו · יושב בסתר")),
            Split("אלהינו ואלהי אבותינו, תבוא", SectionTitle("Confession", "וידוי")),
            Split("אנא, בכח", SectionTitle("Ana Bekoach, HaMapil", "אנא בכח · המפיל"))
        ),
        "$CH, Kiddush Levanah" to listOf(
            Split("יאשר רגליו", SectionTitle("Blessing of the Moon", "ברכת הלבנה")),
            Split("קול דודי", SectionTitle("Kol Dodi, Psalms", "קול דודי · מזמורים")),
            Split("עלינו לשבח", SectionTitle("Aleinu", "עלינו"))
        ),
        torahOr("סדר קבלת שבת") to listOf(
            Split("שמור וזכור", SectionTitle("Lecha Dodi", "לכה דודי")),
            Split("מזמור צ\"ב", SectionTitle("A Psalm for the Sabbath Day", "מזמור שיר ליום השבת")),
            Split("זהר תרומה", SectionTitle("Kegavna", "כגונא"))
        ),
        torahOr("מעריב לשבת ויום טוב") to listOf(
            Split("תפילת העמידה", SectionTitle("Amidah", "עמידה")),
            Split("ויכלו השמים", SectionTitle("Vayechulu, Magen Avot", "ויכולו · מגן אבות"), occurrence = 2),
            Split("מזמור כ\"ג", SectionTitle("Mizmor LeDavid, Aleinu", "מזמור לדוד · עלינו"))
        )
    )

    private val MARKS = Regex("[\\u0591-\\u05BD\\u05BF-\\u05C7]")
    private val TAGS = Regex("<[^>]*>")
    private val SPACES = Regex("\\s+")
    fun normalize(he: String): String =
        SPACES.replace(MARKS.replace(TAGS.replace(he, "").replace('\u05BE', ' '), ""), " ").trim()

    /** A finished part of the reader: its heading (null: the first section's own) and its paragraphs. */
    data class Unit(val title: SectionTitle?, val firstRef: Int, val verses: List<Verse>)

    /** Merges consecutive sections into parts ([group]) and splits long sections at [SPLITS]. */
    fun units(refs: List<String>, paragraphs: List<List<Verse>>): List<Unit> {
        val out = mutableListOf<Unit>()
        for (part in group(refs)) {
            var title = part.title
            var firstRef = part.indices.first
            var cur = mutableListOf<Verse>()
            for (k in part.indices) {
                val rules = SPLITS[refs[k]].orEmpty()
                var next = 0
                var seen = 0
                for (v in paragraphs[k]) {
                    val rule = rules.getOrNull(next)
                    if (rule != null && normalize(v.he).startsWith(rule.marker) && ++seen == rule.occurrence) {
                        if (cur.isNotEmpty()) { out += Unit(title, firstRef, cur); cur = mutableListOf() }
                        title = rule.title; firstRef = k; next++; seen = 0
                    }
                    cur += v
                }
            }
            if (cur.isNotEmpty()) out += Unit(title, firstRef, cur)
        }
        return out
    }
}
