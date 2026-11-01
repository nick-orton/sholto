# Sholto — Build Plan

This plan turns [DESIGN.md](DESIGN.md) into milestones that can each be built
and tested on their own. Every milestone ends with something you can install
on your phone and try.

## How to use this plan

- **One milestone per session.** Start a fresh Claude Code session for each
  milestone. Point it at this file and DESIGN.md, and name the milestone,
  e.g. *"Build M3 from PLAN.md"*.
- **Set the effort level** to the one listed for the milestone before you
  start.
- **Check the "Done when" list** on a real device before moving on. Commit,
  open a PR and merge at the end of each milestone.
- Milestones are in dependency order. Ones marked *(parallel)* can be built
  at the same time as the milestone before them.

### Effort levels at a glance

| Level      | Use for                                                                   |
|------------|---------------------------------------------------------------------------|
| **medium** | Setup, assets, and polish where the work is routine.                      |
| **high**   | Standard feature work: screens, settings, wiring.                         |
| **xhigh**  | Logic that has to be exactly right, and Android behavior that is easy to get subtly wrong (timing, background services). |

## Summary

| #   | Milestone                          | Effort     | Depends on |
|-----|------------------------------------|------------|------------|
| M0  | Project skeleton & tooling         | medium     | —          |
| M1  | Round engine (pure logic)          | **xhigh**  | M0         |
| M2  | Timer screens (MVP)                | high       | M1         |
| M3  | Sounds & haptics                   | medium     | M2         |
| M4  | Settings & presets                 | high       | M2         |
| M5  | Background-safe sessions           | **xhigh**  | M3, M4     |
| M6  | Punch engine (pure logic)          | high       | M0 *(parallel with M1–M5)* |
| M7  | Punch caller screen & voice        | high       | M5, M6     |
| M8  | Combined mode                      | **xhigh**  | M7         |
| M9  | Combos & advanced caller settings  | high       | M8         |
| M10 | Recorded voice pack                | medium     | M7         |
| M11 | Polish, accessibility & release    | medium     | all        |

---

## M0 — Project skeleton & tooling
**Effort: medium.** This is boilerplate. Android Studio templates and
well-known conventions cover nearly all of it.

**Build:**
- Create an Android project: Kotlin, Jetpack Compose, Material 3, Gradle
  Kotlin DSL with a version catalog (`libs.versions.toml`).
- Min SDK 26, target the latest stable SDK. App ID e.g. `com.nickorton.sholto`.
- Set up the package layout from DESIGN.md §5.2. Use empty placeholder files
  where needed.
- Add a JVM unit-test setup (JUnit 5 or JUnit 4, plus kotlinx-coroutines-test).
- Add a code formatter/linter (ktlint or Spotless).
- Add a GitHub Actions workflow that builds the debug APK and runs unit tests
  on every PR.
- Add a `README.md` with build and install steps.

**Done when:**
- `./gradlew assembleDebug test` passes locally and in CI.
- A blank "Sholto" app installs and opens on your phone.

---

## M1 — Round engine (pure logic)
**Effort: xhigh.** The whole app depends on this state machine. Pause/resume
arithmetic, edge cases and drift-free timing are easy to get subtly wrong,
and bugs here show up in every later milestone.

**Build:**
- `Clock` interface (`now(): Long` in ms), with a real
  `SystemClock.elapsedRealtime()` version and a `FakeClock` for tests.
- `RoundConfig`: round length, warning length, rest length, rounds (or
  unlimited), get-ready length.
- `RoundEngine`: a pure Kotlin state machine that computes state from
  `startTime + pausedDuration` and never adds up ticks. It exposes:
  - current phase (`GetReady`, `Work`, `Warning`, `Rest`, `Done`)
  - round number, time left in phase, round progress
  - `start()`, `pause()`, `resume()`, `stop()`
  - phase-change events, for sounds later
- The last round skips Rest. Zero-length phases are skipped cleanly.

**Tests (all JVM, fake clock):**
- Phase order for 3 rounds with default times.
- Pause in the middle of a phase and resume: boundaries move by exactly the
  paused time.
- Edge cases: warning = 0, rest = 0, get-ready = 0, warning = round length,
  1 round, unlimited.
- 12 rounds simulated end to end: total time matches the config exactly.

**Done when:** all tests pass. There's no UI yet.

---

## M2 — Timer screens (MVP)
**Effort: high.** Standard Compose screens and a ViewModel. The tricky
timing is already handled in M1.

**Build:**
- `TimerViewModel` exposing a `StateFlow<TimerUiState>`. It polls the engine
  about every 100 ms.
- **Home screen:** a large START button and a summary line
  (`3:00 × ∞ · 1:00 rest`). It uses hard-coded default settings for now.
- **Running screen:** full-screen phase color, a large tabular countdown,
  the round counter and a progress bar. Colors, icons and labels follow
  DESIGN.md §5.5.
- **Tap anywhere** pauses and shows a Resume / End overlay. **Long-press**
  ends the session.
- **Done screen:** rounds completed and total time, plus Again and Home
  buttons.
- Keep the screen on while running. Support both orientations.

**Done when:**
- On a phone, a full 3-round session runs with the right colors at the
  right times (checked against a stopwatch).
- Pause, resume and end all work with a gloved tap (or a palm).

---

## M3 — Sounds & haptics
**Effort: medium.** `SoundPool` and vibration are well-documented. The main
care point is audio focus.

**Build:**
- Source or create royalty-free bell, clack and beep sounds (`.ogg`, in
  `res/raw`), and credit their sources in the README.
- `SoundPlayer`: a `SoundPool` wrapper that loads the sounds once at
  startup.
- Hook it up to the engine's phase-change events using the event table in
  DESIGN.md §2.3.
- Audio focus with ducking, so Spotify or other music gets quieter instead
  of stopping.
- Vibrate on each phase change.

**Done when:**
- Each phase change plays the right sound with no noticeable delay.
- Music from another app ducks and comes back.

---

## M4 — Settings & presets
**Effort: high.** Mostly UI and persistence. Input validation needs some
care (e.g. the warning can't be longer than the round).

**Build:**
- `SettingsRepository` on DataStore. Stores every timer setting from
  DESIGN.md §2.4.
- **Timer settings screen:** steppers or pickers with the documented ranges
  and steps, per-sound toggles, vibration, keep screen on, tap behavior
  (Pause / Stop immediately).
- **Presets:** the built-in ones (Pro, Amateur, Women's pro, HIIT), plus
  saving, renaming and deleting your own.
- Home shows the active preset. Settings survive app restarts.

**Done when:**
- Changing settings changes the next session.
- Settings survive force-stop and reboot.
- You can't save an impossible combination.

---

## M5 — Background-safe sessions
**Effort: xhigh.** Foreground services, wake locks, notification permissions
and API-34 service types are where Android apps break in hard-to-spot,
device-specific ways. The engine also moves out of the ViewModel, which
touches everything built so far.

**Build:**
- `WorkoutService`: a foreground service that owns the running
  `RoundEngine` and `SoundPlayer`. The UI connects to it, observes its state,
  and sends it commands.
- An ongoing notification showing the phase, time left and round, with
  Pause/Resume and Stop actions.
- A partial wake lock held while a session runs.
- Permissions: `FOREGROUND_SERVICE` plus the type-specific permission,
  runtime `POST_NOTIFICATIONS` on API 33+, and a graceful fallback if it's
  denied.
- Handle these cases: the app is swiped away (the session keeps running from
  the notification), the screen turns off, a phone call comes in (pause or
  keep going, and document which).

**Done when:**
- With the screen off for a full 12 × 3:00 session, every bell fires on
  time.
- Total drift is under 100 ms against a stopwatch.
- Notification controls work from the lock screen.

---

## M6 — Punch engine (pure logic)  *(parallel with M1–M5)*
**Effort: high.** It's pure Kotlin and easy to test. Statistical tests need
some thought so they aren't flaky, but it's well-understood math. Switch to
**xhigh** if the statistical tests keep failing.

**Build:**
- `PunchConfig`: mean, min, max, distribution (Exponential / Uniform /
  Normal / Fixed), std-dev, punch pool with weights, avoid-repeats.
- `PunchEngine`, given a seeded `Random`:
  - `nextGap()`: draws from the distribution, then clamps to [min, max].
  - `nextCall()`: weighted pick from the pool, respecting avoid-repeats.
- Punch vocabulary model: `1`–`6` and `1B`–`4B`, each with its name for
  orthodox and southpaw stances.
- Leave combos out for now (M9).

**Tests (fixed seed, about 10k samples):**
- Every gap is within [min, max].
- The empirical mean is within tolerance of the target (after clamping).
- Weights are respected (chi-squared test).
- Avoid-repeats never calls the same punch twice in a row.
- Weight 0 or disabled means the punch never appears.

**Done when:** all tests pass and are deterministic.

---

## M7 — Punch caller screen & voice
**Effort: high.** TTS setup and timing (warm-up, latency, interruptions) is
the fiddly part. Reusing M5's service keeps the rest straightforward.

**Build:**
- Add punch-caller sessions to `WorkoutService` so the caller also works
  with the screen off.
- `Announcer`: wraps `TextToSpeech`. Warms it up at startup, sets the speech
  rate, and supports Numbers / Names / Both callout styles.
- **Caller screen:** one very large call, a flash on each new call, a fade
  over about 1 s, and a call counter. Same tap-to-pause and long-press-to-end
  as the timer.
- **Caller settings screen:** mean, min, max, distribution, std-dev, punch
  on/off, avoid-repeats, stance, callout style, speech rate, session length.
- Difficulty presets: Beginner, Intermediate, Advanced (DESIGN.md §3.5).
- Navigation on Home: Timer / Punch Caller (/ Combined in M8).

**Done when:**
- On the heavy bag, calls are heard clearly and on time with music playing.
- Changing the mean noticeably changes the pace.

---

## M8 — Combined mode
**Effort: xhigh.** Two timing systems have to coordinate: calls stop at
rest, speed up in the final 30 seconds, and never overlap the bell. These
race-condition-style bugs are easy to miss in testing.

**Build:**
- `CombinedSession` coordinator. Calls happen only during Work and Warning,
  and pause at Rest and on Pause.
- "Final push" setting: multiplies the mean gap during Warning (default
  0.6×).
- Bell protection: delay any call scheduled within 1 s of a phase change
  until after the bell.
- **Combined screen:** phase color in the background, countdown at the top,
  punch call in the center.
- Combined entry on Home, plus a preset that pairs a timer preset with a
  caller preset.

**Tests:** fake-clock JVM tests confirming no calls during rest, faster
calls during warning, and none within 1 s of a bell.

**Done when:** a 6-round combined session on the bag feels right, with no
calls talking over the bell.

---

## M9 — Combos & advanced caller settings
**Effort: high.** It builds on the M6 engine and its tests. The "natural
combos" rules need care, but the scope is contained.

**Build:**
- Combo generation: length drawn from its own weights, spoken as one phrase
  ("one two three body"), shown as `1 · 2 · 3B`.
- The next wait starts after speech ends, plus about 0.4 s per punch.
- "Natural combos only" filter (alternating hands; optionally allow
  doubling).
- **Per-punch weight sliders** in settings.
- **Gap distribution preview chart** in settings that updates live as
  mean, min and max change.
- Unit tests for combo lengths and natural-combo rules.

**Done when:**
- Advanced preset combos feel throwable.
- The preview chart matches what you feel on the bag.

---

## M10 — Recorded voice pack  *(can be built any time after M7)*
**Effort: medium.** Mostly asset work: recording and trimming clips is
your part. The code is a second `Announcer` implementation on `SoundPool`.

**Build:**
- Record or source clips: "one" through "six", "body", the punch names, and
  "round", "thirty seconds", "rest".
- `VoicePackAnnouncer` that builds a combo by stitching clips together
  with tight gaps.
- A Voice setting: System TTS / Bundled voice.

**Done when:** the bundled voice responds noticeably faster than TTS and
sounds natural for combos.

---

## M11 — Polish, accessibility & release
**Effort: medium.** A checklist of small, separate tasks. Bump to **high**
for the release setup if you've never published to the Play Store.

**Build:**
- App icon and splash (a boxing-glove or bell motif).
- TalkBack labels, spoken phase announcements, font scale on non-running
  screens, a colorblind check of the phase palette.
- Check both orientations on a small phone and a tablet.
- Run the full drift test from DESIGN.md §6 and record the result.
- Release build: signing config, R8/minify, version code/name.
- Play Console internal testing track (or a sideloadable APK in GitHub
  Releases).
- Privacy statement: no network, no data collected.

**Done when:** a signed release build is installed from the Play internal
track (or GitHub Release) and passes a full combined-mode workout.

---

## After v1 (not planned yet)
Ideas from DESIGN.md's open questions: defensive calls (slip, roll, pull),
`5B`/`6B`, workout history, and a Wear OS companion app. Write a new plan
section before starting any of these.
