package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SefariaTextParserTest {
    @Test fun parsesFlatSingleChapter() {
        val json = """
            {"heRef":"תהילים קי״ז","sections":["117"],"toSections":["117"],
             "he":["<b>הַֽלְלוּ</b>","פ"],"text":["Praise","for"]}
        """.trimIndent()
        val t = SefariaTextParser.parse(json)
        assertEquals("תהילים קי״ז", t.heRef)
        assertEquals(1, t.chapters.size)
        assertEquals(117, t.chapters[0].number)
        assertEquals(2, t.chapters[0].verses.size)
        assertEquals(1, t.chapters[0].verses[0].num)
        assertEquals("הַֽלְלוּ", t.chapters[0].verses[0].he)   // HTML stripped, nikkud kept
        assertEquals("Praise", t.chapters[0].verses[0].en)
    }

    @Test fun parsesNestedRangeWithChapterNumbers() {
        val json = """
            {"heRef":"תהילים א׳-ב׳","sections":["1"],"toSections":["2"],
             "he":[["אa","אb"],["בa"]],"text":[["1a","1b"],["2a"]]}
        """.trimIndent()
        val t = SefariaTextParser.parse(json)
        assertEquals(listOf(1, 2), t.chapters.map { it.number })
        assertEquals(2, t.chapters[0].verses.size)
        assertEquals(1, t.chapters[1].verses[0].num)
    }

    @Test fun parsesVerseSpanStartingMidChapter() {
        // e.g. a parsha starting at 47:28 — first verse numbered 28.
        val json = """
            {"heRef":"בראשית מ״ז:כ״ח","sections":["47","28"],"toSections":["47","30"],
             "he":["a","b","c"],"text":["a","b","c"]}
        """.trimIndent()
        val t = SefariaTextParser.parse(json)
        assertEquals(47, t.chapters[0].number)
        assertEquals(listOf(28, 29, 30), t.chapters[0].verses.map { it.num })
    }

    @Test fun preservesLayoutMarkersAndLineBreaks() {
        val json = """
            {"heRef":"דברים ל״א","sections":["31"],"toSections":["31"],
             "he":["חִזְקוּ&nbsp;וְאִמְצוּ <span class='mam-spi-pe'>{פ}</span>","שירה<br>שנייה {ס}"],
             "text":["Be strong","Second"]}
        """.trimIndent()
        val t = SefariaTextParser.parse(json)
        assertEquals("חִזְקוּ וְאִמְצוּ {פ}", t.chapters[0].verses[0].he)   // entity decoded, span stripped, marker kept
        assertEquals("שירה\nשנייה {ס}", t.chapters[0].verses[1].he)        // <br> becomes newline
    }

    @Test fun dropsFootnotesFromTranslation() {
        val json = """
            {"heRef":"x","sections":["1"],"toSections":["1"],"he":["א"],
             "text":["God<sup class='footnote-marker'>*</sup><i class=\"footnote\">note text</i> said"]}
        """.trimIndent()
        assertEquals("God said", SefariaTextParser.parse(json).chapters[0].verses[0].en)
    }

    @Test fun handlesNullLanguageFieldWithoutCrashing() {
        val json = """
            {"heRef":"תהילים א׳","sections":["1"],"toSections":["1"],
             "he":null,"text":["v1","v2"]}
        """.trimIndent()
        val t = SefariaTextParser.parse(json)
        assertEquals(1, t.chapters.size)
        assertEquals(2, t.chapters[0].verses.size)
        assertEquals("", t.chapters[0].verses[0].he)   // he missing -> empty
        assertEquals("v1", t.chapters[0].verses[0].en)
    }

    @Test
    fun singleParagraphRefComesAsAString() {
        // "Torah Study 1" returns he/text as bare strings rather than one-element arrays.
        val t = SefariaTextParser.parse("""{"heRef":"x","sections":[1],"he":"יְבָרֶכְךָ","text":"May He bless you"}""")
        assertEquals(1, t.chapters.size)
        assertEquals(1, t.chapters[0].verses.size)
        assertEquals("May He bless you", t.chapters[0].verses[0].en)
    }

    private fun he(raw: String) = SefariaTextParser.parse(
        kotlinx.serialization.json.buildJsonObject {
            put("heRef", kotlinx.serialization.json.JsonPrimitive("x"))
            put("he", kotlinx.serialization.json.JsonPrimitive(raw))
            put("text", kotlinx.serialization.json.JsonPrimitive(""))
        }.toString()
    ).chapters[0].verses[0].he

    private val so = Instructions.SMALL_OPEN
    private val sc = Instructions.SMALL_CLOSE

    @Test
    fun smallLetterStaysInsideItsWord() {
        // Genesis 2:4 (MAM): the small ה of בהבראם.
        assertEquals("הָאָ֖רֶץ בְּ${so}הִ${sc}בָּֽרְאָ֑ם", he("הָאָ֖רֶץ בְּ<small>הִ</small>בָּֽרְאָ֑ם"))
        // Leviticus 1:1: the small א at the end of ויקרא.
        assertEquals("וַיִּקְרָ֖${so}א$sc אֶל־מֹשֶׁ֑ה", he("וַיִּקְרָ֖<small>א</small> אֶל־מֹשֶׁ֑ה"))
    }

    @Test
    fun bigLettersAreAlwaysLetterSize() {
        val bo = Instructions.BIG_OPEN; val bc = Instructions.BIG_CLOSE
        assertEquals("${bo}בְּ${bc}רֵאשִׁ֖ית בָּרָ֣א", he("<big>בְּ</big>רֵאשִׁ֖ית בָּרָ֣א"))
        assertEquals("שְׁמַ֖${bo}ע$bc יִשְׂרָאֵ֑ל", he("שְׁמַ֖<big>ע</big> יִשְׂרָאֵ֑ל"))
    }

    @Test
    fun englishSmallCapsStayOneWord() {
        val t = SefariaTextParser.parse("""{"heRef":"x","he":"א","text":"the E<small>TERNAL</small> God"}""")
        assertEquals("the E${so}TERNAL$sc God", t.chapters[0].verses[0].en)
    }

    @Test
    fun siddurSmallNotesStayInstructions() {
        val o = Instructions.OPEN; val c = Instructions.CLOSE
        assertEquals("מוֹדִים ${o}אומרים$c אֲנַחְנוּ", he("מוֹדִים <small>אומרים</small> אֲנַחְנוּ"))
        // Glued only to punctuation, or holding punctuation: still a note.
        assertEquals("עֹשֶׂה ( ${o}בעשי\"ת$c הַמֶּלֶךְ", he("עֹשֶׂה (<small>בעשי\"ת</small> הַמֶּלֶךְ"))
        assertEquals("סֶלָה ${o}ג״פ$c :", he("סֶלָה <small>ג״פ</small>:"))
        assertEquals("${o}לפורים—$c בִּימֵי", he("<small>לפורים—</small>בִּימֵי"))
    }

    @Test
    fun inlineTagsDoNotSplitWords() {
        assertEquals("עֲנֵֽנוּ)", he("<b>עֲנֵֽנוּ</b>)"))
        assertEquals("כִּ֘י־חָ֥פֵֽץ", he("כִּ֘י<span>־</span>חָ֥פֵֽץ"))
    }
}
