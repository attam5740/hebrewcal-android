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
}
