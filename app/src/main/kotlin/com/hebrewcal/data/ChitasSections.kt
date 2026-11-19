package com.hebrewcal.data

/** Pure shaping of the three Chitas legs into reader sections, so it is testable without Compose. */
object ChitasSections {
    /**
     * One entry per leg, in order. Verses and chapter numbers are carried through untouched
     * (a Tehillim leg spans several psalms); a leg that could not be fetched is empty.
     */
    fun build(
        texts: List<SefariaText?>,
        titles: List<String>,
        @Suppress("UNUSED_PARAMETER") hebrew: Boolean
    ): List<Pair<String, List<AliyotPartitioner.TaggedVerse>>> =
        titles.mapIndexed { i, title ->
            title to (texts.getOrNull(i)?.chapters.orEmpty()
                .flatMap { ch -> ch.verses.map { AliyotPartitioner.TaggedVerse(ch.number, it) } })
        }
}
