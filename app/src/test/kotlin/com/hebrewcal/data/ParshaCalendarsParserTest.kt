package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParshaCalendarsParserTest {
    private val json = """
      {"calendar_items":[
        {"title":{"en":"Parashat Hashavua","he":"פרשת השבוע"},
         "displayValue":{"en":"Ki Tavo","he":"כי תבוא"},
         "url":"Deuteronomy.26.1-29.8","ref":"Deuteronomy 26:1-29:8","heRef":"דברים כ״ו:א׳-כ״ט:ח׳",
         "extraDetails":{"aliyot":["Deuteronomy 26:1-26:11","Deuteronomy 26:12-26:15"]}},
        {"title":{"en":"Haftarah","he":"הפטרה"},"ref":"Isaiah 60:1-22"}
      ]}
    """.trimIndent()

    @Test fun extractsParshaRefAndNames() {
        val p = ParshaCalendarsParser.parse(json)!!
        assertEquals("Deuteronomy.26.1-29.8", p.sefariaRef)
        assertEquals("Ki Tavo", p.nameEn)
        assertEquals("כי תבוא", p.nameHe)
        assertEquals("דברים כ״ו:א׳-כ״ט:ח׳", p.heRef)
        assertEquals(2, p.aliyot.size)
        assertEquals("Deuteronomy 26:1-26:11", p.aliyot[0])
    }

    @Test fun convertsRefWhenUrlMissing() {
        val noUrl = json.replace("\"url\":\"Deuteronomy.26.1-29.8\",", "")
        assertEquals("Deuteronomy.26.1-29.8", ParshaCalendarsParser.parse(noUrl)!!.sefariaRef)
    }

    @Test fun returnsNullWhenNoParsha() {
        assertNull(ParshaCalendarsParser.parse("""{"calendar_items":[]}"""))
    }

    @Test fun parsesEveryHaftarahItem() {
        val json = """{"calendar_items":[
          {"title":{"en":"Parashat Hashavua"},"displayValue":{"en":"Ha'Azinu","he":"האזינו"},
           "ref":"Deuteronomy 32:1-52","url":"Deuteronomy.32.1-52","heRef":"דברים ל״ב"},
          {"title":{"en":"Haftarah"},"displayValue":{"en":"Hosea 14:2-10","he":"הושע י״ד:ב׳-י׳"},
           "ref":"Hosea 14:2-10","url":"Hosea.14.2-10"},
          {"title":{"en":"Haftarah (S)"},"displayValue":{"en":"Micah 7:18-20","he":"מיכה ז׳"},
           "ref":"Micah 7:18-20"},
          {"title":{"en":"Daf Yomi"},"displayValue":{"en":"x"},"ref":"Berakhot 2"}
        ]}"""
        val p = ParshaCalendarsParser.parse(json)!!
        assertEquals(2, p.haftarot.size)
        assertEquals("Hosea.14.2-10", p.haftarot[0].dottedRef)
        assertEquals("Micah.7.18-20", p.haftarot[1].dottedRef)   // dotted form derived when url is absent
        assertEquals("הושע י״ד:ב׳-י׳", p.haftarot[0].nameHe)
    }
}
