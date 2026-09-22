# Latch — Needle 3 action harness (Android)

On-device harness for [Cactus Needle 3](https://github.com/cactus-compute/needle) and the Jev-class “models that act, not chat” loop.

Latch is the code *around* the model:

1. **Instructions** — tools only, never prose
2. **Tools** — lights, fan, thermostat, locks, volume, DND, alarm, timer, notes, weather, calculate
3. **Memory** — house + phone state, local notes
4. **Loop** — `complete()` → gate → mutate → verify
5. **Gates** — confidence ≥ 0.72 auto-executes, middling asks, empty list refuses (never guesses a tool)

The APK ships a Needle-protocol engine so it runs **offline with no download**. Swap in official `libneedle` + `needle3.cact` later via JNI; the Kotlin `NeedleEngine.complete()` shape already matches Needle’s `function_calls` / `confidence` / `suppressed_calls` JSON.

Sibling project: private [`iaz54/jevharness`](https://github.com/iaz54/jevharness) (AccessibilityService + TypeSafe Jev). Latch is the tool-calling rung. JevHarness is the UI-control rung.

## Install the APK

**[Download Latch-1.0.0.apk](https://github.com/iaz54/needle-harness/raw/main/dist/Latch-1.0.0.apk)** — debug-signed, Android 8+, 15.6 MB. Also in [`dist/Latch-1.0.0.apk`](https://github.com/iaz54/needle-harness/blob/main/dist/Latch-1.0.0.apk) and [Releases](https://github.com/iaz54/needle-harness/releases/tag/v1.0.0).

On the phone: allow unknown sources. If Chrome says the file is uncommon, tap Keep, then Install.

GitHub Actions still builds a fresh debug APK on every push to `main` (artifact **`latch-debug`**).

## Try

```
turn on the fan, set temperature to 10°, turn on bedroom light
Dim the bedroom and lock up
set an alarm for 7am
what's the weather in Lagos
```

Watch the house tiles update. Drag the intelligence ladder: 2L keeps one call, 20L keeps the full chain.

## Build locally

Android Studio Ladybug / AGP 8.7, JDK 17:

```
./gradlew :app:assembleDebug
```

If you cloned without a wrapper, GitHub Actions uses Gradle 8.9 via `gradle/actions/setup-gradle`.

Package `com.iaz54.needleharness` · minSdk 26 · targetSdk 35

Needle is Apache-2.0. This harness is MIT.
