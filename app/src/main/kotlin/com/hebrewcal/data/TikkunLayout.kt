package com.hebrewcal.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Authentic Torah-scroll line layout, courtesy of the open-source tikkun.io
 * project (MIT license, github.com/akivajgordon/tikkun.io): 245 column files of
 * 42 lines each (the standard Vellish layout), plus a verse index mapping every
 * book/chapter/verse to its {page, line}. Line breaks are transcription data,
 * not computation — which is exactly how a printed tikkun achieves alignment.
 */
object TikkunLayout {

    data class Line(
        val text: String,          // the scroll line, setuma gaps already widened
        val isPetucha: Boolean,
        val pageNumber: Int,
        val firstLineOfPage: Boolean
    )

    val BOOK_NUMBERS = mapOf(
        "Genesis" to 1, "Exodus" to 2, "Leviticus" to 3, "Numbers" to 4, "Deuteronomy" to 5
    )

    /** "Deuteronomy.29.9-31.30" → (book, startCh, startV, endCh, endV), or null. */
    fun parseTorahRef(dottedRef: String): TorahRange? {
        val m = Regex("""^([A-Za-z ]+)\.(\d+)\.(\d+)-(\d+)\.(\d+)$""").find(dottedRef) ?: return null
        val book = BOOK_NUMBERS[m.groupValues[1]] ?: return null
        return TorahRange(book, m.groupValues[2].toInt(), m.groupValues[3].toInt(),
            m.groupValues[4].toInt(), m.groupValues[5].toInt())
    }

    data class TorahRange(val book: Int, val startCh: Int, val startV: Int, val endCh: Int, val endV: Int)

    /**
     * The scroll's written form (ktiv): tikkun.io marks kri/ktiv with '#' and
     * brackets — for a word "kri#[ktiv]" the scroll shows the part after '#'.
     */
    fun scrollWord(fragment: String): String {
        var t = fragment.replace("#(פ)", "")
        t = t.split(" ").joinToString(" ") { maqafGroup ->
            maqafGroup.split("־").joinToString("־") { word ->
                val parts = word.split("#")
                if (parts.size <= 1) parts[0] else parts.drop(1).joinToString("")
            }
        }
        return t.replace("[", "").replace("]", "").replace(Regex(" {2,}"), " ").trim()
    }

    /** Parses one page file (a JSON array of line objects) into scroll lines. */
    fun parsePage(json: String, pageNumber: Int): List<ParsedLine> {
        val arr = Json.parseToJsonElement(json) as? JsonArray ?: return emptyList()
        return arr.mapIndexedNotNull { i, el ->
            val o = el as? JsonObject ?: return@mapIndexedNotNull null
            val columns = (o["text"] as? JsonArray)?.map { col ->
                (col as? JsonArray)?.mapNotNull { f -> (f as? JsonPrimitive)?.contentOrNull } ?: emptyList()
            } ?: emptyList()
            // Setuma: multiple columns on one physical line, separated by a wide gap.
            val text = columns.joinToString("   ") { col ->
                col.joinToString(" ") { scrollWord(it) }
            }
            val verses = (o["verses"] as? JsonArray)?.mapNotNull { v ->
                val vo = v as? JsonObject ?: return@mapNotNull null
                val ch = (vo["chapter"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                val vs = (vo["verse"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                ch to vs
            } ?: emptyList()
            ParsedLine(
                text = text,
                isPetucha = (o["isPetucha"] as? JsonPrimitive)?.booleanOrNull ?: false,
                startingVerses = verses,
                pageNumber = pageNumber,
                lineIndex = i
            )
        }
    }

    data class ParsedLine(
        val text: String,
        val isPetucha: Boolean,
        val startingVerses: List<Pair<Int, Int>>,  // (chapter, verse) beginning on this line
        val pageNumber: Int,
        val lineIndex: Int
    )
}

/** Fetches and disk-caches tikkun.io layout data; assembles the lines for a Torah range. */
class TikkunLayoutRepository(context: Context) {

    private val cacheDir = File(context.filesDir, "tikkun").apply { mkdirs() }
    private val base = "https://raw.githubusercontent.com/akivajgordon/tikkun.io/develop/src/data"

    private fun cached(name: String, url: String): String? {
        val f = File(cacheDir, name)
        if (f.exists()) return f.readText()
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 15_000
            }
            try {
                if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally { conn.disconnect() }
        }.onSuccess { f.writeText(it) }.getOrNull()
    }

    /** Lines of the scroll for [range], or null when data is unavailable. */
    suspend fun linesFor(range: TikkunLayout.TorahRange): List<TikkunLayout.Line>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val tocJson = cached("toc.json", "$base/tables-of-contents/torah.json") ?: return@runCatching null
                val toc = Json.parseToJsonElement(tocJson) as? JsonObject ?: return@runCatching null
                fun locate(ch: Int, v: Int): Pair<Int, Int>? {
                    val e = ((toc["${range.book}"] as? JsonObject)?.get("$ch") as? JsonObject)
                        ?.get("$v") as? JsonObject ?: return null
                    val p = (e["p"] as? JsonPrimitive)?.intOrNull ?: return null
                    val l = (e["l"] as? JsonPrimitive)?.intOrNull ?: return null
                    return p to l
                }
                val (p1, l1) = locate(range.startCh, range.startV) ?: return@runCatching null
                val (p2, l2) = locate(range.endCh, range.endV) ?: return@runCatching null

                val out = mutableListOf<TikkunLayout.Line>()
                var pastEnd = false
                for (page in p1..p2 + 1) {
                    if (pastEnd) break
                    val pageJson = cached("page_$page.json", "$base/pages/torah/$page.json") ?: break
                    val lines = TikkunLayout.parsePage(pageJson, page)
                    for (ln in lines) {
                        val beforeStart = page < p1 || (page == p1 && ln.lineIndex < l1 - 1)
                        if (beforeStart) continue
                        // The end verse starts at (p2, l2) and may run on; stop at the
                        // first later line where a verse PAST the end begins.
                        val afterEndLine = page > p2 || (page == p2 && ln.lineIndex > l2 - 1)
                        if (afterEndLine && ln.startingVerses.isNotEmpty()) {
                            val (ch, v) = ln.startingVerses.first()
                            if (ch > range.endCh || (ch == range.endCh && v > range.endV)) {
                                pastEnd = true; break
                            }
                        }
                        out += TikkunLayout.Line(
                            text = ln.text,
                            isPetucha = ln.isPetucha,
                            pageNumber = page,
                            firstLineOfPage = ln.lineIndex == 0
                        )
                    }
                }
                out.ifEmpty { null }
            }.getOrNull()
        }
}
