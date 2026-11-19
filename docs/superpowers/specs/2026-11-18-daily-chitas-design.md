# Daily Chitas (חת"ת) — Design

**Date:** 2026-11-18
**Status:** Approved design, ready to plan
**Branch context:** `feat/shabbat-and-celestial-suite` (post-1.21)

## Intent

Add an optional **Chitas** shortcut: one toggle in settings that, when on, puts a
Chitas button on the lockscreen notification and on the home-screen widget. Tapping
it opens today's Chumash, Tehillim and Tanya in the existing reader overlay as three
foldable sections.

Chitas (חת"ת — **ח**ומש, **ת**הלים, **ת**ניא) is the Chabad daily study cycle: a fixed
portion of each of the three texts, every day. Someone who keeps it currently has to
leave this app for it, even though the app already shows them the parsha and the daily
Tehillim on the same notification.

## Goals

- One user-controlled toggle, default **off**, visible to every user regardless of nusach.
- A Chitas button on both the notification (collapsed and expanded) and the widget,
  appearing only when the toggle is on.
- One reader overlay showing all three portions as collapsed, tappable section headings.
- Correct for the Hebrew day: after nightfall the button shows the *next* day's Chitas.
- Works offline, like every other reader text in the app.
- No new content source, no new licensing exposure for the F-Droid build.

## Non-goals

- Rashi on the Chumash. Chabad's Chitas Chumash is published with Rashi, but that is a
  commentary-rendering feature the reader does not have. Plain text of the aliyah only.
- The *Hayom Yom* / Rambam legs that some daily-study apps bundle alongside Chitas.
- A standalone Chitas screen, calendar integration, or study-streak tracking.
- Audio.
- Changing anything about how the existing Tehillim or Parsha buttons behave.

## Anti-goals

Things that would make this a failure even if it technically works:

- **Crowding the notification into uselessness.** It already carries up to seven
  windowed prayer actions plus Tehillim and Parsha. Chitas must add exactly one button,
  and must not appear at all when the toggle is off.
- **Regressing the prayer reader.** `mode == "tefillah"` carries the siddur's section
  merging and typography. Chitas must not alter that path's behaviour.
- **Shipping text we have no right to redistribute.** See Constraints.
- **Silently showing the wrong portion.** A Chitas button that is a day off, or that
  shows the wrong aliyah on a festival, is worse than no button.

## Constraints

### Licensing — why not chabad.org

The original request was to source this from chabad.org. That is not available to us:

- Chabad.org's Terms of Service state the user "shall not copy, reproduce, distribute,
  transmit, broadcast, display, sell, license, or otherwise exploit any Content... without
  the prior express written consent of Chabad.org or the respective licensors," and
  "shall not access Content through any technology or means other than... explicitly
  authorized means Chabad.org may designate."
- Their `robots.txt` disallows `/api/` while allowing `/api/v2/chabadorg/torahtexts/`.
  That path is undocumented and carries no third-party grant; the allowance is for
  crawlers, not for client applications.
- Their Chitas Chumash is the Chabad House Publications / Kehot "Daily Studies in Chitas
  — Schottenstein Edition" — a current commercial translation.

Their separate Copyright Policy permits passing an *article* on unmodified with
attribution, and directs republication requests to `permissions@chabad.org`. Neither
covers bundling their texts into an app.

**Decision: source all three legs through Sefaria**, which the app already uses.

### Licensing — what we will ship

| Leg | Source | License |
|---|---|---|
| Chumash | Sefaria Tanakh, the editions the reader already uses | as today |
| Tehillim | Sefaria Tanakh, as today | as today |
| Tanya | Sefaria, `Kehot Publication Society` versions (he + en) | **CC-BY-NC**, `kehot.com` |

The Tanya text on Sefaria comes from Kehot — Chabad's own publishing house — under
CC-BY-NC. Non-commercial suits a free F-Droid build. It requires attribution, so Tanya
and Kehot get an About-screen credit alongside the existing Sefaria and Wikisource
credits. See `fdroid-release-process` notes before the release that carries this.

### Verified facts the design rests on

Checked against the live APIs on 2026-11-18 (Kislev 8, 5787, parsha Vayetzei):

- **`/api/calendars` already returns `Tanya Yomi`.** The app already calls this endpoint.
  Today: `ref = "Tanya, Part V; Kuntres Acharon 4:35"`, `displayValue.en = "8 Kislev"`.
  The label being a Hebrew date confirms it is the date-keyed Chabad cycle.
- **The Chumash leg needs no new data.** `ParshaCalendarsParser` already parses
  `extraDetails.aliyot`. For Vayetzei that is 8 entries (7 aliyot + maftir). Entry 4 is
  `Genesis 30:14-30:27` — exactly what chabad.org's Daily Chumash showed for that
  Wednesday. So the mapping is **day-of-week index into the existing aliyot list**.
- **The Tehillim leg needs no new data.** `TehillimSchedule` is the standard siddur
  day-of-month division, which is the division Chitas uses.
- **`Tefillot.REF_SEPARATOR` is `"||"`**, so it does not collide with the semicolon and
  commas inside `Tanya, Part V; Kuntres Acharon 4:35`.
- **Nightfall rollover already exists.** `CalendarNotificationService` computes
  `tzetToday` and passes `advanceAfter = tzetToday` when deriving the displayed Hebrew
  date, and `scheduleWidgetTzetAlarm` re-arms the widget at nightfall. Chitas reads the
  same already-advanced date and inherits this for free.

## Decisions taken in the design conversation

| Question | Chosen |
|---|---|
| Source | Sefaria for all three legs |
| Button shape | **One** button → three foldable sections |
| Toggle visibility | Any nusach; toggle always listed, defaults off |
| Day rollover | At nightfall (the Hebrew day) |
| Implementation | Approach B below |
| Section granularity | Each leg keeps **real chapter headings and verse numbers** inside it |
| Offline behaviour | Cache calendars per day in prefetch, **and** degrade gracefully per leg |

## Approaches considered

**A. Reuse `mode == "tefillah"` verbatim.** Join the three refs with
`Tefillot.REF_SEPARATOR` and pass the existing prayer mode. Smallest diff; folding works
immediately. **Rejected:** `fetchCombined` would run the refs through
`PrayerSections.units()`, which expects siddur section structure, and the renderer's
`mode == "tefillah"` branches would apply prayer typography and vocalization to Chumash
and Tehillim. Wrong titles, wrong styling.

**B. A new `chitas` mode that shares the plumbing. — CHOSEN.** Reuse `fetchCombined`'s
multi-ref fetch and the fold mechanism, but skip `PrayerSections.units()`, supply
Chitas's own section titles, and keep Tanakh typography. Reuses the expensive parts
without inheriting siddur assumptions, and leaves `tefillah` untouched so the prayer
reader cannot regress.

**C. Three single-ref loads with in-reader next/prev.** Button opens Chumash; the
existing `navigateTo` walks to Tehillim and Tanya. Avoids multi-ref work entirely.
**Rejected:** it is not the folded three-section view that was chosen.

## Design

### 0. Prerequisite: version pinning in `SefariaRepository`

**This is a bug fix the feature cannot ship without.** `fetchText` appends
`&vhe=Miqra according to the Masorah` to every ref that does not contain `"Siddur"`.
Tanya is not Tanakh, and that edition does not exist for it. Verified against the live
API on the Tanya Yomi ref `Tanya, Part V; Kuntres Acharon 4:35`:

| Request | Result |
|---|---|
| As the app builds it today (`vhe=Miqra…`) | **Hebrew empty** (`he=0`), English = Kehot |
| No version params | he = Kehot, en = Kehot |
| `vhe=Kehot Publication Society`, `ven=Kehot Publication Society (English Translation)` | he = Kehot, en = Kehot |

Two things follow:

1. The `contains("Siddur")` test must become a positive test for **Tanakh refs only** —
   that is the sole category the Masorah edition covers. The Siddur special-case has
   always been a narrow stand-in for this.
2. Pin Kehot explicitly on **both** sides for Tanya rather than relying on Sefaria's
   default. Sefaria's current default English for Tanya happens to be the Kehot
   CC-BY-NC edition, but the design's licensing position must not depend on a default
   that can change under us.

### 1. New: `ChitasSchedule`

`app/src/main/kotlin/com/hebrewcal/data/ChitasSchedule.kt` — pure logic, no I/O, unit
testable.

```
data class ChitasPortion(
    val chumashRef: String,      // one aliyah, e.g. "Genesis 30:14-30:27"
    val chumashParsha: String,   // "Vayetzei"  (he: "ויצא")
    val aliyahIndex: Int,        // 1..7, for the section heading
    val tehillimRef: String,     // from TehillimSchedule
    val tanyaRef: String,        // from the Tanya Yomi calendar item
    val tanyaLabel: String       // "8 Kislev"
)
```

- Chumash: `aliyot[dayOfWeek - 1]` where `dayOfWeek` is 1 (Sunday) through 7 (Shabbat),
  taken from the **already nightfall-advanced** Hebrew date. Take only the first 7
  entries — ignore a trailing maftir entry.
  **Which parsha:** `nextOrSame(SATURDAY)` from the **advanced** date. This matters on
  Saturday night: after tzet the advanced day is Sunday, so Chitas moves to the *coming*
  week's parsha and its first aliyah. Every existing parsha lookup
  (`HebrewCalendarRepository.upcomingParshaName`, `PrefetchPlanner.shabbatDates`) runs
  `nextOrSame(SATURDAY)` against the **raw** `now`, which on a Saturday evening still
  resolves to the Shabbat just ended. Feeding those the advanced date is the fix; getting
  it wrong leaves Chitas a full week stale every Saturday night.
- Israel vs diaspora: pass `prefs.location`, as every other calendars call already does,
  so divergence weeks resolve correctly.
- Tehillim: `TehillimSchedule.portionFor(...)` on the same advanced date. **Exclude** the
  Elul addition: `showElulTehillim` governs the separate Tehillim button, and Chitas
  Tehillim is the monthly cycle only.
- Tanya: ref and label straight from the `Tanya Yomi` item in the calendars response.

### 2. Resolution boundary and the calendars data

**The reader resolves the refs, not the notification service.** This follows the existing
precedent: `ReaderViewModel.load` already handles `mode == "parsha"` with a blank ref by
resolving the parsha itself. The notification and widget pass `EXTRA_MODE = "chitas"` and
`EXTRA_REF = ""`.

The reason is not just symmetry. `CalendarNotificationService`'s current path is entirely
local — `upcomingParshaName` plus `TehillimSchedule` — and never touches the calendars
response, which is where **both** `extraDetails.aliyot` and the Tanya Yomi item live.
Having the service build the ref would mean teaching it to read the calendars cache and
then passing the parsha name, aliyah ordinal and Tanya label as extra intent fields,
since none of them can be recovered from a joined ref string.

- Parse `Tanya Yomi` out of `/api/calendars` in `ParshaCalendarsParser` (or a sibling),
  matching how the other calendar items are already read. No new endpoint.
- **Per-day calendars caching already exists — no new caching needed.**
  `SefariaRepository.parshaFor(date, diaspora)` already requests
  `?diaspora=N&year=…&month=…&day=…` (verified as the only date form the API honours —
  `&date=` is silently ignored and returns today) and already caches the response per
  Gregorian date as `calendars_{diaspora}_{date}.json`.
  A response carries both `extraDetails.aliyot` *and* the `Tanya Yomi` item, and
  `ParshaCalendarsParser` surfaces both from the JSON it already parses.

  **CORRECTED DURING EXECUTION — two calls per day, not one.** An earlier version of
  this section claimed one `parshaFor` call could supply both legs. That is wrong, and
  shipping it caused a real bug caught in Task 8's review. `Tanya Yomi` is a **per-day**
  item, but the Chumash leg must query the **coming Shabbat** (`parshaDateFor`) to get
  the week's aliyot. Reading `tanyaRef` off the Shabbat's response therefore shows the
  Shabbat's Tanya on every day of the week — verified: `2026-11-18` gives
  `Kuntres Acharon 4:35` ("8 Kislev") while `2026-11-21` gives `4:50` ("11 Kislev").

  So: `parshaFor(parshaDateFor(advanced))` for the **aliyot**, and
  `parshaFor(advanced)` for the **Tanya ref**. Both are cached per Gregorian date, so the
  steady-state cost is one extra fetch per day.

### 3. Reader

`ReaderViewModel`:
- Accept `mode = "chitas"`, resolving the three refs from `ChitasSchedule` when the
  incoming ref is blank, then joining them with `Tefillot.REF_SEPARATOR`.
- `fetchCombined` lives here, in `ReaderViewModel` — not in `SefariaRepository`. Give it a
  way to skip `PrayerSections.units()` merging: a `combineMode` parameter defaulting to
  today's behaviour, so no existing call site changes.
- Fetch with `cacheFirst = false` (network-first with cache fallback, like Tanakh), *not*
  the prayer path's `cacheFirst = true`.
- **Partial success.** `fetchCombined` currently fails the whole load if any ref fails.
  Chitas needs per-leg tolerance: a leg that cannot be fetched renders as its heading
  plus a short "needs a connection" line, while the others display. This is what makes
  the offline decision above work, and it also contains a bad or moved Kuntres Acharon
  ref instead of blanking the whole screen.
- Do **not** write Chitas into `lastTehillim` / `lastTorah`; it is its own thing, and
  clobbering those would break the existing Tehillim ⇄ Torah swap.

### 3a. Section structure — the main piece of reader work

Chitas is **three foldable legs, each of which keeps real chapter headings and verse
numbers inside it.** Two of the three legs are multi-chapter: Tehillim 44–48 is five
psalms, and aliyot such as `Genesis 30:28-31:16` cross a chapter boundary.

The existing combined path cannot express this. `PrayerSections.units()` groups refs and
renumbers verses as `Verse(k + 1, …)` within each unit, so:

- Skipping `units()` yields one section *per chapter* — nine-plus sections, and
  `sectionTitles` indices no longer line up with the three legs.
- Keeping the unit flattening yields three sections, but Tehillim 44–48 becomes one
  ~400-verse run with no psalm numbers and synthetic verse numbers.

**The primitive for this already exists, so no new nesting concept is needed.**
`ReaderSection` already carries `markChapters: Boolean`, and the renderer already emits
an inline chapter marker at each chapter transition inside such a section
(`‹Ch. 30›` / `‹פרק ל›`), with every verse keeping its true number from `tv.verse.num`.
This is exactly how the parsha reader already displays an aliyah that spans chapters.

So a Chitas leg is **one `ReaderSection(legTitle, legVerses, markChapters = true)`**,
and folding already operates on top-level section indices. Concretely:

- Build three such sections from the three legs' chapters, carrying
  `AliyotPartitioner.TaggedVerse(ch.number, verse)` through unchanged. Do **not** route
  through `PrayerSections.units()`, which is what renumbers verses.
- Keep the existing `mode == "tefillah"` branch of `chapterSections` exactly as it is,
  and leave every prayer-specific branch (`vocalized`, prayer typography, prayer tap
  handling) keyed to `tefillah` alone.
- Verse numbering must come through unchanged from the Sefaria chapters, not be
  regenerated.

One visual consequence to accept: chapter transitions render as **inline markers**
rather than block headings, so the Tehillim leg reads as a continuous passage with
`‹Ch. 45›` marks rather than five "Psalm NN" headings. That is the established treatment
for multi-chapter aliyot, so it is consistent; promoting them to block headings would be
a separate change.

Leg headings, in reader language:

- `Chumash — Vayetzei, Revi'i` / `חומש — ויצא, רביעי`
  (reuse `AliyotPartitioner.aliyahName` for the ordinal — it already handles maftir)
- `Tehillim 44–48` / `תהלים מד–מח`
- `Tanya — 8 Kislev` / `תניא — ח׳ כסלו`

Chapter sub-headings inside a leg reuse the existing formatting: `Psalm 44` /
`פרק מד` for Tehillim, `Genesis 30` / `פרק ל` for Chumash.

`TextReaderActivity`:
- `foldable` becomes `(mode == "tefillah" || mode == "chitas") && sections.size > 1`, so
  Chitas opens with all three legs collapsed.
- `torahSide` and the aliyot toggle: Chitas shows no aliyot toggle
  (`showAliyotToggle` stays false).

### 4. Settings

- `UserPreferences`: add `chitasButtonOn: Boolean = false`.
- `PreferenceKeys`: add `CHITAS_BUTTON_ON = booleanPreferencesKey("chitas_button_on")`.
- `UserPreferencesRepository`: add `updateChitasButtonOn(on: Boolean)`.
- `MainSettingsScreen`: a toggle row beside the existing Tehillim and Parsha button
  toggles, following the `prayerButtonsOn` pattern. Label: **"Chitas button"**, with a
  one-line summary naming the three texts so a user who does not know the acronym can
  tell what it does.
- `SettingsBackup` round-trips it automatically via `asMap()`; no change needed there.
- Add strings to `app/src/main/res/values/strings.xml`.

### 5. Notification

`LockscreenNotificationBuilder` + both layouts
(`notification_hebrew_date.xml`, `notification_hebrew_date_expanded.xml`):
- Add `tv_btn_chitas` (expanded) and `tv_btn_chitas_c` (collapsed), following the
  existing Tehillim and Parsha custom buttons — these are custom `RemoteViews` pills, not
  `addAction` actions, so they are not subject to the action-count limit.
- Wire a `PendingIntent` to `TextReaderActivity` with `EXTRA_MODE = "chitas"`, the
  combined ref, and the title, with a fresh unique `requestCode`.
- Show only when `prefs.chitasButtonOn`.
- Pass `EXTRA_REF = ""` and let the reader resolve, per section 2.

### 6. Widget

`HebrewDateWidget`: a Chitas button alongside the existing reader buttons, via
`actionStartActivity<TextReaderActivity>` with the same extras (including the blank ref),
shown only when the toggle is on. Per `glance-widget-oneplus-cornerradius`, use a shape
drawable rather than Glance `cornerRadius`.

### 7. Offline prefetch

`TextPrefetch.run`: when `chitasButtonOn`, prefetch Chitas for today and the next
`prefetchTehillimDays` worth of days, reusing `repo.prefetchText`.

Cost per day: one **calendars** fetch (for that day's Tanya Yomi ref) plus one **Tanya
text** fetch. The Chumash and Tehillim refs are computed locally, and the Chumash text is
already covered by the parsha weeks being fetched.

Note the consequence for testability: a `PrefetchPlanner.chitasRefs(...)` helper
**cannot be pure** for the Tanya leg, because that ref only exists in the calendars
response. Split it: keep the Chumash and Tehillim ref computation pure and unit-tested in
`ChitasSchedule`, and let the Tanya ref arrive as a parameter the caller has already
fetched. Include the Chitas counts in the existing completion log line.

### 8. About

Add a Tanya / Kehot Publication Society (CC-BY-NC) credit to the About section added in
`3d3cf15`, alongside the Sefaria and Wikisource credits.

## Calls made in this document

Not raised in conversation; flagged here so they can be overridden on review.

1. **No Rashi.** Listed as a non-goal. The reader has no commentary rendering, and adding
   it is a larger feature than this one.
2. **Chitas Tehillim excludes the Elul addition.** `showElulTehillim` continues to govern
   only the standalone Tehillim button.
3. **Chitas does not participate in the Tehillim ⇄ Torah swap** and does not update the
   session's last-position memory for those modes.
4. **Default off.** Consistent with it being a minority practice, and it keeps the
   notification unchanged for existing users on upgrade.
5. **Settings placement:** grouped with the Tehillim and Parsha button toggles rather than
   with the prayer buttons, because it is untimed like they are.
6. **Network-first, not cache-first**, since two of three legs are Tanakh.
7. **Kehot pinned explicitly** for Tanya on both the Hebrew and English side, rather than
   relying on Sefaria's default happening to be Kehot (section 0).
8. **The reader resolves the refs**, following the `mode == "parsha"` blank-ref precedent,
   rather than the notification service building them (section 2).
9. **`prefs.location`** parameterises the Chumash leg, like every other calendars call.
10. ~~Per-day calendars caching is in scope.~~ **Not needed** — `parshaFor` already
    caches per Gregorian date, and one response carries both the aliyot and the Tanya
    ref (section 2).

## Risks and what the builder must verify

1. **Tanya Yomi alignment — verify before shipping.** Sefaria's `Tanya Yomi` matched the
   Chabad date label on the one day checked. Walk a full year programmatically and
   confirm the cycle matches the published Chitas schedule, paying attention to leap
   years and the Kuntres Acharon sections. If it diverges, the fallback is a locally
   computed Tanya schedule table — which would be a scope increase worth raising.
2. **Festivals and Yom Tov.** When a festival reading displaces the weekly parsha, check
   what `/api/calendars` returns for `Parashat Hashavua` and make sure the Chumash leg
   still follows the **weekly parsha**, which is what Chitas tracks.
3. **Double parshiyot** (e.g. Vayakhel-Pekudei): confirm Sefaria returns a combined
   7-aliyah list and that indexing by day-of-week still lands correctly.
4. **Aliyot list length.** Vayetzei returned 8 entries. Confirm the trailing entry is
   always maftir and never shifts the first seven.
5. **Notification layout room.** Both layouts already carry two pills; confirm a third
   fits at small widths and in the collapsed view without truncating.
6. **Cache keys.** Unrelated to Chitas but adjacent: `textCacheFile` keys on ref alone,
   which is the bug noted in the multilingual research. Chitas does not make it worse —
   its refs are distinct — but a builder touching this area should know.
7. ~~Section nesting is the schedule risk.~~ **Resolved during planning:** the
   `markChapters` primitive already does this (section 3a). The remaining care is simply
   not routing Chitas through `PrayerSections.units()`.
8. **Regression surface on the version-pinning fix.** Section 0 changes a condition that
   today affects *every* non-Siddur fetch in the app. Changing it to a positive Tanakh
   test must be verified against Tanakh, haftarah, siddur and Tanya refs before anything
   else is built on top.

## Testing

Unit tests, matching the existing `PrayerWindowsTest` / `TehillimScheduleTest` style:

- `ChitasScheduleTest`: day-of-week → aliyah index across a full week; Shabbat → 7th;
  the maftir entry ignored; a double parsha; a known fixture asserting Wednesday of
  Vayetzei 5787 yields `Genesis 30:14-30:27`.
- Nightfall rollover: the portion advances after `tzet`, not at midnight.
- **Saturday night specifically:** after tzet on the Shabbat of one parsha, the Chumash
  leg is the *next* parsha's first aliyah. This is the regression most likely to slip
  through, since it only misbehaves one evening a week.
- Version pinning: a Tanya ref returns non-empty Hebrew, and Tanakh refs still get the
  Masorah edition.
- `PrefetchPlannerTest` / `ChitasScheduleTest`: the pure Chumash and Tehillim ref
  computation for N days ahead.
- Reader: the combined ref yields exactly **three foldable legs**, with chapter
  sub-headings and true verse numbers preserved inside each — assert the psalm numbers of
  a five-psalm Tehillim leg and a chapter-crossing aliyah.
- Partial success: one failing leg renders its heading and message while the other two
  display.
- Regression guard: the `tefillah` path's sections, folding and typography are unchanged.

Manual, on the OnePlus test device per `adb-wireless-oneplus`: toggle on and off, confirm
the notification and widget buttons appear and vanish, open the overlay and expand each
of the three sections, then airplane-mode it to confirm the prefetched day still opens.

## Left to the builder

- Exact Compose layout of the settings row and the notification pill (follow the
  neighbouring Tehillim/Parsha code).
- Whether `combineMode` lands as an enum or a boolean on `fetchCombined`.
- Hebrew numeral formatting of the Tehillim range in the section heading — reuse the
  existing `HebrewDateFormatter` / gematria path.
- Icon choice for the notification and widget pill.
