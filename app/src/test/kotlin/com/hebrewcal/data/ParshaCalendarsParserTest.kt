package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParshaCalendarsParserTest {
    private val json = """
      {"calendar_items":[
        {"title":{"en":"Parashat Hashavua","he":"פרשת השבוע"},
         "displayValue":{"en":"Ki Tavo","he":"כי תבוא"},
         "url":"Deuteronomy.26.1-29.8","ref":"Deuteronomy 26:1-29:8","heRef":"דברים כ״ו:א׳-כ״ט:ח׳"},
        {"title":{"en":"Haftarah","he":"הפטרה"},"ref":"Isaiah 60:1-22"}
      ]}
    """.trimIndent()

    @Test fun extractsParshaRefAndNames() {
        val p = ParshaCalendarsParser.parse(json)!!
        assertEquals("Deuteronomy.26.1-29.8", p.sefariaRef)
        assertEquals("Ki Tavo", p.nameEn)
        assertEquals("כי תבוא", p.nameHe)
        assertEquals("דברים כ״ו:א׳-כ״ט:ח׳", p.heRef)
    }

    @Test fun convertsRefWhenUrlMissing() {
        val noUrl = json.replace("\"url\":\"Deuteronomy.26.1-29.8\",", "")
        assertEquals("Deuteronomy.26.1-29.8", ParshaCalendarsParser.parse(noUrl)!!.sefariaRef)
    }

    @Test fun returnsNullWhenNoParsha() {
        assertNull(ParshaCalendarsParser.parse("""{"calendar_items":[]}"""))
    }
}
