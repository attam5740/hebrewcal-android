package com.hebrewcal.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Static structure for the navigation dial: the five books and Tehillim. */
object TorahNavigation {
    const val TEHILLIM_CHAPTERS = 150

    /**
     * Dial steps per full turn, scaled to the range so long books (Tehillim)
     * take ~1.5 turns end to end instead of crawling at a fixed 40/turn.
     */
    fun dialStepsPerTurn(range: Int): Int = range.coerceIn(40, 100)

    data class Book(val en: String, val he: String, val chapters: Int)

    /** A parsha as listed by Sefaria's index (alts.Parasha), with its aliyah refs. */
    data class Parsha(val en: String, val he: String, val dottedRef: String, val aliyot: List<String>)

    val BOOKS = listOf(
        Book("Genesis", "בראשית", 50),
        Book("Exodus", "שמות", 40),
        Book("Leviticus", "ויקרא", 27),
        Book("Numbers", "במדבר", 36),
        Book("Deuteronomy", "דברים", 34)
    )

    /** Parses /api/v2/index/{Book} → its parshiyot (whole ref + aliyot), in order. */
    fun parseParshiyot(json: String): List<Parsha> {
        val root = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonObject ?: return emptyList()
        val nodes = ((root["alts"] as? JsonObject)?.get("Parasha") as? JsonObject)
            ?.get("nodes") as? JsonArray ?: return emptyList()
        fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.contentOrNull ?: ""
        return nodes.mapNotNull { n ->
            val o = n as? JsonObject ?: return@mapNotNull null
            val whole = str(o, "wholeRef")
            if (whole.isBlank()) return@mapNotNull null
            val aliyot = (o["refs"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
            Parsha(
                en = str(o, "title"),
                he = str(o, "heTitle"),
                dottedRef = whole.replace(" ", ".").replace(":", "."),
                aliyot = aliyot
            )
        }
    }
}

/** Fetches (and disk-caches) a book's parsha list from Sefaria's index API. */
class TorahNavigationRepository(context: Context) {
    private val cacheDir = File(context.filesDir, "sefaria").apply { mkdirs() }

    suspend fun parshiyot(bookEn: String): List<TorahNavigation.Parsha> = withContext(Dispatchers.IO) {
        val cacheFile = File(cacheDir, "index_${bookEn}_v1.json")
        runCatching {
            val body = if (cacheFile.exists()) cacheFile.readText() else {
                val url = "https://www.sefaria.org/api/v2/index/" +
                    URLEncoder.encode(bookEn, "UTF-8").replace("+", "%20")
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000; readTimeout = 15_000
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                    conn.inputStream.bufferedReader().use { it.readText() }
                } finally { conn.disconnect() }
            }
            val parsed = TorahNavigation.parseParshiyot(body)
            if (parsed.isNotEmpty() && !cacheFile.exists()) cacheFile.writeText(body)
            parsed
        }.getOrDefault(emptyList())
    }
}
