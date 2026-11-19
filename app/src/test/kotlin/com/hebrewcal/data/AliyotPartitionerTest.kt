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

    @Test fun layoutKeepsTheUncoveredRestOfARefetchedEdgeChapter() {
        // Bereishit is Genesis 1:1-6:8; scrolling down refetches chapter 6 whole (22 verses).
        val sizes = listOf(31, 25, 24, 26, 32, 22)
        val chapters = sizes.mapIndexed { i, n -> Chapter(i + 1, (1..n).map { Verse(it, "he", "en") }) }
        val aliyot = listOf("Genesis 1:1-2:3", "Genesis 2:4-19", "Genesis 2:20-3:21", "Genesis 3:22-4:18",
            "Genesis 4:19-22", "Genesis 4:23-5:24", "Genesis 5:25-6:8")
        val layout = AliyotPartitioner.layout(chapters, aliyot)!!
        assertEquals(emptyList<Chapter>(), layout.leading)
        assertEquals(7, layout.sections.size)
        assertEquals(1, layout.trailing.size)
        assertEquals(6, layout.trailing[0].number)
        assertEquals((9..22).toList(), layout.trailing[0].verses.map { it.num })
        // No verse is dropped or shown twice.
        val shown = layout.leading.sumOf { it.verses.size } + layout.sections.sumOf { it.verses.size } +
            layout.trailing.sumOf { it.verses.size }
        assertEquals(sizes.sum(), shown)
    }
}
