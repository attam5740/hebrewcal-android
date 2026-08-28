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

        return ParshaRef(
            sefariaRef = dotted,
            heRef = (item["heRef"] as? JsonPrimitive)?.content ?: "",
            nameEn = display?.get("en")?.let { (it as? JsonPrimitive)?.content } ?: "",
            nameHe = display?.get("he")?.let { (it as? JsonPrimitive)?.content } ?: ""
        )
    }
}
