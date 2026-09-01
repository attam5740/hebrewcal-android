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
}
