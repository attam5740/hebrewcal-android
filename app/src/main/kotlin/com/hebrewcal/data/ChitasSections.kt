package com.hebrewcal.data

/** Pure shaping of the three Chitas legs into reader sections, so it is testable without Compose. */
object ChitasSections {
    /**
     * One entry per leg, in order. Verses and chapter numbers are carried through untouched
     * (a Tehillim leg spans several psalms); a leg that could not be fetched is empty.
     */
    fun build(
        texts: List<SefariaText?>,
        titles: List<String>
    ): List<Pair<String, List<AliyotPartitioner.TaggedVerse>>> =
        titles.mapIndexed { i, title ->
            title to (texts.getOrNull(i)?.chapters.orEmpty()
                .flatMap { ch -> ch.verses.map { AliyotPartitioner.TaggedVerse(ch.number, it) } })
        }

    /**
     * Splits the joined refs by position: a blank part stays an empty string, so a missing
     * Chumash or Tanya ref can never move another leg under the wrong heading.
     */
    fun splitRefs(joined: String): List<String> =
        joined.split(Tefillot.REF_SEPARATOR).map { it.trim() }

    /** Removes the final verse of the final chapter, and that chapter if it is left empty. */
    fun dropLastVerse(text: SefariaText): SefariaText {
        val last = text.chapters.lastOrNull() ?: return text
        val trimmed = last.copy(verses = last.verses.dropLast(1))
        val chapters = text.chapters.dropLast(1) + if (trimmed.verses.isEmpty()) emptyList() else listOf(trimmed)
        return text.copy(chapters = chapters)
    }

    /**
     * Drops tomorrow's first segment from the Tanya leg (index 2) when [trimTanya]. A leg with a
     * single verse is left alone (a server that returned only the start segment), null legs too.
     */
    fun legsAfterTrim(legs: List<SefariaText?>, trimTanya: Boolean): List<SefariaText?> =
        legs.mapIndexed { i, t ->
            if (i == 2 && trimTanya && t != null && t.chapters.sumOf { it.verses.size } > 1) dropLastVerse(t) else t
        }
}
