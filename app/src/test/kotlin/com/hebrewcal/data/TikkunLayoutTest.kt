package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TikkunLayoutTest {

    @Test fun parsesTorahRef() {
        val r = TikkunLayout.parseTorahRef("Deuteronomy.29.9-31.30")!!
        assertEquals(listOf(5, 29, 9, 31, 30), listOf(r.book, r.startCh, r.startV, r.endCh, r.endV))
    }

    @Test fun rejectsNonTorahRef() {
        assertNull(TikkunLayout.parseTorahRef("Psalms.1-9"))
        assertNull(TikkunLayout.parseTorahRef("Isaiah.60.1-22"))
    }

    @Test fun scrollWordKeepsKtivForm() {
        // tikkun.io marks "kri#ktiv"; the scroll (ktiv) is the part after '#'
        assertEquals("כתיב", TikkunLayout.scrollWord("קרי#כתיב"))
        assertEquals("רגיל", TikkunLayout.scrollWord("רגיל"))
        assertEquals("א־ב", TikkunLayout.scrollWord("א־ב"))
    }

    @Test fun parsesPageLines() {
        val json = """
            [{"text":[["בְּרֵאשִׁית בָּרָא"]],"verses":[{"book":1,"chapter":1,"verse":1}],"aliyot":[],"isPetucha":false},
             {"text":[["קטע א","קטע ב"],["אחרי סתומה"]],"verses":[],"aliyot":[],"isPetucha":true}]
        """.trimIndent()
        val lines = TikkunLayout.parsePage(json, 7)
        assertEquals(2, lines.size)
        assertEquals("בְּרֵאשִׁית בָּרָא", lines[0].text)
        assertEquals(listOf(1 to 1), lines[0].startingVerses)
        assertEquals(true, lines[1].isPetucha)
        // two columns joined with a wide em-space gap (setuma)
        assertEquals("קטע א קטע ב\u2003\u2003\u2003אחרי סתומה", lines[1].text)
        assertEquals(7, lines[1].pageNumber)
    }
}
