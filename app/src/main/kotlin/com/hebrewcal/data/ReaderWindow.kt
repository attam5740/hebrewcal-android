package com.hebrewcal.data

data class ReaderWindow(
    val bookEn: String,
    val chapters: List<Chapter>,
    val firstChapter: Int,
    val lastChapter: Int,
    val firstPartial: Boolean,
    val lastPartial: Boolean,
    val firstBookEn: String = bookEn
)

/** Pure bookkeeping for a reader that grows chapter by chapter in either direction. */
object ReaderWindows {
    private val torah = TorahNavigation.BOOKS.map { it.en }

    fun chapterCount(bookEn: String): Int? =
        if (bookEn == "Psalms") TorahNavigation.TEHILLIM_CHAPTERS
        else TorahNavigation.BOOKS.firstOrNull { it.en == bookEn }?.chapters

    /** Verse counts for the partial-edge test; Sefaria refs mark partial edges with a verse component. */
    fun fromRef(ref: String, text: SefariaText): ReaderWindow {
        val book = ref.substringBefore(".")
        val spec = ref.substringAfter(".", "")
        val dashIdx = spec.indexOf("-")
        val startSpec = if (dashIdx >= 0) spec.substring(0, dashIdx) else spec
        val endSpec = if (dashIdx >= 0) spec.substring(dashIdx + 1) else startSpec
        val startVerse = startSpec.substringAfter(".", "").ifBlank { null }?.toIntOrNull()
        val endVerse = endSpec.substringAfter(".", "").ifBlank { null }?.toIntOrNull()
        val chapters = text.chapters
        val first = chapters.firstOrNull()?.number ?: startSpec.substringBefore(".").toIntOrNull() ?: 1
        val last = chapters.lastOrNull()?.number ?: first
        return ReaderWindow(
            bookEn = book, chapters = chapters, firstChapter = first, lastChapter = last,
            firstPartial = startVerse != null && startVerse > 1,
            // A verse-bounded end is partial unless the fetched last chapter shows the range ran to its end;
            // we cannot know a chapter's full length offline, so any explicit end verse counts as partial.
            lastPartial = endVerse != null
        )
    }

    fun nextFetch(w: ReaderWindow): Pair<String, Int>? {
        if (w.lastPartial) return w.bookEn to w.lastChapter
        val count = chapterCount(w.bookEn)
        if (count == null || w.lastChapter < count) return w.bookEn to w.lastChapter + 1
        val i = torah.indexOf(w.bookEn)
        return if (i in 0 until torah.lastIndex) torah[i + 1] to 1 else null
    }

    fun prevFetch(w: ReaderWindow): Pair<String, Int>? {
        if (w.firstPartial) return w.firstBookEn to w.firstChapter
        if (w.firstChapter > 1) return w.firstBookEn to w.firstChapter - 1
        val i = torah.indexOf(w.firstBookEn)
        return if (i > 0) torah[i - 1] to chapterCount(torah[i - 1])!! else null
    }

    fun appended(w: ReaderWindow, bookEn: String, ch: Chapter): ReaderWindow {
        val replacing = w.lastPartial && bookEn == w.bookEn && ch.number == w.lastChapter
        val chapters = if (replacing) w.chapters.dropLast(1) + ch else w.chapters + ch
        return w.copy(bookEn = bookEn, chapters = chapters, lastChapter = ch.number, lastPartial = false)
    }

    fun prepended(w: ReaderWindow, bookEn: String, ch: Chapter): ReaderWindow {
        val replacing = w.firstPartial && bookEn == w.firstBookEn && ch.number == w.firstChapter
        val chapters = if (replacing) listOf(ch) + w.chapters.drop(1) else listOf(ch) + w.chapters
        return w.copy(firstBookEn = bookEn, chapters = chapters, firstChapter = ch.number, firstPartial = false)
    }

    /** Dotted Torah ref spanning the window (single book only), for tikkun layout lookup. */
    fun torahRange(w: ReaderWindow): String? {
        if (w.bookEn !in torah || w.firstBookEn != w.bookEn || w.chapters.isEmpty()) return null
        val f = w.chapters.first(); val l = w.chapters.last()
        return "${w.bookEn}.${f.number}.${f.verses.first().num}-${l.number}.${l.verses.last().num}"
    }
}
