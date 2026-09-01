package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AliyotPartitionerTest {

    @Test fun parsesCrossChapterRange() {
        val r = AliyotPartitioner.parseRange("Deuteronomy 27:11-28:6")!!
        assertEquals(listOf(27, 11, 28, 6), listOf(r.startChapter, r.startVerse, r.endChapter, r.endVerse))
    }

    @Test fun parsesSameChapterShortForm() {
        val r = AliyotPartitioner.parseRange("Genesis 1:1-13")!!
        assertEquals(listOf(1, 1, 1, 13), listOf(r.startChapter, r.startVerse, r.endChapter, r.endVerse))
    }

    @Test fun parsesBookNameWithSpaces() {
        val r = AliyotPartitioner.parseRange("I Samuel 20:18-20:42")!!
        assertEquals(20, r.startChapter)
    }

    @Test fun rejectsGarbage() {
        assertNull(AliyotPartitioner.parseRange("nonsense"))
    }

    @Test fun partitionsAcrossChapterBoundary() {
        val chapters = listOf(
            Chapter(26, (1..19).map { Verse(it, "he$it", "en$it") }),
            Chapter(27, (1..10).map { Verse(it, "he$it", "en$it") })
        )
        val sections = AliyotPartitioner.partition(
            chapters,
            listOf("Deuteronomy 26:1-26:11", "Deuteronomy 26:12-27:10")
        )
        assertEquals(2, sections.size)
        assertEquals(11, sections[0].verses.size)
        assertEquals(18, sections[1].verses.size) // 26:12-19 (8) + 27:1-10 (10)
        assertEquals(27, sections[1].verses.last().chapter)
    }

    @Test fun namesSevenPlusMaftir() {
        assertEquals("ראשון", AliyotPartitioner.aliyahName(0, 8, hebrew = true))
        assertEquals("שביעי", AliyotPartitioner.aliyahName(6, 8, hebrew = true))
        assertEquals("מפטיר", AliyotPartitioner.aliyahName(7, 8, hebrew = true))
        assertEquals("Maftir", AliyotPartitioner.aliyahName(7, 8, hebrew = false))
    }
}
