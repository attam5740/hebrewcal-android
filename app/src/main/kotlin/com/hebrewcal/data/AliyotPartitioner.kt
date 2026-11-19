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

    /**
     * The reader window laid out around its aliyot: [leading] chapters (or verse runs) before
     * the aliyot, the aliyah [sections], then the [trailing] ones after.
     */
    data class Layout(val leading: List<Chapter>, val sections: List<Section>, val trailing: List<Chapter>)

    /**
     * Splits [chapters] into before / aliyot / after, or null when the aliyot cannot be mapped
     * onto the window (the reader then shows plain chapters).
     *
     * Verses pulled in by scroll extension fall outside every aliyah; they stay as plain chapter
     * sections on the side of the window they came from. The split is on the *verse* boundary,
     * not the chapter boundary: refetching a partial edge chapter whole (Bereishit's
     * Genesis.1.1-6.8 -> all 22 verses of chapter 6) leaves that chapter partly covered, and a
     * chapter-granular split would drop its uncovered remainder (6:9-22) from every bucket.
     *
     * Coverage is keyed by the chapter's index in the window, not its number, so a cross-book
     * window with repeated chapter numbers cannot collide. The aliyah verses are an ordered
     * subsequence of the window's flattened verses, so one merge walk assigns each covered
     * verse to the window slot it actually came from.
     */
    fun layout(chapters: List<Chapter>, aliyotRefs: List<String>): Layout? {
        val parts = partition(chapters, aliyotRefs)
        if (parts.isEmpty()) return null
        val flatCovered = parts.flatMap { it.verses }.distinctBy { it.chapter to it.verse.num }
        val coveredByIndex = HashMap<Int, MutableSet<Int>>()
        var cursor = 0
        var done = false
        for ((i, ch) in chapters.withIndex()) {
            if (done) break
            for (v in ch.verses) {
                if (cursor >= flatCovered.size) {
                    done = true
                    break
                }
                val want = flatCovered[cursor]
                if (want.chapter == ch.number && want.verse.num == v.num) {
                    val slot = coveredByIndex.getOrPut(i) { HashSet<Int>() }
                    slot.add(v.num)
                    cursor++
                }
            }
        }

        val touched = chapters.indices.filter { coveredByIndex[it]?.isNotEmpty() == true }
        val firstTouched = touched.firstOrNull() ?: return null
        val lastTouched = touched.last()
        // Real aliyot cover one contiguous run of chapters. If they somehow do not, the
        // before/aliyot/after shape cannot represent the gap, so fall back to a plain chapter
        // view rather than dropping the chapters in between.
        if (lastTouched - firstTouched != touched.size - 1) return null

        // Wholly uncovered chapters outside the aliyah span.
        val before: List<Chapter> = chapters.subList(0, firstTouched).toList()
        val after: List<Chapter> = chapters.subList(lastTouched + 1, chapters.size).toList()

        // Uncovered verse prefix of the first touched chapter (e.g. Noach's
        // Genesis.6.9-11.32 refetching chapter 6 whole -> 6:1-8 shown above).
        val head = chapters[firstTouched]
        val headCovered = coveredByIndex.getValue(firstTouched)
        val headCut = head.verses.indexOfFirst { it.num in headCovered }
        val headPrefix: Chapter? = if (headCut > 0) {
            Chapter(head.number, head.verses.subList(0, headCut).toList())
        } else null

        // Uncovered verse suffix of the last touched chapter (Bereishit: 6:9-22).
        val tail = chapters[lastTouched]
        val tailCovered = coveredByIndex.getValue(lastTouched)
        val tailCut = tail.verses.indexOfLast { it.num in tailCovered }
        val tailSuffix: Chapter? = if (tailCut >= 0 && tailCut < tail.verses.lastIndex) {
            Chapter(tail.number, tail.verses.subList(tailCut + 1, tail.verses.size).toList())
        } else null

        return Layout(before + listOfNotNull(headPrefix), parts, listOfNotNull(tailSuffix) + after)
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
