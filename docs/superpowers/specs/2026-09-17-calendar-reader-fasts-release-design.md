# Calendar screen, reader continuity, fasts, notification and widget fixes — design

**Date:** 2026-09-17
**Status:** Draft for review
**Ships as:** version 1.14 (versionCode 21), together with the already-built
1.13 follow-ups (RTL book order, flame chips, faster Tehillim dial, open-book
nav icon, offline downloads). One F-Droid changelog entry covers all of it.

## Scope

1. Reader: Tehillim ⇄ Torah mode toggle.
2. Reader: continuous scrolling loads the next/previous chapters.
3. Reader: haftarah button and a week banner.
4. Notification: expanded view no longer crops the zmanim list.
5. Widget: fix the "Can't load widget" placeholder.
6. Fasts: correct start/end times shown next to every fast name.
7. New Hebrew calendar screen (translucent minimalist overlay).
8. Release: version bump, F-Droid metadata, README.

Out of scope: cache eviction, Gregorian-first calendar navigation, per-fast
custom opinions (Rabbeinu Tam etc.), i18n beyond the existing He/En pair.

---

## 1. Reader mode toggle

A 44×22 dp Canvas-drawn toggle in the reader bar, left of the open-book icon.
Track is a rounded glass pill; the knob is a small flame disc (same gradient
as the dial's go button) that slides with a spring animation. Endpoints are
drawn as glyphs on the track: a tiny **scroll** (two rollers) for Torah, a
tiny **harp** (three strings) for Tehillim. The knob covers the active glyph;
the inactive one shows faintly. Size stays within a standard Material switch.

Behaviour: flipping loads **the last position visited in that mode this
session**, else today's daily Tehillim / this week's parsha. The ViewModel
keeps `lastTehillim` and `lastTorah` `(ref, title, aliyot)` updated on every
successful load. Haftarah views count as the Torah side (the toggle sits on
Torah; flipping back returns to the last Torah position).

## 2. Continuous scrolling

`ReaderViewModel` gains a *window* over a book:

```
data class Window(val bookEn: String, val chapters: List<Chapter>,
                  val firstChapter: Int, val lastChapter: Int,
                  val firstPartial: Boolean, val lastPartial: Boolean)
```

The initial load fills the window from the fetched ref. Parsha refs often
start or end mid-chapter (Genesis 1:1–6:8); those edges are marked *partial*.

Extending forward: if `lastPartial`, refetch `Book.last` whole and replace the
partial chapter; otherwise fetch `Book.(last+1)`. Backward is symmetric.
Limits: Tehillim 1–150; Torah continues across books (Genesis 50 → Exodus 1,
Deuteronomy 34 is the end); other books (haftarah) stop when Sefaria returns
an error or an empty chapter. Each direction is single-flight; a thin
"loading" row shows at the edge being extended.

Trigger: a `NestedScrollConnection` on the text list. Overscroll past the
bottom (`available.y < 0` with nothing consumed while the last item is
visible) extends forward; overscroll at the top (`available.y > 0` at index 0,
offset 0) extends backward. Prepending keeps the reader still: after the
window grows at the front by *n* sections, the list scrolls to
`firstVisibleIndex + n` with the same offset in the same frame.

Interaction with existing modes:
- Aliyah partitioning applies to the original ref; extended chapters render
  as plain chapter sections before/after it.
- Tikkun mode recomputes scroll lines for the extended `TorahRange` (best
  effort, unchanged fallback to flowing text).
- The Elul supplement stays appended after the Tehillim window.
- The fast-scroll thumb uses the live section count.

Refs cache exactly as today (`Book.N` per chapter), so scrolled-in chapters
are reused offline. Prefetch is unchanged.

## 3. Haftarah button and week banner

`ParshaCalendarsParser` also collects every `Haftarah*` item into
`ParshaRef.haftarot: List<Reading(ref, dottedRef, nameEn, nameHe)>`. Existing
cached calendars JSON reparses without refetch.

**Banner:** a single 18 dp-high strip directly under the reader bar, always
present while the reader is open (any mode), text 11 sp, gold on the bar's
glass tint:
`פרשת האזינו · דברים ל״ב:א׳–נ״ב · הפטרה: הושע י״ד:ב׳–י׳` (English mirrors
it: `Parashat Ha'Azinu · Deuteronomy 32:1-52 · Haftarah: Hosea 14:2-10`).
Multiple haftarot are joined with `,`. Tapping the parsha part opens the
parsha; tapping the haftarah part behaves like the button. The banner's data
comes from `SefariaRepository.currentParsha` (cached; when unavailable the
banner shows only what is known and never blocks the text).

**Button:** an `ה` glyph button in the reader bar, shown only while
`mode == "parsha"` (Torah text, including chapters reached via the dial or by
scrolling). One haftarah → opens it directly. Several → a small popup lists
them (display names in the current language); choosing one opens it.
Haftarah opens with `mode = "haftarah"`: Hebrew, nikkud on, te'amim on, no
aliyot toggle, no tikkun toggle, continuous scrolling within that book,
banner still shown, toggle knob on the Torah side.

## 4. Notification: zmanim grid

Android caps expanded custom views (about 256 dp; OxygenOS is tighter), so a
one-column list of eight rows plus the header overflows and the shade crops
it. Fix: `ll_zmanim_container` becomes two `LinearLayout` columns filled
alternately (row *i* → column `i % 2`), each `item_zman_row` with 1 dp
vertical padding and 11 sp text. Header rows lose 2 dp of spacing each. With
the maximum 14 zmanim selected the expanded view fits in ~230 dp; with the
default 8 it uses ~170 dp. Past/next highlighting is unchanged.

## 5. Widget: "Can't load widget"

Glance shows that placeholder when `provideGlance` throws or when the emitted
tree cannot be translated to RemoteViews. Diagnosis first (systematic
debugging): install, add the widget, capture `logcat` filtered on
`Glance|AppWidget|HebrewDateWidget|AndroidRuntime`, and fix the actual cause.
Regardless of cause, two hardening changes ship:

- `provideGlance` wraps content building in `runCatching`; on failure it
  provides a minimal fallback (Hebrew date string only, or "Hebrew Calendar"
  if even that failed) and logs the throwable, so the widget never shows the
  system placeholder again.
- Widget body tap opens the new calendar screen (was Settings).

Known candidate causes to check in order: RemoteViews child limit after the
pill column; an action parameter with a null value; a Glance 1.0.0
`cornerRadius` + `background` combination on Android 13.

## 6. Fasts

Classification (KosherJava indices):
- **Major** — Yom Kippur, Tisha B'Av: begins at **sunset of the previous
  civil day**, ends at **tzeit hakochavim**.
- **Minor** — Tzom Gedalyah, Asara B'Tevet, Ta'anit Esther, Shiva Asar
  B'Tammuz: begins at **alot hashachar**, ends at **tzeit hakochavim**.

`ZmanimRepository.getFastTimes(date, major, lat, lng, tz, format): FastTimes?`
returns formatted begin/end (null when no location is configured). Alot uses
the same `alosHashachar` the zmanim list uses; tzeit uses `tzais`; sunset uses
`sunset`. `HebrewCalendarRepository.getDateInfo` gains an optional
`fastTimes` provider so the holiday name becomes e.g.
`Asara B'Tevet (5:41 AM–5:12 PM)` / `עשרה בטבת (5:41–17:12)`. Without a
location the name is unchanged. The same string flows to the notification,
widget, and calendar day sheet with no further changes. Ta'anit Bechorot is
not shown (it is not in the app's holiday list today).

## 7. Hebrew calendar screen

New `CalendarActivity` (Compose), launcher entry point, translucent theme like
the reader: 50 % dark scrim, no cards, hairline dividers, white text, gold
accents, thin type. A gear icon top-right opens Settings.

**Header:** Hebrew month + year (e.g. `תשרי תשפ״ז`) with the Gregorian span
beneath (`Sep 12 – Oct 11, 2026`), arrows and horizontal swipe to change
month, a small "today" dot button. A tiny **ע/G** toggle switches the grid
between Hebrew-month and Gregorian-month layout; cells always show both
numbers.

**Grid:** 7 columns starting Sunday. Each cell: Hebrew day in gematria (or
number, per language) large; Gregorian day small beneath; Shabbat cells
tinted; a gold dot for holidays/fasts, a blue dot for Rosh Chodesh; today
ringed in gold. Month data is computed on the fly with `JewishCalendar`
(no network).

**Day sheet:** tapping a cell slides up a translucent bottom sheet showing:
- Both dates, weekday, holiday/fast (with fast times), parsha (if the week
  has one), omer, Chanukah/other minor labels — via the existing
  `getDateInfo` with that date.
- Zmanim for that date (all currently selected zmanim, past/next not
  highlighted since the date may not be today), or a one-line hint to set a
  city if none is configured.
- Three link rows: **Daily Tehillim** (day-of-month portion plus the Elul
  supplement when applicable), **Parsha**, **Haftarah**. Each opens the
  reader with the appropriate ref. Parsha/haftarah use
  `SefariaRepository.parshaFor(thatWeeksShabbat)` (cached by the download
  job for upcoming weeks; otherwise a fetch, or the offline message).

**Entry points:** launcher icon → calendar; widget body tap → calendar;
notification content tap → calendar; pills keep opening the reader.

## 8. Release

- `versionCode 21`, `versionName "1.14"`.
- `fastlane/metadata/android/en-US/changelogs/21.txt` (≤500 chars,
  F-Droid style bullet list) covering: calendar screen, reader mode toggle,
  continuous scrolling, haftarah, fast times, notification fit, widget fix,
  and the 1.13 follow-ups (navigation dial polish, offline downloads).
- `full_description.txt` gains the calendar screen and offline downloads;
  README feature list and settings section updated to match.
- Existing `19.txt` and `20.txt` stay as the record of the last month.

## Testing

Unit (JVM, no Android):
- `ParshaCalendarsParser` haftarah extraction (single and multiple).
- `ReaderWindow` pure logic: next/previous ref computation across partial
  edges, book boundaries, Tehillim limits.
- Fast classification and time selection (`FastSchedule`: index → major/
  minor, begin/end selection).
- Calendar month model: cells for a Hebrew month and a Gregorian month,
  Shabbat/holiday flags, today marking.

On device (OnePlus over adb): widget renders; expanded notification shows
every selected zman; reader scroll-extends both ways in He/En/tikkun; toggle
and haftarah button; calendar month navigation and day sheet links open the
reader with the right text; a fast day (e.g. Tzom Gedalyah 2026-09-14 is
past — use the calendar sheet on 10 Tevet 5787) shows the times.
