package com.hebrewcal.data

import kotlinx.serialization.json.*

object ParshaCalendarsParser {
    fun parse(json: String): ParshaRef? {
        val items = (Json.parseToJsonElement(json) as? JsonObject)?.get("calendar_items") as? JsonArray
            ?: return null

        val item = items
            .mapNotNull { it as? JsonObject }
            .firstOrNull {
                (it["title"] as? JsonObject)?.get("en")?.let { titleEn ->
                    (titleEn as? JsonPrimitive)?.content
                } == "Parashat Hashavua"
            }
            ?: return null

        val url = (item["url"] as? JsonPrimitive)?.content
        val ref = (item["ref"] as? JsonPrimitive)?.content ?: return null
        val dotted = url ?: ref.replace(" ", ".").replace(":", ".")
        val display = item["displayValue"] as? JsonObject

        val aliyot = ((item["extraDetails"] as? JsonObject)?.get("aliyot") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
            ?: emptyList()

        fun str(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.contentOrNull
        val haftarot = items.mapNotNull { it as? JsonObject }
            .filter { str(it["title"] as? JsonObject, "en")?.startsWith("Haftarah") == true }
            .mapNotNull { h ->
                val r = str(h, "ref") ?: return@mapNotNull null
                val dv = h["displayValue"] as? JsonObject
                Reading(
                    ref = r,
                    dottedRef = str(h, "url") ?: r.replace(" ", ".").replace(":", "."),
                    nameEn = str(dv, "en") ?: r,
                    nameHe = str(dv, "he") ?: r
                )
            }

        return ParshaRef(
            sefariaRef = dotted,
            heRef = (item["heRef"] as? JsonPrimitive)?.content ?: "",
            nameEn = display?.get("en")?.let { (it as? JsonPrimitive)?.content } ?: "",
            nameHe = display?.get("he")?.let { (it as? JsonPrimitive)?.content } ?: "",
            aliyot = aliyot,
            haftarot = haftarot
        )
    }
}
