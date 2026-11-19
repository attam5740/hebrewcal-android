# Daily Chitas Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional Chitas (חת"ת) button to the notification and widget that opens the day's Chumash, Tehillim and Tanya as three foldable sections in the existing reader overlay.

**Architecture:** All three portions come from Sefaria, which the app already uses. A new pure `ChitasSchedule` computes the Chumash aliyah (day-of-week index into the parsha's existing `extraDetails.aliyot`) and the Tehillim range (existing `TehillimSchedule`); the Tanya ref comes from the `Tanya Yomi` item in the same dated `/api/calendars` response the app already fetches and caches. The reader resolves the refs from a blank `EXTRA_REF`, following the existing `mode == "parsha"` precedent, and renders each leg as one `ReaderSection(markChapters = true)`.

**Tech Stack:** Kotlin, Jetpack Compose, Glance (widget), DataStore Preferences, kotlinx.serialization, JUnit 4, Gradle.

**Spec:** `docs/superpowers/specs/2026-11-18-daily-chitas-design.md`

## Global Constraints

- Source every leg from Sefaria. Do **not** fetch, scrape or display chabad.org content — their ToS forbids reproduction and non-designated access.
- Tanya versions are pinned to Kehot on both sides: `vhe=Kehot Publication Society`, `ven=Kehot Publication Society (English Translation)` (CC-BY-NC). Never rely on Sefaria's default.
- The only date form `/api/calendars` honours is `&year=&month=&day=`. `&date=` is silently ignored and returns today.
- Chitas follows the **Hebrew day**: all date arithmetic uses the nightfall-advanced date, never the raw `now`.
- The toggle defaults to **off** and is visible for every nusach.
- `mode == "tefillah"` behaviour must not change: no edits to its typography, `vocalized` logic, tap handling, or `PrayerSections.units()` path.
- Chitas must not route through `PrayerSections.units()`, which renumbers verses.
- Israel/diaspora comes from `prefs.location`, like every other calendars call.
- Run tests with `./gradlew testDebugUnitTest`.

## Review Focus

Failure modes the spec implies that no task's happy path would catch. Each has a test added to the task that owns the code.

1. **Saturday night after tzet shows the previous week's parsha.** Every existing lookup runs `nextOrSame(SATURDAY)` on the raw `now`, which on Saturday evening resolves to the Shabbat just ended; Chitas must show the *coming* parsha's first aliyah. Misbehaves one evening a week. → Task 3.
2. **The version-pinning fix breaks existing fetches.** The condition being changed affects every non-Siddur fetch in the app today. Tanakh, haftarah, siddur and Tanya refs must all still resolve. → Task 1.
3. **A combined parsha mis-indexes the aliyah.** Vayakhel-Pekudei and friends must still yield seven aliyot indexed by day-of-week. → Task 3.
4. ~~**A festival week displaces the weekly parsha.**~~ **UNRESOLVED — see the ledger's "OPEN DESIGN DEFECT".** Verified during execution: on festival Shabbatot Sefaria *replaces* the `Parashat Hashavua` item with the festival reading (2027-04-24 → "Pesach Shabbat Chol haMoed", `Exodus 33:12-34:26`), so there is no weekly-parsha item to prefer. Needs a decision about practice before it can be fixed. Task 3's test commented "Review Focus 4" is actually the empty-aliyot guard, a different concern.
5. **One unavailable leg blanks the whole screen.** `fetchCombined` currently fails the entire load if any ref fails; a missing Tanya must leave Chumash and Tehillim readable. → Task 5.

---

## File Structure

| File | Responsibility |
|---|---|
| `data/SefariaRepository.kt` (modify) | Tanakh-only `vhe`; Kehot pinning for Tanya; extracted pure URL builder |
| `data/ParshaRef.kt` (modify) | Carry the day's Tanya ref and label |
| `data/ParshaCalendarsParser.kt` (modify) | Parse the `Tanya Yomi` calendar item |
| `data/ChitasSchedule.kt` (create) | Pure: day-of-week → aliyah, parsha selection, Tehillim range, assembly |
| `data/UserPreferences.kt` (modify) | `chitasButtonOn` field, key, read, updater |
| `ui/settings/MainSettingsScreen.kt`, `SettingsViewModel.kt` (modify) | Toggle row; About credit |
| `ui/reader/ReaderViewModel.kt` (modify) | `chitas` mode, `combineMode`, per-leg tolerance |
| `ui/reader/TextReaderActivity.kt` (modify) | Three `markChapters` leg sections; foldable |
| `ui/notification/LockscreenNotificationBuilder.kt` + 2 layouts (modify) | Chitas pill |
| `ui/notification/CalendarNotificationService.kt` (modify) | Pass the label when the toggle is on |
| `ui/widget/HebrewDateWidget.kt` (modify) | Chitas button |
| `service/TextPrefetchJob.kt` (modify) | Prefetch the Tanya text per day |

Execute in order. Task 1 is independent. **Task 3 depends on Task 2** — its tests construct `ParshaRef` with the `tanyaRef`/`tanyaLabel` fields Task 2 adds, so it will not compile before then. Task 4 is independent. Task 5 depends on 1–3. Tasks 6–8 depend on 3 and 4; Task 8 also edits `MainSettingsScreen.kt`, which Task 4 has already touched.

---

### Task 1: Tanakh-only Hebrew version, Kehot pinning for Tanya

The prerequisite bug fix. Verified against the live API: as the app builds URLs today, the Tanya Yomi ref returns **zero Hebrew**, because `&vhe=Miqra according to the Masorah` is appended to everything that does not contain `"Siddur"`.

**Files:**
- Modify: `app/src/main/kotlin/com/hebrewcal/data/SefariaRepository.kt:26-45`
- Test: `app/src/test/kotlin/com/hebrewcal/data/SefariaTextUrlTest.kt` (create)

**Interfaces:**
- Consumes: nothing.
- Produces: `SefariaRefs.textUrl(ref: String): String` — a pure function returning the full request URL for a ref, extracted from `fetchText` so it can be tested. `fetchText` calls it and is otherwise unchanged.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hebrewcal.data

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class SefariaTextUrlTest {
    @Test fun tanakhRefAsksForTheMasorahEdition() {
        val u = SefariaRefs.textUrl("Genesis.30.14-27")
        assertTrue(u.contains("vhe=Miqra%20according%20to%20the%20Masorah"))
    }

    @Test fun tanyaRefPinsKehotOnBothSides() {
        val u = SefariaRefs.textUrl("Tanya, Part V; Kuntres Acharon 4:35")
        assertFalse("Masorah must not be requested for Tanya", u.contains("Masorah"))
        assertTrue(u.contains("vhe=Kehot%20Publication%20Society"))
        // Do not assert on the parenthesised part: URLEncoder percent-encodes
        // ( and ) as %28/%29, so match only the stable prefix.
        assertTrue(u.contains("ven=Kehot%20Publication%20Society"))
        assertTrue(u.contains("English"))
    }

    @Test fun siddurRefAsksForNoHebrewVersion() {
        assertFalse(SefariaRefs.textUrl("Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Modeh Ani").contains("vhe="))
    }

    @Test fun haftarahRefIsStillTanakh() {
        assertTrue(SefariaRefs.textUrl("Hosea 12:13-14:10").contains("Masorah"))
    }

    @Test fun unknownNonTanakhRefAsksForNoHebrewVersion() {
        assertFalse(SefariaRefs.textUrl("Mishnah Berakhot 1").contains("vhe="))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hebrewcal.data.SefariaTextUrlTest"`
Expected: FAIL — `textUrl` unresolved.

- [ ] **Step 3: Implement `SefariaRefs.textUrl(ref: String): String` in `data/SefariaText.kt`**

Move the URL construction out of `SefariaRepository.fetchText` verbatim, then change the version-selection rule from "not a Siddur ref" to a positive test:
- A Tanakh ref (book name matches the 39 Tanakh books, as `TehillimSchedule`/`AliyotPartitioner` already assume) gets `&vhe=Miqra according to the Masorah`.
- A ref whose book is `Tanya` gets the two Kehot parameters from Global Constraints.
- Anything else gets neither, which is today's behaviour for Siddur refs.

Keep the existing `URLEncoder.encode(...).replace("+", "%20")` encoding.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hebrewcal.data.SefariaTextUrlTest"`
Expected: PASS

- [ ] **Step 5: Run the whole suite — this touches every fetch in the app**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, no regressions.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/hebrewcal/data/SefariaText.kt app/src/main/kotlin/com/hebrewcal/data/SefariaRepository.kt app/src/test/kotlin/com/hebrewcal/data/SefariaTextUrlTest.kt
git commit -m "fix: ask for the Masorah Hebrew edition only on Tanakh refs, pin Kehot for Tanya"
```

---

### Task 2: Parse the Tanya Yomi calendar item

**Files:**
- Modify: `app/src/main/kotlin/com/hebrewcal/data/ParshaRef.kt`
- Modify: `app/src/main/kotlin/com/hebrewcal/data/ParshaCalendarsParser.kt:24-47`
- Test: `app/src/test/kotlin/com/hebrewcal/data/ParshaCalendarsParserTest.kt`

**Interfaces:**
- Produces: `ParshaRef` gains `val tanyaRef: String = ""` and `val tanyaLabel: String = ""`, populated from the `Tanya Yomi` item's `ref` and `displayValue.en`. Defaults keep every existing construction site compiling.

- [ ] **Step 1: Write the failing test**

Add to `ParshaCalendarsParserTest`, extending the existing `json` fixture with a Tanya Yomi item shaped like the live response:

```kotlin
@Test fun extractsTanyaYomi() {
    val p = ParshaCalendarsParser.parse(jsonWithTanya)!!
    assertEquals("Tanya, Part V; Kuntres Acharon 4:35", p.tanyaRef)
    assertEquals("8 Kislev", p.tanyaLabel)
}

@Test fun missingTanyaYomiLeavesRefBlank() {
    assertEquals("", ParshaCalendarsParser.parse(json)!!.tanyaRef)
}
```

where `jsonWithTanya` adds:
`{"title":{"en":"Tanya Yomi","he":"תניא יומי"},"ref":"Tanya, Part V; Kuntres Acharon 4:35","displayValue":{"en":"8 Kislev","he":"ח כסלו"}}`

(Corrected during execution: the live API's `displayValue.he` has **no** geresh. Use it verbatim — do not synthesise `ח׳`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hebrewcal.data.ParshaCalendarsParserTest"`
Expected: FAIL — `tanyaRef` unresolved.

- [ ] **Step 3: Add the fields and populate them**

Find the `Tanya Yomi` entry by its `title.en`, the way the parser already locates `Parashat Hashavua` and `Haftarah`. Absent item → empty strings, never an exception.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.hebrewcal.data.ParshaCalendarsParserTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hebrewcal/data/ParshaRef.kt app/src/main/kotlin/com/hebrewcal/data/ParshaCalendarsParser.kt app/src/test/kotlin/com/hebrewcal/data/ParshaCalendarsParserTest.kt
git commit -m "feat: parse the Tanya Yomi item out of the calendars response"
```

---

### Task 3: `ChitasSchedule`

The feature's logic, and where four of the five Review Focus items land. Pure — no I/O.

**Files:**
- Create: `app/src/main/kotlin/com/hebrewcal/data/ChitasSchedule.kt`
- Test: `app/src/test/kotlin/com/hebrewcal/data/ChitasScheduleTest.kt` (create)

**Interfaces:**
- Consumes: `ParshaRef` (with `aliyot`, `tanyaRef`, `tanyaLabel`) from Task 2; `TehillimSchedule.portionFor(hebrewDay: Int, daysInMonth: Int): TehillimPortion`; `AliyotPartitioner.aliyahName(index: Int, total: Int, hebrew: Boolean): String`.
- Produces:
  - `data class ChitasPortion(val chumashRef: String, val chumashParsha: String, val chumashParshaHe: String, val aliyahIndex: Int, val tehillimRef: String, val tehillimFirst: Int, val tehillimLast: Int, val tanyaRef: String, val tanyaLabel: String, val tanyaLabelHe: String)`
    (`tanyaLabelHe` was added during execution — the Hebrew heading needs the API's `displayValue.he`. Tasks 5–7 should plan against this 10-field shape.)
  - `ChitasSchedule.parshaDateFor(advanced: LocalDate): LocalDate` — the Shabbat whose parsha this Chitas day belongs to.
  - `ChitasSchedule.portionFor(advanced: LocalDate, hebrewDay: Int, daysInMonth: Int, parsha: ParshaRef): ChitasPortion`
  - `ChitasSchedule.legTitles(p: ChitasPortion, hebrew: Boolean): List<String>` — exactly three, in Chumash/Tehillim/Tanya order.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ChitasScheduleTest {
    // Vayetzei 5787: aliyot as the live API returns them, 7 + maftir.
    private val vayetzei = ParshaRef(
        sefariaRef = "Genesis.28.10-32.3", heRef = "", nameEn = "Vayetzei", nameHe = "ויצא",
        aliyot = listOf(
            "Genesis 28:10-28:22", "Genesis 29:1-29:17", "Genesis 29:18-30:13",
            "Genesis 30:14-30:27", "Genesis 30:28-31:16", "Genesis 31:17-31:42",
            "Genesis 31:43-32:3", "Genesis 32:1-32:3"
        ),
        tanyaRef = "Tanya, Part V; Kuntres Acharon 4:35", tanyaLabel = "8 Kislev"
    )

    @Test fun wednesdayOfVayetzeiIsTheFourthAliyah() {
        val p = ChitasSchedule.portionFor(LocalDate.of(2026, 11, 18), 8, 30, vayetzei)
        assertEquals("Genesis 30:14-30:27", p.chumashRef)
        assertEquals(4, p.aliyahIndex)
    }

    @Test fun sundayIsTheFirstAliyahAndShabbatIsTheSeventh() {
        assertEquals(1, ChitasSchedule.portionFor(LocalDate.of(2026, 11, 15), 5, 30, vayetzei).aliyahIndex)
        assertEquals(7, ChitasSchedule.portionFor(LocalDate.of(2026, 11, 21), 11, 30, vayetzei).aliyahIndex)
    }

    @Test fun maftirIsNeverSelected() {
        // Every day of the week maps into the first seven entries only.
        val refs = (15..21).map {
            ChitasSchedule.portionFor(LocalDate.of(2026, 11, it), 5, 30, vayetzei).chumashRef
        }
        assertEquals(7, refs.distinct().size)
        assertEquals(false, refs.contains("Genesis 32:1-32:3"))
    }

    // Review Focus 1
    @Test fun theParshaDateAdvancesToTheComingShabbat() {
        // Saturday 21 Nov is the Shabbat of Vayetzei. The advanced date on that
        // evening is Sunday 22 Nov, which belongs to the NEXT Shabbat, 28 Nov.
        assertEquals(LocalDate.of(2026, 11, 21), ChitasSchedule.parshaDateFor(LocalDate.of(2026, 11, 21)))
        assertEquals(LocalDate.of(2026, 11, 28), ChitasSchedule.parshaDateFor(LocalDate.of(2026, 11, 22)))
    }

    // Review Focus 3
    @Test fun aCombinedParshaStillIndexesByDayOfWeek() {
        val double = vayetzei.copy(nameEn = "Vayakhel-Pekudei", aliyot = vayetzei.aliyot.take(7))
        assertEquals(3, ChitasSchedule.portionFor(LocalDate.of(2026, 11, 17), 7, 30, double).aliyahIndex)
    }

    // Review Focus 4
    @Test fun aParshaWithNoAliyotYieldsABlankChumashRefNotACrash() {
        val p = ChitasSchedule.portionFor(LocalDate.of(2026, 11, 18), 8, 30, vayetzei.copy(aliyot = emptyList()))
        assertEquals("", p.chumashRef)
    }

    @Test fun tehillimAndTanyaComeThrough() {
        val p = ChitasSchedule.portionFor(LocalDate.of(2026, 11, 18), 8, 30, vayetzei)
        assertEquals(TehillimSchedule.portionFor(8, 30).sefariaRef, p.tehillimRef)
        assertEquals("Tanya, Part V; Kuntres Acharon 4:35", p.tanyaRef)
        assertEquals("8 Kislev", p.tanyaLabel)
    }

    @Test fun legTitlesAreThreeAndNameTheDaysPortions() {
        val t = ChitasSchedule.legTitles(ChitasSchedule.portionFor(LocalDate.of(2026, 11, 18), 8, 30, vayetzei), hebrew = false)
        assertEquals(3, t.size)
        assertEquals(true, t[0].startsWith("Chumash — Vayetzei"))
        assertEquals(true, t[2].contains("8 Kislev"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hebrewcal.data.ChitasScheduleTest"`
Expected: FAIL — `ChitasSchedule` unresolved.

- [ ] **Step 3: Implement `ChitasSchedule` in `data/ChitasSchedule.kt`**

`parshaDateFor` is `advanced.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))` — correct precisely because it is given the already-advanced date. `portionFor` takes `aliyot.take(7)`, indexes by `advanced.dayOfWeek` with Sunday as 1, and returns a blank `chumashRef` when the list is empty. `legTitles` uses `AliyotPartitioner.aliyahName(aliyahIndex - 1, 7, hebrew)` for the ordinal and the spec's heading wording.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.hebrewcal.data.ChitasScheduleTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/hebrewcal/data/ChitasSchedule.kt app/src/test/kotlin/com/hebrewcal/data/ChitasScheduleTest.kt
git commit -m "feat: add ChitasSchedule for the daily Chumash, Tehillim and Tanya portions"
```

---

### Task 4: Settings toggle

**Files:**
- Modify: `app/src/main/kotlin/com/hebrewcal/data/UserPreferences.kt` (field ~line 28, key ~line 184, read ~line 226, updater ~line 298)
- Modify: `app/src/main/kotlin/com/hebrewcal/ui/settings/MainSettingsScreen.kt`, `SettingsViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `UserPreferences.chitasButtonOn: Boolean` (default `false`); `PreferenceKeys.CHITAS_BUTTON_ON = booleanPreferencesKey("chitas_button_on")`; `UserPreferencesRepository.updateChitasButtonOn(on: Boolean)`; `SettingsViewModel.setChitasButtonOn(on: Boolean)`.

- [ ] **Step 1: Add the field, key, read and updater**

Follow `showTehillimButton` exactly, except the default is `false`. `SettingsBackup` round-trips it automatically via `asMap()` — no change there.

- [ ] **Step 2: Add the toggle row**

Place it with the Tehillim and Parsha button toggles, not the prayer buttons. Label "Chitas button"; summary naming Chumash, Tehillim and Tanya so the acronym is not required knowledge. Strings in `strings.xml`.

- [ ] **Step 3: Build and verify the toggle persists**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: PASS. Install and confirm the toggle survives an app restart.

- [ ] **Step 4: Commit**

```bash
git add -A app/src/main/kotlin/com/hebrewcal/data/UserPreferences.kt app/src/main/kotlin/com/hebrewcal/ui/settings app/src/main/res/values/strings.xml
git commit -m "feat: add the Chitas button setting, off by default"
```

---

### Task 5: Reader — `chitas` mode

**Files:**
- Modify: `app/src/main/kotlin/com/hebrewcal/ui/reader/ReaderViewModel.kt:95-163`
- Modify: `app/src/main/kotlin/com/hebrewcal/ui/reader/TextReaderActivity.kt:563-600` (section build), `:594` (`foldable`)
- Test: `app/src/test/kotlin/com/hebrewcal/data/ChitasSectionsTest.kt` (create)

**Interfaces:**
- Consumes: `ChitasSchedule.portionFor`, `ChitasSchedule.legTitles` (Task 3); `ParshaRef.tanyaRef` (Task 2); `repo.parshaFor(date, diaspora)`.
- Produces: `ReaderViewModel.fetchCombined(ref: String, cacheFirst: Boolean, siddurMerge: Boolean = true)`. When `siddurMerge` is false, one `Chapter` per ref with verses carried through unchanged, and `sectionTitles` supplied by the caller. A leg whose fetch fails yields a `Chapter` with no verses rather than failing the whole result.
- Produces: `ChitasSections.build(texts: List<SefariaText?>, titles: List<String>, hebrew: Boolean)` — a pure helper returning the three legs' `ReaderSection` inputs, so section shape is unit-testable without Compose. Return `List<Pair<String, List<AliyotPartitioner.TaggedVerse>>>`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChitasSectionsTest {
    private fun text(vararg chapters: Pair<Int, IntRange>) = SefariaText(
        heRef = "",
        chapters = chapters.map { (num, vs) -> Chapter(num, vs.map { Verse(it, "ה$it", "e$it") }) }
    )

    @Test fun threeLegsInOrder() {
        val legs = ChitasSections.build(
            listOf(text(30 to 14..27), text(44 to 1..5, 45 to 1..3), text(1 to 1..1)),
            listOf("Chumash", "Tehillim", "Tanya"), hebrew = false
        )
        assertEquals(3, legs.size)
        assertEquals(listOf("Chumash", "Tehillim", "Tanya"), legs.map { it.first })
    }

    @Test fun verseAndChapterNumbersSurviveMultiChapterLegs() {
        val legs = ChitasSections.build(
            listOf(text(30 to 14..15), text(44 to 1..2, 45 to 1..2), text(1 to 1..1)),
            listOf("Chumash", "Tehillim", "Tanya"), hebrew = false
        )
        // Tehillim leg keeps real psalm numbers and real verse numbers.
        assertEquals(listOf(44, 44, 45, 45), legs[1].second.map { it.chapter })
        assertEquals(listOf(1, 2, 1, 2), legs[1].second.map { it.verse.num })
        // Chumash keeps its true verse numbers, not 1..N.
        assertEquals(listOf(14, 15), legs[0].second.map { it.verse.num })
    }

    // Review Focus 5
    @Test fun aMissingLegKeepsTheOthers() {
        val legs = ChitasSections.build(
            listOf(text(30 to 14..15), text(44 to 1..2), null),
            listOf("Chumash", "Tehillim", "Tanya"), hebrew = false
        )
        assertEquals(3, legs.size)
        assertEquals(2, legs[0].second.size)
        assertEquals(0, legs[2].second.size)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hebrewcal.data.ChitasSectionsTest"`
Expected: FAIL — `ChitasSections` unresolved.

- [ ] **Step 3: Implement `ChitasSections.build` in `data/ChitasSections.kt`**

One entry per leg; flatten each text's chapters into `TaggedVerse(ch.number, verse)` with nothing renumbered; a `null` text yields an empty verse list.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.hebrewcal.data.ChitasSectionsTest"`
Expected: PASS

- [ ] **Step 5: Wire `mode == "chitas"` into `ReaderViewModel.load`**

On a blank ref: resolve via `repo.parshaFor(ChitasSchedule.parshaDateFor(advanced), diaspora)`, build the `ChitasPortion`, join the three refs with `Tefillot.REF_SEPARATOR`. Fetch with `cacheFirst = false` and `siddurMerge = false`. Do not touch `lastTehillim` / `lastTorah`. Add per-leg tolerance to `fetchCombined` so a failed ref becomes an empty chapter.

Pass `elulRef = ""` for this mode regardless of `prefs.showElulTehillim`: per the spec, the Chitas Tehillim leg is the monthly cycle only, and `showElulTehillim` continues to govern the standalone Tehillim button alone.

- [ ] **Step 6: Render the legs**

In `TextReaderActivity`, add a `chitas` branch to the section build that maps `ChitasSections.build(...)` to `ReaderSection(title, verses, markChapters = true)`. Change `foldable` to `(mode == "tefillah" || mode == "chitas") && sections.size > 1`. Leave `showAliyotToggle` false. A leg with no verses shows its heading plus the offline line. Do not alter any `mode == "tefillah"` branch.

- [ ] **Step 7: Verify the prayer reader has not regressed**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, including the existing prayer/siddur tests.

- [ ] **Step 8: Commit**

```bash
git add -A app/src/main/kotlin/com/hebrewcal/ui/reader app/src/main/kotlin/com/hebrewcal/data/ChitasSections.kt app/src/test/kotlin/com/hebrewcal/data/ChitasSectionsTest.kt
git commit -m "feat: add the chitas reader mode with three foldable legs"
```

---

### Task 6: Notification button

**Files:**
- Modify: `app/src/main/res/layout/notification_hebrew_date_expanded.xml` (beside `tv_btn_parsha`), `notification_hebrew_date.xml` (beside `tv_btn_parsha_c`)
- Modify: `app/src/main/kotlin/com/hebrewcal/ui/notification/LockscreenNotificationBuilder.kt:196-265`
- Modify: `app/src/main/kotlin/com/hebrewcal/ui/notification/CalendarNotificationService.kt:160-210`

**Interfaces:**
- Consumes: `prefs.chitasButtonOn` (Task 4); `ChitasSchedule.legTitles` for the pill label.
- Produces: `buildNotification(..., chitasActionLabel: String? = null)` — a new trailing optional parameter; `null` hides the pill.

- [ ] **Step 1: Add the two pill views**

`tv_btn_chitas` and `tv_btn_chitas_c`, copying the `tv_btn_parsha` blocks including `bg_pill_*`, `layout_marginStart="8dp"` and `visibility="gone"`.

- [ ] **Step 2: Wire the PendingIntent**

Follow the Tehillim block at `:199-224`, with `EXTRA_MODE = "chitas"`, `EXTRA_REF = ""`, a new `CHITAS_ACTION_REQUEST_CODE`, and `data = Uri.parse("hebrewcal://reader/chitas")` so it does not collide with the other pills' intents. Set `anyReaderButton = true`. Hide both views when the label is null.

- [ ] **Step 3: Pass the label from the service**

Only when `prefs.chitasButtonOn`. Use the same nightfall-advanced `dateInfo` the Tehillim label already uses. Label: "Chitas" / "חת״ת".

- [ ] **Step 4: Verify on device**

Run: `./gradlew installDebug`
Expected: with the toggle off the notification is unchanged; with it on a third pill appears in both the collapsed and expanded views and opens the Chitas overlay. Check the collapsed view at a narrow width for truncation.

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main/res/layout app/src/main/kotlin/com/hebrewcal/ui/notification
git commit -m "feat: add the Chitas pill to the lockscreen notification"
```

---

### Task 7: Widget button

**Files:**
- Modify: `app/src/main/kotlin/com/hebrewcal/ui/widget/HebrewDateWidget.kt:258-300`

**Interfaces:**
- Consumes: `prefs.chitasButtonOn`; the same extras as Task 6.

- [ ] **Step 1: Add the button**

Follow the `parshaLabel` block, which already passes a blank `EXTRA_REF` — the same pattern Chitas needs. Shown only when the toggle is on. Watch the Glance child budget noted in the comment at `:258`; use the nested `Column` already there. Per `glance-widget-oneplus-cornerradius`, any background uses a shape drawable, not Glance `cornerRadius`.

- [ ] **Step 2: Verify on device**

Run: `./gradlew installDebug`
Expected: the button appears and opens the Chitas overlay; the widget still renders with all buttons enabled.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/hebrewcal/ui/widget/HebrewDateWidget.kt
git commit -m "feat: add the Chitas button to the widget"
```

---

### Task 8: Offline prefetch and the Kehot credit

**Files:**
- Modify: `app/src/main/kotlin/com/hebrewcal/service/TextPrefetchJob.kt:51-72`
- Modify: `app/src/main/kotlin/com/hebrewcal/ui/settings/MainSettingsScreen.kt` (About section)

**Interfaces:**
- Consumes: `ChitasSchedule.parshaDateFor`, `ChitasSchedule.portionFor` (Task 3); `repo.parshaFor`, `repo.prefetchText`.

- [ ] **Step 1: Prefetch Chitas for the days ahead**

When `prefs.chitasButtonOn`, for today plus `prefs.prefetchTehillimDays`, make **two** per-date calendars calls and prefetch **two** texts. Count failures the way the surrounding loops do and add the Chitas counts to the completion log line.

> **Two corrections made during execution, both caught by Task 8's review. The original text of this step was wrong on both counts.**
>
> 1. **One calendars call cannot serve both legs.** `Tanya Yomi` is a per-day item, but the Chumash leg must query the coming Shabbat (`parshaDateFor`) for the week's aliyot. Reading `tanyaRef` off the Shabbat's response shows the Shabbat's Tanya on every day of the week — verified: `2026-11-18` gives `Kuntres Acharon 4:35` ("8 Kislev") while `2026-11-21` gives `4:50` ("11 Kislev"). Use `repo.parshaFor(ChitasSchedule.parshaDateFor(day), diaspora)` for the **aliyot** and `repo.parshaFor(day, diaspora)` for the **Tanya ref**. Both are cached per Gregorian date. This bug also existed in the reader and was fixed there in the same round.
> 2. **"The Chumash text is already covered by the existing parsha loop" was false.** That loop prefetches only the whole-parsha `sefariaRef`, while the reader fetches a single aliyah sub-range — a different cache key, since the key is the sanitized ref string. The day's aliyah ref must be prefetched explicitly, or the Chumash leg is empty offline.
>
> The Tehillim claim does hold: Chitas and the Tehillim loop both use `TehillimSchedule.portionFor` over the same Hebrew days, so that text is genuinely already cached.

- [ ] **Step 2: Add the About credit**

Tanya text: Kehot Publication Society, CC-BY-NC, via Sefaria — beside the existing Sefaria and Wikisource credits.

- [ ] **Step 3: Verify offline**

Run: `./gradlew installDebug testDebugUnitTest`
Expected: tests PASS. On device, let the prefetch run, enable airplane mode, and confirm the Chitas overlay still opens with all three legs. Then clear the cache, go offline, and confirm the Tanya leg shows its heading and offline line while Chumash and Tehillim still render.

- [ ] **Step 4: Commit**

```bash
git add -A app/src/main/kotlin/com/hebrewcal/service/TextPrefetchJob.kt app/src/main/kotlin/com/hebrewcal/ui/settings
git commit -m "feat: prefetch the daily Tanya and credit Kehot in About"
```

---

## Before release

Not a task — it needs a year of data, not a code change, and it is the one thing this plan cannot prove.

- [x] **Verify Sefaria's `Tanya Yomi` is the real Chitas cycle.** Largely discharged during execution:
  - The cycle **restarts on 19 Kislev** (Yud-Tes Kislev, the day the annual Tanya cycle begins) at `Tanya, Part I; Likkutei Amarim, Title Page 1`, with 18 Kislev on `Part V; Kuntres Acharon 9:1` — the book's last section. A wrong or generic schedule would not land its boundary on that date.
  - Across every day sampled: exactly one section per Hebrew day, labels advancing one Hebrew date at a time, refs strictly progressing through the book in order, no repeats and no gaps.
- [ ] **Residual check (optional):** a day-by-day comparison against a published Chitas calendar for a Hebrew leap year, where Adar I/II could shift the distribution. The cycle-boundary evidence above makes a mismatch unlikely, but leap years were not directly sampled.
