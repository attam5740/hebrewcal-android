# Hebrew Calendar Lockscreen Widget
### Android App — Kotlin + Jetpack Compose

Displays the Hebrew (Jewish) calendar date, holidays, Parsha, and Zmanim (prayer times) as a
persistent lockscreen notification on Android 8+.

---

## Features

- **Hebrew date** in Hebrew (`כ״ב ניסן תשפ״ו`) or English (`22 Nisan 5786`)
- **Jewish holidays** with Israel / Diaspora rules (affects Yom Tov length, Chol HaMoed, etc.)
- **Weekly Parsha** displayed on Shabbat
- **Zmanim (prayer times)** — user selects exactly which times to show
- **Lockscreen notification** — persistent, always visible on lockscreen (Android 8+)
- **Home screen Glance widget** — also available as a resizable home screen widget
- **Auto-updating** — refreshes at midnight, on each zman transition, and on date/timezone change
- **Torah & Tehillim reader** — daily Tehillim (day-of-month cycle) and weekly parsha buttons on
  the widget and notification open a translucent Sefaria-backed reader: RTL flowing text,
  pinch-to-zoom, nikkud/te'amim toggles, Hebrew/English, aliyah partitioning, original
  petucha/setuma spacing, and the customary Elul Tehillim supplement
- **Tikkun mode** — unvocalized STaM sofer script rendered in the authentic 42-line
  Torah-scroll column layout (line-break data from the tikkun.io project)

---

## Architecture

```
app/
├── data/
│   ├── HebrewCalendarRepository.kt   # KosherJava wrapper — date, holiday, parsha
│   ├── ZmanimRepository.kt           # KosherJava zmanim calculation
│   ├── UserPreferences.kt            # DataStore persistence + all models/enums
│   └── LocationHelper.kt             # GPS + geocoding via FusedLocationProvider
├── service/
│   ├── CalendarNotificationService.kt # Foreground service — notification + alarm scheduling
│   └── MidnightUpdateWorker.kt        # WorkManager daily backup refresh
├── receiver/
│   └── Receivers.kt                   # Boot, DateChange, ZmanAlarm broadcast receivers
├── ui/
│   ├── notification/
│   │   └── LockscreenNotificationBuilder.kt  # RemoteViews notification layout builder
│   ├── widget/
│   │   └── HebrewDateWidget.kt               # Glance AppWidget (home screen)
│   ├── settings/
│   │   ├── SettingsActivity.kt               # Entry point, Navigation host
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
| `POST_NOTIFICATIONS` | Showing the notification (Android 13+) |
| `FOREGROUND_SERVICE` | Persistent notification service |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ foreground service |
| `RECEIVE_BOOT_COMPLETED` | Restart after device reboot |
| `SCHEDULE_EXACT_ALARM` | Precise zman transition timing |
| `ACCESS_FINE_LOCATION` | GPS-based zmanim (optional) |
| `WAKE_LOCK` | Ensures alarm delivery |

---

## Getting Started

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- Kotlin 1.9+
- Min SDK 26 (Android 8.0)
- Target SDK 35

### Build
```bash
git clone <repo>
cd hebrewcal
./gradlew assembleDebug
```

### Install
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Holiday Priority Logic

When multiple events overlap, the notification shows the highest-priority label:

1. Major Yom Tov (Rosh Hashana, Yom Kippur, Pesach, Shavuot, Sukkot, Shmini Atzeret/Simchat Torah)
2. Chol HaMoed + Hoshana Raba
3. Rosh Chodesh
4. Fast days (Tisha B'Av, Yom Kippur Katan, etc.)
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
  Hebrew from *Miqra according to the Masorah* (CC-BY-SA), English from the JPS 1917
  translation (public domain). Texts are cached on-device for offline rereading.
- **Scroll line layout**: [tikkun.io](https://github.com/akivajgordon/tikkun.io) (MIT License).
- **STaM font**: *Stam Ashkenaz CLM* from the [Culmus project](https://culmus.sourceforge.io/)
  (GPL with font-embedding exception).
- **Calendar & zmanim**: [KosherJava zmanim](https://github.com/KosherJava/zmanim) (LGPL 2.1).

The `INTERNET` permission is used solely for fetching these texts; the calendar,
zmanim, notification, and widget work fully offline.
