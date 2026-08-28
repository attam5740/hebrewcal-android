package com.hebrewcal.data

import kotlinx.serialization.json.*

data class Verse(val num: Int, val he: String, val en: String)
data class Chapter(val number: Int, val verses: List<Verse>)
data class SefariaText(val heRef: String, val chapters: List<Chapter>)

object SefariaTextParser {
    private val TAGS = Regex("<[^>]*>")
    private val WS = Regex("\\s+")

    private fun clean(raw: String): String =
        WS.replace(TAGS.replace(raw, " "), " ").trim()

    private fun asStr(e: JsonElement): String =
        clean((e as? JsonPrimitive)?.contentOrNull ?: "")

    fun parse(json: String): SefariaText {
        val obj = Json.parseToJsonElement(json).jsonObject
        val heRef = obj["heRef"]?.jsonPrimitive?.contentOrNull ?: ""
        val sections = obj["sections"]?.jsonArray?.map { it.jsonPrimitive.content } ?: listOf("1")
        val startChapter = sections.getOrNull(0)?.toIntOrNull() ?: 1
        val startVerse = sections.getOrNull(1)?.toIntOrNull() ?: 1

        val he = obj["he"]?.jsonArray ?: JsonArray(emptyList())
        val en = obj["text"]?.jsonArray ?: JsonArray(emptyList())
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
