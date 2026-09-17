package com.hebrewcal.data

/** One reading from Sefaria's calendar (a haftarah), in both ref forms. */
data class Reading(val ref: String, val dottedRef: String, val nameEn: String, val nameHe: String)

data class ParshaRef(
    val sefariaRef: String,
    val heRef: String,
    val nameEn: String,
    val nameHe: String,
    val aliyot: List<String> = emptyList(),
    val haftarot: List<Reading> = emptyList()
)
