package com.hebrewcal.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * A siddur's table of contents from Sefaria's index API. A whole service (e.g. "Siddur
 * Ashkenaz, Weekday, Shacharit") is split into many small sections there; [leaves] lists
 * the readable refs under one path, in the siddur's order.
 */
object SiddurIndex {
    /** Refs written with this prefix in [Tefillot] stand for every section under that path. */
    const val ALL_UNDER = "@"
    /** "@path>>leaf": only the sections after [leaf] (the earlier ones belong to another button). */
    const val AFTER = ">>"
    /** "@path<<leaf": stop before [leaf] (the rest belongs to another button). */
    const val BEFORE = "<<"

    fun parseSpec(spec: String): Pair<String, String?> = parse(spec).let { it.path to it.after }

    data class Spec(val path: String, val after: String?, val before: String?)

    fun parse(spec: String): Spec {
        var body = spec.removePrefix(ALL_UNDER)
        var before: String? = null
        body.indexOf(BEFORE).takeIf { it >= 0 }?.let { before = body.substring(it + BEFORE.length); body = body.substring(0, it) }
        val i = body.indexOf(AFTER)
        return if (i < 0) Spec(body, null, before) else Spec(body.substring(0, i), body.substring(i + AFTER.length), before)
    }

    fun leaves(indexJson: String, path: String, after: String? = null, before: String? = null): List<String> {
        val schema = Json.parseToJsonElement(indexJson).jsonObject["schema"] as? JsonObject ?: return emptyList()
        val out = mutableListOf<String>()
        fun walk(node: JsonObject, prefix: String) {
            val title = (node["title"] as? JsonPrimitive)?.content.orEmpty()
            // Sefaria "default" nodes have no title of their own; they stand for their parent.
            val full = if (title.isBlank()) prefix else if (prefix.isEmpty()) title else "$prefix, $title"
            val children = node["nodes"] as? JsonArray
            if (children == null) out += full
            else children.forEach { c -> (c as? JsonObject)?.let { walk(it, full) } }
        }
        walk(schema, "")
        val under = out.filter { it == path || it.startsWith("$path, ") }
        val skip = after?.let { under.indexOf(it) } ?: -1
        val from = if (skip >= 0) under.drop(skip + 1) else under
        val stop = before?.let { from.indexOf(it) } ?: -1
        return if (stop >= 0) from.take(stop) else from
    }
}
