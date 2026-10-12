# Changelog

All notable changes to Hebrew Calendar are listed here, newest first.
Version codes match `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`,
which is what F-Droid shows.

## 1.16 (version code 23)

### Fixed
- **Birkat HaLevana.** Tapping the Birkat HaLevana badge on the day sheet opened an empty reader
  with a "No internet connection" message. It now opens the blessing, shown as plain paragraphs
  in Hebrew or English.

## 1.15 (version code 22)

Everything since 1.12. Versions 1.13 and 1.14 were development builds and were never published.

### Added
- **Calendar screen.** The app now opens into a translucent month grid that can be browsed by
  Hebrew or Gregorian month, with both dates in every cell, dots for holidays, fasts, and Rosh
  Chodesh, and a "Today" button. Tapping a day opens a sheet with its holidays, zmanim, and links
  that open the parsha, haftarah, and daily Tehillim in the reader. Settings moved behind the gear icon.
- **Shabbat and Yom Tov.** Candle-lighting time with a live countdown on Friday and Erev Yom Tov
  (18, 20, 30, or 40 minutes before sunset), Havdalah by a chosen opinion (8.5°, 42, 50, or 72
  minutes), and a Shabbat Shalom banner on the lockscreen. Candle lighting, Havdalah, and
  Havdalah (Rabbeinu Tam) can also be selected in the zmanim list.
- **Sun, moon, and direction.** A sun-arc view of the day with the current sha'ah zmanit, moon
  phase and illumination, the Birkat HaLevana window with its text, and a Jerusalem compass
  showing direction and distance.
- **Reader.** A Tehillim/Torah switch, continuous scrolling that loads the next or previous
  chapter (across Torah books), the haftarah of the week (with a chooser when there are
  several), a banner showing the week's parsha and haftarah references, and a navigation dial
  for jumping to any Tehillim chapter, Torah chapter, or parsha.
- **Offline downloads.** Upcoming Tehillim (1 week by default) and parsha readings (3 weeks by
  default) are fetched in the background, on Wi-Fi only unless mobile data is allowed.
- **Fast times.** Fast days show their start and end: minor fasts from alot hashachar to tzeit
  hakochavim, major fasts from the previous sunset to tzeit hakochavim.
- **Widget transparency.** The widget background opacity is adjustable from 10% to 100%.
- New launcher icon with themed-icon (monochrome) support.

### Changed
- English text now uses Sefaria's contemporary JPS Tanakh (2023) instead of the 1917 translation;
  footnote markers are removed.
- Chapter and verse labels take the colour of their source: amber for Torah and Tehillim, mint
  for haftarah.
- The navigation overlay reads right to left in Hebrew, and the Tehillim dial turns about 2.5
  times faster.
- Widget text is right-aligned.
- Tapping the widget or the notification opens the calendar instead of Settings.

### Fixed
- Widget showed "Can't load widget" on OnePlus/OxygenOS launchers.
- The expanded notification cut off the last zmanim; they now use a two-column grid.
- In aliyot view, verses fetched by scrolling past a mid-chapter boundary were not shown.
- Tapping the widget could stack two calendar overlays.

### Release engineering
- Releases are now built in release mode and signed with the release key, so F-Droid can verify
  them. The 1.12 GitHub asset was a debug build, which is why F-Droid stayed on 1.3.

## 1.12 (version code 19) — 2026-09-01

- Built-in Sefaria text reader: daily Tehillim and weekly parsha buttons on the widget and
  notification, right-to-left flowing text, pinch zoom, nikkud and te'amim toggles, Hebrew numerals.
- Tikkun mode: STaM script in the authentic Torah-scroll line layout (tikkun.io data).
- Parsha by chapters or aliyot; petucha/setuma scroll spacing option.
- Customary Elul Tehillim, three additional daily chapters through Yom Kippur.
- Texts are cached for offline rereading.

## 1.3 (version code 4) — 2026-06-17

- Last version published on F-Droid before 1.15.

## 1.2 (version code 3) — 2026-05-24

## 1.1 (version code 2) — 2026-04-27

## 1.0 (version code 1) — 2026-04-27

- Initial release: Hebrew date on the lockscreen and home-screen widget, Jewish holidays,
  Shabbat, parsha, Omer count, zmanim via KosherJava, Diaspora and Israel modes, Hebrew and
  English display languages.
