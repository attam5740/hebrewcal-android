package com.hebrewcal.data

/**
 * Sefaria splits a siddur into many small sections (Siddur Ashkenaz's weekday Shacharit
 * alone is ~100). For reading, consecutive sections are merged into the service's main
 * parts: each entry below starts a part, which runs until the next part starts.
 */
object PrayerSections {
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
        "Siddur Ashkenaz, Shabbat, Minchah, Tzidkatkhah Tzedek" to SectionTitle("Tzidkatcha, Aleinu", "צדקתך · עלינו")
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
}
