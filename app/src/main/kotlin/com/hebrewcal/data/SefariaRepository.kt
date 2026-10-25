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
        if (ref.startsWith(WikisourceText.PREFIX)) return@withContext fetchWikisource(ref, cacheFirst = false)
        val cacheFile = textCacheFile(ref)
        // Try network; on failure fall back to cache.
        val url = "https://www.sefaria.org/api/texts/" +
            URLEncoder.encode(ref, "UTF-8").replace("+", "%20") +
            "?context=0&commentary=0" +
            // The Masorah edition exists only for Tanakh; asking for it on a siddur
            // ref returns the prayer with no Hebrew at all.
            (if (ref.contains("Siddur")) "" else "&vhe=" + URLEncoder.encode(heVersion, "UTF-8")) +
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
        if (ref.startsWith(WikisourceText.PREFIX)) wikisourceCacheFile(WikisourceText.page(ref))
        else File(cacheDir, ref.replace(Regex("[^A-Za-z0-9._-]"), "_") + "_v3.json")

    // Hebrew page titles would all sanitize to underscores, so the file name is a hash.
    private fun wikisourceCacheFile(page: String) = File(cacheDir, "ws_" + java.security.MessageDigest
        .getInstance("SHA-1").digest(page.toByteArray()).joinToString("") { "%02x".format(it) } + ".json")

    /** One Wikisource page is fetched (and cached) once; each section ref slices it. */
    private fun fetchWikisource(ref: String, cacheFirst: Boolean): Result<SefariaText> {
        val file = wikisourceCacheFile(WikisourceText.page(ref))
        val heading = WikisourceText.heading(ref)
        if (cacheFirst && file.exists()) runCatching { WikisourceText.toSefariaText(WikisourceText.extract(file.readText()), heading) }
            .takeIf { it.isSuccess }?.let { return it }
        return runCatching {
            val body = httpGet(WikisourceText.apiUrl(WikisourceText.page(ref)))
            val text = WikisourceText.toSefariaText(WikisourceText.extract(body), heading)
            file.writeText(body)
            text
        }.recoverCatching {
            if (file.exists()) WikisourceText.toSefariaText(WikisourceText.extract(file.readText()), heading) else throw it
        }
    }

    /**
     * Cache first: for siddur sections, which do not change, so a long service opens offline
     * and without re-downloading dozens of sections every time.
     */
    suspend fun fetchTextCacheFirst(ref: String): Result<SefariaText> = withContext(Dispatchers.IO) {
        if (ref.startsWith(WikisourceText.PREFIX)) return@withContext fetchWikisource(ref, cacheFirst = true)
        val cacheFile = textCacheFile(ref)
        if (cacheFile.exists()) runCatching { SefariaTextParser.parse(cacheFile.readText()) }
            .takeIf { it.isSuccess }?.let { return@withContext it }
        fetchText(ref)
    }

    /** Readable refs under [path] from the book's Sefaria index (cached on disk). */
    /** Every ref a reader spec stands for: parts joined by "||", each a ref or "@path[>>leaf]". */
    suspend fun expandRefs(spec: String): Result<List<String>> = runCatching {
        spec.split(Tefillot.REF_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.flatMap { p ->
            if (p.startsWith(SiddurIndex.ALL_UNDER)) {
                val s = SiddurIndex.parse(p)
                siddurSections(s.path, s.after, s.before).getOrThrow()
            } else listOf(p)
        }
    }

    suspend fun siddurSections(path: String, after: String? = null, before: String? = null): Result<List<String>> = withContext(Dispatchers.IO) {
        val book = path.substringBefore(", ")
        val cacheFile = File(cacheDir, "index_" + book.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")
        runCatching {
            val json = if (cacheFile.exists()) cacheFile.readText() else {
                httpGet("https://www.sefaria.org/api/v2/index/" + URLEncoder.encode(book.replace(' ', '_'), "UTF-8"))
                    .also { cacheFile.writeText(it) }
            }
            SiddurIndex.leaves(json, path, after, before).ifEmpty { error("no sections under $path") }
        }
    }

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
            // Wikimedia asks API clients to identify themselves.
            setRequestProperty("User-Agent", "HebrewCalendar (https://github.com/attam5740/hebrewcal-android)")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
