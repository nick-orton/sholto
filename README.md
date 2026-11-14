# Sholto

An Android boxing-training app: a round timer and a random punch caller.
See [DESIGN.md](DESIGN.md) for what it does and [PLAN.md](PLAN.md) for the
build plan.

## Requirements

- JDK 21 (17 or newer works)
- Android SDK with platform `android-37` and build-tools 37. Point Gradle at
  it with `ANDROID_HOME` or a `local.properties` file containing
  `sdk.dir=/path/to/sdk`.
- Android Studio is optional. The Gradle wrapper is checked in.

## Build

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Test and lint

```bash
./gradlew test
```

```bash
./gradlew spotlessCheck
```

Fix formatting automatically with `./gradlew spotlessApply`.

## Install on a phone

1. On the phone, turn on **Developer options** and **USB debugging**.
2. Connect it over USB and check that `adb devices` lists it.
3. Build and install:

```bash
./gradlew installDebug
```

Or install a built APK directly (for example one downloaded from a CI run's
`sholto-debug-apk` artifact):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Sounds

The timer sounds in `app/src/main/res/raw` (bells, wood-block clacks and
beeps) are original: they are synthesized from sine waves and noise by
[`tools/sounds/generate_sounds.py`](tools/sounds/generate_sounds.py), so no
third-party credit or license applies. To change one, edit the script and run
it again (it needs Python 3 and ffmpeg):

```bash
python3 tools/sounds/generate_sounds.py
```

## CI

GitHub Actions runs `spotlessCheck`, `assembleDebug` and `test` on every pull
request and on pushes to `main` and `develop`. The debug APK is uploaded as a
build artifact.
