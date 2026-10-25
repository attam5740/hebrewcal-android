package com.hebrewcal.data

import kotlinx.serialization.json.*

data class Verse(val num: Int, val he: String, val en: String)
data class Chapter(val number: Int, val verses: List<Verse>)
/** A siddur section's name in both languages, for the reader's headings. */
data class SectionTitle(val en: String, val he: String)
data class SefariaText(
    val heRef: String,
    val chapters: List<Chapter>,
    /** For a prayer built from several siddur sections: one title per chapter, in order. */
    val sectionTitles: List<SectionTitle>? = null
)

object SefariaRefs {
    private val RANGE = Regex(" \\d+(-\\d+)?$")

    /** The section name at the end of a ref, minus a paragraph range ("Torah Study 2-3"). */
    fun sectionTitle(ref: String, heRef: String): SectionTitle {
        val en = ref.substringAfterLast(", ").trim()
        val he = heRef.substringAfterLast(", ").trim()
        return if (RANGE.containsMatchIn(en)) SectionTitle(RANGE.replace(en, ""), he.substringBeforeLast(' ', he))
        else SectionTitle(en, he)
    }
}

object SefariaTextParser {
    private val BR = Regex("<br ?/?>")
    private val TAGS = Regex("<[^>]*>")
    private val SPACES = Regex("[ \\t\\u00A0\\u2009\\u2005]+")
    private val NL_TRIM = Regex(" ?\\n ?")

    /**
     * Strips HTML but preserves the source's layout data: <br> becomes a real
     * newline and the {פ}/{ס} petucha/setuma markers stay in the text for the
     * reader's spacing option to interpret.
     */
    private val FOOTNOTE_MARK = Regex("<sup[^>]*footnote-marker[^>]*>.*?</sup>", RegexOption.DOT_MATCHES_ALL)
    private val FOOTNOTE_BODY = Regex("<i[^>]*class=\"footnote\"[^>]*>.*?</i>", RegexOption.DOT_MATCHES_ALL)

    private fun clean(raw: String): String {
        var t = raw.replace("&nbsp;", " ").replace("&thinsp;", " ").replace("&amp;", "&")
        t = FOOTNOTE_MARK.replace(t, "")
        t = FOOTNOTE_BODY.replace(t, "")
        t = BR.replace(t, "\n")
        t = TAGS.replace(t, " ")
        t = SPACES.replace(t, " ")
        t = NL_TRIM.replace(t, "\n")
        return t.trim()
    }

    private fun asStr(e: JsonElement): String =
        clean((e as? JsonPrimitive)?.contentOrNull ?: "")

    fun parse(json: String): SefariaText {
        val obj = Json.parseToJsonElement(json).jsonObject
        val heRef = (obj["heRef"] as? JsonPrimitive)?.contentOrNull ?: ""
        val sections = (obj["sections"] as? JsonArray)?.map { (it as? JsonPrimitive)?.content ?: "" } ?: listOf("1")
        val startChapter = sections.getOrNull(0)?.toIntOrNull() ?: 1
        val startVerse = sections.getOrNull(1)?.toIntOrNull() ?: 1

        // A ref to one paragraph ("Torah Study 1") comes back as a bare string, not an array.
        fun segments(e: JsonElement?): JsonArray = when (e) {
            is JsonArray -> e
            is JsonPrimitive -> JsonArray(listOf(e))
            else -> JsonArray(emptyList())
        }
        val he = segments(obj["he"])
        val en = segments(obj["text"])
        val nested = he.firstOrNull() is JsonArray

        val chapters = mutableListOf<Chapter>()
        if (nested) {
            val count = maxOf(he.size, en.size)
            for (i in 0 until count) {
                val heCh = (he.getOrNull(i) as? JsonArray) ?: JsonArray(emptyList())
                val enCh = (en.getOrNull(i) as? JsonArray) ?: JsonArray(emptyList())
                val firstVerse = if (i == 0) startVerse else 1
                chapters += Chapter(startChapter + i, zipVerses(heCh, enCh, firstVerse))
            }
        } else {
            chapters += Chapter(startChapter, zipVerses(he, en, startVerse))
        }
        return SefariaText(heRef, chapters)
    }

    private fun zipVerses(he: JsonArray, en: JsonArray, firstVerse: Int): List<Verse> {
        val n = maxOf(he.size, en.size)
        return (0 until n).map { i ->
            Verse(firstVerse + i, he.getOrNull(i)?.let(::asStr) ?: "", en.getOrNull(i)?.let(::asStr) ?: "")
        }
    }
}
