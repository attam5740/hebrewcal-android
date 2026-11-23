package com.hebrewcal.data

/**
 * Sefaria's Tanya Yomi calendar item names only the first segment of the day's lesson; the lesson
 * runs to just before the next day's start. This builds the range covering both starts.
 */
object TanyaSpan {
    private val POSITION = Regex("^(.*) (\\d+):(\\d+)$")
    private val PARAGRAPH = Regex("^(.*) (\\d+)$")

    /**
     * A comma-suffixed Tanya node after the part ("Tanya, Part I; Likkutei Amarim, Compiler's
     * Foreword") whose segments are addressed by a bare number: there "Compiler's Foreword 9" is
     * paragraph 9 of the node, not chapter 9. ("Approbation" is such a node too but has chapters;
     * its daily starts are always written "3:1", so they never reach the bare-number paths.)
     */
    fun isCommaNode(prefix: String): Boolean =
        prefix.startsWith("Tanya") && prefix.contains(';') && prefix.substringAfterLast(';').contains(',')

    /** "<node> 1" .. "<node> 9" in the same comma node → "<node> 1-9"; null for any other shape. */
    private fun paragraphSpan(start: String, nextStart: String): String? {
        val a = PARAGRAPH.matchEntire(start) ?: return null
        val b = PARAGRAPH.matchEntire(nextStart) ?: return null
        val prefix = a.groupValues[1]
        if (prefix != b.groupValues[1] || !isCommaNode(prefix)) return null
        val p1 = a.groupValues[2].toIntOrNull() ?: return null
        val p2 = b.groupValues[2].toIntOrNull() ?: return null
        return if (p2 > p1) "$prefix $p1-$p2" else null
    }

    /**
     * A range from [start] to [nextStart] inclusive (the caller drops the final segment, which
     * belongs to tomorrow), or null when the refs are unparseable, sit in different parts/sections
     * (Sefaria rejects those), or [nextStart] is not strictly after [start].
     */
    fun spanRef(start: String, nextStart: String): String? {
        paragraphSpan(start.trim(), nextStart.trim())?.let { return it }
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
