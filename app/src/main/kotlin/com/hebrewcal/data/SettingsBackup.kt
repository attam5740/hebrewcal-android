package com.hebrewcal.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Settings file format: every setting with its concrete type, so a restore recreates each
 * DataStore key exactly as it was. The type tag matters — DataStore keys are typed, and a
 * latitude written back as an Int (or a version-code-sized Long as an Int) would corrupt
 * the store. Values may be Boolean, Int, Long, Float, Double, String, or Set<String>.
 */
object SettingsBackup {
    private const val APP = "com.hebrewcal"

    fun toJson(values: Map<String, Any>): String = buildJsonObject {
        put("app", APP)
        put("version", 1)
        put("settings", buildJsonObject {
            values.forEach { (name, value) ->
                put(name, buildJsonObject {
                    when (value) {
                        is Boolean -> { put("t", "bool"); put("v", value) }
                        is Int -> { put("t", "int"); put("v", value) }
                        is Long -> { put("t", "long"); put("v", value) }
                        is Float -> { put("t", "float"); put("v", value) }
                        is Double -> { put("t", "double"); put("v", value) }
                        is String -> { put("t", "string"); put("v", value) }
                        is Set<*> -> {
                            put("t", "stringset")
                            put("v", JsonArray(value.map { JsonPrimitive(it as String) }))
                        }
                        else -> throw IllegalArgumentException("unsupported setting type: $name")
                    }
                })
            }
        })
    }.toString()

    /** Throws on anything that is not a Hebrew Calendar settings file. */
    fun fromJson(json: String): Map<String, Any> {
        val root = Json.parseToJsonElement(json).jsonObject
        val app = (root["app"] as? JsonPrimitive)?.content
        require(app == APP) { "not a Hebrew Calendar settings file" }
        val out = LinkedHashMap<String, Any>()
        root["settings"]!!.jsonObject.forEach { (name, e) ->
            val o = e.jsonObject
            val v = o["v"]!!
            out[name] = when (val t = o["t"]!!.jsonPrimitive.content) {
                "bool" -> v.jsonPrimitive.boolean
                "int" -> v.jsonPrimitive.int
                "long" -> v.jsonPrimitive.long
                "float" -> v.jsonPrimitive.float
                "double" -> v.jsonPrimitive.double
                "string" -> v.jsonPrimitive.content
                "stringset" -> v.jsonArray.map { it.jsonPrimitive.content }.toSet()
                else -> throw IllegalArgumentException("unknown setting type: $t")
            }
        }
        return out
    }
}
