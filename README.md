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

**Focus timer.** Pick a goal and a length, start it, and get on with your life —
it runs as a foreground service, so it keeps counting while you read, browse or
code. Logged minutes feed XP, the heatmap and a "focused today" figure.

**Capacity check.** Tell the app how many minutes a day you can honestly commit
to. If the ladder schedules more than that, Today points out which days are
overbooked.

**Weekly rollover.** Opt-in. When Monday arrives, each weekly goal grows a fresh
set of daily goals for that week, derived from its unchecked steps.

**The cat.** Mochi is drawn in code as a pixel grid (no image assets, ~0 KB).
He idles and blinks, bounces when you complete things, looks sad when you skip,
falls asleep at night or if you've been away for days, and gets visibly excited
past a 7-day streak. His name is configurable, and he takes the accent colour of
whichever theme is active.

**The companion.** Optionally put Mochi on top of your other apps. He walks
along the bottom of the screen; only his own pixels are touchable, so he never
gets in the way. Tap him to pet him. Hold him to talk — type or use the mic —
and he answers in a speech bubble and *out loud*, via Android's text-to-speech.
That means real meow audio with **zero sound files shipped**. With an AI key
configured he replies in character (occasional jokes included); without one he
falls back to canned lines.

How much he can "watch" is entirely your choice, and each option states plainly
what it can and cannot see:

| Tier | Sees | Cost |
|---|---|---|
| Off | nothing | none |
| Idle | when you last checked in | none |
| + foreground app | *which* app is open, not what's in it | Usage-access toggle |
| + on-screen content | window titles and text | Accessibility service, broad access, off by default |

No Android app can watch the screen silently — that is the sandbox, not an
oversight. The deepest tier exists because it was asked for, and it stays off
unless you deliberately enable it.

**Stats.** Level and XP derived from real check-ins, a consistency heatmap that
adapts to your screen width, per-horizon completion, and your strongest habits
ranked by streak.

**Review.** A weekly/monthly look-back: completion rate for the period, a list of
what slipped, a mood picker, a notes field, and a one-tap "carry into next week"
that turns neglected items into fresh daily goals.

**Reminders.** Local notifications, scheduled with inexact alarms (no special
permission needed). Per goal, per weekday. Rescheduled after reboot.

**Home-screen widget.** Today's goals plus Mochi. Tap a row to tick it off without
opening the app.

**Themes.** Six — Ember, Crimson, Ocean, Forest, Violet, Slate — each with its own
dark and light scheme and its own set of ladder colours.

**AI plan builder.** Describe what you want to learn, how many hours a day you can
commit, and how long you want to take, and it generates a month-by-month,
week-by-week, day-by-day plan with milestones. Works with *any* OpenAI-compatible
endpoint (OpenRouter, Groq, DeepSeek, OpenAI, a local llama.cpp server). If no key
is configured — or the call fails — it falls back to a built-in offline planner
that produces the same structure from templates.

**Backup.** Export/import everything as JSON. Copy to clipboard, paste back later,
move to a new phone. An optional weekly snapshot writes a dated copy into app
storage; it is **off by default**.

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

### Performance

Progress used to be recomputed by scanning every check-in on every
recomposition, which made the app crawl once a plan had been materialised. All
of it now lives in `data/Snapshot.kt`: an indexed, memoised view built once per
data change via `remember(data) { Snapshot(data) }`. `Progress` is reduced to pure
statics so the slow path cannot creep back in. Reminder and widget calls are
binder work, so they are kept off the main thread and the widget redraw is
coalesced.

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
├── focus/FocusService.kt     Foreground service for the focus timer
├── companion/
│   ├── CompanionService.kt   Walkable overlay window + talk bubble
│   ├── CatView.kt            The cat, drawn per frame (plain View)
│   ├── Talker.kt             TTS voice — no audio files shipped
│   ├── Meow.kt               Canned lines and AI cat-speak
│   └── Watchers.kt           Usage-stats and accessibility tiers
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
