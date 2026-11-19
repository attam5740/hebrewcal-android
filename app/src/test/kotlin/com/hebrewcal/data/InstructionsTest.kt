package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructionsTest {
    private fun kinds(text: String, voc: Boolean = true) =
        Instructions.segments(text, voc).map { (if (it.instruction) "I:" else "P:") + it.text }

    @Test
    fun leadingUnvowelledWordsAreInstructions() {
        assertEquals(listOf("I:קהל וחזן", "P:בָּרוּךְ יְיָ הַמְבֹרָךְ"), kinds("קהל וחזן בָּרוּךְ יְיָ הַמְבֹרָךְ"))
        assertEquals(listOf("I:בעשי״ת", "P:זָכְרֵנוּ לְחַיִּים"), kinds("בעשי״ת זָכְרֵנוּ לְחַיִּים"))
        assertEquals(listOf("I:חזן", "P:בָּרְכוּ אֶת יְיָ"), kinds("חזן בָּרְכוּ אֶת יְיָ"))
    }

    @Test
    fun prayerWordsWithoutVowelsStayPrayer() {
        // "אמן" in Kaddish, and a lone unvowelled name, are part of the prayer.
        assertEquals(listOf("P:רַבָּא. אמן בְּעָלְמָא"), kinds("רַבָּא. אמן בְּעָלְמָא"))
        assertEquals(listOf("P:בָּרוּךְ יי לְעוֹלָם"), kinds("בָּרוּךְ יי לְעוֹלָם"))
    }

    @Test
    fun wholeUnvowelledParagraphIsANote() {
        assertEquals(listOf("I:כל הברכות הללו מברך אפילו לא נתחייב בהן"), kinds("כל הברכות הללו מברך אפילו לא נתחייב בהן"))
    }

    @Test
    fun explicitMarkersWinAndUnvocalizedTextIsLeftAlone() {
        val o = Instructions.OPEN; val c = Instructions.CLOSE
        assertEquals(listOf("I:The Chazzan says:", "P:Blessed is God"), kinds("${o}The Chazzan says:$c Blessed is God", voc = false))
        // In an unvocalized siddur only the markers count.
        assertEquals(listOf("P:ברוך אתה יי אלהינו"), kinds("ברוך אתה יי אלהינו", voc = false))
        assertEquals("ab", Instructions.strip("a${o}b$c"))
    }

    @Test
    fun vocalizedShareIgnoresMarkedNotes() {
        val o = Instructions.OPEN; val c = Instructions.CLOSE
        val share = Instructions.vocalizedShare(listOf("${o}הערה ארוכה מאוד בלי ניקוד כלל$c בָּרוּךְ אַתָּה"))
        assertTrue(share >= 0.99f)
    }

    @Test
    fun vowelsDecideInsideSmallTags() {
        val o = Instructions.OPEN; val c = Instructions.CLOSE
        // Said quietly, set in <small>, but it is prayer.
        assertEquals(listOf("P:בָּרוּךְ שֵׁם כְּבוֹד"), kinds("${o}בָּרוּךְ שֵׁם כְּבוֹד$c"))
        // Instruction and insertion inside one <small>: the insertion is prayer.
        assertEquals(listOf("I:במוצאי שבת אומרים:", "P:אַתָּה חוֹנַנְתָּנוּ"), kinds("${o}במוצאי שבת אומרים: אַתָּה חוֹנַנְתָּנוּ$c"))
        // A response alone in <small> stays prayer.
        assertEquals(listOf("P:רַבָּא. אמן בְּעָלְמָא"), kinds("רַבָּא. ${o}אמן$c בְּעָלְמָא"))
    }

    @Test
    fun unvowelledPrayerAddressedToGodIsPrayer() {
        // Weekday Siddur Chabad, Bedtime Shema: Siddur Torah Or prints this passage of the
        // confession without vowels, but it is said, not a note.
        val text = "יהי רצון מלפניך יהוה אלהינו ואלהי אבותינו שאם חטאתי עויתי פשעתי לפניך ופגמתי באות (י) של שמך הגדול (יהוה) בביטול ק״ש ובאות (א) של (אדני) ונתחייבתי בבית דינך הצדק סקילה או כיוצא בה הרי אני מקבל עלי סקילה והריני כאילו נסקלתי על ידי אות (א) של שם (אדני) בבית דין הגדול שבירושלים על דבר כבוד שמך:"
        assertEquals(listOf("P:" + text), kinds(text))
        // A long note without vowels is still a note, even when it quotes a prayer.
        val note = "בימים שאין אומרים תחנון אין אומרים יהי רצון מלפניך ולא אתה הוא עד אחר התפלה"
        assertEquals(listOf("I:" + note), kinds(note))
    }

    @Test
    fun letterSizeMarkersAreIgnoredWhenClassifying() {
        val so = Instructions.SMALL_OPEN; val sc = Instructions.SMALL_CLOSE
        val text = "בְּ${so}הִ${sc}בָּֽרְאָ֑ם"
        assertEquals(listOf("P:" + text), kinds(text))
        assertEquals("בְּהִבָּֽרְאָ֑ם", Instructions.strip(text))
        assertEquals("רַבָּא. אמן בְּעָלְמָא", Instructions.strip("רַבָּא. ${so}אמן$sc בְּעָלְמָא"))
    }
}
