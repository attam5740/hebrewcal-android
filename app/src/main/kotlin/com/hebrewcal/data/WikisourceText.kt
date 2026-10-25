package com.hebrewcal.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/**
 * Prayer texts from Hebrew Wikisource, used where Sefaria has no edition (Siddur Torah Or's
 * Shabbat services for Chabad). A ref is "ws:<page>#<heading>": the section under that heading
 * of the page's plain-text extract, up to the next heading of the same or a higher level.
 * Wikisource transcriptions are CC BY-SA; the app credits them in About.
 */
object WikisourceText {
    const val PREFIX = "ws:"
    /** Siddur Torah Or (1940) on Hebrew Wikisource. */
    const val TORAH_OR_SHABBAT_EVE = "סידור תורה אור/קבלת שבת"

    fun ref(page: String, heading: String) = "$PREFIX$page#$heading"
    fun page(ref: String) = ref.removePrefix(PREFIX).substringBefore('#')
    fun heading(ref: String) = ref.substringAfter('#', "")

    /** The MediaWiki plain-text extract of a page, with "== heading ==" lines kept. */
    fun apiUrl(page: String) = "https://he.wikisource.org/w/api.php?action=query&prop=extracts" +
        "&explaintext=1&exsectionformat=wiki&format=json&formatversion=2&titles=" +
        URLEncoder.encode(page, "UTF-8").replace("+", "%20")

    /** The extract text out of an API response; throws when the page is missing. */
    fun extract(json: String): String {
        val pages = Json.parseToJsonElement(json).jsonObject["query"]?.jsonObject?.get("pages") as? JsonArray
        val page = pages?.firstOrNull() as? JsonObject ?: error("no page in response")
        return (page["extract"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: error("page has no text")
    }

    private val HEADING = Regex("^(=+)\\s*(.+?)\\s*=+\\s*$")
    // Footnote numbers survive the extract as digits glued to the word before them ("אַחֲרִית2").
    private val FOOTNOTE = Regex("(?<=[\\u05D0-\\u05EA\\u05B0-\\u05C7])\\d+")

    /** The paragraphs of the section under [heading]; sub-headings stay in as their own lines. */
    fun section(extract: String, heading: String): List<String> {
        val lines = extract.lines()
        val start = lines.indexOfFirst { HEADING.matchEntire(it)?.groupValues?.get(2) == heading }
        require(start >= 0) { "no section \"$heading\"" }
        val level = HEADING.matchEntire(lines[start])!!.groupValues[1].length
        val out = mutableListOf<String>()
        for (line in lines.drop(start + 1)) {
            val h = HEADING.matchEntire(line)
            if (h != null && h.groupValues[1].length <= level) break
            val text = (h?.groupValues?.get(2) ?: line).let { FOOTNOTE.replace(it, "") }.trim()
            if (text.isNotEmpty()) out += text
        }
        return out
    }

    fun toSefariaText(extract: String, heading: String): SefariaText =
        SefariaText(heading, listOf(Chapter(1, section(extract, heading).mapIndexed { i, p -> Verse(i + 1, p, "") })))
}
