# Ascend

A personal goal-ladder app for Android. Yearly → monthly → weekly → daily, with a
pixel-art cat named Mochi who reacts to how you're actually doing.

Built as a single-purpose, offline-first app: no account, no cloud, no analytics.
The only network call the app can make is the one you configure yourself.

---

## What it does

**The ladder.** Goals live on one of four horizons. A goal can *serve* a higher
goal, so a daily habit points at the week it belongs to, which points at the
month, which points at the year. Progress rolls up automatically — the number at
the top is earned from check-ins at the bottom, never typed in.

**Check-ins.** Every goal takes a Done / Partial / Skipped mark for its own
period (a day, a week, a month). Tapping the mark you already have clears it, so
mistakes are one tap to undo. You can attach a short note to any check-in.

**Streaks.** Counted per goal and per horizon. Today never breaks a streak while
it is still in progress — the streak only ends when a full period passes without
a mark.

**The cat.** Mochi is drawn in code as a pixel grid (no image assets, ~0 KB).
He idles and blinks, bounces when you complete things, looks sad when you skip,
falls asleep at night or if you've been away for days, and gets visibly excited
past a 7-day streak. His name is configurable.

**Stats.** Level and XP derived from real check-ins, a 10-week heatmap, per-horizon
completion, and your strongest habits ranked by streak.

**Review.** A weekly/monthly look-back: completion rate for the period, a list of
what slipped, a mood picker, a notes field, and a one-tap "carry into next week"
that turns neglected items into fresh daily goals.

**Reminders.** Local notifications, scheduled with inexact alarms (no special
permission needed). Per goal, per weekday. Rescheduled after reboot.

**Home-screen widget.** Today's goals plus Mochi. Tap a row to tick it off without
opening the app.

**AI plan builder.** Describe what you want to learn, how many hours a day you can
commit, and how long you want to take, and it generates a month-by-month,
week-by-week, day-by-day plan with milestones. Works with *any* OpenAI-compatible
endpoint (OpenRouter, Groq, DeepSeek, OpenAI, a local llama.cpp server). If no key
is configured — or the call fails — it falls back to a built-in offline planner
that produces the same structure from templates.

**Backup.** Export/import everything as JSON. Copy to clipboard, paste back later,
move to a new phone.

---

## Tech

| | |
|---|---|
| Language | Kotlin 2.4.21 |
| UI | Jetpack Compose + Material 3 |
| Build | AGP 9.4.1, Gradle 9.8.1 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 |
| Storage | One JSON file in app-private storage |
| Networking | `HttpURLConnection` (no HTTP library) |
| Icons | Drawn with `Canvas` (no icon pack) |
| Sprite | Drawn from a pixel grid (no assets) |

Notable choices:

- **JSON file instead of Room.** A personal goal list is small. One file makes
  export/import free, and keeps the build free of annotation processors.
- **No icon dependency.** `material-icons-core` is deprecated and frozen, so all
  icons are plain geometry in `ui/Glyph.kt`.
- **Bytecode choice over convenience.** `HttpURLConnection` and `RemoteViews` were
  picked over OkHttp and Glance to keep the dependency surface tiny and the build
  reliable.

### Architecture

```
com.mochi.ascend
├── AscendApp.kt              Application: init store, schedule reminders
├── MainActivity.kt           Tab scaffold + navigation state
├── data/
│   ├── Model.kt              Goal, CheckIn, Reflection, Settings, AppData
│   ├── Dates.kt              Period keys ("2026-W42") and date maths
│   ├── Store.kt              JSON persistence, StateFlow, all mutations
│   └── Progress.kt           Completion, roll-up, streaks, XP, mood
├── ai/
│   ├── Plan.kt              Plan / PlanRequest shapes
│   ├── AiClient.kt          OpenAI-compatible chat completions
│   └── Planner.kt           Prompt, offline planner, plan → goals
├── notify/Reminders.kt       AlarmManager scheduling + receivers
├── art/CatArt.kt             The sprite, as a pixel grid (shared)
├── ui/                       Theme, Glyph icons, Cat, screens
└── widget/                   Home-screen widget + bitmap renderer
```

`CatArt` is deliberately free of Android imports so the same artwork drives both
the Compose sprite and the widget bitmap.

---

## Building

Requires a JDK 17+ and the Android SDK with platform 37.0 and build-tools 36.0.0.

```bash
./build.sh            # debug, copies the output to ./Ascend-debug.apk
./build.sh release    # release
```

Or drive Gradle directly:

```bash
export ANDROID_HOME=/path/to/android-sdk
gradle :app:assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` must point at the SDK:

```properties
sdk.dir=/path/to/android-sdk
```

### Installing

Sideload `Ascend-debug.apk` (allow install from unknown sources), or:

```bash
adb install -r Ascend-debug.apk
```

The debug APK is signed with the standard Android debug key, which is fine for
personal sideloading. For a release build you would generate your own keystore.

Supports Android 8.0 (API 26) and up.

### Notes on the build environment

This project was built on a machine with ~1 GB RAM, so `gradle.properties` is
tuned down (`-Xmx560m`, in-process Kotlin compilation, single worker, no daemon).
On a normal workstation those limits can be raised or deleted for much faster
builds.

Two AGP 9 specifics worth remembering:

- AGP 9 has **built-in Kotlin**, so `org.jetbrains.kotlin.android` must *not* be
  applied. A newer KGP is pulled in through the root `buildscript` classpath.
- Minor SDK versions exist (`compileSdkMinor`), because AndroidX 1.19.x requires
  compiling against API 37.

---

## Configuring the AI planner

Settings → AI planner:

- **Base URL** — e.g. `https://openrouter.ai/api/v1`, `https://api.openai.com/v1`,
  `http://192.168.1.10:8080/v1` for a local server
- **Model** — e.g. `openai/gpt-4o-mini`, `deepseek-chat`
- **API key**

Hit **Test** to verify. Leave it blank and the offline planner is used instead.

The key is stored in the app's private JSON file, which is readable only by this
app on a non-rooted device.
