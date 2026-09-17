# Calendar screen, reader continuity, fasts, and fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship version 1.14 with a Hebrew calendar overlay screen, a reader that flips Tehillim⇄Torah, scroll-extends through chapters, and opens the week's haftarah, fast start/end times, a notification that fits, and a widget that renders.

**Architecture:** Pure Kotlin logic lives in `data/` with JVM tests (`ReaderWindow`, `FastSchedule`, `CalendarMonth`, parser changes). `ReaderViewModel` owns a chapter *window* that grows in either direction. `CalendarActivity` is a new Compose activity on the translucent reader theme, computing month cells with KosherJava and reusing existing repositories for the day sheet. Notification and widget fixes are contained to their builders.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2024.02.00, Material3), Glance 1.0.0, KosherJava zmanim 2.4.0, kotlinx-serialization, JUnit 4, Gradle (`./gradlew --offline`).

**Spec:** `docs/superpowers/specs/2026-09-17-calendar-reader-fasts-release-design.md`

## Global Constraints

- Build/test offline: `./gradlew --offline assembleDebug testDebugUnitTest -q`.
- No new permissions. `ACCESS_NETWORK_STATE` stays removed (`tools:node="remove"`).
- No new libraries.
- Version for this release: `versionCode 21`, `versionName "1.14"` (Task 10 only).
- Subagents must run in the foreground (background agents cannot edit in this repo).
- All user-facing Hebrew/English pairs follow `hebrew = language == HEBREW` as elsewhere.
- Commits use the Nimbalyst commit-proposal tool (the user reviews each). GitNexus MCP is down this session; skip its impact checks and say so in the commit reasoning.
- Reader modes are strings: `"tehillim"`, `"parsha"`, and new `"haftarah"`.

---

### Task 1: Haftarot in the calendars parser

**Files:**
- Modify: `app/src/main/kotlin/com/hebrewcal/data/ParshaRef.kt`
- Modify: `app/src/main/kotlin/com/hebrewcal/data/ParshaCalendarsParser.kt`
- Test: `app/src/test/kotlin/com/hebrewcal/data/ParshaCalendarsParserTest.kt`

**Interfaces:**
- Produces: `data class Reading(val ref: String, val dottedRef: String, val nameEn: String, val nameHe: String)`; `ParshaRef.haftarot: List<Reading>` (default empty).

- [ ] **Step 1: Write the failing test** (append to the existing test class)

```kotlin
    @Test fun parsesEveryHaftarahItem() {
        val json = """{"calendar_items":[
          {"title":{"en":"Parashat Hashavua"},"displayValue":{"en":"Ha'Azinu","he":"האזינו"},
           "ref":"Deuteronomy 32:1-52","url":"Deuteronomy.32.1-52","heRef":"דברים ל״ב"},
          {"title":{"en":"Haftarah"},"displayValue":{"en":"Hosea 14:2-10","he":"הושע י״ד:ב׳-י׳"},
           "ref":"Hosea 14:2-10","url":"Hosea.14.2-10"},
          {"title":{"en":"Haftarah (S)"},"displayValue":{"en":"Micah 7:18-20","he":"מיכה ז׳"},
           "ref":"Micah 7:18-20"},
          {"title":{"en":"Daf Yomi"},"displayValue":{"en":"x"},"ref":"Berakhot 2"}
        ]}"""
        val p = ParshaCalendarsParser.parse(json)!!
        assertEquals(2, p.haftarot.size)
        assertEquals("Hosea.14.2-10", p.haftarot[0].dottedRef)
        assertEquals("Micah.7.18-20", p.haftarot[1].dottedRef)   // dotted form derived when url is absent
        assertEquals("הושע י״ד:ב׳-י׳", p.haftarot[0].nameHe)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --offline testDebugUnitTest --tests 'com.hebrewcal.data.ParshaCalendarsParserTest' -q`
Expected: compile error `Unresolved reference: haftarot`.

- [ ] **Step 3: Implement**

`ParshaRef.kt`:
```kotlin
package com.hebrewcal.data

/** One reading from Sefaria's calendar (a haftarah), in both ref forms. */
data class Reading(val ref: String, val dottedRef: String, val nameEn: String, val nameHe: String)

data class ParshaRef(
    val sefariaRef: String,
    val heRef: String,
    val nameEn: String,
    val nameHe: String,
    val aliyot: List<String> = emptyList(),
    val haftarot: List<Reading> = emptyList()
)
```

`ParshaCalendarsParser.parse`: after computing `items`, add before the `return ParshaRef(...)`:
```kotlin
        fun str(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.contentOrNull
        val haftarot = items.mapNotNull { it as? JsonObject }
            .filter { str(it["title"] as? JsonObject, "en")?.startsWith("Haftarah") == true }
            .mapNotNull { h ->
                val r = str(h, "ref") ?: return@mapNotNull null
                val dv = h["displayValue"] as? JsonObject
                Reading(
                    ref = r,
                    dottedRef = str(h, "url") ?: r.replace(" ", ".").replace(":", "."),
                    nameEn = str(dv, "en") ?: r,
                    nameHe = str(dv, "he") ?: r
                )
            }
```
and pass `haftarot = haftarot` to the constructor. (`contentOrNull` is in `kotlinx.serialization.json.*`, already imported.)

- [ ] **Step 4: Run tests**

Run: `./gradlew --offline testDebugUnitTest -q` — Expected: all pass.

- [ ] **Step 5: Commit** — `feat: parse haftarah readings from Sefaria calendars`

---

### Task 2: Fast schedule and fast times

**Files:**
- Create: `app/src/main/kotlin/com/hebrewcal/data/FastSchedule.kt`
- Modify: `app/src/main/kotlin/com/hebrewcal/data/ZmanimRepository.kt` (add `getFastTimes`)
- Modify: `app/src/main/kotlin/com/hebrewcal/data/HebrewCalendarRepository.kt:29-84,118-215`
- Modify: `app/src/main/kotlin/com/hebrewcal/service/CalendarNotificationService.kt:107-114`
- Modify: `app/src/main/kotlin/com/hebrewcal/ui/widget/HebrewDateWidget.kt:52-58`
- Test: `app/src/test/kotlin/com/hebrewcal/data/FastScheduleTest.kt`

**Interfaces:**
- Produces: `enum class FastKind { MAJOR, MINOR }`; `FastSchedule.kindFor(yomTovIndex: Int): FastKind?`; `FastSchedule.label(begin: String, end: String) = "($begin–$end)"`; `data class FastTimes(val begin: Date, val end: Date)`; `ZmanimRepository.getFastTimes(kind, lat, lng, tz, date): FastTimes?`; `getDateInfo(..., fastTimes: ((FastKind) -> String?)? = null)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FastScheduleTest {
    @Test fun majorFasts() {
        assertEquals(FastKind.MAJOR, FastSchedule.kindFor(JewishCalendar.YOM_KIPPUR))
        assertEquals(FastKind.MAJOR, FastSchedule.kindFor(JewishCalendar.TISHA_BEAV))
    }
    @Test fun minorFasts() {
        for (i in listOf(JewishCalendar.FAST_OF_GEDALYAH, JewishCalendar.TENTH_OF_TEVES,
                         JewishCalendar.FAST_OF_ESTHER, JewishCalendar.SEVENTEEN_OF_TAMMUZ))
            assertEquals(FastKind.MINOR, FastSchedule.kindFor(i))
    }
    @Test fun nonFastsHaveNoKind() {
        assertNull(FastSchedule.kindFor(JewishCalendar.PURIM))
        assertNull(FastSchedule.kindFor(-1))
    }
    @Test fun labelIsParenthesizedRange() {
        assertEquals("(5:41 AM–5:12 PM)", FastSchedule.label("5:41 AM", "5:12 PM"))
    }
    @Test fun fastTimesAppendToHolidayName() {
        // 10 Tevet 5787 = 2026-12-20 (Sunday).
        val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse("2026-12-20")!!
        val info = HebrewCalendarRepository().getDateInfo(
            d, CalendarLanguage.ENGLISH, CalendarLocation.DIASPORA,
            fastTimes = { kind -> if (kind == FastKind.MINOR) "(5:41 AM–5:12 PM)" else null }
        )
        assertEquals("Asara B'Tevet (5:41 AM–5:12 PM)", info.holidayName)
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `Unresolved reference: FastSchedule`.

- [ ] **Step 3: Implement**

`FastSchedule.kt`:
```kotlin
package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar

enum class FastKind { MAJOR, MINOR }

/**
 * Major fasts (Yom Kippur, Tisha B'Av) run from sunset the evening before to
 * tzeit hakochavim; minor fasts run from alot hashachar to tzeit.
 */
object FastSchedule {
    fun kindFor(yomTovIndex: Int): FastKind? = when (yomTovIndex) {
        JewishCalendar.YOM_KIPPUR, JewishCalendar.TISHA_BEAV -> FastKind.MAJOR
        JewishCalendar.FAST_OF_GEDALYAH, JewishCalendar.TENTH_OF_TEVES,
        JewishCalendar.FAST_OF_ESTHER, JewishCalendar.SEVENTEEN_OF_TAMMUZ -> FastKind.MINOR
        else -> null
    }

    fun label(begin: String, end: String) = "($begin–$end)"
}
```

`ZmanimRepository.kt` — add after `getTzetHakochavim`:
```kotlin
    data class FastTimes(val begin: Date, val end: Date)

    /** Begin/end of a fast on [date]; null without a location or when the sun times are undefined. */
    fun getFastTimes(
        kind: FastKind, latitude: Double, longitude: Double,
        timeZone: TimeZone = TimeZone.getDefault(), date: Date = Date()
    ): FastTimes? {
        if (latitude == 0.0 && longitude == 0.0) return null
        val geo = GeoLocation("User Location", latitude, longitude, 0.0, timeZone)
        val today = ComplexZmanimCalendar(geo).apply { calendar.time = date }
        val end = today.tzais ?: return null
        val begin = when (kind) {
            FastKind.MINOR -> today.alosHashachar
            FastKind.MAJOR -> ComplexZmanimCalendar(geo).apply {
                calendar.time = date; calendar.add(Calendar.DATE, -1)
            }.sunset
        } ?: return null
        return FastTimes(begin, end)
    }

    fun formatTime(d: Date, format: ZmanimTimeFormat): String =
        SimpleDateFormat(if (format == ZmanimTimeFormat.TWELVE_HOUR) "h:mm a" else "HH:mm", Locale.getDefault()).format(d)
```
(add `import java.util.Calendar` if missing.)

`HebrewCalendarRepository.getDateInfo`: add parameter `fastTimes: ((FastKind) -> String?)? = null` after `advanceAfter`. Change the holiday line to:
```kotlin
        val holidayInfo = getHolidayInfo(jewishCalendar, language)?.let { h ->
            val kind = FastSchedule.kindFor(jewishCalendar.yomTovIndex)
            val suffix = if (kind != null) fastTimes?.invoke(kind) else null
            if (suffix != null) h.copy(name = "${h.name} $suffix") else h
        }
```

`CalendarNotificationService.kt` (the `getDateInfo` call ~line 108) and `HebrewDateWidget.provideGlance` (~line 52): pass
```kotlin
                        fastTimes = { kind ->
                            zmanimRepo.getFastTimes(kind, lat, lng, timeZone, now)?.let {
                                FastSchedule.label(zmanimRepo.formatTime(it.begin, prefs.zmanimTimeFormat),
                                                   zmanimRepo.formatTime(it.end, prefs.zmanimTimeFormat))
                            }
                        }
```
(in the widget, `lat`/`lng` are `prefs.zmanimManualLat`/`prefs.zmanimManualLng`). Note: the fast date must be the *halachic* date; since `getDateInfo` may advance the calendar past tzeit, compute times for `jewishCalendar.gregorianCalendar.time` instead of `now` — add that: inside `getDateInfo` call the provider with the advanced date by changing the provider type to `((FastKind, Date) -> String?)?` and invoking `fastTimes?.invoke(kind, jewishCalendar.gregorianCalendar.time)`. Update the test lambda to `{ kind, _ -> ... }` and the two call sites to `{ kind, date -> zmanimRepo.getFastTimes(kind, lat, lng, timeZone, date)... }`.

- [ ] **Step 4: Run tests** — all pass.
- [ ] **Step 5: Commit** — `feat: show fast begin and end times next to fast names`

---

### Task 3: Reader window logic (pure)

**Files:**
- Create: `app/src/main/kotlin/com/hebrewcal/data/ReaderWindow.kt`
- Test: `app/src/test/kotlin/com/hebrewcal/data/ReaderWindowTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class ReaderWindow(val bookEn: String, val chapters: List<Chapter>, val firstChapter: Int, val lastChapter: Int, val firstPartial: Boolean, val lastPartial: Boolean)
object ReaderWindows {
    fun fromRef(ref: String, text: SefariaText): ReaderWindow
    fun nextFetch(w: ReaderWindow): Pair<String, Int>?        // (bookEn, chapter) to fetch, null at the end
    fun prevFetch(w: ReaderWindow): Pair<String, Int>?
    fun appended(w: ReaderWindow, bookEn: String, ch: Chapter): ReaderWindow
    fun prepended(w: ReaderWindow, bookEn: String, ch: Chapter): ReaderWindow
    fun chapterCount(bookEn: String): Int?                    // Torah books + Psalms; null otherwise
    fun torahRange(w: ReaderWindow): String?                  // "Genesis.1.1-6.22" style ref for tikkun
}
```
Semantics: Torah books chain Genesis→Exodus→Leviticus→Numbers→Deuteronomy (using `TorahNavigation.BOOKS`); Psalms 1..150; other books: `nextFetch` returns `(book, last+1)` and `prevFetch` `(book, first-1)` while `first > 1`, callers stop on fetch failure. Partial edge: `nextFetch` returns `(book, last)` itself when `lastPartial` (refetch whole), and `appended` with `ch.number == lastChapter` replaces the last chapter and clears `lastPartial`. A window whose chapters span two books keeps `bookEn` of the *last* chapter and tracks the first book in `firstBookEn` (add that field; default = bookEn).

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderWindowTest {
    private fun ch(n: Int, verses: Int, from: Int = 1) =
        Chapter(n, (from until from + verses).map { Verse(it, "he$it", "en$it") })

    @Test fun parshaRefWithPartialEdges() {
        val w = ReaderWindows.fromRef("Genesis.1.1-6.8", SefariaText("x", listOf(ch(1, 31), ch(2, 25), ch(3, 24), ch(4, 26), ch(5, 32), ch(6, 8))))
        assertEquals("Genesis", w.bookEn); assertEquals(1, w.firstChapter); assertEquals(6, w.lastChapter)
        assertEquals(false, w.firstPartial)   // starts at 1:1
        assertEquals(true, w.lastPartial)     // 6:8 is not the end of chapter 6 (22 verses)
    }

    @Test fun wholeChapterRangeIsNotPartial() {
        val w = ReaderWindows.fromRef("Psalms.1-9", SefariaText("x", (1..9).map { ch(it, 5) }))
        assertEquals(false, w.firstPartial); assertEquals(false, w.lastPartial)
    }

    @Test fun partialTailRefetchesItself() {
        val w = ReaderWindows.fromRef("Genesis.1.1-6.8", SefariaText("x", listOf(ch(6, 8))))
        assertEquals("Genesis" to 6, ReaderWindows.nextFetch(w))
        val w2 = ReaderWindows.appended(w, "Genesis", ch(6, 22))
        assertEquals(false, w2.lastPartial); assertEquals(22, w2.chapters.last().verses.size)
        assertEquals("Genesis" to 7, ReaderWindows.nextFetch(w2))
    }

    @Test fun torahChainsAcrossBooksAndEndsAtDeuteronomy() {
        val gen50 = ReaderWindows.fromRef("Genesis.50", SefariaText("x", listOf(ch(50, 26))))
        assertEquals("Exodus" to 1, ReaderWindows.nextFetch(gen50))
        val deut34 = ReaderWindows.fromRef("Deuteronomy.34", SefariaText("x", listOf(ch(34, 12))))
        assertNull(ReaderWindows.nextFetch(deut34))
        val ex1 = ReaderWindows.fromRef("Exodus.1", SefariaText("x", listOf(ch(1, 22))))
        assertEquals("Genesis" to 50, ReaderWindows.prevFetch(ex1))
    }

    @Test fun psalmsStopAtBothEnds() {
        assertNull(ReaderWindows.nextFetch(ReaderWindows.fromRef("Psalms.150", SefariaText("x", listOf(ch(150, 6))))))
        assertNull(ReaderWindows.prevFetch(ReaderWindows.fromRef("Psalms.1", SefariaText("x", listOf(ch(1, 6))))))
    }

    @Test fun prependKeepsOrderAndFirstBook() {
        val ex1 = ReaderWindows.fromRef("Exodus.1", SefariaText("x", listOf(ch(1, 22))))
        val w = ReaderWindows.prepended(ex1, "Genesis", ch(50, 26))
        assertEquals(listOf(50, 1), w.chapters.map { it.number })
        assertEquals("Genesis", w.firstBookEn); assertEquals("Exodus", w.bookEn)
        assertEquals("Genesis" to 49, ReaderWindows.prevFetch(w))
    }

    @Test fun tikkunRangeCoversTheWindow() {
        val w = ReaderWindows.fromRef("Genesis.1.1-6.8", SefariaText("x", listOf(ch(5, 32), ch(6, 8))))
        assertEquals("Genesis.5.1-6.8", ReaderWindows.torahRange(w))
        assertNull(ReaderWindows.torahRange(ReaderWindows.fromRef("Psalms.3", SefariaText("x", listOf(ch(3, 9))))))
    }
}
```

- [ ] **Step 2: Run to verify failure** — `Unresolved reference: ReaderWindows`.

- [ ] **Step 3: Implement** `ReaderWindow.kt`

```kotlin
package com.hebrewcal.data

data class ReaderWindow(
    val bookEn: String,
    val chapters: List<Chapter>,
    val firstChapter: Int,
    val lastChapter: Int,
    val firstPartial: Boolean,
    val lastPartial: Boolean,
    val firstBookEn: String = bookEn
)

/** Pure bookkeeping for a reader that grows chapter by chapter in either direction. */
object ReaderWindows {
    private val torah = TorahNavigation.BOOKS.map { it.en }

    fun chapterCount(bookEn: String): Int? =
        if (bookEn == "Psalms") TorahNavigation.TEHILLIM_CHAPTERS
        else TorahNavigation.BOOKS.firstOrNull { it.en == bookEn }?.chapters

    /** Verse counts for the partial-edge test; Sefaria refs mark partial edges with a verse component. */
    fun fromRef(ref: String, text: SefariaText): ReaderWindow {
        val book = ref.substringBefore('.')
        val spec = ref.substringAfter('.', "")
        val (startSpec, endSpec) = spec.split('-', limit = 2).let { it[0] to it.getOrElse(1) { it[0] } }
        val startVerse = startSpec.split('.').getOrNull(1)?.toIntOrNull()
        val endVerse = endSpec.split('.').getOrNull(1)?.toIntOrNull()
        val chapters = text.chapters
        val first = chapters.firstOrNull()?.number ?: startSpec.substringBefore('.').toIntOrNull() ?: 1
        val last = chapters.lastOrNull()?.number ?: first
        return ReaderWindow(
            bookEn = book, chapters = chapters, firstChapter = first, lastChapter = last,
            firstPartial = startVerse != null && startVerse > 1,
            // A verse-bounded end is partial unless the fetched last chapter shows the range ran to its end;
            // we cannot know a chapter's full length offline, so any explicit end verse counts as partial.
            lastPartial = endVerse != null
        )
    }

    fun nextFetch(w: ReaderWindow): Pair<String, Int>? {
        if (w.lastPartial) return w.bookEn to w.lastChapter
        val count = chapterCount(w.bookEn)
        if (count == null || w.lastChapter < count) return w.bookEn to w.lastChapter + 1
        val i = torah.indexOf(w.bookEn)
        return if (i in 0 until torah.lastIndex) torah[i + 1] to 1 else null
    }

    fun prevFetch(w: ReaderWindow): Pair<String, Int>? {
        if (w.firstPartial) return w.firstBookEn to w.firstChapter
        if (w.firstChapter > 1) return w.firstBookEn to w.firstChapter - 1
        val i = torah.indexOf(w.firstBookEn)
        return if (i > 0) torah[i - 1] to chapterCount(torah[i - 1])!! else null
    }

    fun appended(w: ReaderWindow, bookEn: String, ch: Chapter): ReaderWindow {
        val replacing = w.lastPartial && bookEn == w.bookEn && ch.number == w.lastChapter
        val chapters = if (replacing) w.chapters.dropLast(1) + ch else w.chapters + ch
        return w.copy(bookEn = bookEn, chapters = chapters, lastChapter = ch.number, lastPartial = false)
    }

    fun prepended(w: ReaderWindow, bookEn: String, ch: Chapter): ReaderWindow {
        val replacing = w.firstPartial && bookEn == w.firstBookEn && ch.number == w.firstChapter
        val chapters = if (replacing) listOf(ch) + w.chapters.drop(1) else listOf(ch) + w.chapters
        return w.copy(firstBookEn = bookEn, chapters = chapters, firstChapter = ch.number, firstPartial = false)
    }

    /** Dotted Torah ref spanning the window (single book only), for tikkun layout lookup. */
    fun torahRange(w: ReaderWindow): String? {
        if (w.bookEn !in torah || w.firstBookEn != w.bookEn || w.chapters.isEmpty()) return null
        val f = w.chapters.first(); val l = w.chapters.last()
        return "${w.bookEn}.${f.number}.${f.verses.first().num}-${l.number}.${l.verses.last().num}"
    }
}
```
Note on `fromRef` for the first test: `Genesis.1.1-6.8` has `startVerse = 1` (not partial) and `endVerse = 8` (partial) — matches. `Psalms.1-9` has no verse parts — not partial. A single-chapter ref like `Genesis.50` yields `first = last = 50`.

- [ ] **Step 4: Run tests** — all pass. If `torahRange` disagrees with the test, the test is authoritative.
- [ ] **Step 5: Commit** — `feat: pure reader window logic for chapter-by-chapter extension`

---

### Task 4: ViewModel — window, extension, mode memory, haftarah, week info

**Files:**
- Modify: `app/src/main/kotlin/com/hebrewcal/ui/reader/ReaderViewModel.kt` (whole file rewrite of state/load)

**Interfaces:**
- Consumes: Task 1 `ParshaRef.haftarot`, Task 3 `ReaderWindows`, `SefariaRepository.fetchText/currentParsha`, `TikkunLayoutRepository.linesFor`, `TikkunLayout.parseTorahRef`.
- Produces (used by Task 5):
```kotlin
data class UiState(loading, error, title, text: SefariaText?, mode, bookNameEn, aliyot, elulText, tikkunLines,
                   extendingUp: Boolean = false, extendingDown: Boolean = false, prependedSections: Int = 0)
data class WeekInfo(val parsha: ParshaRef?)     // for banner + haftarah button
val week: StateFlow<WeekInfo>
fun extendDown(); fun extendUp()
fun toggleMode(hebrew: Boolean)                 // Tehillim ⇄ Torah using session memory
fun openHaftarah(r: Reading, hebrew: Boolean)
```

- [ ] **Step 1: Rewrite `ReaderViewModel`**

Replace the `UiState`, `_state`, `loadedKey`, `load`, `navigateTo` block with:

```kotlin
    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val title: String = "",
        val text: SefariaText? = null,
        val mode: String = "tehillim",
        val bookNameEn: String? = null,
        val aliyot: List<String> = emptyList(),
        val elulText: SefariaText? = null,
        val tikkunLines: List<TikkunLayout.Line>? = null,
        val extendingUp: Boolean = false,
        val extendingDown: Boolean = false,
        /** Sections added at the top by the last prepend; the UI consumes this to keep its scroll still. */
        val prependedSections: Int = 0
    )
    data class WeekInfo(val parsha: ParshaRef? = null)
    private data class Position(val ref: String, val title: String, val aliyot: List<String>)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private val _week = MutableStateFlow(WeekInfo())
    val week: StateFlow<WeekInfo> = _week

    private var window: ReaderWindow? = null
    private var loadedKey: String? = null
    private var diasporaPref = true
    private var elulRefPref = ""
    private var lastTehillim: Position? = null
    private var lastTorah: Position? = null
    private var extendJob: Job? = null

    fun load(mode: String, ref: String, title: String, diaspora: Boolean, elulRef: String = "",
             presetAliyot: List<String> = emptyList()) {
        val key = "$mode|$ref|$diaspora|$elulRef"
        if (loadedKey == key) return
        loadedKey = key
        diasporaPref = diaspora
        if (mode == "tehillim") elulRefPref = elulRef
        extendJob?.cancel()
        _state.value = UiState(loading = true, title = title, mode = mode)
        ensureWeek()
        viewModelScope.launch {
            val resolvedRef: String
            var resolvedTitle = title
            var aliyot: List<String> = presetAliyot
            if (mode == "parsha" && ref.isBlank()) {
                val p = repo.currentParsha(diaspora).getOrNull()
                if (p == null) { _state.value = UiState(loading = false, error = OFFLINE, title = title, mode = mode); return@launch }
                resolvedRef = p.sefariaRef; aliyot = p.aliyot
                if (resolvedTitle.isBlank()) resolvedTitle = p.heRef
            } else resolvedRef = ref
            val bookNameEn = if (mode != "tehillim") resolvedRef.substringBefore('.', "").ifBlank { null } else null
            repo.fetchText(resolvedRef).fold(
                onSuccess = { text ->
                    val w = ReaderWindows.fromRef(resolvedRef, text); window = w
                    val elulText = if (mode == "tehillim" && elulRef.isNotBlank()) repo.fetchText(elulRef).getOrNull() else null
                    val tikkunLines = if (mode == "parsha") TikkunLayout.parseTorahRef(resolvedRef)?.let { tikkunRepo.linesFor(it) } else null
                    val finalTitle = resolvedTitle.ifBlank { text.heRef }
                    val pos = Position(resolvedRef, finalTitle, aliyot)
                    if (mode == "tehillim") lastTehillim = pos else lastTorah = pos
                    _state.value = UiState(false, null, finalTitle, text, mode, bookNameEn, aliyot, elulText, tikkunLines)
                },
                onFailure = { _state.value = UiState(false, OFFLINE, resolvedTitle, null, mode, bookNameEn, aliyot) }
            )
        }
    }

    /** Jump to any Tehillim chapter or Torah chapter/parsha from the navigation dial. */
    fun navigateTo(mode: String, ref: String, title: String, aliyot: List<String> = emptyList()) =
        load(mode, ref, title, diaspora = diasporaPref, elulRef = if (mode == "tehillim") elulRefPref else "", presetAliyot = aliyot)

    /** Tehillim ⇄ Torah: the last place visited in that mode this session, else today's / this week's. */
    fun toggleMode(hebrew: Boolean) {
        val s = _state.value
        if (s.mode == "tehillim") {
            val p = lastTorah
            if (p != null) navigateTo("parsha", p.ref, p.title, p.aliyot)
            else load("parsha", "", if (hebrew) "פרשת השבוע" else "Weekly Parsha", diasporaPref)
        } else {
            val p = lastTehillim
            if (p != null) navigateTo("tehillim", p.ref, p.title)
            else {
                val jd = com.kosherjava.zmanim.hebrewcalendar.JewishDate()
                val portion = TehillimSchedule.portionFor(jd.jewishDayOfMonth, jd.daysInJewishMonth)
                navigateTo("tehillim", portion.sefariaRef, ReaderButtons.tehillimLabel(portion, hebrew))
            }
        }
    }

    fun openHaftarah(r: Reading, hebrew: Boolean) =
        load("haftarah", r.dottedRef, (if (hebrew) "הפטרה · " else "Haftarah · ") + (if (hebrew) r.nameHe else r.nameEn), diasporaPref)

    private fun ensureWeek() {
        if (_week.value.parsha != null) return
        viewModelScope.launch { repo.currentParsha(diasporaPref).getOrNull()?.let { _week.value = WeekInfo(it) } }
    }

    fun extendDown() = extend(down = true)
    fun extendUp() = extend(down = false)

    private fun extend(down: Boolean) {
        val s = _state.value
        val w = window ?: return
        if (s.loading || s.error != null || extendJob?.isActive == true) return
        val target = (if (down) ReaderWindows.nextFetch(w) else ReaderWindows.prevFetch(w)) ?: return
        _state.value = s.copy(extendingDown = down, extendingUp = !down, prependedSections = 0)
        extendJob = viewModelScope.launch {
            val (book, chNum) = target
            val fetched = repo.fetchText("$book.$chNum").getOrNull()?.chapters?.firstOrNull()
            val cur = _state.value
            if (fetched == null || fetched.verses.isEmpty()) {
                _state.value = cur.copy(extendingDown = false, extendingUp = false); return@launch
            }
            val grown = if (down) ReaderWindows.appended(w, book, fetched) else ReaderWindows.prepended(w, book, fetched)
            window = grown
            val text = SefariaText(cur.text?.heRef ?: "", grown.chapters)
            val tikkun = if (cur.mode == "parsha") ReaderWindows.torahRange(grown)?.let { TikkunLayout.parseTorahRef(it) }?.let { tikkunRepo.linesFor(it) } else cur.tikkunLines
            val added = grown.chapters.size - w.chapters.size
            _state.value = cur.copy(
                text = text, tikkunLines = tikkun, bookNameEn = if (down) grown.bookEn else cur.bookNameEn,
                extendingDown = false, extendingUp = false, prependedSections = if (down) 0 else added
            )
        }
    }

    fun consumedPrepend() { _state.value = _state.value.copy(prependedSections = 0) }
```
Add imports: `kotlinx.coroutines.Job`, `com.hebrewcal.data.*` (already wildcard? if not, add `ReaderWindow`, `ReaderWindows`, `Reading`, `ParshaRef`, `TehillimSchedule`, `ReaderButtons`, `SefariaText`).

Aliyah partition note: `aliyot` stays as loaded; `AliyotPartitioner.partition` runs over the full chapter list, so verses outside the parsha's aliyot fall outside every partition. Task 5 renders those leftover chapters as chapter sections (see Step 3 there).

- [ ] **Step 2: Build** — `./gradlew --offline assembleDebug -q` compiles (the UI still ignores the new fields).
- [ ] **Step 3: Commit** — `feat: reader view model grows a chapter window and remembers mode positions`

---

### Task 5: Reader UI — mode toggle, scroll extension, haftarah button, week banner

**Files:**
- Modify: `app/src/main/kotlin/com/hebrewcal/ui/reader/TextReaderActivity.kt` (ReaderBar, ReaderBody, activity body; new composables `ModeToggle`, `WeekBanner`, `HaftarahChooser`)

**Interfaces:**
- Consumes: Task 4 `UiState.extendingUp/Down/prependedSections`, `week`, `extendUp/extendDown/toggleMode/openHaftarah/consumedPrepend`.

- [ ] **Step 1: Mode toggle composable** (add near `FlameGoButton`)

```kotlin
/** 44×22dp Tehillim ⇄ Torah switch: glass track, harp glyph (Tehillim) and scroll glyph (Torah), flame knob. */
@Composable
private fun ModeToggle(torah: Boolean, onToggle: () -> Unit) {
    val knobX by animateFloatAsState(if (torah) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "knob")
    val (phase, breath) = rememberFlame()
    Canvas(
        Modifier.padding(horizontal = 4.dp).size(44.dp, 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle() }
    ) {
        val h = size.height; val r = h / 2f
        drawRoundRect(Color.White.copy(alpha = 0.16f), cornerRadius = CornerRadius(r))
        drawRoundRect(Color.White.copy(alpha = 0.35f), cornerRadius = CornerRadius(r), style = Stroke(1.dp.toPx()))
        val faint = Color.White.copy(alpha = 0.55f)
        // Harp (left): a curved frame with three strings.
        val hx = r; val hy = r
        drawArc(faint, 200f, 200f, false, Offset(hx - 5.dp.toPx(), hy - 5.dp.toPx()), Size(10.dp.toPx(), 10.dp.toPx()), style = Stroke(1.2.dp.toPx()))
        for (i in -1..1) drawLine(faint, Offset(hx + i * 2.5f.dp.toPx(), hy - 3.dp.toPx()), Offset(hx + i * 2.5f.dp.toPx(), hy + 4.dp.toPx()), 1.dp.toPx())
        // Scroll (right): two rollers with a sheet between.
        val sx = size.width - r; val sy = r
        drawRoundRect(faint, Offset(sx - 6.dp.toPx(), sy - 3.dp.toPx()), Size(12.dp.toPx(), 6.dp.toPx()), CornerRadius(1.dp.toPx()), style = Stroke(1.dp.toPx()))
        drawLine(faint, Offset(sx - 6.dp.toPx(), sy - 5.dp.toPx()), Offset(sx - 6.dp.toPx(), sy + 5.dp.toPx()), 1.6f.dp.toPx())
        drawLine(faint, Offset(sx + 6.dp.toPx(), sy - 5.dp.toPx()), Offset(sx + 6.dp.toPx(), sy + 5.dp.toPx()), 1.6f.dp.toPx())
        // Flame knob.
        val kx = r + (size.width - h) * knobX
        drawCircle(Color(0xFFFF6A00).copy(alpha = 0.25f * breath.value), radius = r + 3.dp.toPx(), center = Offset(kx, r))
        rotate(phase.value * 360f, pivot = Offset(kx, r)) { drawCircle(Brush.sweepGradient(FLAME, center = Offset(kx, r)), radius = r - 2.dp.toPx(), center = Offset(kx, r)) }
        drawCircle(Color(0xFFFFB300), radius = r - 5.dp.toPx(), center = Offset(kx, r))
    }
}
```
Imports: `androidx.compose.animation.core.animateFloatAsState`, `androidx.compose.animation.core.spring`.

- [ ] **Step 2: Week banner and haftarah chooser**

```kotlin
/** Thin strip under the bar: this week's parsha and haftarah refs; tap a part to open it. */
@Composable
private fun WeekBanner(parsha: ParshaRef?, hebrew: Boolean, onParsha: () -> Unit, onHaftarah: () -> Unit) {
    if (parsha == null) return
    val sep = " · "
    val parshaText = (if (hebrew) "פרשת ${parsha.nameHe}$sep${parsha.heRef}" else "Parashat ${parsha.nameEn}$sep${parsha.sefariaRef.replace('.', ' ').replaceFirst(' ', ' ')}")
    val haft = parsha.haftarot.joinToString(", ") { if (hebrew) it.nameHe else it.nameEn }
    Row(
        Modifier.fillMaxWidth().height(18.dp).background(Color(0x59181818)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(parshaText, color = ACTIVE_GOLD, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onParsha() })
        if (haft.isNotBlank()) {
            Text(sep, color = INACTIVE, fontSize = 11.sp)
            Text((if (hebrew) "הפטרה: " else "Haftarah: ") + haft, color = Color(0xFF6EE7B7), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onHaftarah() })
        }
    }
}

/** Small popup listing the week's haftarot when there is more than one. */
@Composable
private fun HaftarahChooser(readings: List<Reading>, hebrew: Boolean, onPick: (Reading) -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }, Alignment.Center) {
        Column(Modifier.fillMaxWidth(0.8f).clip(RoundedCornerShape(14.dp)).background(Color(0xE6202020)).padding(8.dp)) {
            Text(if (hebrew) "הפטרה" else "Haftarah", color = ACTIVE_GOLD, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
            readings.forEach { r ->
                Text(if (hebrew) r.nameHe else r.nameEn, color = INACTIVE, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(r) }.padding(horizontal = 12.dp, vertical = 10.dp))
            }
        }
    }
}
```
For the English parsha ref, show `parsha.sefariaRef` with dots turned into `Deuteronomy 32:1-52` form: use `TorahNavigationDisplay`-free helper: `parsha.sefariaRef.replaceFirst('.', ' ').replace('.', ':').replace("-", "–")` is wrong for ranges spanning chapters; keep it simple: `parsha.sefariaRef.replaceFirst('.', ' ').replace('.', ':')` (gives `Genesis 1:1-6:8`). Use that instead of the `replace` chain above.

- [ ] **Step 3: ReaderBar changes**

Add parameters `torahSide: Boolean, onToggleMode: () -> Unit, showHaftarah: Boolean, onHaftarah: () -> Unit`. In the top row, before the open-book icon:
```kotlin
            ModeToggle(torah = torahSide, onToggle = onToggleMode)
            if (showHaftarah) TextButton(onClick = onHaftarah) { Text("ה", color = Color(0xFF6EE7B7), fontSize = 17.sp, fontWeight = FontWeight.Bold) }
```
In the expanded row, hide `תיקון` and `עליות` when `mode == "haftarah"`: pass `mode` in and wrap those toggles with `if (mode != "haftarah")`.

- [ ] **Step 4: ReaderBody — sections for extended chapters, edge rows, scroll extension**

Change the `sections` computation so chapters outside the aliyot partitions still render:
```kotlin
        val main = if (aliyot.isNotEmpty()) {
            val parts = AliyotPartitioner.partition(text.chapters, aliyot)
            if (parts.isNotEmpty()) {
                val covered = parts.flatMap { p -> p.verses.map { it.chapter to it.verse.num } }.toHashSet()
                val before = text.chapters.filter { ch -> ch.verses.none { (ch.number to it.num) in covered } && ch.number < parts.first().verses.first().chapter }
                val after = text.chapters.filter { ch -> ch.verses.none { (ch.number to it.num) in covered } && ch.number > parts.last().verses.last().chapter }
                chapterSections(SefariaText(text.heRef, before), mode, bookNameEn, hebrew, gematria) +
                parts.map { sec -> ReaderSection(AliyotPartitioner.aliyahName(sec.index, aliyot.size, hebrew), sec.verses, markChapters = true) } +
                chapterSections(SefariaText(text.heRef, after), mode, bookNameEn, hebrew, gematria)
            } else chapterSections(text, mode, bookNameEn, hebrew, gematria)
        } else chapterSections(text, mode, bookNameEn, hebrew, gematria)
```
Add parameters to `ReaderBody`: `extendingUp: Boolean, extendingDown: Boolean, prependedSections: Int, onExtendUp: () -> Unit, onExtendDown: () -> Unit, onPrependConsumed: () -> Unit`.

Nested scroll connection (inside `ReaderBody`, before the `Box`):
```kotlin
    val edgeScroll = remember(sections.size) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -2f && consumed.y == 0f) {
                    val info = listState.layoutInfo
                    if (info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1) onExtendDown()
                } else if (available.y > 2f && consumed.y == 0f && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    onExtendUp()
                }
                return Offset.Zero
            }
        }
    }
    // Keep the view still when sections are prepended.
    LaunchedEffect(prependedSections) {
        if (prependedSections > 0) {
            listState.scrollToItem(listState.firstVisibleItemIndex + prependedSections, listState.firstVisibleItemScrollOffset)
            onPrependConsumed()
        }
    }
```
Apply `.nestedScroll(edgeScroll)` to the `LazyColumn` modifier. In the list, add before `itemsIndexed`:
```kotlin
                if (extendingUp) item { EdgeLoading() }
```
and after the source line item: `if (extendingDown) item { EdgeLoading() }`, with
```kotlin
@Composable private fun EdgeLoading() {
    Box(Modifier.fillMaxWidth().padding(10.dp), Alignment.Center) { CircularProgressIndicator(color = ACTIVE_GOLD, modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
}
```
Because `extendingUp` inserts one item at index 0 while loading, the prepend correction must account for it: the `LaunchedEffect` above runs after `extendingUp` is already false and the loading row gone, so the correction is exactly `prependedSections` (the loading row's removal and the chapters' insertion land in the same state update). Pass the same three params through `TikkunScrollBody` too (append/prepend of lines works the same way with `lines.size`; use the same connection with `listState`), so tikkun mode extends as well.

Imports: `androidx.compose.ui.input.nestedscroll.NestedScrollConnection`, `NestedScrollSource`, `nestedScroll`.

- [ ] **Step 5: Activity wiring**

In `setContent`:
```kotlin
            val week by vm.week.collectAsState()
            var showHaftarahChooser by rememberSaveable { mutableStateOf(false) }
            val hebrewUi = textMode != MODE_EN
            fun haftarah() {
                val list = week.parsha?.haftarot ?: return
                when (list.size) { 0 -> {}; 1 -> vm.openHaftarah(list[0], hebrewUi); else -> showHaftarahChooser = true }
            }
```
ReaderBar call: add `torahSide = state.mode != "tehillim", onToggleMode = { vm.toggleMode(hebrewUi) }, showHaftarah = state.mode == "parsha" && !week.parsha?.haftarot.isNullOrEmpty(), onHaftarah = { haftarah() }, mode = state.mode`.
Right after `ReaderBar(...)`: `WeekBanner(week.parsha, hebrewUi, onParsha = { vm.load("parsha", "", if (hebrewUi) "פרשת השבוע" else "Weekly Parsha", diaspora) }, onHaftarah = { haftarah() })`.
ReaderBody call: add `extendingUp = state.extendingUp, extendingDown = state.extendingDown, prependedSections = state.prependedSections, onExtendUp = { vm.extendUp() }, onExtendDown = { vm.extendDown() }, onPrependConsumed = { vm.consumedPrepend() }`.
After the `if (showNav)` block: `if (showHaftarahChooser) HaftarahChooser(week.parsha!!.haftarot, hebrewUi, onPick = { showHaftarahChooser = false; vm.openHaftarah(it, hebrewUi) }, onDismiss = { showHaftarahChooser = false })`.
When mode is `"haftarah"` force `teamim = true; nikkud = true` on load: add `LaunchedEffect(state.mode) { if (state.mode == "haftarah") { nikkud = true; teamim = true; if (textMode == MODE_TIKKUN) textMode = MODE_HE } }`.

- [ ] **Step 6: Build and smoke on device**

Run `./gradlew --offline assembleDebug -q`, install, open Tehillim, flip the toggle both ways, scroll past the end of Psalm 9 and above Psalm 1; open the parsha, tap `ה`; confirm the banner shows parsha + haftarah. No `FATAL` in logcat.

- [ ] **Step 7: Commit** — `feat: reader mode toggle, continuous chapter scrolling, haftarah and week banner`

---

### Task 6: Notification zmanim grid

**Files:**
- Modify: `app/src/main/res/layout/notification_hebrew_date_expanded.xml:112-120`
- Modify: `app/src/main/res/layout/item_zman_row.xml`
- Modify: `app/src/main/kotlin/com/hebrewcal/ui/notification/LockscreenNotificationBuilder.kt:124-147`

- [ ] **Step 1: Layout** — replace the `ll_zmanim_container` block with:
```xml
    <LinearLayout
        android:id="@+id/ll_zmanim_container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:visibility="gone">
        <LinearLayout android:id="@+id/ll_zmanim_col0" android:layout_width="0dp" android:layout_weight="1"
            android:layout_height="wrap_content" android:orientation="vertical" android:paddingEnd="6dp" />
        <LinearLayout android:id="@+id/ll_zmanim_col1" android:layout_width="0dp" android:layout_weight="1"
            android:layout_height="wrap_content" android:orientation="vertical" android:paddingStart="6dp" />
    </LinearLayout>
```
`item_zman_row.xml`: `paddingTop/Bottom` → `1dp`, both `textSize` → `11sp`, `minWidth` → `52dp`. In the expanded layout, change the divider's `layout_marginTop` to `5dp` and `layout_marginBottom` to `3dp`, and the `layout_marginTop="2dp"` entries to `1dp`.

- [ ] **Step 2: Builder** — replace `removeAllViews(R.id.ll_zmanim_container)` with clearing both columns, and `addView(R.id.ll_zmanim_container, rowView)` with:
```kotlin
                expandedViews.addView(if (index % 2 == 0) R.id.ll_zmanim_col0 else R.id.ll_zmanim_col1, rowView)
```
(`forEach` → `forEachIndexed { index, entry -> ... }`).

- [ ] **Step 3: Verify on device** — with all 14 zmanim selected in Settings, expand the notification: the last zman is visible. Commit — `fix: two-column zmanim grid so the expanded notification is not cropped`

---

### Task 7: Widget — diagnose and harden

**Files:**
- Modify: `app/src/main/kotlin/com/hebrewcal/ui/widget/HebrewDateWidget.kt:36-113`

- [ ] **Step 1: Diagnose (systematic-debugging)** — install the current build, remove and re-add the widget, then:
```
adb logcat -d | grep -E 'Glance|AppWidget|HebrewDateWidget|AndroidRuntime|RemoteViews' | head -60
```
Record the root cause in the commit message. Fix it at the source (e.g. a null action parameter → pass `""`; child-count overflow → nest; unsupported modifier → remove).

- [ ] **Step 2: Harden `provideGlance`** — wrap everything after `val prefs = ...` in:
```kotlin
        val content: (@Composable () -> Unit) = runCatching {
            // ... existing computation producing dateInfo/zmanimData/labels ...
            val block: @Composable () -> Unit = { HebrewDateWidgetContent(...) }
            block
        }.getOrElse { t ->
            Log.e(TAG, "widget content failed; showing fallback", t)
            val fallback = runCatching { HebrewCalendarRepository().getDateInfo(Date(), prefs.language, prefs.location).hebrewDateString }.getOrDefault("Hebrew Calendar")
            ({ Box(GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFF1A1A2E))).padding(16.dp)) { Text(fallback, style = TextStyle(color = ColorProvider(Color(0xFFE8D5B7)), fontSize = TextUnit(20f, TextUnitType.Sp))) } })
        }
        provideContent { content() }
```
- [ ] **Step 3: Body tap → calendar** — `.clickable(actionStartActivity<SettingsActivity>())` → `actionStartActivity<CalendarActivity>()` (Task 9 creates it; if executing out of order, keep Settings until Task 9 and change it there).
- [ ] **Step 4: Verify** — widget renders after re-adding; `adb shell dumpsys appwidget | grep -A3 hebrewcal` shows the provider. Commit — `fix: widget renders again and falls back instead of the system error placeholder`

---

### Task 8: Calendar month model (pure)

**Files:**
- Create: `app/src/main/kotlin/com/hebrewcal/data/CalendarMonth.kt`
- Test: `app/src/test/kotlin/com/hebrewcal/data/CalendarMonthTest.kt`

**Interfaces:**
```kotlin
data class DayCell(val date: LocalDate, val hebrewDay: Int, val hebrewMonth: Int, val hebrewYear: Int,
                   val inMonth: Boolean, val isShabbat: Boolean, val holidayIndex: Int, val isRoshChodesh: Boolean, val isToday: Boolean)
data class MonthGrid(val title: String, val subtitle: String, val cells: List<DayCell>)   // cells.size % 7 == 0, starts Sunday
object CalendarMonths {
    fun hebrewMonth(year: Int, month: Int, inIsrael: Boolean, hebrewLabels: Boolean, today: LocalDate): MonthGrid
    fun gregorianMonth(year: Int, month: Int, inIsrael: Boolean, hebrewLabels: Boolean, today: LocalDate): MonthGrid
    fun nextHebrewMonth(year: Int, month: Int): Pair<Int, Int>; fun prevHebrewMonth(year: Int, month: Int): Pair<Int, Int>
}
```

- [ ] **Step 1: Failing tests**
```kotlin
package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CalendarMonthTest {
    private val today = LocalDate.of(2026, 9, 17)

    @Test fun tishrei5787StartsOnSaturdayAndPadsToSunday() {
        val g = CalendarMonths.hebrewMonth(5787, JewishDate.TISHREI, inIsrael = false, hebrewLabels = false, today = today)
        assertEquals(0, g.cells.size % 7)
        assertEquals(6, g.cells.indexOfFirst { it.inMonth })          // 1 Tishrei 5787 = Sat 2026-09-12
        assertEquals(LocalDate.of(2026, 9, 12), g.cells[6].date)
        assertEquals(30, g.cells.count { it.inMonth })
        assertTrue(g.cells[6].holidayIndex == JewishCalendar.ROSH_HASHANA)
        assertTrue(g.title.contains("Tishrei") && g.title.contains("5787"))
    }

    @Test fun todayIsMarkedOnce() {
        val g = CalendarMonths.hebrewMonth(5786, JewishDate.ELUL, false, true, today)
        assertEquals(1, g.cells.count { it.isToday })
        assertEquals(today, g.cells.first { it.isToday }.date)
    }

    @Test fun gregorianMonthCarriesHebrewDays() {
        val g = CalendarMonths.gregorianMonth(2026, 9, false, false, today)
        val sep12 = g.cells.first { it.date == LocalDate.of(2026, 9, 12) }
        assertEquals(1, sep12.hebrewDay); assertEquals(JewishDate.TISHREI, sep12.hebrewMonth); assertTrue(sep12.isShabbat)
    }

    @Test fun monthStepping() {
        assertEquals(5787 to JewishDate.TISHREI, CalendarMonths.nextHebrewMonth(5786, JewishDate.ELUL))
        assertEquals(5786 to JewishDate.ELUL, CalendarMonths.prevHebrewMonth(5787, JewishDate.TISHREI))
        assertEquals(5787 to JewishDate.ADAR, CalendarMonths.nextHebrewMonth(5787, JewishDate.SHEVAT)) // 5787 is not leap
    }
}
```
- [ ] **Step 2: Run to verify failure** — `Unresolved reference: CalendarMonths`.
- [ ] **Step 3: Implement**
```kotlin
package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class DayCell(
    val date: LocalDate, val hebrewDay: Int, val hebrewMonth: Int, val hebrewYear: Int,
    val inMonth: Boolean, val isShabbat: Boolean, val holidayIndex: Int, val isRoshChodesh: Boolean, val isToday: Boolean
)
data class MonthGrid(val title: String, val subtitle: String, val cells: List<DayCell>)

object CalendarMonths {
    private fun cell(date: LocalDate, inMonth: Boolean, inIsrael: Boolean, today: LocalDate): DayCell {
        val jc = JewishCalendar(date).apply { this.inIsrael = inIsrael }
        return DayCell(date, jc.jewishDayOfMonth, jc.jewishMonth, jc.jewishYear, inMonth,
            date.dayOfWeek == DayOfWeek.SATURDAY, jc.yomTovIndex, jc.isRoshChodesh, date == today)
    }

    private fun grid(first: LocalDate, last: LocalDate, inIsrael: Boolean, today: LocalDate): List<DayCell> {
        val start = first.minusDays(((first.dayOfWeek.value % 7)).toLong())          // back to Sunday
        val end = last.plusDays(((6 - last.dayOfWeek.value % 7)).toLong())            // forward to Saturday
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }
            .map { cell(it, !it.isBefore(first) && !it.isAfter(last), inIsrael, today) }.toList()
    }

    private val greg = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    private val gregYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    fun hebrewMonth(year: Int, month: Int, inIsrael: Boolean, hebrewLabels: Boolean, today: LocalDate): MonthGrid {
        val jd = JewishDate(year, month, 1)
        val first = jd.localDate
        val last = first.plusDays((jd.daysInJewishMonth - 1).toLong())
        val f = HebrewDateFormatter().apply { isHebrewFormat = hebrewLabels; isUseGershGershayim = true }
        val title = if (hebrewLabels) "${f.formatMonth(jd)} ${f.formatHebrewNumber(year)}" else "${f.formatMonth(jd)} $year"
        return MonthGrid(title, "${first.format(greg)} – ${last.format(gregYear)}", grid(first, last, inIsrael, today))
    }

    fun gregorianMonth(year: Int, month: Int, inIsrael: Boolean, hebrewLabels: Boolean, today: LocalDate): MonthGrid {
        val first = LocalDate.of(year, month, 1); val last = first.withDayOfMonth(first.lengthOfMonth())
        val f = HebrewDateFormatter().apply { isHebrewFormat = hebrewLabels; isUseGershGershayim = true }
        val a = JewishDate(first); val b = JewishDate(last)
        val sub = if (a.jewishMonth == b.jewishMonth) "${f.formatMonth(a)} ${if (hebrewLabels) f.formatHebrewNumber(a.jewishYear) else a.jewishYear}"
                  else "${f.formatMonth(a)} – ${f.formatMonth(b)} ${if (hebrewLabels) f.formatHebrewNumber(b.jewishYear) else b.jewishYear}"
        val title = "${first.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} $year"
        return MonthGrid(title, sub, grid(first, last, inIsrael, today))
    }

    fun nextHebrewMonth(year: Int, month: Int): Pair<Int, Int> {
        val jd = JewishDate(year, month, 1); jd.forward(java.util.Calendar.MONTH, 1); return jd.jewishYear to jd.jewishMonth
    }
    fun prevHebrewMonth(year: Int, month: Int): Pair<Int, Int> {
        val jd = JewishDate(year, month, 1); jd.back(); jd.setJewishDayOfMonth(1); return jd.jewishYear to jd.jewishMonth
    }
}
```
`JewishDate.localDate` exists in KosherJava 2.4 (`getLocalDate()`); if not, use `LocalDate.of(jd.gregorianYear, jd.gregorianMonth + 1, jd.gregorianDayOfMonth)`. `forward(Calendar.MONTH, 1)` moves one Jewish month; `back()` moves one day back.

- [ ] **Step 4: Run tests** — pass. Fix any KosherJava API mismatch (the tests define the behaviour).
- [ ] **Step 5: Commit** — `feat: pure calendar month grid model for Hebrew and Gregorian months`

---

### Task 9: Calendar screen

**Files:**
- Create: `app/src/main/kotlin/com/hebrewcal/ui/calendar/CalendarActivity.kt`
- Create: `app/src/main/kotlin/com/hebrewcal/ui/calendar/CalendarViewModel.kt`
- Create: `app/src/main/kotlin/com/hebrewcal/ui/calendar/DaySheet.kt`
- Modify: `app/src/main/AndroidManifest.xml:33-47` (launcher moves to CalendarActivity; new activity uses `Theme.HebrewCalendar.Reader`)
- Modify: `app/src/main/kotlin/com/hebrewcal/ui/notification/LockscreenNotificationBuilder.kt:61` (content intent → CalendarActivity)
- Modify: `app/src/main/kotlin/com/hebrewcal/ui/widget/HebrewDateWidget.kt` (body tap → CalendarActivity, if not done in Task 7)

**Interfaces:** consumes Task 8 `CalendarMonths`, Task 2 `getFastTimes/FastSchedule`, `SefariaRepository.parshaFor`, `TehillimSchedule`, `ElulTehillim`, `ZmanimRepository.getZmanim`, `TextReaderActivity.EXTRA_*`.

- [ ] **Step 1: ViewModel**
```kotlin
package com.hebrewcal.ui.calendar

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val prefsRepo = UserPreferencesRepository(app)
    private val calRepo = HebrewCalendarRepository()
    private val zmanimRepo = ZmanimRepository()
    private val sefaria = SefariaRepository(app)

    val prefs = prefsRepo.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    data class View(val hebrewMode: Boolean = true, val hYear: Int, val hMonth: Int, val gYear: Int, val gMonth: Int)
    private val _view = MutableStateFlow(JewishDate().let { View(true, it.jewishYear, it.jewishMonth, LocalDate.now().year, LocalDate.now().monthValue) })
    val view: StateFlow<View> = _view

    fun grid(p: UserPreferences, v: View): MonthGrid {
        val israel = p.location == CalendarLocation.ISRAEL; val he = p.language == CalendarLanguage.HEBREW
        return if (v.hebrewMode) CalendarMonths.hebrewMonth(v.hYear, v.hMonth, israel, he, LocalDate.now())
               else CalendarMonths.gregorianMonth(v.gYear, v.gMonth, israel, he, LocalDate.now())
    }
    fun step(delta: Int) {
        val v = _view.value
        _view.value = if (v.hebrewMode) {
            val (y, m) = if (delta > 0) CalendarMonths.nextHebrewMonth(v.hYear, v.hMonth) else CalendarMonths.prevHebrewMonth(v.hYear, v.hMonth)
            v.copy(hYear = y, hMonth = m)
        } else LocalDate.of(v.gYear, v.gMonth, 1).plusMonths(delta.toLong()).let { v.copy(gYear = it.year, gMonth = it.monthValue) }
    }
    fun today() { val jd = JewishDate(); val now = LocalDate.now(); _view.value = _view.value.copy(hYear = jd.jewishYear, hMonth = jd.jewishMonth, gYear = now.year, gMonth = now.monthValue) }
    fun toggleMode() { _view.value = _view.value.copy(hebrewMode = !_view.value.hebrewMode) }

    data class DayDetail(
        val info: HebrewDateInfo, val weekday: String, val zmanim: List<ZmanEntry>, val hasLocation: Boolean,
        val tehillim: TehillimPortion, val elulRef: String?, val parsha: ParshaRef?, val parshaLoading: Boolean
    )
    private val _detail = MutableStateFlow<DayDetail?>(null)
    val detail: StateFlow<DayDetail?> = _detail
    private var detailJob: Job? = null

    fun select(date: LocalDate?) {
        detailJob?.cancel()
        if (date == null) { _detail.value = null; return }
        val p = prefs.value
        val d = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())
        val lat = p.zmanimManualLat; val lng = p.zmanimManualLng; val tz = TimeZone.getDefault()
        val hasLoc = lat != 0.0 && lng != 0.0
        val info = calRepo.getDateInfo(d, p.language, p.location, showParshaOnWeekdays = true,
            fastTimes = { kind, fd -> if (!hasLoc) null else zmanimRepo.getFastTimes(kind, lat, lng, tz, fd)?.let {
                FastSchedule.label(zmanimRepo.formatTime(it.begin, p.zmanimTimeFormat), zmanimRepo.formatTime(it.end, p.zmanimTimeFormat)) } })
        val zm = if (hasLoc) zmanimRepo.getZmanim(lat, lng, timeZone = tz, date = d, selectedZmanim = p.selectedZmanim, timeFormat = p.zmanimTimeFormat).entries else emptyList()
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, if (p.language == CalendarLanguage.HEBREW) Locale("he") else Locale.ENGLISH)
        val jd = JewishDate(d)
        val tehillim = TehillimSchedule.portionFor(jd.jewishDayOfMonth, jd.daysInJewishMonth)
        val elul = if (p.showElulTehillim) ElulTehillim.portionFor(jd.jewishMonth, jd.jewishDayOfMonth)?.sefariaRef else null
        _detail.value = DayDetail(info, weekday, zm, hasLoc, tehillim, elul, null, parshaLoading = true)
        detailJob = viewModelScope.launch {
            val shabbat = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            val pr = sefaria.parshaFor(shabbat, p.location == CalendarLocation.DIASPORA).getOrNull()
            _detail.value = _detail.value?.copy(parsha = pr, parshaLoading = false)
        }
    }
}
```
Imports: `java.time.*`, `java.time.format.TextStyle`, `java.time.temporal.TemporalAdjusters`, `java.util.*`, `kotlinx.coroutines.Job`, `com.hebrewcal.data.*`, `com.kosherjava.zmanim.hebrewcalendar.JewishDate`.

- [ ] **Step 2: Activity + grid**
```kotlin
package com.hebrewcal.ui.calendar

class CalendarActivity : ComponentActivity() {
    private val vm: CalendarViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by vm.prefs.collectAsState()
            val view by vm.view.collectAsState()
            val detail by vm.detail.collectAsState()
            val hebrew = prefs.language == CalendarLanguage.HEBREW
            val grid = remember(prefs, view) { vm.grid(prefs, view) }
            val gematria = remember { HebrewDateFormatter() }
            fun hNum(n: Int) = if (hebrew) gematria.formatHebrewNumber(n) else n.toString()

            Box(Modifier.fillMaxSize().background(Color(0xFF2E2E2E).copy(alpha = 0.5f))) {
                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Header
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.step(-1) }) { Text("‹", color = INACTIVE, fontSize = 26.sp) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(grid.title, color = Color.White, fontSize = 20.sp)
                            Text(grid.subtitle, color = INACTIVE, fontSize = 12.sp)
                        }
                        TextButton(onClick = { vm.step(1) }) { Text("›", color = INACTIVE, fontSize = 26.sp) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.today() }) { Text("●", color = ACTIVE_GOLD, fontSize = 12.sp) }
                        TextButton(onClick = { vm.toggleMode() }) { Text(if (view.hebrewMode) "ע" else "G", color = ACTIVE_GOLD, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { startActivity(Intent(this@CalendarActivity, SettingsActivity::class.java)) }) { Text("⚙", color = INACTIVE, fontSize = 16.sp) }
                    }
                    // Weekday header, Sunday first
                    val days = if (hebrew) listOf("א","ב","ג","ד","ה","ו","ש") else listOf("S","M","T","W","T","F","S")
                    Row(Modifier.fillMaxWidth()) { days.forEach { Text(it, color = INACTIVE, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 0.5.dp)
                    // Grid with horizontal swipe to change month
                    var drag by remember { mutableStateOf(0f) }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier.fillMaxWidth().weight(1f).pointerInput(Unit) {
                            detectHorizontalDragGestures(onDragEnd = { if (drag < -80f) vm.step(1) else if (drag > 80f) vm.step(-1); drag = 0f }) { _, dx -> drag += dx }
                        }
                    ) {
                        items(grid.cells) { c ->
                            val alpha = if (c.inMonth) 1f else 0.35f
                            Column(
                                Modifier.aspectRatio(0.85f).padding(2.dp)
                                    .background(if (c.isShabbat && c.inMonth) Color.White.copy(alpha = 0.07f) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .then(if (c.isToday) Modifier.border(1.dp, ACTIVE_GOLD, RoundedCornerShape(8.dp)) else Modifier)
                                    .clickable { vm.select(c.date) }.padding(top = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(if (view.hebrewMode) hNum(c.hebrewDay) else c.date.dayOfMonth.toString(), color = Color.White.copy(alpha = alpha), fontSize = 17.sp)
                                Text(if (view.hebrewMode) c.date.dayOfMonth.toString() else hNum(c.hebrewDay), color = INACTIVE.copy(alpha = alpha * 0.8f), fontSize = 10.sp)
                                Row {
                                    if (c.holidayIndex != -1 && !c.isRoshChodesh) Box(Modifier.size(5.dp).background(ACTIVE_GOLD, CircleShape))
                                    if (c.isRoshChodesh) Box(Modifier.padding(start = 2.dp).size(5.dp).background(Color(0xFF7EC8E3), CircleShape))
                                }
                            }
                        }
                    }
                }
                detail?.let { d -> DaySheet(d, hebrew, prefs, onDismiss = { vm.select(null) }, onOpen = { intent -> startActivity(intent) }) }
            }
        }
    }
}
private val ACTIVE_GOLD = Color(0xFFFFD255)
private val INACTIVE = Color(0xE6FFFFFF)
```
Imports as needed (`androidx.compose.foundation.lazy.grid.*`, `androidx.compose.foundation.gestures.detectHorizontalDragGestures`, `androidx.compose.foundation.border`, `HorizontalDivider`, etc.).

- [ ] **Step 3: Day sheet** (`DaySheet.kt`)
```kotlin
@Composable
fun DaySheet(d: CalendarViewModel.DayDetail, hebrew: Boolean, prefs: UserPreferences, onDismiss: () -> Unit, onOpen: (Intent) -> Unit) {
    val ctx = LocalContext.current
    fun reader(mode: String, ref: String, title: String, teamim: Boolean, elul: String = "") = Intent(ctx, TextReaderActivity::class.java).apply {
        putExtra(TextReaderActivity.EXTRA_MODE, mode); putExtra(TextReaderActivity.EXTRA_REF, ref); putExtra(TextReaderActivity.EXTRA_TITLE, title)
        putExtra(TextReaderActivity.EXTRA_LANG, prefs.language.name); putExtra(TextReaderActivity.EXTRA_NIKKUD, true); putExtra(TextReaderActivity.EXTRA_TEAMIM, teamim)
        putExtra(TextReaderActivity.EXTRA_DIASPORA, prefs.location == CalendarLocation.DIASPORA); putExtra(TextReaderActivity.EXTRA_ELUL_REF, elul)
    }
    Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }, Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.62f).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(Color(0xE0141414))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState()).padding(18.dp)
        ) {
            Text(d.info.hebrewDateString, color = Color.White, fontSize = 22.sp)
            Text("${d.weekday} · ${d.info.gregorianDateString}", color = INACTIVE, fontSize = 13.sp)
            val labels = listOfNotNull(d.info.holidayName, d.info.parshaName?.let { if (hebrew) "פרשת $it" else it }, d.info.omerText)
            if (labels.isNotEmpty()) Text(labels.joinToString(" · "), color = ACTIVE_GOLD, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.15f), thickness = 0.5.dp)
            // Links
            val portion = d.tehillim
            LinkRow(if (hebrew) "תהילים יומי" else "Daily Tehillim", ReaderButtons.tehillimLabel(portion, hebrew)) {
                onOpen(reader("tehillim", portion.sefariaRef, ReaderButtons.tehillimLabel(portion, hebrew), false, d.elulRef ?: "")) }
            when {
                d.parshaLoading -> Text(if (hebrew) "טוען פרשה…" else "Loading parsha…", color = INACTIVE, fontSize = 12.sp)
                d.parsha != null -> {
                    val p = d.parsha
                    LinkRow(if (hebrew) "פרשה" else "Parsha", if (hebrew) p.nameHe else p.nameEn) {
                        onOpen(reader("parsha", p.sefariaRef, if (hebrew) "פרשת ${p.nameHe}" else "Parashat ${p.nameEn}", true)) }
                    p.haftarot.forEach { h ->
                        LinkRow(if (hebrew) "הפטרה" else "Haftarah", if (hebrew) h.nameHe else h.nameEn) {
                            onOpen(reader("haftarah", h.dottedRef, (if (hebrew) "הפטרה · " else "Haftarah · ") + (if (hebrew) h.nameHe else h.nameEn), true)) }
                    }
                }
                else -> Text(if (hebrew) "אין חיבור לאינטרנט" else "No internet connection", color = INACTIVE, fontSize = 12.sp)
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.15f), thickness = 0.5.dp)
            if (!d.hasLocation) Text(if (hebrew) "הגדר עיר בהגדרות לזמני היום" else "Set a city in Settings to see zmanim", color = INACTIVE, fontSize = 12.sp)
            d.zmanim.forEach { z ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(z.displayName, color = INACTIVE, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(z.timeString, color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun LinkRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ACTIVE_GOLD, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 14.sp); Text("  ›", color = ACTIVE_GOLD, fontSize = 16.sp)
    }
}
```
Wrap the sheet content in `CompositionLocalProvider(LocalLayoutDirection provides if (hebrew) LayoutDirection.Rtl else LayoutDirection.Ltr)`.

- [ ] **Step 4: Manifest and entry points**
Manifest: remove the `<intent-filter>` from `SettingsActivity`; add
```xml
        <activity
            android:name=".ui.calendar.CalendarActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.HebrewCalendar.Reader">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
```
Notification builder line 61: `Intent(context, CalendarActivity::class.java)`. Widget body tap: `actionStartActivity<CalendarActivity>()`.

- [ ] **Step 5: Build, install, verify** — launcher opens the calendar; swipe/arrows change months; `ע/G` toggles; tapping today's cell shows holiday/zmanim; Tehillim/parsha/haftarah links open the reader with correct text; gear opens Settings; the widget body and notification body open the calendar.
- [ ] **Step 6: Commit** — `feat: translucent Hebrew calendar screen with day details and reader links`

---

### Task 10: Release metadata and version

**Files:**
- Modify: `app/build.gradle:15-16`
- Create: `fastlane/metadata/android/en-US/changelogs/21.txt`
- Modify: `fastlane/metadata/android/en-US/full_description.txt`
- Modify: `README.md` (Features list, Settings section, Permissions unchanged)
- Delete: `fastlane/metadata/android/en-US/changelogs/20.txt` content stays (already added this session, uncommitted) — commit it with this task.

- [ ] **Step 1:** `versionCode 21`, `versionName "1.14"`.
- [ ] **Step 2:** `21.txt` (F-Droid: plain text, ≤500 characters):
```
New: Hebrew calendar screen and offline downloads.

• Calendar: month grid (Hebrew or Gregorian), day details, zmanim, links to parsha, haftarah, daily Tehillim
• Reader: Tehillim/Torah switch, scroll past a chapter to load the next, haftarah of the week
• Fast days show start and end times
• Downloads upcoming Tehillim (1 week) and parsha (3 weeks) on Wi‑Fi for offline reading
• Navigation dial polish, faster Tehillim dial
• Fix: expanded notification no longer cuts off zmanim; widget renders again
```
Check length: `wc -c` must be ≤ 500.
- [ ] **Step 3:** `full_description.txt`: add a `<b>Calendar screen</b>` block (month navigation, dual dates, day sheet, links) and an `• Offline downloads of upcoming readings on Wi‑Fi (mobile optional)` bullet under the reader block; mention fast times under Calendar.
- [ ] **Step 4:** README: add the calendar screen and offline downloads to Features; document the three download settings; note the launcher change.
- [ ] **Step 5:** Commit — `chore: release 1.14 metadata, changelog, and version bump`

---

### Task 11: Full verification and APK

- [ ] **Step 1:** `./gradlew --offline assembleDebug testDebugUnitTest -q` → 0 failures.
- [ ] **Step 2:** Install on the OnePlus (ask the user for the current wireless-debugging port if the connection dropped; see memory `adb-wireless-oneplus`). Run the spec's on-device checklist; capture screenshots of calendar, day sheet, reader with banner, expanded notification, widget.
- [ ] **Step 3:** Report the APK path `app/build/outputs/apk/debug/app-debug.apk` and any checklist item that failed. Do not push.

## Self-review notes

- Spec coverage: §1 → T5 (ModeToggle) + T4 (toggleMode); §2 → T3, T4, T5; §3 → T1, T4, T5; §4 → T6; §5 → T7; §6 → T2; §7 → T8, T9; §8 → T10; testing → each task + T11.
- Types: `Reading`/`ParshaRef.haftarot` (T1) used in T4, T5, T9; `ReaderWindows` API (T3) used verbatim in T4; `UiState` fields (T4) match T5 parameters; `FastKind`/`FastSchedule.label`/`getFastTimes`/`formatTime` (T2) used in T9; `CalendarMonths` (T8) used in T9.
- Known risk: KosherJava `JewishDate.localDate` / `forward(MONTH)` names — T8 notes the fallbacks; tests are authoritative.
