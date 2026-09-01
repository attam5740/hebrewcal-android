package com.hebrewcal.data

/**
 * Parses Sefaria aliyah refs (e.g. "Deuteronomy 26:1-26:11", "Genesis 1:1-2:3",
 * "Numbers 29:35-30:1") into verse ranges and partitions fetched chapters into
 * aliyah sections for the reader's "עליות" view.
 */
object AliyotPartitioner {

    data class AliyahRange(
        val startChapter: Int, val startVerse: Int,
        val endChapter: Int, val endVerse: Int
    )

    /** A verse tagged with its chapter, flattened out of the chapter list. */
    data class TaggedVerse(val chapter: Int, val verse: Verse)

    data class Section(val index: Int, val verses: List<TaggedVerse>)

    fun parseRange(ref: String): AliyahRange? {
        // Chapter:verse span is everything after the last space (book names may
        // contain spaces; the span itself never does).
        val span = ref.substringAfterLast(' ')
        val parts = span.split('-')
        if (parts.isEmpty()) return null
        val start = parts[0].split(':')
        if (start.size != 2) return null
        val sc = start[0].toIntOrNull() ?: return null
        val sv = start[1].toIntOrNull() ?: return null
        if (parts.size == 1) return AliyahRange(sc, sv, sc, sv)
        val end = parts[1].split(':')
        return when (end.size) {
            1 -> {
                val ev = end[0].toIntOrNull() ?: return null
                AliyahRange(sc, sv, sc, ev)          // "26:1-11" — same chapter
            }
            2 -> {
                val ec = end[0].toIntOrNull() ?: return null
                val ev = end[1].toIntOrNull() ?: return null
                AliyahRange(sc, sv, ec, ev)          // "26:1-27:10"
            }
            else -> null
        }
    }

    /** Partition [chapters] into one section per parseable aliyah ref, in order. */
    fun partition(chapters: List<Chapter>, aliyotRefs: List<String>): List<Section> {
        val flat = chapters.flatMap { ch -> ch.verses.map { TaggedVerse(ch.number, it) } }
        return aliyotRefs.mapIndexedNotNull { i, ref ->
            val r = parseRange(ref) ?: return@mapIndexedNotNull null
            val inRange = flat.filter { tv ->
                (tv.chapter > r.startChapter || (tv.chapter == r.startChapter && tv.verse.num >= r.startVerse)) &&
                (tv.chapter < r.endChapter || (tv.chapter == r.endChapter && tv.verse.num <= r.endVerse))
            }
            if (inRange.isEmpty()) null else Section(i, inRange)
        }
    }

    private val HEBREW_ORDINALS = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שביעי")
    private val ENGLISH_ORDINALS = listOf("First", "Second", "Third", "Fourth", "Fifth", "Sixth", "Seventh")

    /** Display name for aliyah [index] out of [total]; the 8th of 8 is maftir. */
    fun aliyahName(index: Int, total: Int, hebrew: Boolean): String {
        val isMaftir = total >= 8 && index == total - 1
        return when {
            isMaftir -> if (hebrew) "מפטיר" else "Maftir"
            hebrew -> HEBREW_ORDINALS.getOrNull(index) ?: "עלייה ${index + 1}"
            else -> ENGLISH_ORDINALS.getOrNull(index) ?: "Aliyah ${index + 1}"
        }
    }
}
