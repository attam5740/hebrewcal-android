# Hebrew Calendar
### Android App — Kotlin + Jetpack Compose

A Hebrew (Jewish) calendar for Android 8+: the date, holidays, parsha, and zmanim (halachic
times) on the lockscreen and home screen, a month calendar, Shabbat times, and a built-in
Torah and Tehillim reader.

[<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="72">](https://f-droid.org/packages/com.hebrewcal/)

Also available from [GitHub Releases](https://github.com/attam5740/hebrewcal-android/releases).
See the [changelog](CHANGELOG.md) for what is new.

---

## Features

- **Hebrew date** in Hebrew (`כ״ב ניסן תשפ״ו`) or English (`22 Nisan 5786`)
- **Jewish holidays** with Israel / Diaspora rules (affects Yom Tov length, Chol HaMoed, etc.)
- **Fast days** show start and end times
- **Weekly Parsha** displayed on Shabbat
- **Zmanim (prayer times)** — user selects exactly which times to show
- **Lockscreen notification** — persistent, always visible on lockscreen (Android 8+)
- **Home screen Glance widget** — resizable, with adjustable background transparency
- **Shabbat and Yom Tov** — candle-lighting time with a live countdown on Friday and Erev Yom
  Tov, Havdalah by your chosen opinion, and a Shabbat Shalom banner on the lockscreen
- **Sun, moon, and direction** — a sun arc for today that shows where the sun is heading, with
  sunrise, sunset, the time left until sunset and the current sha'ah zmanit; the moon drawn in
  its current phase; the Birkat HaLevana window; and a compass pointing to Jerusalem from true north
- **Siddur at prayer times** — while a prayer's time is open, the notification offers a button
  that opens it from your custom's siddur (Ashkenaz, Sefard, or Edot HaMizrach) on Sefaria:
  Birchot HaShachar, Shacharit, Mincha, candle lighting, Havdalah, Arvit, and Kriat Shema al
  HaMita, each chosen in Settings
- **Date navigator** — tap the month heading to jump to any month and year, Hebrew or Gregorian
- **Settings backup** — save every setting to a file and restore it later
- **First-run setup** — a new install asks for notifications and location in one short
  checklist, and saves the nearest city as soon as location is allowed
- **Calendar screen** — the app launches into a full month grid (Hebrew or Gregorian),
  with both the Hebrew and Gregorian date shown in every cell; tap a day to see its holidays,
  fast start/end times, and zmanim, with links straight into the reader for that day's parsha,
  haftarah, and daily Tehillim; Settings is reached from its gear (⚙) icon
- **Auto-updating** — refreshes at midnight, on each zman transition, and on date/timezone change
- **Torah & Tehillim reader** — daily Tehillim (day-of-month cycle) and weekly parsha buttons on
  the widget, notification, and calendar screen open a translucent Sefaria-backed reader: RTL
  flowing text, pinch-to-zoom, nikkud/te'amim toggles, Hebrew/English, aliyah partitioning, a
  navigation dial to jump to any chapter or parsha, original petucha/setuma spacing, and the
  customary Elul Tehillim supplement. A Tehillim/Torah switch flips between them without leaving
  the reader, continuous scrolling loads the next (or previous) chapter automatically, and the
  haftarah of the week is available alongside the parsha
- **Tikkun mode** — unvocalized STaM sofer script rendered in the authentic 42-line
  Torah-scroll column layout (line-break data from the tikkun.io project)
- **Offline downloads** — upcoming Tehillim and parsha readings are fetched ahead of time on
  Wi-Fi (mobile data optional) so the reader works without a connection

---

## Architecture

```
app/
├── data/
│   ├── HebrewCalendarRepository.kt   # KosherJava wrapper — date, holiday, parsha
│   ├── ZmanimRepository.kt           # KosherJava zmanim calculation
│   ├── UserPreferences.kt            # DataStore persistence + all models/enums
│   ├── LocationHelper.kt             # One-shot coarse location via the platform LocationManager
│   ├── CalendarMonth.kt              # Month grid model (Hebrew or Gregorian)
│   ├── ShabbatTimes.kt               # Candle lighting and Havdalah
│   ├── FastSchedule.kt               # Major/minor fast start and end
│   ├── CelestialCalculator.kt        # Sun arc and sha'ot zmaniyot
│   ├── LunarCalculator.kt            # Moon phase and Birkat HaLevana window
│   ├── MizrachCalculator.kt          # Bearing and distance to Jerusalem
│   ├── SefariaRepository.kt          # Sefaria text + calendars API with disk cache
│   ├── ReaderWindow.kt               # Chapter-by-chapter reader extension logic
│   └── PrefetchPlanner.kt            # Which texts to download ahead
├── service/
│   ├── CalendarNotificationService.kt # Foreground service — notification + alarm scheduling
│   ├── MidnightUpdateWorker.kt        # WorkManager daily backup refresh
│   └── TextPrefetchJob.kt             # JobScheduler job for offline text downloads
├── receiver/
│   └── Receivers.kt                   # Boot, DateChange, ZmanAlarm broadcast receivers
├── ui/
│   ├── notification/
│   │   └── LockscreenNotificationBuilder.kt  # RemoteViews notification layout builder
│   ├── widget/
│   │   └── HebrewDateWidget.kt               # Glance AppWidget (home screen)
│   ├── calendar/
│   │   ├── CalendarActivity.kt               # Launcher: translucent month grid
│   │   ├── DaySheet.kt                       # Day details, zmanim, reader links
│   │   ├── CelestialArcView.kt               # Sun arc, sha'ah zmanit, moon phase
│   │   └── MizrachCompassDialog.kt           # Jerusalem compass
│   ├── reader/
│   │   ├── TextReaderActivity.kt             # Translucent Torah/Tehillim reader
│   │   └── ReaderViewModel.kt                # Loading, chapter window, mode memory
│   ├── settings/
│   │   ├── SettingsActivity.kt               # Settings, opened from the calendar's gear icon
│   │   ├── SettingsViewModel.kt              # State + business logic for settings
│   │   ├── MainSettingsScreen.kt             # Main settings Composable
│   │   └── ZmanimSelectionScreen.kt          # Per-zman checklist Composable
│   └── theme/
│       └── Theme.kt                          # Material3 dark/light theme (gold + parchment)
└── HebrewCalendarApp.kt                      # Application — starts service + WorkManager
```

---

## Hebrew Calendar Library

Uses **KosherJava** (`com.kosherjava:zmanim:2.4.0`) — the most comprehensive open-source
Jewish calendar library for the JVM.

**Key classes used:**
| Class | Purpose |
|---|---|
| `JewishCalendar` | Hebrew date, holidays, Israel/Diaspora flag |
| `HebrewDateFormatter` | Format dates in Hebrew or English |
| `ComplexZmanimCalendar` | All 14+ zmanim calculations |
| `GeoLocation` | Lat/lng/timezone for zmanim |

---

## User Settings

### Calendar
| Setting | Options | Default |
|---|---|---|
| Language | English / Hebrew | English |
| Holiday Location | Diaspora / Israel | Diaspora |
| Show Parsha | On / Off | On |
| Show Gregorian Date | On / Off | On |

### Zmanim
| Setting | Options | Default |
|---|---|---|
| Show Zmanim | On / Off | Off |
| Time Format | 12-hour / 24-hour | 12-hour |
| Location Source | GPS / Manual City | GPS |
| Selected Zmanim | Per-zman checklist | 8 defaults |

### Shabbat & Celestial
| Setting | Options | Default |
|---|---|---|
| Shabbat & Chag dynamic lockscreen | On / Off | On |
| Candle lighting offset | 18 / 20 / 30 / 40 minutes before sunset | 18 |
| Havdalah opinion | 8.5° / 42 min / 50 min / 72 min (Rabbeinu Tam) | 8.5° |
| Show celestial arc | On / Off | On |

### Reader & Downloads
| Setting | Options | Default |
|---|---|---|
| Download texts ahead | Off / Wi-Fi only / Any network | Wi-Fi only |
| Tehillim ahead | 3 days / 1 week / 2 weeks | 1 week |
| Parsha ahead | 1 week / 3 weeks / 6 weeks | 3 weeks |
| Widget background opacity | 10% to 100% | 60% |

### Default selected zmanim
- Alot HaShachar, HaNetz, Sof Zman Shema (GRA), Sof Zman Tefilla (GRA),
  Chatzot, Plag HaMincha, Shkiah, Tzet HaKochavim

### All available zmanim
- Alot HaShachar (Dawn)
- Misheyakir (Earliest Tallit/Tefillin)
- HaNetz (Sunrise)
- Sof Zman Shema — GRA
- Sof Zman Shema — MGA
- Sof Zman Tefilla — GRA
- Sof Zman Tefilla — MGA
- Chatzot (Halachic Midday)
- Mincha Gedola
- Mincha Ketana
- Plag HaMincha
- Shkiah (Sunset)
- Tzet HaKochavim (Nightfall)
- Tzet HaKochavim (Rabbeinu Tam)
- Candle Lighting, Havdalah, Havdalah (Rabbeinu Tam) — shown on the relevant days

---

## Lockscreen Delivery

Android 5+ removed native lockscreen widget APIs. This app uses a **persistent foreground
notification** (`ongoing = true`, `VISIBILITY_PUBLIC`) which Android always shows on the
lockscreen, even when the device is locked.

**Notification layout:**
- **Collapsed**: Hebrew date, Gregorian date, holiday label, next upcoming zman
- **Expanded**: All of the above + full Parsha name + selected zmanim list with past/upcoming highlighting

---

## Update Strategy

| Trigger | Mechanism |
|---|---|
| Midnight date rollover | `AlarmManager` exact alarm + `WorkManager` daily backup |
| Each zman transition | `AlarmManager` exact alarm per selected zman |
| Date/timezone change | `BroadcastReceiver` (`ACTION_DATE_CHANGED`, `TIMEZONE_CHANGED`) |
| Device reboot | `BroadcastReceiver` (`BOOT_COMPLETED`) |
| Settings change | Direct `startForegroundService()` from ViewModel |

---

## Permissions

| Permission | Required for |
|---|---|
| `POST_NOTIFICATIONS` | Showing the notification (Android 13+); asked on first launch |
| `FOREGROUND_SERVICE` | Persistent notification service |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ foreground service |
| `RECEIVE_BOOT_COMPLETED` | Restart after device reboot |
| `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` | Precise zman transition timing |
| `ACCESS_COARSE_LOCATION` | One-time match to the nearest city for zmanim (optional; asked on first launch) |
| `ACCESS_NOTIFICATION_POLICY` | Keeping the notification visible in Do Not Disturb (optional) |
| `INTERNET` | Fetching Torah and Tehillim texts from Sefaria |
| `WAKE_LOCK` | Ensures alarm delivery |

The app requests no precise location, no network-state access, and no vibration permission.
The Jerusalem compass uses the rotation sensor, which needs no permission.

---

## Getting Started

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- Kotlin 1.9+
- JDK 17 or newer
- Min SDK 26 (Android 8.0)
- Target SDK 34

### Build
```bash
git clone https://github.com/attam5740/hebrewcal-android.git
cd hebrewcal-android
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

### Install
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Releasing

F-Droid builds every tag from source and compares its build with the APK attached to the
GitHub release ([reproducible builds](https://f-droid.org/docs/Reproducible_Builds/)). The
recipe is mirrored in [`metadata/com.hebrewcal.yml`](metadata/com.hebrewcal.yml). A release
only reaches F-Droid if all of these hold:

1. `versionCode` and `versionName` are bumped in `app/build.gradle`, and
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` exists (500 characters at most).
2. The tag is `v<versionName>` and points at the commit the APK was built from.
3. The attached asset is named `Hebrew-Calendar-v<versionName>.apk`.
4. It is a **release** build (`./gradlew assembleRelease`, not debug) of a clean checkout of
   that tag, signed with the release key.

```bash
git worktree add /tmp/hebrewcal-release v1.20 && cd /tmp/hebrewcal-release
./gradlew assembleRelease
scripts/release.sh app/build/outputs/apk/release/app-release-unsigned.apk 1.20
gh release create v1.20 dist/Hebrew-Calendar-v1.20.apk --title v1.20
```

`scripts/release.sh` refuses debug builds and checks the signing certificate against the one
F-Droid allows. The keystore and its password never belong in the repository.

---

## Holiday Priority Logic

When multiple events overlap, the notification shows the highest-priority label:

1. Major Yom Tov (Rosh Hashana, Yom Kippur, Pesach, Shavuot, Sukkot, Shmini Atzeret/Simchat Torah)
2. Chol HaMoed + Hoshana Raba
3. Rosh Chodesh
4. Fast days (Tisha B'Av, Tzom Gedalyah, Asara B'Tevet, Ta'anit Esther, Shiva Asar B'Tammuz)
5. Minor holidays (Chanukah, Purim, Lag B'Omer, Tu B'Shvat, Yom Ha'Atzmaut, etc.)
6. Shabbat
7. Regular weekday (no label shown)

---

## Extending

**Add a new zman:** Add a new constant to `ZmanimKeys`, add its `displayName()` mapping,
add the `ComplexZmanimCalendar` call in `ZmanimRepository`, and add it to the appropriate
section in `ZmanimSelectionScreen`.

**Add a new holiday:** Add a new `when` branch in `HebrewCalendarRepository.getHolidayInfo()`
and its Hebrew translation in `translateHolidayToHebrew()`.

---

## License

This project is released under the MIT License.
KosherJava is licensed under the LGPL v2.1 — see [KosherJava GitHub](https://github.com/KosherJava/zmanim).

---

## Data sources & credits

- **Text**: fetched from the [Sefaria API](https://developers.sefaria.org/) —
  Hebrew from *Miqra according to the Masorah* (CC-BY-SA), English from *THE JPS TANAKH:
  Gender-Sensitive Edition* (2023, CC-BY-NC). Texts are cached on-device for offline rereading.
- **Scroll line layout**: [tikkun.io](https://github.com/akivajgordon/tikkun.io) (MIT License).
- **STaM font**: *Stam Ashkenaz CLM* from the [Culmus project](https://culmus.sourceforge.io/)
  (GPL with font-embedding exception).
- **Calendar & zmanim**: [KosherJava zmanim](https://github.com/KosherJava/zmanim) (LGPL 2.1).

The `INTERNET` permission is used solely for fetching these texts; the calendar,
zmanim, notification, and widget work fully offline.
