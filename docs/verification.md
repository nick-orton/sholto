# Verification log

Results of the checks PLAN.md M11 asks for, so they can be repeated and
compared. Dates are when the check was run.

## Drift test (DESIGN.md §6): 2026-11-15, emulator

**Setup:** Pro preset (12 × 3:00, 0:30 warning, 1:00 rest, 10 s get ready) on
the `ghac` emulator (API 37). The app asks for audio focus at the moment it
plays each bell, clack and beep, so the emulator's audio-focus log (`dumpsys
audio`, millisecond timestamps) records when each cue went out. A script polled
it every 30 s; the expected time of each cue was computed from round 1's bell.

**Result: no drift found, but the full run was interrupted.** The Mac running
the emulator went to sleep (lid closed) 17 minutes in, which froze the whole
emulator, including the app's clock, for about 18 minutes and again for
2 minutes. So this isn't the uninterrupted 47-minute run DESIGN.md asks for.
While the Mac was awake:

| Stretch | Cues | Span | Worst error | Drift over the span |
|---|---|---|---|---|
| Rounds 1–4 and round 5's bell | 28 | 960 s | +808 ms, once (see below); otherwise ±8 ms | −7 ms |
| Rounds 6–7, after waking | 10 | 420 s | ±3 ms | +3 ms |

- After each freeze, the bells picked up on the new schedule within
  milliseconds. Timing comes from the clock, not from adding up ticks, so
  nothing builds up.
- **One cue was late:** round 1's warning clacks went out 0.81 s late. That was
  while Gradle builds were running on the same Mac, starving the emulator. The
  next cue was on time again, so the delay didn't carry over.

**Still to do (on a phone):** the full 12-round run against a stopwatch, with
the target of under 100 ms total drift.

## Colorblind check of the phase palette: 2026-11-15

**Method:** the dataviz palette validator, run on the four phase colors
against the dark background, comparing all pairs. It uses OKLab ΔE ×100 with
Machado 2009 simulation at full severity.

- **The original palette failed.** Work green `#1B8E3E` and rest red `#C62828`
  were ΔE 3.3 under deuteranopia: nearly the same color.
- **The new palette passes.** Green `#00796B` and red `#D32F2F`: every pair is
  ΔE ≥ 10.1 under protan and deutan simulation and ≥ 15.3 under tritan.
  White text is 5.3:1 on green and 5.0:1 on red.
- The icons (▶ ⚠ ❚❚) and labels stay as a second signal.
- The validator's lightness-band, chroma and surface-contrast checks are rules
  for chart marks, not full-screen phase backgrounds, so they don't apply. The
  grey get-ready color is neutral on purpose.

## Accessibility: 2026-11-15, emulator

- **TalkBack:**
  - The phase header is a polite live region, so TalkBack says each new phase
    once ("Get ready, round 1", "Work, round 1", "Rest, next round 4 of 12"),
    without reading out the countdown.
  - The settings steppers' − and + buttons are labelled ("Decrease Mean gap").
- **Font scale 2.0:** Home and Settings fit.
  - Segmented-button labels used to break mid-word ("Combin|ed",
    "Exponentia|l"). They now stay on one line and shrink only as much as
    needed.
  - The running screens size their text to the display on purpose, so the
    countdown never overflows.

## Screen sizes and orientations: 2026-11-15, emulator

Small phone (360 × 640 dp) and tablet (800 × 1280 dp), portrait and landscape,
on Home and a running session: everything fits. One fix came out of this: in
landscape the settings gear overlapped the START button's corner, and the
button now sits below it.

## Release build: 2026-11-15, emulator

- `assembleRelease` with R8 and resource shrinking: a 1.6 MB APK.
- For this test only, it was signed with the debug key and installed.
- It cold-starts in about 1 s, showing the bell splash.
- A combined session runs with calls.
- Settings persist across a force-stop.
