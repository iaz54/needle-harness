# Latch — Needle 3 action harness (Android)

On-device harness for [Cactus Needle 3](https://github.com/cactus-compute/needle) and the Jev-class “models that act, not chat” loop.

Latch is the code *around* the model:

1. **Instructions** — tools only, never prose
2. **Tools** — lights, fan, thermostat, locks, volume, DND, alarm, timer, notes, weather, calculate
3. **Memory** — house + phone state, local notes
4. **Loop** — `complete()` → gate → mutate → verify
5. **Gates** — confidence ≥ 0.72 auto-executes, middling asks, empty list refuses (never guesses a tool)

v1.1.1 keeps every stop on the route. “Drive to the gas station and then the pharmacy and then JFK” stays one trip. Google Maps gets each place as its own stop (`daddr=a+to:b+to:c`), instead of turn-by-turn to a single destination.

Sibling project: private [`iaz54/jevharness`](https://github.com/iaz54/jevharness) (AccessibilityService + TypeSafe Jev). Latch is the tool-calling rung. JevHarness is the UI-control rung.

## Install the APK

**[Download Latch-1.1.1.apk](https://github.com/iaz54/needle-harness/raw/main/dist/Latch-1.1.1.apk)** — debug-signed, Android 8+. Reinstall over 1.1.0. Also on [Releases](https://github.com/iaz54/needle-harness/releases/tag/v1.0.0).

On the phone: allow unknown sources. If Chrome says the file is uncommon, tap Keep, then Install.

GitHub Actions builds a fresh debug APK on every push to `main` (artifact **`latch-debug`**).

## Try

```
drive from home to JFK via a gas station and the pharmacy, avoid tolls
walk from Washington Square to the Brooklyn Bridge via the High Line
transit from Penn Station to the Met then Central Park
take me home
open wifi settings and turn on do not disturb
call 311
text 9175550100 saying I'm on the way
find late night pizza near me
turn on the fan, set temperature to 10°, turn on bedroom light
```

Navigation fills every stop in Google Maps: origin, up to nine stops, then the destination. A single destination still starts turn-by-turn. Multi-stop routes use Maps’ `+to:` chain so the app enters each place, not just the last one.

Phone actions that Android will not toggle silently hand off to the real system surface: Wi-Fi, Bluetooth, airplane mode, Do Not Disturb, display, sound (volume is applied on the music stream), alarms, timers, dialer, SMS, email, calendar, and launching installed apps.

House tiles still update for lights / fan / locks.


## Web console

`web/` is the same deck in a browser: full routes, saved places, and Android intent handoffs.

```
cd web
npm install
npm test
npm run dev
```

## Build locally

Android Studio Ladybug / AGP 8.7, JDK 17:

```
./gradlew :app:assembleDebug
```

If you cloned without a wrapper, GitHub Actions uses Gradle 8.9 via `gradle/actions/setup-gradle`.

Package `com.iaz54.needleharness` · minSdk 26 · targetSdk 35

Needle is Apache-2.0. This harness is MIT.
