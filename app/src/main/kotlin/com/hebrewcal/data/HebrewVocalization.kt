package com.hebrewcal.data

/** Removes Hebrew cantillation (te'amim) and/or vowel points (nikkud) by Unicode range. */
object HebrewVocalization {
    private val TEAMIM = Regex("[֑-֕֗-֚֙-֯]")
    private val NIKKUD = Regex("[ְ-ׇ]")

    /** Te'amim implies nikkud: callers must not pass showNikkud=false with showTeamim=true. */
    fun strip(text: String, showNikkud: Boolean, showTeamim: Boolean): String {
        var s = text
        if (!showTeamim) s = TEAMIM.replace(s, "")
        if (!showNikkud) s = NIKKUD.replace(s, "")
        return s
    }
}
