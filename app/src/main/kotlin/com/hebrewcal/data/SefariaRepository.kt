package com.hebrewcal.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class SefariaRepository(context: Context) {

    private val cacheDir = File(context.filesDir, "sefaria").apply { mkdirs() }

    // Fully-vocalized Hebrew (nikkud + te'amim) + JPS 1917 English.
    // MAM carries nikkud + te'amim AND the original layout data (petucha/setuma
    // markers, poetic line breaks) that the spacing display option renders.
    private val heVersion = "Miqra according to the Masorah"
    // English: Sefaria's default edition — currently the contemporary JPS
    // Gender-Sensitive Tanakh (2023), modern "you" English rather than 1917 "thou".
    private val enVersion: String? = null

    suspend fun fetchText(ref: String): Result<SefariaText> = withContext(Dispatchers.IO) {
        val cacheFile = textCacheFile(ref)
        // Try network; on failure fall back to cache.
        val url = "https://www.sefaria.org/api/texts/" +
            URLEncoder.encode(ref, "UTF-8").replace("+", "%20") +
            "?context=0&commentary=0" +
            // The Masorah edition exists only for Tanakh; asking for it on a siddur
            // ref returns the prayer with no Hebrew at all.
            (if (ref.startsWith("Siddur")) "" else "&vhe=" + URLEncoder.encode(heVersion, "UTF-8")) +
            (enVersion?.let { "&ven=" + URLEncoder.encode(it, "UTF-8") } ?: "")
        runCatching {
            val body = httpGet(url)
            val parsed = SefariaTextParser.parse(body)
            cacheFile.writeText(body)
            parsed
        }.recoverCatching {
            if (cacheFile.exists()) SefariaTextParser.parse(cacheFile.readText()) else throw it
        }
    }

    private fun textCacheFile(ref: String) =
        File(cacheDir, ref.replace(Regex("[^A-Za-z0-9._-]"), "_") + "_v3.json")

    /** True when [ref]'s text is already on disk under the same key [fetchText] reads. */
    fun isCached(ref: String): Boolean = textCacheFile(ref).exists()

    /** Downloads [ref] into the cache unless it is already there. */
    suspend fun prefetchText(ref: String): Boolean = isCached(ref) || fetchText(ref).isSuccess

    private fun datedCalendarsFile(date: LocalDate, diaspora: Boolean) =
        File(cacheDir, "calendars_${if (diaspora) 1 else 0}_$date.json")

    /**
     * Parasha for the week containing [date] via the calendars API, cached per date
     * so the download job can fill in future Shabbatot and the reader can fall
     * back to them offline.
     */
    suspend fun parshaFor(date: LocalDate, diaspora: Boolean): Result<ParshaRef> = withContext(Dispatchers.IO) {
        val cacheFile = datedCalendarsFile(date, diaspora)
        val url = "https://www.sefaria.org/api/calendars?diaspora=${if (diaspora) 1 else 0}" +
            "&year=${date.year}&month=${date.monthValue}&day=${date.dayOfMonth}"
        runCatching {
            // A corrupt/truncated cache file must not make this day permanently "offline":
            // drop it and fall through to the network path.
            val cached = if (cacheFile.exists()) {
                runCatching { ParshaCalendarsParser.parse(cacheFile.readText()) }.getOrNull()
                    ?: run { cacheFile.delete(); null }
            } else null
            cached ?: run {
                val body = httpGet(url)
                val parsed = ParshaCalendarsParser.parse(body) ?: error("No parsha in calendars response")
                cacheFile.writeText(body)
                parsed
            }
        }
    }

    suspend fun currentParsha(diaspora: Boolean): Result<ParshaRef> = withContext(Dispatchers.IO) {
        val cacheFile = File(cacheDir, "calendars_${if (diaspora) 1 else 0}.json")
        val url = "https://www.sefaria.org/api/calendars?diaspora=${if (diaspora) 1 else 0}"
        runCatching {
            val body = httpGet(url)
            val parsed = ParshaCalendarsParser.parse(body) ?: error("No parsha in calendars response")
            cacheFile.writeText(body)
            parsed
        }.recoverCatching {
            // Offline: prefer the download job's entry for the coming Shabbat (always
            // this week's parsha), then whatever the last live lookup left behind.
            val shabbat = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            val dated = datedCalendarsFile(shabbat, diaspora)
            val cached = when {
                dated.exists() -> ParshaCalendarsParser.parse(dated.readText())
                cacheFile.exists() -> ParshaCalendarsParser.parse(cacheFile.readText())
                else -> null
            }
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
