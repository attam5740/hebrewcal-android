package com.hebrewcal.data

import kotlinx.serialization.json.*
import java.net.URLEncoder

data class Verse(val num: Int, val he: String, val en: String)
data class Chapter(val number: Int, val verses: List<Verse>)
/** A siddur section's name in both languages, for the reader's headings. */
data class SectionTitle(val en: String, val he: String)
data class SefariaText(
    val heRef: String,
    val chapters: List<Chapter>,
    /** For a prayer built from several siddur sections: one title per chapter, in order. */
    val sectionTitles: List<SectionTitle>? = null,
    /** Chitas: one parsed text per leg (null = could not be fetched); chapter numbers stay per leg. */
    val legs: List<SefariaText?>? = null
)

object SefariaRefs {
    // Fully-vocalized Hebrew (nikkud + te'amim). MAM carries nikkud + te'amim AND the
    // original layout data (petucha/setuma markers, poetic line breaks) that the spacing
    // display option renders. It exists only for Tanakh.
    private const val TANAKH_HE = "Miqra according to the Masorah"
    // Tanya is pinned to Kehot on both sides; never rely on Sefaria's default.
    private const val KEHOT_HE = "Kehot Publication Society"
    private const val KEHOT_EN = "Kehot Publication Society (English Translation)"

    private val TANAKH_BOOKS = setOf(
        "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy",
        "Joshua", "Judges", "I Samuel", "II Samuel", "I Kings", "II Kings",
        "Isaiah", "Jeremiah", "Ezekiel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah",
        "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi",
        "Psalms", "Proverbs", "Job", "Song of Songs", "Ruth", "Lamentations", "Ecclesiastes",
        "Esther", "Daniel", "Ezra", "Nehemiah", "I Chronicles", "II Chronicles"
    )
    private val BOOK_END = Regex("[ .]\\d")

    /** The leading book name of a ref, for both "Genesis.30.14-27" and "Hosea 12:13-14:10". */
    private fun bookOf(ref: String): String {
        val head = ref.substringBefore(',').substringBefore(';')
        val m = BOOK_END.find(head)
        return (if (m != null) head.substring(0, m.range.first) else head).trim()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** The full Sefaria text request for [ref], with the Hebrew/English edition suited to its book. */
    fun textUrl(ref: String): String {
        val book = bookOf(ref)
        val versions = when {
            book in TANAKH_BOOKS -> "&vhe=" + enc(TANAKH_HE)
            book == "Tanya" -> "&vhe=" + enc(KEHOT_HE) + "&ven=" + enc(KEHOT_EN)
            else -> "" // e.g. siddur: asking for the Masorah edition returns no Hebrew at all
        }
        return "https://www.sefaria.org/api/texts/" + enc(ref) + "?context=0&commentary=0" + versions
    }

    private val RANGE = Regex(" \\d+(-\\d+)?$")

    /** The section name at the end of a ref, minus a paragraph range ("Torah Study 2-3"). */
    fun sectionTitle(ref: String, heRef: String): SectionTitle {
        if (ref.startsWith(WikisourceText.PREFIX)) WikisourceText.heading(ref).let { return SectionTitle(it, it) }
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

    private val INSTRUCTION_OPEN = Regex("<(small|i)(\\s[^>]*)?>")
    private val INSTRUCTION_CLOSE = Regex("</(small|i)>")
    // A <small> holding one run of letters with no spaces or punctuation: a candidate small letter.
    private val SMALL_RUN = Regex("<small(\\s[^>]*)?>([\\p{L}\\p{Mn}]+)</small>")
    private val BIG_OPEN = Regex("<big(\\s[^>]*)?>")
    private val BIG_CLOSE = Regex("</big>")
    private val LEADING_TAGS = Regex("^(<[^>]*>)+")
    private val TRAILING_TAGS = Regex("(<[^>]*>)+$")
    // Block-level tags separate words; inline ones (<b>, <span>, <a>) sit inside them.
    private val BLOCK_TAGS = Regex("</?(p|div|li|ul|ol|h\\d|table|tr|td|blockquote)(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
    private val HEBREW_LETTER = Regex("[\\u05D0-\\u05EA]")

    private fun isLetterish(c: Char) = c.isLetter() || Character.getType(c) == Character.NON_SPACING_MARK.toInt()

    /**
     * Small letters inside a word (בְּ<small>הִ</small>בָּֽרְאָ֑ם, וַיִּקְרָ֖<small>א</small>, English
     * E<small>TERNAL</small>): the <small> holds letters only — at most one Hebrew letter — and
     * touches a letter on at least one side (ignoring other tags). A siddur's <small> notes are
     * whole words set apart by spaces or punctuation ((<small>בעשי"ת</small>, <small>ג״פ</small>:).
     */
    private fun markSmallLetters(t: String): String = SMALL_RUN.replace(t) { m ->
        val content = m.groupValues[2]
        val before = TRAILING_TAGS.replace(t.substring(0, m.range.first), "").lastOrNull()
        val after = LEADING_TAGS.replace(t.substring(m.range.last + 1), "").firstOrNull()
        val glued = (before != null && isLetterish(before)) || (after != null && isLetterish(after))
        if (glued && HEBREW_LETTER.findAll(content).count() <= 1)
            "${Instructions.SMALL_OPEN}$content${Instructions.SMALL_CLOSE}"
        else m.value
    }

    private fun clean(raw: String): String {
        var t = raw.replace("&nbsp;", " ").replace("&thinsp;", " ").replace("&amp;", "&")
        t = FOOTNOTE_MARK.replace(t, "")
        t = FOOTNOTE_BODY.replace(t, "")
        t = BR.replace(t, "\n")
        // Small and large letters are part of their word: markers only, never a space.
        t = markSmallLetters(t)
        t = BIG_OPEN.replace(t, Instructions.BIG_OPEN.toString())
        t = BIG_CLOSE.replace(t, Instructions.BIG_CLOSE.toString())
        // Siddur instructions and notes: Sefaria sets them in <small> (English notes also in
        // <i>). Keep the boundaries as markers; the prayer reader styles them, others strip them.
        t = INSTRUCTION_OPEN.replace(t, " ${Instructions.OPEN}")
        t = INSTRUCTION_CLOSE.replace(t, "${Instructions.CLOSE} ")
        t = BLOCK_TAGS.replace(t, " ")
        t = TAGS.replace(t, "")
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
