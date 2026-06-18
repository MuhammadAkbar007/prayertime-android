# 🕌 Namoz Vaqti — Android

A minimal, dark, glassy prayer-times app for **Namangan, Uzbekistan**, built for an
old phone (**Samsung A3 2016, Android 7.0 / API 24**). It is the Android sibling of
the [`namozvaqti`](../namozvaqti) Linux service and reuses the **exact same** Aladhan
tuning and offline/stale logic.

* 📅 Monthly fetch from the Aladhan API (one call covers the whole month)
* 🕑 Today view — live countdown card on top + the day's prayers below (current prayer highlighted)
* 🗓️ Full-month view, auto-centered on today
* 🧩 Home-screen widget: today's times + a circular countdown ring for the next prayer
* 🔔 Exact, battery-friendly notifications at each prayer (no polling, no foreground service)
* 💾 Offline-first: one successful fetch runs the rest of the month
* 🌙 Faux-glass dark UI with English labels (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha) and a golden accent

---

## 🧭 Data source & tuning (identical to the Linux version)

| Parameter        | Value               |
| ---------------- | ------------------- |
| `latitude`       | `41.0058`           |
| `longitude`      | `71.6436`           |
| `method`         | `99`                |
| `methodSettings` | `15.5,null,15.5`    |
| `school`         | `1` (Hanafi Asr)    |
| `timezonestring` | `Asia/Tashkent`     |
| `tune`           | `0,0,0,0,0,4,0,0,0` |

Prayers shown: **Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha**. Sunrise is kept as an
informational row. **Tahajjud / Ishraq** omitted (no reliable Aladhan equivalent).

### Stale / offline behavior — ported 1:1 from `service.py`

* Today cached → works fully offline.
* New month + offline → falls back to the **most recent cached day**, re-stamped onto
  today's clock (`getNextPrayerResilient` ↔ Python `get_next_prayer_resilient`,
  `rebuildForDate` ↔ `rebuild_for_date`, `loadLatestBefore` ↔ `load_latest_before`).
  The "Offline" chip appears while running on stale data.
* Reconnected → next launch re-fetches the month and the chip clears.

---

## 🏗️ How it works

```
Fetch (1 month) → Parse → Transform (timestamps) → Cache (per-day JSON)
                                                       │
                       ┌───────────────────────────────┤
                       ▼                                ▼
                 UI (today + month)            PrayerScheduler
                                                       │  setExactAndAllowWhileIdle
                                                       ▼
                                          PrayerAlarmReceiver → notify → arm next
```

Instead of a polling service, the app arms **one exact alarm** for the next prayer
(`AlarmManager.setExactAndAllowWhileIdle`, survives Doze). When it fires, it shows the
notification and arms the following one — the direct Android analogue of the Python
scheduler that "sleeps until the next prayer." `BootReceiver` re-arms after a reboot.

---

## 🧩 Home-screen widget

A resizable, semi-transparent glass widget (`PrayerWidget`, an `AppWidgetProvider`):

* **Left** — today's six prayer times with icons; the **current** prayer (the most
  recent one that has begun) is highlighted in gold and stays lit until the next one.
* **Right** — a **circular countdown ring** for the next prayer: its name, time, and a
  live countdown. The ring arc fills to show how far you are between the previous and
  next prayer.

It reads from the same per-day cache as the app (fetching off the main thread if a day
is missing). The countdown is a native `Chronometer` in count-down mode
(`setChronometerCountDown`, API 24+), so it **ticks live without waking the widget every
second**; it re-bases on each refresh — app open, the ~30-min update period, and at every
prayer alarm (`PrayerAlarmReceiver` calls `PrayerWidget.updateAll`). The progress ring is
drawn to a `Bitmap` and pushed via `setImageViewBitmap`, since `RemoteViews` can't host a
custom canvas view.

> After installing, add it from your launcher's widget picker. If you rebuild with a
> changed `applicationId`, re-add the widget once (the old instance is bound to the old
> package).

---

## ⚙️ Build requirements

You need a JDK 17–21 and the Android SDK (API 24 + build tools). Two ways:

* **Android Studio** — `File → Open` this `namozvaqti-android/` folder; first sync
  downloads the SDK and writes `local.properties`. Set the Gradle JDK to Studio's
  bundled **JBR** (Settings → Build → Gradle) — AGP 8.5 expects JDK 17–21, not a newer one.
* **CLI** — point `JAVA_HOME` at a JDK 17–21 (e.g. Android Studio's bundled JBR) and run
  the Gradle wrapper directly; the SDK path comes from `local.properties` (`~/Android/Sdk`).
  The wrapper jar is committed, so no Studio is required to build.

> Versions pinned: AGP 8.5.2, Gradle 8.7, Kotlin 1.9.24, `minSdk 24`, `targetSdk 34`.
> Time logic uses `java.util.Calendar` anchored to `Asia/Tashkent` (no `java.time`,
> so no core-library desugaring is needed on Android 7).

---

## 📲 Build & install on the phone (no emulator)

On the **A3**: Settings → About phone → tap *Build number* 7× to enable Developer
options, then Developer options → enable **USB debugging**. Plug into the laptop.

```bash
# from namozvaqti-android/
./gradlew assembleDebug                 # outputs app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or just press **Run ▶** in Android Studio with the phone selected.
(If `adb` isn't on PATH, it's in `~/Android/Sdk/platform-tools/`.)

---

## 🔋 Make notifications reliable on Samsung (important!)

Samsung aggressively kills background apps, which silently breaks alarms. On first
launch the app asks to be excluded from battery optimization — **accept it**. Then also:

* Settings → **Battery / Device care** → remove *Namoz Vaqti* from **"Sleeping apps"**
  / "Deep sleeping apps".
* Allow background activity for the app.

Without this, the scheduled notifications may not fire on time.

---

## 📁 Structure

```
app/src/main/java/uz/akbar/namozvaqti/   (applicationId: uz.akbar.namozvaqti)
├── data/        Fetch · Parse · Transform · Cache · TimeUtils · PrayerService (stale logic) · Repo · Labels · DateFmt
├── alarm/       PrayerScheduler · PrayerAlarmReceiver · BootReceiver
├── notify/      Notifier (channel + bundled sound)
├── widget/      PrayerWidget (AppWidgetProvider + countdown-ring rendering)
├── PrayerIcons  per-prayer vector icon mapping (shared by app + widget)
└── ui/          MainActivity · TodayAdapter · MonthAdapter
app/src/main/res/
├── layout/      activity_main · item_prayer · item_month_day · item_month_cell · widget_prayer
├── drawable/    bg_gradient · glass_card · glass_row · glass_hero · tabs · ic_stat_prayer · ic_prayer_* · widget_bg · widget_row_next
├── xml/         prayer_widget_info (widget metadata)
└── raw/         prayer_notification.wav
```

---

## ✍️ Author

Created by [Akbar](https://github.com/MuhammadAkbar007).
