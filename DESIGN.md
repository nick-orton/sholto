# Sholto — Design Document

> Named after John Sholto Douglas, 9th Marquess of Queensberry, whose name is
> attached to the Queensberry Rules that gave modern boxing its three-minute
> rounds.

Sholto is an Android app for solo boxing training: heavy bag, shadowboxing and
pad work without a coach. It has two tools:

1. **Round Timer**: a full-screen, color-coded round and rest timer.
2. **Punch Caller**: calls out random punch numbers at random intervals so you
   practice reacting instead of running memorized combos.

The two can run on their own or together (see [Combined mode](#combined-mode)).

---

## 1. Goals and non-goals

### Goals
- **Readable across the room.** You should know the round state from 3–4 m
  away with sweat in your eyes. Full-screen color matters more than small
  text.
- **Usable with gloves on.** Starting, stopping and pausing take one big tap
  anywhere on the screen. No small targets during a workout.
- **Hands-free.** Audio (bell, beeps, spoken callouts) carries the
  information, so the screen is optional once a session is running.
- **Reliable.** Timing must not drift, and it must keep working with the
  screen off or the app in the background.
- **Offline and private.** No account, no network, no analytics.

### Non-goals (v1)
- Workout logging, history, or stats.
- Wearable or smartwatch support.
- Video or motion tracking.
- iOS.

---

## 2. Round Timer

### 2.1 Phases

A round has three phases, each shown as a full-screen color:

| Phase   | Default length | Color  | Meaning                         |
|---------|----------------|--------|---------------------------------|
| Work    | 2:30           | Green  | Fight                           |
| Warning | 0:30           | Yellow | Last 30 seconds of the round    |
| Rest    | 1:00           | Red    | Rest between rounds             |

With the defaults, that is a standard 3-minute round (2:30 green + 0:30
yellow) followed by 1 minute of rest. The cycle repeats:

```
[Get ready] → Work → Warning → Rest → Work → Warning → Rest → … → Done
```

- **Get ready** (default 10 s, can be set to 0): a neutral/grey countdown
  before round 1 so you have time to put on gloves and get to the bag.
- **Last round:** no rest phase after the final round. The app goes straight
  to a **Done** screen.
- **Round count:** default is *unlimited* (repeat until stopped). It can be
  set to a fixed number such as 3, 6 or 12.

### 2.2 Screens

**Home / Idle**
- App name, current preset summary (e.g. `3:00 × 6 · 1:00 rest`).
- One **big START button** filling most of the lower half of the screen.
- Settings gear (top right).
- Toggle or tab to switch between Timer, Punch Caller, and Combined.

**Running**
- The whole background is the phase color.
- Very large countdown for the time left in the current phase (`MM:SS`),
  filling about 40% of the screen height.
- Smaller label: phase name and round counter, e.g. `ROUND 3 / 6` or
  `ROUND 3` when unlimited.
- Thin progress bar along the bottom for the whole round.
- **Tapping anywhere stops the timer.**

**Stopped / Paused**
- Tapping during a run pauses the timer and shows an overlay with two big
  buttons:
  - **Resume** (the most prominent)
  - **End session** (goes back to Home)
- Long-press (about 1 s) anywhere ends the session right away. This is for
  when you don't want to aim at a button with gloves on.
- This way a mistaken tap from a glove or towel doesn't wipe out the session.
  To match the original sketch more literally, a setting can make a tap stop
  the session immediately instead.

**Done**
- Summary: rounds completed and total time.
- Big **Again** button and a **Home** button.

### 2.3 Audio and haptics

| Event                    | Default sound        |
|--------------------------|----------------------|
| Round start (→ green)    | Single bell ring     |
| Warning (→ yellow)       | Wood-block clack ×3  |
| Round end (→ red)        | Triple bell ring     |
| Last 3 s of rest or get ready | Short beep each sec |
| Session done             | Long bell            |

- Every sound can be switched on or off, and there is a master volume
  separate from media volume.
- Optional vibration on each phase change.
- Requests transient audio focus with ducking, so music playing from another
  app gets quieter instead of stopping. Sounds play on the media stream
  (`USAGE_MEDIA`, `CONTENT_TYPE_SONIFICATION`): the sonification stream
  follows the ringer on most phones, so a phone on vibrate would silence the
  bell.

### 2.4 Timer settings

| Setting              | Default  | Range / options               |
|----------------------|----------|-------------------------------|
| Round length         | 3:00     | 0:30 – 10:00, 15 s steps      |
| Warning length       | 0:30     | 0:00 – round length, 5 s steps |
| Rest length          | 1:00     | 0:00 – 5:00, 15 s steps       |
| Number of rounds     | Unlimited| 1 – 99 or Unlimited           |
| Get-ready countdown  | 0:10     | 0:00 – 0:30                   |
| Tap behavior         | Pause    | Pause / Stop immediately      |
| Sounds               | All on   | Per-event toggles             |
| Vibration            | On       | On / Off                      |
| Keep screen on       | On       | On / Off                      |

The work (green) phase is not set directly. It is always
`round length − warning length`, so the two can never disagree.

**Presets** (built in, and you can save your own):
- *Pro*: 3:00 × 12, 1:00 rest
- *Amateur*: 3:00 × 3, 1:00 rest
- *Women's pro*: 2:00 × 10, 1:00 rest
- *HIIT*: 0:30 × 10, 0:30 rest, no warning
- *Custom*: whatever you last set

---

## 3. Punch Caller (Random Punch Simulator)

### 3.1 Punch vocabulary

Standard numbering for an orthodox stance (it mirrors automatically for
southpaw; see settings):

| Call | Punch                | Body variant |
|------|----------------------|--------------|
| 1    | Jab                  | 1B           |
| 2    | Cross / straight right | 2B         |
| 3    | Lead hook            | 3B           |
| 4    | Rear hook            | 4B           |
| 5    | Lead uppercut        | —            |
| 6    | Rear uppercut        | —            |

`B` = to the body. Uppercuts have no body variant in the default set, since
`5`/`6` already travel upward through the body line.

Full default pool: `1, 2, 3, 4, 5, 6, 1B, 2B, 3B, 4B`.

### 3.2 Running screen

- One very large call in the middle of the screen (e.g. `3B`) that appears
  the moment it is called.
- The call fades over about 1 s, so you can tell a new call from an old one
  even when the same punch comes up twice in a row. Each new call also gives
  a short flash/pulse.
- Small counter in a corner showing how many calls have been made.
- Same tap-to-pause / long-press-to-end behavior as the timer.

### 3.3 Audio

**Spoken callouts are the main output.** You are looking at the bag, not the
phone.

- Default: Android `TextToSpeech`, saying e.g. "three", "three body", "one
  two".
- Optional: a recorded voice pack (bundled `.ogg` clips) for lower latency
  and a more coach-like delivery. Short clips are loaded into `SoundPool` so
  they play the instant they are called.
- Optional: say the punch name instead of the number ("jab", "rear hook
  body"). Useful for beginners still learning the numbers.

### 3.4 Randomness model

**When a call happens:** the wait between calls is drawn from a
distribution:

```
gap = clamp( minGap, sample(distribution, mean), maxGap )
```

| Distribution   | Feel                                                         |
|----------------|--------------------------------------------------------------|
| Exponential    | Truly unpredictable (Poisson process). Some calls come in bursts, some long gaps. **Default.** |
| Uniform        | Evenly spread between min and max.                            |
| Normal         | Mostly near the mean, with set spread (std-dev).              |
| Fixed          | Metronome. Good for drilling pace.                            |

The `minGap` floor matters. Without it, an exponential distribution
sometimes produces gaps too short to physically react to.

**What gets called:** each punch in the pool has a **weight** (default 1).
Calls are drawn by weighted random choice. Optional rule: *avoid
repeats*, which never calls the same punch twice in a row.

**Combos (optional):** instead of single punches, a call can be a combo of
length 1…N, e.g. `1-2-3B`.
- Combo length is drawn from its own weights (default: 1 = 50%, 2 = 30%,
  3 = 15%, 4 = 5%).
- A combo is spoken as one phrase ("one two three body") and shown as
  `1 · 2 · 3B`.
- Optional "natural combos only" filter: alternate lead/rear hands (e.g.
  `1-2-3-2` is allowed, `1-1-3` only if doubling is enabled).
- The wait to the next call starts *after* the combo has been spoken, plus
  about 0.4 s per punch so you have time to throw it.

**Seed:** each session uses a new random seed. In debug builds the seed can
be fixed so a sequence can be reproduced for testing.

### 3.5 Punch Caller settings

| Setting                   | Default      | Range / options                  |
|---------------------------|--------------|----------------------------------|
| Mean time between calls   | 3.0 s        | 0.5 – 15 s                       |
| Distribution              | Exponential  | Exponential / Uniform / Normal / Fixed |
| Min gap                   | 1.0 s        | 0.3 s – mean                     |
| Max gap                   | 8.0 s        | mean – 30 s                      |
| Std-dev (Normal only)     | 1.0 s        | 0.1 – 5 s                        |
| Punch pool & weights      | All, weight 1| Per punch: on/off + weight 0–5   |
| Avoid repeats             | Off          | On / Off                         |
| Combos                    | Off          | On / Off + max length 2–6 + length weights |
| Stance                    | Orthodox     | Orthodox / Southpaw (only changes punch names, not numbers) |
| Callout style             | Numbers      | Numbers / Names / Both           |
| Voice                     | System TTS   | System TTS / Bundled voice       |
| Speech rate               | 1.2×         | 0.8× – 2.0×                      |
| Session length            | Until stopped| Fixed time or until stopped      |

**Difficulty presets** set the above in one tap:
- *Beginner*: mean 4 s, uniform, min 2 s, singles only, punches 1–4.
- *Intermediate*: mean 3 s, exponential, combos up to 3.
- *Advanced*: mean 1.8 s, exponential, min 0.6 s, combos up to 4, full pool.

A small preview chart in settings shows the gap distribution, so changes to
mean, min and max are easy to see.

---

## 4. Combined mode

This runs the punch caller *inside* the round timer, which is the main way
the app is meant to be used on the bag.

- Calls happen only during the **Work (green)** and **Warning (yellow)**
  phases. They pause during Rest.
- Optional **"Final push"**: during the yellow phase, the mean gap is
  multiplied by a factor (default 0.6×) so calls come faster.
- The screen shows the phase color in the background, the countdown at the
  top, and the current punch call large in the center.
- Callouts never overlap the bell. If a call would fire within 1 s of a phase
  change, it waits until after the bell.

---

## 5. Technical design

### 5.1 Platform
- **Language:** Kotlin
- **UI:** Jetpack Compose, Material 3
- **Min SDK:** 26 (Android 8.0). **Target SDK:** latest stable.
- **Architecture:** single activity with MVVM (`ViewModel` + `StateFlow`)
  and Compose Navigation.
- **Persistence:** Jetpack DataStore (Proto or Preferences) for settings and
  presets.
- **DI:** Hilt, or manual DI because the app is small.

### 5.2 Module / package layout

```
app/
  ui/
    home/            HomeScreen
    timer/           TimerScreen, TimerViewModel
    caller/          CallerScreen, CallerViewModel
    combined/        CombinedScreen
    settings/        TimerSettings, CallerSettings, Presets
    theme/           Colors (phase palette), Typography
  engine/
    RoundEngine.kt   pure Kotlin state machine for phases (no Android deps)
    PunchEngine.kt   pure Kotlin random call generator (no Android deps)
    Clock.kt         interface over SystemClock.elapsedRealtime() (for tests)
  audio/
    SoundPlayer.kt   SoundPool wrapper for bells/beeps
    Announcer.kt     TTS / voice-pack callouts
  service/
    WorkoutService.kt  foreground service that owns the running session
  data/
    SettingsRepository.kt  DataStore
    Presets.kt
```

`RoundEngine` and `PunchEngine` do not depend on Android, so they can be
unit-tested on the JVM with a fake clock and seeded RNG.

### 5.3 Timing accuracy
- Every phase boundary is computed from a single **session start timestamp**
  (`SystemClock.elapsedRealtime()`), plus the time spent paused. Times are
  never built up by adding ticks, so the timer can't drift.
- The UI redraws about every 100 ms from that timestamp. Losing a frame
  never changes the real time.
- Sounds and callouts are scheduled from the same clock in the service, not
  in the UI layer.

### 5.4 Background and screen-off
- A **foreground service** (`foregroundServiceType="mediaPlayback"` or
  `"specialUse"`) runs the session. It shows an ongoing notification with
  the phase, time left, and Pause / Stop actions.
- `FLAG_KEEP_SCREEN_ON` is set on the running screens when *Keep screen on*
  is enabled.
- A partial wake lock is held while a session is running, so audio cues
  still fire with the screen off.
- Exact timing in the background relies on the foreground service and a
  coroutine scheduler. It does not use `AlarmManager`, which is too coarse
  for sub-second cues.

### 5.5 Visual design
- Phase colors are chosen for contrast and colorblind safety. Each phase
  also has a **pattern/icon and label**, so color is never the only signal:
  - Green `#1B8E3E`: ▶ "WORK"
  - Yellow `#F2C200`: ⚠ "30 SEC" (with dark text)
  - Red `#C62828`: ❚❚ "REST"
  - Grey `#37474F`: "GET READY"
- Countdown font: a heavy monospaced/tabular-figure face, so digits don't
  shift as they change.
- Supports both orientations. Landscape is good for a phone propped on the
  floor or a shelf.
- Dark app chrome. The phase colors fill the screen only while a session is
  running.

### 5.6 Accessibility
- Every action works by tapping anywhere on the screen, plus a
  TalkBack-labelled button.
- Spoken phase announcements are available ("Round three", "Thirty
  seconds", "Rest") for people who can't see the screen well.
- Respects the system font scale on non-running screens.

### 5.7 Permissions
- `FOREGROUND_SERVICE` (+ the type-specific foreground service permission
  on API 34+)
- `POST_NOTIFICATIONS` (API 33+, for the ongoing notification)
- `WAKE_LOCK`
- `VIBRATE`
- No internet permission.

---

## 6. Testing

- **Unit tests (JVM):**
  - `RoundEngine`: phase order, last round skips rest, pause/resume keeps
    timing exact, edge cases (warning = 0, rest = 0, warning = round
    length).
  - `PunchEngine`: with a fixed seed, check gaps stay within [min, max],
    the empirical mean lands within tolerance of the target over 10k
    samples, weights are respected (chi-squared check), avoid-repeats holds,
    and combo lengths follow their weights.
- **Instrumented tests:** service survives screen-off, notification actions
  work, settings persist across restarts.
- **Manual drift check:** run 12 × 3:00 rounds and compare against a
  stopwatch. Target is under 100 ms total drift.

---

## 7. Milestones

1. **M1: Timer MVP.** Round engine, running screen with phase colors, big
   start button, tap to stop, bell sounds, basic settings.
2. **M2: Background-safe.** Foreground service, notification controls, wake
   lock, keep-screen-on.
3. **M3: Punch Caller MVP.** Exponential gaps with mean/min/max, the full
   10-punch pool, on-screen display, TTS callouts.
4. **M4: Combined mode** and difficulty presets.
5. **M5: Polish.** Combos, weights, distribution preview, recorded voice
   pack, timer presets, southpaw naming.

---

## 8. Open questions

- Should a tap **stop** (as in the original sketch) or **pause**? This doc
  defaults to pause with long-press to end, because gloves and towels cause
  accidental taps. Worth testing on the bag.
- Should body shots for uppercuts (`5B`, `6B`) be included as optional
  extras?
- Should there be defensive calls ("slip", "roll", "pull") in the punch
  pool in a later version?
- Bundled voice: record our own, or rely only on TTS?
