# 🕌 Namoz Vaqti — Android

A minimal, dark, glassy prayer-times app for **Namangan, Uzbekistan**, built for an
old phone (**Samsung A3 2016, Android 7.0 / API 24**). It is the Android sibling of
the [`namozvaqti`](../namozvaqti) Linux service and reuses the **exact same** Aladhan
tuning and offline/stale logic.

* 📅 Monthly fetch from the Aladhan API (one call covers the whole month)
* 🕑 Today view (live countdown) + full-month view
* 🔔 Exact, battery-friendly notifications at each prayer (no polling, no foreground service)
* 💾 Offline-first: one successful fetch runs the rest of the month
* 🌙 Faux-glass dark UI (Uzbek labels: Bomdod, Quyosh, Ishroq, Peshin, Asr, Shom, Xufton)

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

**Ishroq** = Sunrise + 20 min. **Tahajjud** omitted (no reliable Aladhan equivalent).

### Stale / offline behavior — ported 1:1 from `service.py`

* Today cached → works fully offline.
* New month + offline → falls back to the **most recent cached day**, re-stamped onto
  today's clock (`getNextPrayerResilient` ↔ Python `get_next_prayer_resilient`,
  `rebuildForDate` ↔ `rebuild_for_date`, `loadLatestBefore` ↔ `load_latest_before`).
  The "Oflayn" chip appears while running on stale data.
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

## ⚙️ Build requirements

This machine has a JDK but **no Android SDK / Gradle / Android Studio** yet. Easiest path:

1. Install **Android Studio** (it bundles a compatible JDK — JBR 17/21).
2. `File → Open` this `namozvaqti-android/` folder. On first sync Android Studio will:
   * download the Android SDK (API 24 + build tools),
   * generate `gradle/wrapper/gradle-wrapper.jar` (not committed here),
   * create `local.properties` with your `sdk.dir`.
3. **Set the Gradle JDK to Studio's bundled JBR** (Settings → Build → Gradle), *not*
   your system JDK 25 — AGP 8.5 expects JDK 17–21.

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
app/src/main/java/ai/wakil/namozvaqti/
├── data/        Fetch · Parse · Transform · Cache · TimeUtils · PrayerService (stale logic) · Repo · Labels · DateFmt
├── alarm/       PrayerScheduler · PrayerAlarmReceiver · BootReceiver
├── notify/      Notifier (channel + bundled sound)
└── ui/          MainActivity · TodayAdapter · MonthAdapter
app/src/main/res/
├── layout/      activity_main · item_prayer · item_month_day · item_month_cell
├── drawable/    bg_gradient · glass_card · glass_row · glass_hero · tabs · ic_stat_prayer
└── raw/         prayer_notification.wav
```

---

## ✍️ Author

Created by [Akbar](https://github.com/MuhammadAkbar007).
