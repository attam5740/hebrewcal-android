package com.hebrewcal.data

/**
 * Sefaria's Tanya Yomi calendar item names only the first segment of the day's lesson; the lesson
 * runs to just before the next day's start. This builds the range covering both starts.
 */
object TanyaSpan {
    private val POSITION = Regex("^(.*) (\\d+):(\\d+)$")

    /**
     * A range from [start] to [nextStart] inclusive (the caller drops the final segment, which
     * belongs to tomorrow), or null when the refs are unparseable, sit in different parts/sections
     * (Sefaria rejects those), or [nextStart] is not strictly after [start].
     */
    fun spanRef(start: String, nextStart: String): String? {
        val a = POSITION.matchEntire(start.trim()) ?: return null
        val b = POSITION.matchEntire(nextStart.trim()) ?: return null
        val prefix = a.groupValues[1]
        if (prefix != b.groupValues[1]) return null
        val c1 = a.groupValues[2].toIntOrNull() ?: return null
        val s1 = a.groupValues[3].toIntOrNull() ?: return null
        val c2 = b.groupValues[2].toIntOrNull() ?: return null
        val s2 = b.groupValues[3].toIntOrNull() ?: return null
        if (c2 < c1 || (c2 == c1 && s2 <= s1)) return null
        return "$prefix $c1:$s1-$c2:$s2"
    }
}
