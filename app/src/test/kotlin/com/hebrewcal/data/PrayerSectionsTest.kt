package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PrayerSectionsTest {
    private val ref = "Weekday Siddur Chabad, Shacharit, Morning Blessings 1-31"

    @Test
    fun splitsAtAPhraseSefariaSetsInSmall() {
        // Sefaria wraps the halachic note that opens the Morning Blessings in <small>.
        val json = """{"heRef":"x","sections":[1],"he":[
            "<b>מוֹדֶה</b> אֲנִי לְפָנֶיךָ",
            "<small>כל הברכות הללו מברך אפילו לא נתחייב בהן</small>",
            "<b>בָּרוּךְ</b> אַתָּה יְיָ",
            "<small>ברכת התורה צריך ליזהר בה מאד</small>",
            "<b>בָּרוּךְ</b> אַתָּה יְיָ אֱלֹהֵינוּ"
        ],"text":["","","","",""]}"""
        val verses = SefariaTextParser.parse(json).chapters.flatMap { it.verses }
        val units = PrayerSections.units(listOf(ref), listOf(verses))
        assertEquals(listOf(null, "Morning Blessings", "Torah Blessings"), units.map { it.title?.en })
        assertEquals(listOf(1, 2, 2), units.map { it.verses.size })
    }

    @Test
    fun normalizeDropsMarkersVowelsAndTags() {
        val o = Instructions.OPEN; val c = Instructions.CLOSE
        assertEquals("כל הברכות הללו", PrayerSections.normalize("${o}כָּל הַבְּרָכוֹת הַלָּלוּ$c"))
        assertEquals("בהבראם", PrayerSections.normalize("בְּ${Instructions.SMALL_OPEN}הִ${Instructions.SMALL_CLOSE}בָּֽרְאָ֑ם"))
    }
}
