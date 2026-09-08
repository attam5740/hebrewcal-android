package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TorahNavigationTest {

    private val indexJson = """
        {"title":"Genesis","alts":{"Parasha":{"nodes":[
          {"title":"Bereshit","heTitle":"בראשית","wholeRef":"Genesis 1:1-6:8",
           "refs":["Genesis 1:1-2:3","Genesis 2:4-2:19","Genesis 2:20-3:21"]},
          {"title":"Noach","heTitle":"נח","wholeRef":"Genesis 6:9-11:32","refs":[]},
          {"title":"broken","heTitle":"x"}
        ]}}}
    """.trimIndent()

    @Test fun parsesParshiyotWithDottedRefsAndAliyot() {
        val list = TorahNavigation.parseParshiyot(indexJson)
        assertEquals(2, list.size)                          // node without wholeRef is skipped
        assertEquals("Bereshit", list[0].en)
        assertEquals("בראשית", list[0].he)
        assertEquals("Genesis.1.1-6.8", list[0].dottedRef)   // Sefaria-API dotted form
        assertEquals(3, list[0].aliyot.size)
        assertEquals("Genesis 2:4-2:19", list[0].aliyot[1])  // aliyah refs kept in partitioner form
        assertEquals("Genesis.6.9-11.32", list[1].dottedRef)
    }

    @Test fun booksTableIsTheFiveBooksWithChapterCounts() {
        assertEquals(5, TorahNavigation.BOOKS.size)
        assertEquals(listOf(50, 40, 27, 36, 34), TorahNavigation.BOOKS.map { it.chapters })
        assertEquals(150, TorahNavigation.TEHILLIM_CHAPTERS)
    }

    @Test fun malformedIndexYieldsEmptyList() {
        assertTrue(TorahNavigation.parseParshiyot("{}").isEmpty())
        assertTrue(TorahNavigation.parseParshiyot("not json").isEmpty())
    }
}
