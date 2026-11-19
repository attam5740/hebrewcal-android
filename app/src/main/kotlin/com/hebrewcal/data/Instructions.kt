package com.hebrewcal.data

/**
 * Tells a siddur's instructions ("the chazzan says", "on fast days add", halachic notes) apart
 * from the prayer text, so the reader can show them smaller and grey.
 *
 * Two signals: Sefaria marks many instructions with <small> (and English notes with <i>), which
 * the parser keeps as [OPEN]/[CLOSE]; and in vocalized Hebrew, instructions are the words
 * printed without vowels. In vocalized Hebrew the vowels decide, since Sefaria also sets some
 * prayer text in <small> (words said quietly, seasonal insertions); the markers then only
 * mark word boundaries. Elsewhere (English, unvocalized siddurim) the markers decide.
 */
object Instructions {
    const val OPEN = '\uE000'
    const val CLOSE = '\uE001'
    /**
     * Letter-size markup inside words: Sefaria's <small>/<big> around a small or large letter
     * (בְּ<small>הִ</small>בָּֽרְאָ֑ם, <big>בְּ</big>רֵאשִׁ֖ית) or English small caps (E<small>TERNAL</small>).
     * The reader draws these a little smaller or larger; everything else strips them.
     */
    const val SMALL_OPEN = '\uE002'
    const val SMALL_CLOSE = '\uE003'
    const val BIG_OPEN = '\uE004'
    const val BIG_CLOSE = '\uE005'

    data class Segment(val text: String, val instruction: Boolean)

    private val VOWEL = Regex("[\\u05B0-\\u05BC\\u05BF\\u05C1\\u05C2\\u05C7]")
    private val LETTER = Regex("[\\u05D0-\\u05EA]")
    // Single unvowelled words that belong to the prayer itself.
    private val PRAYER_WORDS = setOf("אמן", "יי", "ייי", "ה׳", "ה'", "|")
    // A long unvowelled paragraph that speaks to God ("לפניך", "שמך", "אתה") and has none of the
    // words a note is made of ("אומרים", "יאמר", "צריך", "ג' פעמים", "ש"ץ") is prayer printed
    // without vowels, as Siddur Torah Or does with the kabbalistic passages of the bedtime Shema.
    private const val H = "\\u05D0-\\u05EA"
    private val ADDRESS = Regex("(?<![$H])(מ?לפניך|שמך|רצונך|ב?ידך|אתה)(?![$H])")
    private val NOTE_WORDS = Regex("(?<![$H])[וה]{0,2}((אומר|יאמר|מברכ|מברך|נוהג|צריכ|צריך)(ים|ין|ו|ה)?|מנהג|פעמים|ש[\"״]ץ|חזן|קהל)(?![$H])")
    private const val UNVOWELLED_PRAYER_MIN_WORDS = 10

    private val MARKERS = Regex("[\\uE000-\\uE005]")

    /** The text without any markers (instruction or letter-size). */
    fun strip(text: String): String = MARKERS.replace(text, "")

    /** The text's explicit segments: inside OPEN..CLOSE is an instruction. */
    fun explicit(text: String): List<Segment> {
        val out = mutableListOf<Segment>()
        var depth = 0
        val cur = StringBuilder()
        fun flush() { if (cur.isNotEmpty()) { out += Segment(cur.toString(), depth > 0); cur.clear() } }
        for (c in text) when (c) {
            OPEN -> { flush(); depth++ }
            CLOSE -> { flush(); if (depth > 0) depth-- }
            else -> cur.append(c)
        }
        flush()
        return out
    }

    /** Share of Hebrew words with vowels, outside explicit instructions. */
    fun vocalizedShare(texts: List<String>): Float {
        var heb = 0; var voc = 0
        for (t in texts) for (s in explicit(t)) if (!s.instruction) for (w in s.text.split(' ')) {
            if (!LETTER.containsMatchIn(w)) continue
            heb++; if (VOWEL.containsMatchIn(w)) voc++
        }
        return if (heb == 0) 0f else voc.toFloat() / heb
    }

    /**
     * Instruction and prayer segments of one paragraph. [hebrewVocalized]: the paragraph is
     * shown in Hebrew from a mostly vocalized text, so unvowelled runs count as instructions.
     */
    fun segments(text: String, hebrewVocalized: Boolean): List<Segment> {
        val base = explicit(text)
        if (!hebrewVocalized) return merge(base)
        val out = mutableListOf<Segment>()
        for (seg in base) {
            val words = seg.text.split(' ')
            // Unvowelled Hebrew word (an instruction candidate); words without Hebrew letters stay neutral.
            fun unv(w: String) = LETTER.containsMatchIn(w) && !VOWEL.containsMatchIn(w)
            val hasVowels = words.any { VOWEL.containsMatchIn(it) }
            if (!hasVowels) {
                // A wholly unvowelled piece in a vocalized siddur is a note, unless it is only a
                // response like "אמן", or a long passage addressed to God (see [ADDRESS]).
                val plain = strip(seg.text)
                val prayer = words.count { unv(it) } >= UNVOWELLED_PRAYER_MIN_WORDS &&
                    ADDRESS.containsMatchIn(plain) && !NOTE_WORDS.containsMatchIn(plain)
                out += Segment(seg.text, !prayer && words.any { unv(it) && strip(it).trim(':', ',', '.', '(', ')') !in PRAYER_WORDS })
                continue
            }
            var i = 0
            while (i < words.size) {
                if (!unv(words[i])) {
                    val start = i
                    while (i < words.size && !unv(words[i])) i++
                    out += Segment(words.subList(start, i).joinToString(" "), false)
                    continue
                }
                val start = i
                while (i < words.size && (unv(words[i]) || !LETTER.containsMatchIn(words[i]))) i++
                val run = words.subList(start, i)
                val heb = run.filter(::unv)
                val atEdge = start == 0 || i == words.size
                val instruction = heb.size >= 2 ||
                    heb.any { w -> w.any { it in "״\"׳'():0123456789" } } ||
                    (atEdge && heb.none { strip(it).trim(':', ',', '.', '(', ')') in PRAYER_WORDS })
                out += Segment(run.joinToString(" "), instruction)
            }
        }
        return merge(out)
    }

    /** Joins neighbours of the same kind and drops empty pieces. */
    private fun merge(segs: List<Segment>): List<Segment> {
        val out = mutableListOf<Segment>()
        for (s in segs) {
            if (s.text.isBlank()) { if (out.isNotEmpty()) out[out.lastIndex] = out.last().copy(text = out.last().text + s.text); continue }
            if (out.isNotEmpty() && out.last().instruction == s.instruction) {
                val sep = if (out.last().text.endsWith(" ") || s.text.startsWith(" ")) "" else " "
                out[out.lastIndex] = out.last().copy(text = out.last().text + sep + s.text)
            } else out += s
        }
        return out.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }
    }
}
