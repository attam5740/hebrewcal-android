package com.hebrewcal.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SefariaRepository(context: Context) {

    private val cacheDir = File(context.filesDir, "sefaria").apply { mkdirs() }

    // Fully-vocalized Hebrew (nikkud + te'amim) + JPS 1917 English.
    private val heVersion = "Tanach with Ta'amei Hamikra"
    private val enVersion = "The Holy Scriptures: A New Translation (JPS 1917)"

    suspend fun fetchText(ref: String): Result<SefariaText> = withContext(Dispatchers.IO) {
        val cacheFile = File(cacheDir, ref.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")
        // Try network; on failure fall back to cache.
        val url = "https://www.sefaria.org/api/texts/" +
            URLEncoder.encode(ref, "UTF-8").replace("+", "%20") +
            "?context=0&commentary=0" +
            "&vhe=" + URLEncoder.encode(heVersion, "UTF-8") +
            "&ven=" + URLEncoder.encode(enVersion, "UTF-8")
        runCatching {
            val body = httpGet(url)
            cacheFile.writeText(body)
            SefariaTextParser.parse(body)
        }.recoverCatching {
            if (cacheFile.exists()) SefariaTextParser.parse(cacheFile.readText()) else throw it
        }
    }

    suspend fun currentParsha(diaspora: Boolean): Result<ParshaRef> = withContext(Dispatchers.IO) {
        val cacheFile = File(cacheDir, "calendars_${if (diaspora) 1 else 0}.json")
        val url = "https://www.sefaria.org/api/calendars?diaspora=${if (diaspora) 1 else 0}"
        runCatching {
            val body = httpGet(url)
            cacheFile.writeText(body)
            ParshaCalendarsParser.parse(body) ?: error("No parsha in calendars response")
        }.recoverCatching {
            val cached = if (cacheFile.exists()) ParshaCalendarsParser.parse(cacheFile.readText()) else null
            cached ?: throw it
        }
    }

    private fun httpGet(spec: String): String {
        val conn = (URL(spec).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
