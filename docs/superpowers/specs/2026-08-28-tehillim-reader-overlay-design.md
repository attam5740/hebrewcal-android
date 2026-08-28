# Daily Tehillim + Weekly Parsha reader overlay — design

**Date:** 2026-08-28
**Status:** Approved design (in revision), pending implementation plan
**Area:** widget · new in-app reader screen · Sefaria networking

## Goal

Add two buttons to the Hebrew-date home-screen widget:

1. **תהילים יומי** (Daily Tehillim) — label includes today's chapter range in
   the display language (Hebrew gematria `א׳–ט׳`, or Arabic numerals `1–9` in
   English).
2. **פרשת השבוע / Weekly Parsha** — label includes the upcoming week's parsha
   name (e.g. `פרשת השבוע · כי תבוא`).

Tapping either opens the **same in-app reader overlay** — not the browser —
displaying the relevant text fetched from the **Sefaria API**.

The overlay shows the full portion with:
- a 50%-opacity dark-grey scrim over the home screen, white text;
- a small **text-size slider**;
- **fast-scroll** to jump anywhere with a finger;
- a **language toggle** (Hebrew ⇄ English);
- **nikkud** and **te'amim** on/off toggles (Hebrew vocalization).

Per-screen defaults:
- **Tehillim:** Hebrew, nikkud **on**, te'amim **off**.
- **Parsha:** Hebrew, nikkud **on**, te'amim **on**.

## Key idea: one reader, client-side vocalization

The overlay is a single reusable component that renders **any Sefaria ref**.
To make the nikkud/te'amim/language toggles instant (no refetch), it fetches
the **fully-vocalized** Hebrew (nikkud + te'amim) plus the English translation
**once**, then removes marks **client-side** by Unicode range:

- **Te'amim (cantillation):** `U+0591`–`U+05AF`.
- **Nikkud (vowel points):** `U+05B0`–`U+05BC`, `U+05C1`, `U+05C2`, `U+05C7`.

Constraint: te'amim implies nikkud. Turning nikkud **off** also hides te'amim
(and disables the te'amim toggle); turning te'amim **on** forces nikkud on.

## Feature 1 — Daily Tehillim

### Division (day-of-month)

Keyed off the widget's already-computed **halachic** Hebrew day
(`hebrewDayNumber`, which already rolls at tzet hakochavim).

| Day | Chapters | Sefaria ref |
|----:|----------|-------------|
| 1  | 1–9      | `Psalms.1-9` |
| 2  | 10–17    | `Psalms.10-17` |
| 3  | 18–22    | `Psalms.18-22` |
| 4  | 23–28    | `Psalms.23-28` |
| 5  | 29–34    | `Psalms.29-34` |
| 6  | 35–38    | `Psalms.35-38` |
| 7  | 39–43    | `Psalms.39-43` |
| 8  | 44–48    | `Psalms.44-48` |
| 9  | 49–54    | `Psalms.49-54` |
| 10 | 55–59    | `Psalms.55-59` |
| 11 | 60–65    | `Psalms.60-65` |
| 12 | 66–68    | `Psalms.66-68` |
| 13 | 69–71    | `Psalms.69-71` |
| 14 | 72–76    | `Psalms.72-76` |
| 15 | 77–78    | `Psalms.77-78` |
| 16 | 79–82    | `Psalms.79-82` |
| 17 | 83–87    | `Psalms.83-87` |
| 18 | 88–89    | `Psalms.88-89` |
| 19 | 90–96    | `Psalms.90-96` |
| 20 | 97–103   | `Psalms.97-103` |
| 21 | 104–105  | `Psalms.104-105` |
| 22 | 106–107  | `Psalms.106-107` |
| 23 | 108–112  | `Psalms.108-112` |
| 24 | 113–118  | `Psalms.113-118` |
| 25 | 119:1–96   | `Psalms.119.1-96` |
| 26 | 119:97–176 | `Psalms.119.97-176` |
| 27 | 120–134  | `Psalms.120-134` |
| 28 | 135–139  | `Psalms.135-139` |
| 29 | 140–144  | `Psalms.140-144` |
| 30 | 145–150  | `Psalms.145-150` |

**29-day-month rule:** no day 30 exists, so on the **29th** the reader combines
days 29 + 30 → chapters **140–150** (`Psalms.140-150`). Requires `daysInMonth`.

**Button label range (follows display language):** first–last chapter. In
Hebrew, gematria via KosherJava `HebrewDateFormatter.formatHebrewNumber()`
(handles טו/טז, geresh/gershayim); in English, Arabic numerals (`1–9`). Days 25
& 26 are within one chapter → display `קי״ט` / `119`. Both pill labels and the
parsha name follow the app display `language`.

`TehillimSchedule.kt` (new, `data/`), pure logic:
- `fun portionFor(hebrewDay: Int, daysInMonth: Int): TehillimPortion`
- `TehillimPortion(sefariaRef, firstChapter, lastChapter, labelRange)`.

## Feature 2 — Weekly Parsha

### Name for the button label (offline)

Widgets must render without network, so the label name comes from **KosherJava**
(already in the app). Use the **upcoming** parsha regardless of the existing
`showParshaOnWeekdays` display toggle:
- `JewishCalendar.getUpcomingParshah()` (KosherJava 2.4.0) → format with
  `HebrewDateFormatter.formatParsha()`. Fallback if unavailable: advance a temp
  calendar to the next Shabbat and read `.parshah`.
- Respect `location` (`inIsrael`) so Israel/Diaspora weeks match.
- Label follows the app **display language**: `פרשת השבוע · כי תבוא` (He) /
  `Weekly Parsha · Ki Tavo` (En).

### Torah ref for the reader (authoritative)

Resolve the ref at overlay-open time via the Sefaria **calendars API**, which
handles double portions and Israel/Diaspora:
- `GET https://www.sefaria.org/api/calendars?diaspora=<0|1>` → the
  `"Parashat Hashavua"` item gives `ref` (e.g. `Deuteronomy 26:1-29:8`),
  `heRef`, and `displayValue.{en,he}` (the name).
- `diaspora` from the app `location` setting.
- `ParshaRepository.currentParsha(diaspora): Result<ParshaRef>` — short-lived
  cache (a day) in `filesDir`. On network failure with no cache, the overlay
  shows the offline-error state.

Consistency note: the KosherJava label name and the Sefaria ref are both
deterministic from date + location and agree in normal weeks; the label is
cosmetic while the ref drives the text.

## Shared components

### `SefariaTextRepository.kt` (new, `data/`)
Network + parse + cache. **No new library** — `HttpURLConnection` +
`kotlinx-serialization-json` (both already present).
- `suspend fun fetchText(ref: String): Result<SefariaText>` on `Dispatchers.IO`.
- **Endpoint:** classic text API
  `https://www.sefaria.org/api/texts/<ref>?context=0&commentary=0&vhe=<HE_VERSION>&ven=<EN_VERSION>`
  (URL-encoded). Returns `he` + `text` verse arrays (flat for single chapter,
  nested one-array-per-chapter for ranges) and `heRef`.
  - `HE_VERSION` = the **fully-vocalized** edition (nikkud **and** te'amim):
    Sefaria "Tanach with Ta'amei Hamikra". Client strips marks per toggles.
  - `EN_VERSION` = JPS 1917 public-domain translation.
  - **Verify exact version identifiers during implementation** (the v3
    `?version=` form returned an empty `versions` array in spike testing). If a
    requested version is unavailable, fall back to the API default rather than
    failing.
- **HTML handling:** strip residual tags (`<span>`, `<br>`, `<small>`, footnote
  markup) to plain text; keep Hebrew vocalization characters intact.
- **Model:** `SefariaText(heRef, chapters: List<Chapter>)`,
  `Chapter(number, verses: List<Verse>)`, `Verse(num, he, en)` — **both**
  languages loaded together so language toggling is instant.
- **Disk cache:** `filesDir/sefaria/<sanitized-ref>.json`; read cache first,
  overwrite on success. Bounds `INTERNET` use to first-view-per-ref and enables
  offline re-reads.

### `HebrewVocalization.kt` (new, `data/` or `util/`)
- `fun strip(text: String, nikkud: Boolean, teamim: Boolean): String` removing
  the Unicode ranges above. Pure, unit-tested.

### `TextReaderActivity.kt` (new, `ui/reader/`)
Translucent single-activity Compose screen, reused by both buttons.
- **Window:** translucent theme (`windowIsTranslucent=true`, transparent
  background) so the home screen shows behind the scrim.
- **Scrim:** full-screen `Box`, `Color(0xFF2E2E2E).copy(alpha = 0.5f)`, white text.
- **Top bar (translucent):** portion title (`heRef` / English ref), **language
  toggle** (He⇄En), **nikkud** toggle, **te'amim** toggle (te'amim disabled when
  nikkud off), text-size **slider** (14sp–34sp), close (X).
- **Body:** `LazyColumn` — per chapter a header (`פרק א׳`/`Psalm 1`; for Torah,
  the book+chapter), then numbered verses in the active language; Hebrew RTL,
  English LTR. Hebrew verses passed through `HebrewVocalization.strip(...)`.
- **Fast-scroll:** draggable vertical thumb mapping drag to `LazyListState` index.
- **States:** loading spinner; offline-with-no-cache error
  ("אין חיבור לאינטרנט" / "No internet connection"); "מקור: ספריא / Source:
  Sefaria" attribution footer.
- **Dismiss:** system Back, scrim tap-outside, or X.
- **Intent input:** `EXTRA_REF` (Sefaria ref), `EXTRA_TITLE`, `EXTRA_INITIAL_LANG`,
  `EXTRA_DEFAULT_NIKKUD`, `EXTRA_DEFAULT_TEAMIM`. Text-size persists via DataStore
  (§ Prefs); language/nikkud/te'amim start from the passed defaults and toggle
  live within the session.

### `HebrewDateWidget.kt` (edit)
- Render two pills beneath the existing content: `תהילים יומי · <range>` and the
  parsha pill. Each pill's `clickable(actionStartActivity(...))` launches
  `TextReaderActivity` with the appropriate extras. Glance child `clickable`
  overrides the outer open-Settings tap for each pill's region.
  - Tehillim extras: ref from `TehillimSchedule.portionFor(...)`, defaults
    nikkud=on/te'amim=off.
  - Parsha extras: ref from `ParshaRepository.currentParsha(...)`; label name
    from KosherJava; defaults nikkud=on/te'amim=on.
- Resolving the parsha ref may touch the network; keep it off the Glance
  composition path — pass the parsha **name/date** and resolve the ref inside
  `TextReaderActivity`, or resolve lazily. (Widget composition stays offline.)
- **Impact:** run `gitnexus_impact` on `HebrewDateWidgetContent` before editing.

### `HebrewCalendarRepository.kt` (edit)
- Add `daysInMonth: Int` to `HebrewDateInfo` (from `jewishCalendar
  .daysInJewishMonth`, same rollover-adjusted calendar).
- Optionally expose an `upcomingParshaName` helper (or a small dedicated
  resolver) so the widget label doesn't depend on `showParshaOnWeekdays`.
- **Impact:** `HebrewDateInfo` is consumed by the widget and the notification
  builder; run `gitnexus_impact` before editing and update all call sites.

### `UserPreferences.kt` (edit — minimal)
- Add `readerTextSizeSp: Float` (default ~18f) with its `PreferenceKeys` entry
  and `update…` method, mirroring existing prefs. Shared by both reader screens.
- **No language setting change.** Reader initial language reuses existing
  `language`; the in-overlay toggle is view-local.

### `AndroidManifest.xml` (edit)
- Add `<uses-permission android:name="android.permission.INTERNET" />`.
- Register `TextReaderActivity` with a translucent theme, `exported=false`.

## Data flow

```
Widget pill tap
  Tehillim:  TehillimSchedule.portionFor(hebrewDay, daysInMonth) → ref + label
  Parsha:    KosherJava upcoming parsha → label; ref via Sefaria calendars API
  → TextReaderActivity(EXTRA_REF, EXTRA_TITLE, initial lang/nikkud/teamim)
      → SefariaTextRepository.fetchText(ref)
           → disk cache hit? return
           → else HTTP GET Sefaria (he maximal + en) → parse → cache → return
      → LazyColumn renders active language;
        Hebrew via HebrewVocalization.strip(nikkud, teamim);
        slider + fast-scroll + language/nikkud/te'amim toggles all client-side
```

## Non-goals (YAGNI)

- No weekly-Tehillim division; Tehillim is the single monthly-portion button.
- No haftarah/other calendar items — parsha Torah reading only.
- No bookmarking, audio, or per-chapter nav menu (fast-scroll covers jumping).
- No offline bundling of full texts — disk cache of viewed refs only.
- No changes to the Settings language control.

## Risks / tradeoffs

- **Adds the `INTERNET` permission** (app is currently fully offline; recent
  commits reduced its permission footprint). Disk-caching confines network use
  to first view of each ref. Accepted by product owner.
- **Sefaria version identifiers** must be confirmed at build time; fall back to
  defaults if unavailable.
- **Parsha ref resolution** depends on the calendars API (or a local
  name→ref map as a fallback); double portions / Israel-Diaspora handled by the
  API's `diaspora` param.
- **Widget space:** two extra pills must degrade gracefully on small widget
  sizes (compact rows; may be the last visible elements).

## Testing

- `TehillimSchedule`: unit tests for all 30 days + 29-day-month combine rule +
  gematria labels (א׳–ט׳, קי״ט, קמ׳–קן׳).
- `HebrewVocalization`: strip tests — te'amim-off keeps nikkud, both-off yields
  bare consonants, English untouched.
- `SefariaTextRepository`: parse tests against captured Sefaria JSON fixtures
  (single-chapter flat + multi-chapter nested); cache read/write; HTML strip.
- `ParshaRepository`: parse the calendars API `Parashat Hashavua` item → ref;
  diaspora flag wiring.
- Reader: manual UAT — slider resizes, fast-scroll jumps, language/nikkud/te'amim
  toggles apply instantly, offline shows cache or error, back/scrim dismiss.
