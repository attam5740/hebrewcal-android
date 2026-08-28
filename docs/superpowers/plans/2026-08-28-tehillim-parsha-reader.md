# Tehillim + Parsha Reader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two home-screen widget buttons — daily Tehillim and weekly parsha — that open a shared in-app translucent reader displaying Sefaria text with text-size, fast-scroll, language, nikkud, and te'amim controls.

**Architecture:** Pure-logic units (schedule, vocalization stripping, JSON parsing) are unit-tested on the JVM. A `SefariaTextRepository` fetches Sefaria text over `HttpURLConnection` and caches it to `filesDir`. A single translucent `TextReaderActivity` (Compose) renders any Sefaria ref; nikkud/te'amim/language toggles are applied client-side over one fully-vocalized fetch. The Glance widget launches the activity with intent extras.

**Tech Stack:** Kotlin, Jetpack Compose, Glance app-widget, kotlinx-serialization-json, kotlinx-coroutines, KosherJava zmanim 2.4.0, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-08-28-tehillim-reader-overlay-design.md`

## Global Constraints

- Package base: `com.hebrewcal`; Kotlin sources under `app/src/main/kotlin/com/hebrewcal/`; JVM unit tests under `app/src/test/kotlin/com/hebrewcal/`.
- `minSdk 26`, `compileSdk 34`, `targetSdk 34`.
- No new networking library — use `java.net.HttpURLConnection` + `kotlinx-serialization-json` (already a dependency).
- Hebrew source edition = Sefaria "Tanach with Ta'amei Hamikra" (nikkud + te'amim); English = JPS 1917. Verify exact version identifiers at build; fall back to API defaults if unavailable.
- Vocalization Unicode ranges — te'amim: `֑`–`֯` plus `ֽ`; nikkud: `ְ`–`ּ`, `ׁ`, `ׂ`, `ׇ`. Te'amim implies nikkud.
- Tehillim division keyed off the halachic Hebrew day already computed by `HebrewCalendarRepository` (rolls at tzet). 29-day month: on the 29th read 140–150.
- Reader defaults — Tehillim: Hebrew, nikkud on, te'amim off. Parsha: Hebrew, nikkud on, te'amim on.
- Button labels and parsha name follow the app `language`; Tehillim chapter range is gematria in Hebrew, Arabic numerals in English.
- Per project CLAUDE.md: run `gitnexus_impact` on any symbol before editing it, and `gitnexus_detect_changes()` before each commit.
- Unit test command: `./gradlew testDebugUnitTest --tests "<fqcn>"`.

---

### Task 1: Hebrew vocalization stripping (pure) + test infra

**Files:**
- Create: `app/src/main/kotlin/com/hebrewcal/data/HebrewVocalization.kt`
- Modify: `app/build.gradle` (add JUnit + coroutines-test)
- Test: `app/src/test/kotlin/com/hebrewcal/data/HebrewVocalizationTest.kt`

**Interfaces:**
- Produces: `object HebrewVocalization { fun strip(text: String, showNikkud: Boolean, showTeamim: Boolean): String }`

- [ ] **Step 1: Add test dependencies**

In `app/build.gradle`, inside the `dependencies { }` block, add:

```groovy
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3'
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HebrewVocalizationTest {
    // "בְּרֵאשִׁית" with nikkud, plus a te'am (etnahta ֑) after the bet.
    private val withAll = "בְּ֑רֵאשִׁית"

    @Test fun keepsEverythingWhenBothShown() {
        assertEquals(withAll, HebrewVocalization.strip(withAll, showNikkud = true, showTeamim = true))
    }

    @Test fun removesTeamimKeepsNikkud() {
        val out = HebrewVocalization.strip(withAll, showNikkud = true, showTeamim = false)
        assertEquals("בְּרֵאשִׁית", out)
        assertTrue(out.none { it in '֑'..'֯' })
    }

    @Test fun removesAllMarksWhenNikkudOff() {
        val out = HebrewVocalization.strip(withAll, showNikkud = false, showTeamim = false)
        assertEquals("בראשית", out)
    }

    @Test fun leavesLatinTextUntouched() {
        assertEquals("Happy is the one", HebrewVocalization.strip("Happy is the one", false, false))
    }

    private fun assertTrue(b: Boolean) = assertEquals(true, b)
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hebrewcal.data.HebrewVocalizationTest"`
Expected: FAIL — `HebrewVocalization` unresolved.

- [ ] **Step 4: Write minimal implementation**

```kotlin
package com.hebrewcal.data

/** Removes Hebrew cantillation (te'amim) and/or vowel points (nikkud) by Unicode range. */
object HebrewVocalization {
    private val TEAMIM = Regex("[֑-ֽ֯]")
    private val NIKKUD = Regex("[ְ-ׇּׁׂ]")

    /** Te'amim implies nikkud: callers must not pass showNikkud=false with showTeamim=true. */
    fun strip(text: String, showNikkud: Boolean, showTeamim: Boolean): String {
        var s = text
        if (!showTeamim) s = TEAMIM.replace(s, "")
        if (!showNikkud) s = NIKKUD.replace(s, "")
        return s
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hebrewcal.data.HebrewVocalizationTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle app/src/main/kotlin/com/hebrewcal/data/HebrewVocalization.kt app/src/test/kotlin/com/hebrewcal/data/HebrewVocalizationTest.kt
git commit -m "feat: add Hebrew vocalization stripping util with tests"
```

---

### Task 2: Daily Tehillim schedule (pure)

**Files:**
- Create: `app/src/main/kotlin/com/hebrewcal/data/TehillimSchedule.kt`
- Test: `app/src/test/kotlin/com/hebrewcal/data/TehillimScheduleTest.kt`

**Interfaces:**
- Produces:
  - `data class TehillimPortion(val sefariaRef: String, val firstChapter: Int, val lastChapter: Int)`
  - `object TehillimSchedule { fun portionFor(hebrewDay: Int, daysInMonth: Int): TehillimPortion }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TehillimScheduleTest {
    @Test fun day1IsChapters1to9() {
        val p = TehillimSchedule.portionFor(1, 30)
        assertEquals("Psalms.1-9", p.sefariaRef)
        assertEquals(1, p.firstChapter); assertEquals(9, p.lastChapter)
    }

    @Test fun day25IsChapter119FirstHalf() {
        val p = TehillimSchedule.portionFor(25, 30)
        assertEquals("Psalms.119.1-96", p.sefariaRef)
        assertEquals(119, p.firstChapter); assertEquals(119, p.lastChapter)
    }

    @Test fun day26IsChapter119SecondHalf() {
        assertEquals("Psalms.119.97-176", TehillimSchedule.portionFor(26, 30).sefariaRef)
    }

    @Test fun day29In30DayMonthStopsAt144() {
        val p = TehillimSchedule.portionFor(29, 30)
        assertEquals("Psalms.140-144", p.sefariaRef)
        assertEquals(140, p.firstChapter); assertEquals(144, p.lastChapter)
    }

    @Test fun day30In30DayMonthIs145to150() {
        assertEquals("Psalms.145-150", TehillimSchedule.portionFor(30, 30).sefariaRef)
    }

    @Test fun day29In29DayMonthCombinesTo150() {
        val p = TehillimSchedule.portionFor(29, 29)
        assertEquals("Psalms.140-150", p.sefariaRef)
        assertEquals(140, p.firstChapter); assertEquals(150, p.lastChapter)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hebrewcal.data.TehillimScheduleTest"`
Expected: FAIL — `TehillimSchedule` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.hebrewcal.data

data class TehillimPortion(
    val sefariaRef: String,
    val firstChapter: Int,
    val lastChapter: Int
)

/** Standard siddur day-of-month division of Tehillim. */
object TehillimSchedule {
    // day -> (firstChapter, lastChapter) for a full 30-day month.
    private val ranges: Map<Int, Pair<Int, Int>> = mapOf(
        1 to (1 to 9), 2 to (10 to 17), 3 to (18 to 22), 4 to (23 to 28), 5 to (29 to 34),
        6 to (35 to 38), 7 to (39 to 43), 8 to (44 to 48), 9 to (49 to 54), 10 to (55 to 59),
        11 to (60 to 65), 12 to (66 to 68), 13 to (69 to 71), 14 to (72 to 76), 15 to (77 to 78),
        16 to (79 to 82), 17 to (83 to 87), 18 to (88 to 89), 19 to (90 to 96), 20 to (97 to 103),
        21 to (104 to 105), 22 to (106 to 107), 23 to (108 to 112), 24 to (113 to 118),
        // 25 & 26 are verse-spans within chapter 119 — handled specially below.
        27 to (120 to 134), 28 to (135 to 139), 29 to (140 to 144), 30 to (145 to 150)
    )

    fun portionFor(hebrewDay: Int, daysInMonth: Int): TehillimPortion {
        when (hebrewDay) {
            25 -> return TehillimPortion("Psalms.119.1-96", 119, 119)
            26 -> return TehillimPortion("Psalms.119.97-176", 119, 119)
            29 -> if (daysInMonth == 29) return TehillimPortion("Psalms.140-150", 140, 150)
        }
        val (first, last) = ranges[hebrewDay] ?: ranges.getValue(30)
        return TehillimPortion("Psalms.$first-$last", first, last)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hebrewcal.data.TehillimScheduleTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hebrewcal/data/TehillimSchedule.kt app/src/test/kotlin/com/hebrewcal/data/TehillimScheduleTest.kt
git commit -m "feat: add daily Tehillim day-of-month schedule with tests"
```

---

### Task 3: Sefaria text model + parser (pure)

**Files:**
- Create: `app/src/main/kotlin/com/hebrewcal/data/SefariaText.kt`
- Test: `app/src/test/kotlin/com/hebrewcal/data/SefariaTextParserTest.kt`

**Interfaces:**
- Produces:
  - `data class Verse(val num: Int, val he: String, val en: String)`
  - `data class Chapter(val number: Int, val verses: List<Verse>)`
  - `data class SefariaText(val heRef: String, val chapters: List<Chapter>)`
  - `object SefariaTextParser { fun parse(json: String): SefariaText }`
- Consumes: `kotlinx.serialization.json`

**Context for the implementer — Sefaria `/api/texts` response shape:**
- `he` and `text` are verse arrays. For a single-chapter ref they are flat arrays of strings; for a multi-chapter range they are nested (one sub-array per chapter).
- `sections` / `toSections` give the start/end coordinates within the ref: a chapter ref → `["1"]`; a verse-span → `["119","1"]`. The first element is the starting chapter; a second element (if present) is the starting verse.
- Verses may contain HTML (`<span>`, `<br>`, `<small>`, footnotes) which must be stripped; Hebrew vocalization characters must be preserved.
- Chapter numbering: `startChapter + subArrayIndex`. Verse numbering: the first chapter starts at `startVerse` (from `sections`, default 1); later chapters start at 1.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SefariaTextParserTest {
    @Test fun parsesFlatSingleChapter() {
        val json = """
            {"heRef":"תהילים קי״ז","sections":["117"],"toSections":["117"],
             "he":["<b>הַֽלְלוּ</b>","פ"],"text":["Praise","for"]}
        """.trimIndent()
        val t = SefariaTextParser.parse(json)
        assertEquals("תהילים קי״ז", t.heRef)
        assertEquals(1, t.chapters.size)
        assertEquals(117, t.chapters[0].number)
        assertEquals(2, t.chapters[0].verses.size)
        assertEquals(1, t.chapters[0].verses[0].num)
        assertEquals("הַֽלְלוּ", t.chapters[0].verses[0].he)   // HTML stripped, nikkud kept
        assertEquals("Praise", t.chapters[0].verses[0].en)
    }

    @Test fun parsesNestedRangeWithChapterNumbers() {
        val json = """
            {"heRef":"תהילים א׳-ב׳","sections":["1"],"toSections":["2"],
             "he":[["אa","אb"],["בa"]],"text":[["1a","1b"],["2a"]]}
        """.trimIndent()
        val t = SefariaTextParser.parse(json)
        assertEquals(listOf(1, 2), t.chapters.map { it.number })
        assertEquals(2, t.chapters[0].verses.size)
        assertEquals(1, t.chapters[1].verses[0].num)
    }

    @Test fun parsesVerseSpanStartingMidChapter() {
        // e.g. a parsha starting at 47:28 — first verse numbered 28.
        val json = """
            {"heRef":"בראשית מ״ז:כ״ח","sections":["47","28"],"toSections":["47","30"],
             "he":["a","b","c"],"text":["a","b","c"]}
        """.trimIndent()
        val t = SefariaTextParser.parse(json)
        assertEquals(47, t.chapters[0].number)
        assertEquals(listOf(28, 29, 30), t.chapters[0].verses.map { it.num })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hebrewcal.data.SefariaTextParserTest"`
Expected: FAIL — `SefariaTextParser` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.hebrewcal.data

import kotlinx.serialization.json.*

data class Verse(val num: Int, val he: String, val en: String)
data class Chapter(val number: Int, val verses: List<Verse>)
data class SefariaText(val heRef: String, val chapters: List<Chapter>)

object SefariaTextParser {
    private val TAGS = Regex("<[^>]*>")
    private val WS = Regex("\\s+")

    private fun clean(raw: String): String =
        WS.replace(TAGS.replace(raw, " "), " ").trim()

    private fun asStr(e: JsonElement): String =
        clean((e as? JsonPrimitive)?.contentOrNull ?: "")

    fun parse(json: String): SefariaText {
        val obj = Json.parseToJsonElement(json).jsonObject
        val heRef = obj["heRef"]?.jsonPrimitive?.contentOrNull ?: ""
        val sections = obj["sections"]?.jsonArray?.map { it.jsonPrimitive.content } ?: listOf("1")
        val startChapter = sections.getOrNull(0)?.toIntOrNull() ?: 1
        val startVerse = sections.getOrNull(1)?.toIntOrNull() ?: 1

        val he = obj["he"]?.jsonArray ?: JsonArray(emptyList())
        val en = obj["text"]?.jsonArray ?: JsonArray(emptyList())
        val nested = he.firstOrNull() is JsonArray

        val chapters = mutableListOf<Chapter>()
        if (nested) {
            val count = maxOf(he.size, en.size)
            for (i in 0 until count) {
                val heCh = (he.getOrNull(i) as? JsonArray) ?: JsonArray(emptyList())
                val enCh = (en.getOrNull(i) as? JsonArray) ?: JsonArray(emptyList())
                val firstVerse = if (i == 0) startVerse else 1
                chapters += Chapter(startChapter + i, zipVerses(heCh, enCh, firstVerse))
            }
        } else {
            chapters += Chapter(startChapter, zipVerses(he, en, startVerse))
        }
        return SefariaText(heRef, chapters)
    }

    private fun zipVerses(he: JsonArray, en: JsonArray, firstVerse: Int): List<Verse> {
        val n = maxOf(he.size, en.size)
        return (0 until n).map { i ->
            Verse(firstVerse + i, he.getOrNull(i)?.let(::asStr) ?: "", en.getOrNull(i)?.let(::asStr) ?: "")
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hebrewcal.data.SefariaTextParserTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hebrewcal/data/SefariaText.kt app/src/test/kotlin/com/hebrewcal/data/SefariaTextParserTest.kt
git commit -m "feat: add Sefaria text model and JSON parser with tests"
```

---

### Task 4: Parsha calendars parser (pure)

**Files:**
- Create: `app/src/main/kotlin/com/hebrewcal/data/ParshaRef.kt`
- Test: `app/src/test/kotlin/com/hebrewcal/data/ParshaCalendarsParserTest.kt`

**Interfaces:**
- Produces:
  - `data class ParshaRef(val sefariaRef: String, val heRef: String, val nameEn: String, val nameHe: String)`
  - `object ParshaCalendarsParser { fun parse(json: String): ParshaRef? }`

**Context — Sefaria `/api/calendars` response:** a `calendar_items` array; the item whose `title.en == "Parashat Hashavua"` carries `ref` (e.g. `"Deuteronomy 26:1-29:8"`), `heRef`, and `displayValue.{en,he}` (the parsha name). The reader needs a dotted ref for `/api/texts`, so convert `"Deuteronomy 26:1-29:8"` → `"Deuteronomy.26.1-29.8"` (spaces→`.`, colons→`.`). Prefer the item's `url` field if present (already dotted), else convert `ref`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParshaCalendarsParserTest {
    private val json = """
      {"calendar_items":[
        {"title":{"en":"Parashat Hashavua","he":"פרשת השבוע"},
         "displayValue":{"en":"Ki Tavo","he":"כי תבוא"},
         "url":"Deuteronomy.26.1-29.8","ref":"Deuteronomy 26:1-29:8","heRef":"דברים כ״ו:א׳-כ״ט:ח׳"},
        {"title":{"en":"Haftarah","he":"הפטרה"},"ref":"Isaiah 60:1-22"}
      ]}
    """.trimIndent()

    @Test fun extractsParshaRefAndNames() {
        val p = ParshaCalendarsParser.parse(json)!!
        assertEquals("Deuteronomy.26.1-29.8", p.sefariaRef)
        assertEquals("Ki Tavo", p.nameEn)
        assertEquals("כי תבוא", p.nameHe)
        assertEquals("דברים כ״ו:א׳-כ״ט:ח׳", p.heRef)
    }

    @Test fun convertsRefWhenUrlMissing() {
        val noUrl = json.replace("\"url\":\"Deuteronomy.26.1-29.8\",", "")
        assertEquals("Deuteronomy.26.1-29.8", ParshaCalendarsParser.parse(noUrl)!!.sefariaRef)
    }

    @Test fun returnsNullWhenNoParsha() {
        assertNull(ParshaCalendarsParser.parse("""{"calendar_items":[]}"""))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hebrewcal.data.ParshaCalendarsParserTest"`
Expected: FAIL — unresolved reference.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.hebrewcal.data

import kotlinx.serialization.json.*

data class ParshaRef(
    val sefariaRef: String,
    val heRef: String,
    val nameEn: String,
    val nameHe: String
)

object ParshaCalendarsParser {
    fun parse(json: String): ParshaRef? {
        val items = Json.parseToJsonElement(json).jsonObject["calendar_items"]?.jsonArray ?: return null
        val item = items.map { it.jsonObject }.firstOrNull {
            it["title"]?.jsonObject?.get("en")?.jsonPrimitive?.contentOrNull == "Parashat Hashavua"
        } ?: return null

        val url = item["url"]?.jsonPrimitive?.contentOrNull
        val ref = item["ref"]?.jsonPrimitive?.contentOrNull ?: return null
        val dotted = url ?: ref.replace(" ", ".").replace(":", ".")
        val display = item["displayValue"]?.jsonObject
        return ParshaRef(
            sefariaRef = dotted,
            heRef = item["heRef"]?.jsonPrimitive?.contentOrNull ?: "",
            nameEn = display?.get("en")?.jsonPrimitive?.contentOrNull ?: "",
            nameHe = display?.get("he")?.jsonPrimitive?.contentOrNull ?: ""
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hebrewcal.data.ParshaCalendarsParserTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hebrewcal/data/ParshaRef.kt app/src/test/kotlin/com/hebrewcal/data/ParshaCalendarsParserTest.kt
git commit -m "feat: add Sefaria calendars parsha parser with tests"
```

---

### Task 5: Upcoming parsha name + daysInMonth in calendar repo

**Files:**
- Modify: `app/src/main/kotlin/com/hebrewcal/data/HebrewCalendarRepository.kt`
- Test: `app/src/test/kotlin/com/hebrewcal/data/UpcomingParshaTest.kt`

**Interfaces:**
- Produces (new on `HebrewCalendarRepository`):
  - `fun upcomingParshaName(date: Date, location: CalendarLocation, language: CalendarLanguage): String?`
- Modifies: `HebrewDateInfo` gains `val daysInMonth: Int` (append with a default so existing constructions still compile, then set it in `getDateInfo`).

- [ ] **Step 1: Impact check**

Run in terminal: `npx gitnexus impact --target HebrewDateInfo --direction upstream` (or the `gitnexus_impact` MCP tool). Confirm consumers are the widget and notification builder; note them. Proceed only if risk is not HIGH/CRITICAL without flagging.

- [ ] **Step 2: Write the failing test**

```kotlin
package com.hebrewcal.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class UpcomingParshaTest {
    private val repo = HebrewCalendarRepository()
    private fun d(s: String) = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s)!!

    @Test fun returnsUpcomingParshaOnAWeekday() {
        // 2026-08-28 is during the week of Parashat Ki Tavo (Ashkenazi transliteration "Ki Savo").
        val name = repo.upcomingParshaName(d("2026-08-28"), CalendarLocation.DIASPORA, CalendarLanguage.ENGLISH)
        assertNotNull(name)
    }

    @Test fun hebrewNameIsHebrewScript() {
        val name = repo.upcomingParshaName(d("2026-08-28"), CalendarLocation.DIASPORA, CalendarLanguage.HEBREW)!!
        assertEquals(true, name.any { it in 'א'..'ת' })
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hebrewcal.data.UpcomingParshaTest"`
Expected: FAIL — `upcomingParshaName` unresolved.

- [ ] **Step 4: Implement**

Add these imports if missing at the top of `HebrewCalendarRepository.kt`:

```kotlin
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import java.util.Calendar
import java.util.Date
```

Add the method to the `HebrewCalendarRepository` class body:

```kotlin
/**
 * Name of the parsha read on the upcoming Shabbat (or today if today is Shabbat),
 * independent of the weekday-display preference. Returns null if none can be found
 * (e.g. a run of festival Shabbatot), which the caller can treat as "no parsha".
 */
fun upcomingParshaName(
    date: Date,
    location: CalendarLocation,
    language: CalendarLanguage
): String? {
    val cal = JewishCalendar(date).apply { inIsrael = (location == CalendarLocation.ISRAEL) }
    while (cal.dayOfWeek != Calendar.SATURDAY) cal.forward(Calendar.DATE, 1)
    var guard = 0
    while (cal.parshah == JewishCalendar.Parsha.NONE && guard < 8) {
        cal.forward(Calendar.DATE, 7); guard++
    }
    if (cal.parshah == JewishCalendar.Parsha.NONE) return null
    val formatter = HebrewDateFormatter().apply { isHebrewFormat = (language == CalendarLanguage.HEBREW) }
    return formatter.formatParsha(cal).takeIf { it.isNotBlank() }
}
```

In the `HebrewDateInfo` data class, append:

```kotlin
    val daysInMonth: Int = 30
```

In `getDateInfo(...)`, where `HebrewDateInfo(...)` is constructed, add the argument (using the same rollover-adjusted `jewishCalendar`):

```kotlin
    daysInMonth = jewishCalendar.daysInJewishMonth,
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hebrewcal.data.UpcomingParshaTest"`
Expected: PASS. If the Ashkenazi transliteration surprises you, that is expected (library default) — the test only asserts non-null / Hebrew script.

- [ ] **Step 6: gitnexus detect + commit**

Run `gitnexus_detect_changes()` and confirm only `HebrewCalendarRepository` / `HebrewDateInfo` are affected.

```bash
git add app/src/main/kotlin/com/hebrewcal/data/HebrewCalendarRepository.kt app/src/test/kotlin/com/hebrewcal/data/UpcomingParshaTest.kt
git commit -m "feat: expose upcoming parsha name and daysInMonth"
```

---

### Task 6: Sefaria network repository + disk cache + INTERNET permission

**Files:**
- Create: `app/src/main/kotlin/com/hebrewcal/data/SefariaRepository.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add INTERNET permission)

**Interfaces:**
- Consumes: `SefariaTextParser`, `ParshaCalendarsParser`, `SefariaText`, `ParshaRef`.
- Produces:
  - `class SefariaRepository(context: Context)`
  - `suspend fun fetchText(ref: String): Result<SefariaText>`
  - `suspend fun currentParsha(diaspora: Boolean): Result<ParshaRef>`

This task is network I/O; it is verified by manual on-device UAT in Task 8, not a unit test. Keep the code small and obviously correct.

- [ ] **Step 1: Add the INTERNET permission**

In `AndroidManifest.xml`, alongside the other `<uses-permission>` lines, add:

```xml
    <uses-permission android:name="android.permission.INTERNET" />
```

- [ ] **Step 2: Implement the repository**

```kotlin
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
```

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/hebrewcal/data/SefariaRepository.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add Sefaria network repository with disk cache and INTERNET permission"
```

---

### Task 7: Reader overlay activity (Compose) + translucent theme

**Files:**
- Create: `app/src/main/kotlin/com/hebrewcal/ui/reader/ReaderViewModel.kt`
- Create: `app/src/main/kotlin/com/hebrewcal/ui/reader/TextReaderActivity.kt`
- Modify: `app/src/main/res/values/themes.xml` (add a translucent theme)
- Modify: `app/src/main/AndroidManifest.xml` (register the activity)
- Modify: `app/src/main/kotlin/com/hebrewcal/data/UserPreferences.kt` (persist reader text size)

**Interfaces:**
- Consumes: `SefariaRepository`, `SefariaText`, `HebrewVocalization`, `UserPreferencesRepository`.
- Intent extras (constants on `TextReaderActivity`): `EXTRA_MODE` (`"tehillim"|"parsha"`), `EXTRA_REF` (String, empty for parsha), `EXTRA_TITLE` (String), `EXTRA_DIASPORA` (Boolean), `EXTRA_LANG` (`"HEBREW"|"ENGLISH"`), `EXTRA_NIKKUD` (Boolean), `EXTRA_TEAMIM` (Boolean).

This task is verified by manual UAT (Task 8 wires the launch point). No unit test.

- [ ] **Step 1: Persist reader text size**

In `UserPreferences.kt`: add `val readerTextSizeSp: Float = 18f` to the `UserPreferences` data class; add `val READER_TEXT_SIZE = floatPreferencesKey("reader_text_size")` to `PreferenceKeys`; read it in the flow with `readerTextSizeSp = prefs[PreferenceKeys.READER_TEXT_SIZE] ?: 18f`; and add:

```kotlin
    suspend fun updateReaderTextSize(sp: Float) {
        context.dataStore.edit { it[PreferenceKeys.READER_TEXT_SIZE] = sp }
    }
```

- [ ] **Step 2: Add a translucent theme**

In `app/src/main/res/values/themes.xml`, add a theme (parent it to the app theme actually used in the file):

```xml
    <style name="Theme.HebrewCal.Reader" parent="Theme.HebrewCal">
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:windowContentOverlay">@null</item>
        <item name="android:backgroundDimEnabled">false</item>
    </style>
```

- [ ] **Step 3: ViewModel — load + render state**

```kotlin
package com.hebrewcal.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hebrewcal.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SefariaRepository(app)
    private val prefsRepo = UserPreferencesRepository(app)

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val title: String = "",
        val text: SefariaText? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    val textSize = prefsRepo.preferences.map { it.readerTextSizeSp }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 18f)

    fun load(mode: String, ref: String, title: String, diaspora: Boolean) {
        _state.value = UiState(loading = true, title = title)
        viewModelScope.launch {
            val resolvedRef: String
            var resolvedTitle = title
            if (mode == "parsha" && ref.isBlank()) {
                val pr = repo.currentParsha(diaspora)
                val p = pr.getOrNull()
                if (p == null) { _state.value = UiState(loading = false, error = OFFLINE, title = title); return@launch }
                resolvedRef = p.sefariaRef
                if (resolvedTitle.isBlank()) resolvedTitle = p.heRef
            } else {
                resolvedRef = ref
            }
            val res = repo.fetchText(resolvedRef)
            res.fold(
                onSuccess = { _state.value = UiState(false, null, resolvedTitle.ifBlank { it.heRef }, it) },
                onFailure = { _state.value = UiState(false, OFFLINE, resolvedTitle, null) }
            )
        }
    }

    fun setTextSize(sp: Float) = viewModelScope.launch { prefsRepo.updateReaderTextSize(sp) }

    companion object { const val OFFLINE = "offline" }
}
```

- [ ] **Step 4: Activity + Compose UI**

```kotlin
package com.hebrewcal.ui.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hebrewcal.data.CalendarLanguage
import com.hebrewcal.data.HebrewVocalization
import com.hebrewcal.data.SefariaText

class TextReaderActivity : ComponentActivity() {
    private val vm: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: "tehillim"
        val ref = intent.getStringExtra(EXTRA_REF) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val diaspora = intent.getBooleanExtra(EXTRA_DIASPORA, true)
        val lang0 = if (intent.getStringExtra(EXTRA_LANG) == "HEBREW") CalendarLanguage.HEBREW else CalendarLanguage.ENGLISH
        val nikkud0 = intent.getBooleanExtra(EXTRA_NIKKUD, true)
        val teamim0 = intent.getBooleanExtra(EXTRA_TEAMIM, false)
        vm.load(mode, ref, title, diaspora)

        setContent {
            val state by vm.state.collectAsState()
            val sizeSp by vm.textSize.collectAsState()
            var lang by remember { mutableStateOf(lang0) }
            var nikkud by remember { mutableStateOf(nikkud0) }
            var teamim by remember { mutableStateOf(teamim0) }

            Box(
                Modifier.fillMaxSize()
                    .background(Color(0xFF2E2E2E).copy(alpha = 0.5f))
                    .clickable { finish() },     // tap scrim to dismiss
                contentAlignment = Alignment.TopCenter
            ) {
                // Inner column consumes clicks so taps on content don't dismiss.
                Column(Modifier.fillMaxSize().clickable(enabled = false) {}) {
                    ReaderBar(
                        title = state.title,
                        lang = lang, nikkud = nikkud, teamim = teamim, sizeSp = sizeSp,
                        onLang = { lang = if (lang == CalendarLanguage.HEBREW) CalendarLanguage.ENGLISH else CalendarLanguage.HEBREW },
                        onNikkud = { nikkud = it; if (!it) teamim = false },
                        onTeamim = { teamim = it; if (it) nikkud = true },
                        onSize = { vm.setTextSize(it) },
                        onClose = { finish() }
                    )
                    when {
                        state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                        state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(if (lang == CalendarLanguage.HEBREW) "אין חיבור לאינטרנט" else "No internet connection", color = Color.White)
                        }
                        state.text != null -> ReaderBody(state.text!!, lang, nikkud, teamim, sizeSp)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"; const val EXTRA_REF = "ref"; const val EXTRA_TITLE = "title"
        const val EXTRA_DIASPORA = "diaspora"; const val EXTRA_LANG = "lang"
        const val EXTRA_NIKKUD = "nikkud"; const val EXTRA_TEAMIM = "teamim"
    }
}

@Composable
private fun ReaderBar(
    title: String, lang: CalendarLanguage, nikkud: Boolean, teamim: Boolean, sizeSp: Float,
    onLang: () -> Unit, onNikkud: (Boolean) -> Unit, onTeamim: (Boolean) -> Unit,
    onSize: (Float) -> Unit, onClose: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF1A1A2E)).padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = onLang) { Text(if (lang == CalendarLanguage.HEBREW) "EN" else "עב", color = Color.White) }
            TextButton(onClick = onClose) { Text("✕", color = Color.White) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = nikkud, onClick = { onNikkud(!nikkud) }, label = { Text("ניקוד") })
            Spacer(Modifier.width(6.dp))
            FilterChip(selected = teamim, onClick = { onTeamim(!teamim) }, enabled = nikkud, label = { Text("טעמים") })
        }
        Slider(value = sizeSp, onValueChange = onSize, valueRange = 14f..34f)
    }
}

@Composable
private fun ReaderBody(text: SefariaText, lang: CalendarLanguage, nikkud: Boolean, teamim: Boolean, sizeSp: Float) {
    val listState = rememberLazyListState()
    val hebrew = lang == CalendarLanguage.HEBREW
    // Flatten to display rows for a simple fast-scrollable list.
    data class Row(val header: String?, val verseNum: Int?, val body: String)
    val rows = remember(text, lang, nikkud, teamim) {
        buildList {
            text.chapters.forEach { ch ->
                add(Row(if (hebrew) "פרק ${ch.number}" else "Psalm ${ch.number}", null, ""))
                ch.verses.forEach { v ->
                    val body = if (hebrew) HebrewVocalization.strip(v.he, nikkud, teamim) else v.en
                    add(Row(null, v.num, body))
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(rows) { row ->
                if (row.header != null) {
                    Text(row.header, color = Color(0xFFD4AF37), fontSize = (sizeSp + 4).sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                        textAlign = if (hebrew) TextAlign.End else TextAlign.Start)
                } else {
                    Text("${row.verseNum}. ${row.body}", color = Color.White, fontSize = sizeSp.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        textAlign = if (hebrew) TextAlign.End else TextAlign.Start)
                }
            }
            item { Text(if (hebrew) "מקור: ספריא" else "Source: Sefaria", color = Color(0xFFAA9977), fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
        }
        // Fast-scroll thumb: drag maps to list index.
        FastScrollThumb(listState, rows.size, Modifier.align(Alignment.CenterEnd))
    }
}
```

- [ ] **Step 5: Fast-scroll thumb composable**

Add to `TextReaderActivity.kt`:

```kotlin
@Composable
private fun FastScrollThumb(
    listState: androidx.compose.foundation.lazy.LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var trackHeight by remember { mutableStateOf(1f) }
    Box(
        modifier
            .fillMaxHeight()
            .width(28.dp)
            .onGloballyPositioned { trackHeight = it.size.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(itemCount, trackHeight) {
                detectVerticalDragGestures { change, _ ->
                    val fraction = (change.position.y / trackHeight).coerceIn(0f, 1f)
                    val target = (fraction * (itemCount - 1)).toInt()
                    scope.launch { listState.scrollToItem(target) }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight(0.4f).background(Color.White.copy(alpha = 0.4f)))
    }
}
```

Add the imports these two composables need:

```kotlin
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
```

- [ ] **Step 6: Register the activity**

In `AndroidManifest.xml`, inside `<application>`, add:

```xml
        <activity
            android:name=".ui.reader.TextReaderActivity"
            android:exported="false"
            android:theme="@style/Theme.HebrewCal.Reader" />
```

- [ ] **Step 7: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Fix any import/reference errors before continuing.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/hebrewcal/ui/reader/ app/src/main/res/values/themes.xml app/src/main/AndroidManifest.xml app/src/main/kotlin/com/hebrewcal/data/UserPreferences.kt
git commit -m "feat: add translucent Sefaria reader overlay activity"
```

---

### Task 8: Wire the two widget pills + on-device UAT

**Files:**
- Modify: `app/src/main/kotlin/com/hebrewcal/ui/widget/HebrewDateWidget.kt`

**Interfaces:**
- Consumes: `TehillimSchedule`, `HebrewCalendarRepository.upcomingParshaName`, `TextReaderActivity` extras, `HebrewDateFormatter.formatHebrewNumber`.

- [ ] **Step 1: Impact check**

Run `gitnexus_impact` on `HebrewDateWidgetContent`; confirm the widget is the only consumer.

- [ ] **Step 2: Add a chapter-range label helper**

At file scope in `HebrewDateWidget.kt`:

```kotlin
private fun chapterRangeLabel(first: Int, last: Int, hebrew: Boolean): String {
    val fmt = com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter()
    fun n(x: Int) = if (hebrew) fmt.formatHebrewNumber(x) else x.toString()
    return if (first == last) n(first) else "${n(first)}–${n(last)}"
}
```

- [ ] **Step 3: Compute pill data in `provideGlance`**

In `provideGlance`, after `dateInfo` is built, compute:

```kotlin
        val hebrew = prefs.language == CalendarLanguage.HEBREW
        val portion = TehillimSchedule.portionFor(dateInfo.hebrewDayNumber, dateInfo.daysInMonth)
        val tehillimLabel = (if (hebrew) "תהילים יומי" else "Daily Tehillim") + " · " +
            chapterRangeLabel(portion.firstChapter, portion.lastChapter, hebrew)
        val parshaName = calRepo.upcomingParshaName(now, prefs.location, prefs.language)
        val parshaLabel = parshaName?.let {
            (if (hebrew) "פרשת השבוע" else "Weekly Parsha") + " · " + it
        }
```

Pass `tehillimLabel`, `portion.sefariaRef`, `parshaLabel`, and `prefs` into `HebrewDateWidgetContent` (extend its parameter list), plus a `diaspora = prefs.location == CalendarLocation.DIASPORA` boolean.

- [ ] **Step 4: Render the pills**

Inside `HebrewDateWidgetContent`'s `Column`, after the existing content, add a Tehillim pill and (when `parshaLabel != null`) a parsha pill. Each pill is a `Text` wrapped so its own `clickable` launches the reader:

```kotlin
            Spacer(GlanceModifier.height(8.dp))
            Text(
                text = tehillimLabel,
                style = TextStyle(color = goldColor, fontSize = TextUnit(12f, TextUnitType.Sp), fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.clickable(
                    actionStartActivity(
                        Intent(context, TextReaderActivity::class.java).apply {
                            putExtra(TextReaderActivity.EXTRA_MODE, "tehillim")
                            putExtra(TextReaderActivity.EXTRA_REF, tehillimRef)
                            putExtra(TextReaderActivity.EXTRA_TITLE, tehillimLabel)
                            putExtra(TextReaderActivity.EXTRA_LANG, prefs.language.name)
                            putExtra(TextReaderActivity.EXTRA_NIKKUD, true)
                            putExtra(TextReaderActivity.EXTRA_TEAMIM, false)
                        }
                    )
                )
            )
            if (parshaLabel != null) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = parshaLabel,
                    style = TextStyle(color = goldColor, fontSize = TextUnit(12f, TextUnitType.Sp), fontWeight = FontWeight.Medium),
                    modifier = GlanceModifier.clickable(
                        actionStartActivity(
                            Intent(context, TextReaderActivity::class.java).apply {
                                putExtra(TextReaderActivity.EXTRA_MODE, "parsha")
                                putExtra(TextReaderActivity.EXTRA_REF, "")
                                putExtra(TextReaderActivity.EXTRA_TITLE, parshaLabel)
                                putExtra(TextReaderActivity.EXTRA_DIASPORA, diaspora)
                                putExtra(TextReaderActivity.EXTRA_LANG, prefs.language.name)
                                putExtra(TextReaderActivity.EXTRA_NIKKUD, true)
                                putExtra(TextReaderActivity.EXTRA_TEAMIM, true)
                            }
                        )
                    )
                )
            }
```

Add imports: `import com.hebrewcal.data.TehillimSchedule`, `import com.hebrewcal.ui.reader.TextReaderActivity`, `import com.hebrewcal.data.CalendarLocation`. (`CalendarLanguage`, `Intent`, `actionStartActivity` are already imported.)

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: gitnexus detect + commit**

Run `gitnexus_detect_changes()`; confirm scope is the widget.

```bash
git add app/src/main/kotlin/com/hebrewcal/ui/widget/HebrewDateWidget.kt
git commit -m "feat: add Tehillim and Parsha pills to the widget"
```

- [ ] **Step 7: On-device UAT (manual)**

Install (`./gradlew installDebug`) and, with the widget on the home screen, verify:
1. Tehillim pill shows today's range in the app language (gematria in Hebrew, numerals in English).
2. Parsha pill shows the upcoming parsha name.
3. Tapping each opens the translucent reader (50% grey scrim, white text) — not Settings.
4. Tehillim opens with nikkud, no te'amim; Parsha opens with nikkud + te'amim.
5. Language toggle flips He⇄En instantly; nikkud/te'amim chips add/remove marks instantly; te'amim disabled when nikkud off.
6. Text-size slider resizes and the size persists across reopen.
7. Fast-scroll thumb jumps through a long portion (e.g. day 25, Psalm 119).
8. Airplane mode after one successful open → reopening the same portion still shows text (cache); an un-fetched portion shows the offline message.
9. Back button and tapping the scrim dismiss the reader.

---

### Task 9: Version bump

**Files:**
- Modify: `app/build.gradle` (versionCode / versionName)

- [ ] **Step 1: Bump version**

In `app/build.gradle`, increment `versionCode` by 1 and set `versionName` to the next value (follow the existing scheme, e.g. `1.4`).

- [ ] **Step 2: Commit**

```bash
git add app/build.gradle
git commit -m "chore: bump version for Tehillim + Parsha reader"
```

---

## Self-Review notes

- **Spec coverage:** division table → Task 2; vocalization stripping → Task 1; text fetch/parse/cache → Tasks 3 & 6; parsha name (offline) → Task 5; parsha ref (calendars API) → Tasks 4 & 6; reader overlay with slider/fast-scroll/toggles → Task 7; two widget pills + labels → Task 8; INTERNET permission → Task 6; text-size persistence → Task 7. All spec sections mapped.
- **Type consistency:** `SefariaText`/`Chapter`/`Verse` (Task 3) consumed unchanged by Tasks 6–7; `ParshaRef` (Task 4) consumed by Task 6; `TehillimPortion.sefariaRef/firstChapter/lastChapter` (Task 2) consumed by Task 8; `TextReaderActivity` EXTRA_* constants defined in Task 7 and used in Task 8 match.
- **Known follow-ups for the executor:** confirm Sefaria version identifier strings resolve (fall back to defaults); the `he`/`text` version params use the classic API which returns defaults if a version name mismatches — verify the fetched Hebrew actually carries te'amim during UAT, and adjust `heVersion` if Sefaria renamed it.
```
