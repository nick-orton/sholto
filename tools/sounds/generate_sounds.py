#!/usr/bin/env python3
"""Generates Sholto's timer sounds and writes them to app/src/main/res/raw.

Every sound is synthesized here from sine waves and seeded noise, so the
files are original and free of third-party rights. Run it again after
changing anything below:

    python3 tools/sounds/generate_sounds.py

Needs only Python 3 and ffmpeg (for the Ogg Vorbis encoding).
"""

import math
import os
import random
import struct
import subprocess
import sys
import tempfile
import wave

RATE = 44_100
PEAK = 0.9  # About -1 dBFS: loud, for a noisy gym, with no clipping.
OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res", "raw")

# A small brass bell: an inharmonic set of partials, as (frequency ratio,
# amplitude). The prime partial sits at BELL_HZ.
BELL_HZ = 740.0
BELL_PARTIALS = [
    (0.5, 0.35),
    (1.0, 1.0),
    (1.19, 0.55),
    (1.5, 0.45),
    (2.0, 0.7),
    (2.74, 0.35),
    (3.0, 0.25),
    (4.07, 0.2),
]

# A wood block: a few modes that die out within tens of milliseconds.
WOOD_MODES = [(880.0, 1.0, 0.030), (2_350.0, 0.6, 0.018), (3_720.0, 0.35, 0.010)]


def silence(seconds):
    return [0.0] * int(seconds * RATE)


def mix_in(track, sound, at_seconds):
    start = int(at_seconds * RATE)
    end = start + len(sound)
    if end > len(track):
        track.extend([0.0] * (end - len(track)))
    for i, s in enumerate(sound):
        track[start + i] += s


def bell_strike(seconds, ring_seconds, rng):
    """One strike. Higher partials die out faster, as on a real bell."""
    n = int(seconds * RATE)
    out = [0.0] * n
    for ratio, amp in BELL_PARTIALS:
        freq = BELL_HZ * ratio
        tau = ring_seconds * ratio ** -0.6
        phase = rng.random() * 2 * math.pi
        w = 2 * math.pi * freq / RATE
        for i in range(n):
            out[i] += amp * math.exp(-i / (tau * RATE)) * math.sin(w * i + phase)
    # The hammer: a few milliseconds of fading noise.
    for i in range(int(0.004 * RATE)):
        out[i] += 0.6 * (rng.random() * 2 - 1) * (1 - i / (0.004 * RATE))
    return out


def wood_clack(rng):
    n = int(0.12 * RATE)
    out = [0.0] * n
    for freq, amp, tau in WOOD_MODES:
        w = 2 * math.pi * freq / RATE
        for i in range(n):
            out[i] += amp * math.exp(-i / (tau * RATE)) * math.sin(w * i)
    for i in range(int(0.002 * RATE)):
        out[i] += 0.5 * (rng.random() * 2 - 1)
    return out


def beep():
    """A short 1 kHz tone with a touch of third harmonic so it cuts through."""
    n = int(0.15 * RATE)
    ramp = int(0.005 * RATE)
    w = 2 * math.pi * 1_000.0 / RATE
    out = []
    for i in range(n):
        env = min(1.0, i / ramp, (n - 1 - i) / ramp)
        out.append(env * (math.sin(w * i) + 0.2 * math.sin(3 * w * i)))
    return out


def fade_out(track, seconds=0.05):
    n = min(len(track), int(seconds * RATE))
    for i in range(n):
        track[len(track) - n + i] *= 1 - i / n
    return track


def normalize(track):
    peak = max(abs(s) for s in track) or 1.0
    return [s * PEAK / peak for s in track]


def sounds():
    rng = random.Random(1)

    single = fade_out(bell_strike(2.0, 0.9, rng))

    triple = silence(0)
    for k in range(3):
        mix_in(triple, bell_strike(1.8, 0.7, rng), k * 0.3)
    triple = fade_out(triple)

    long_bell = fade_out(bell_strike(4.0, 2.0, rng))

    clacks = silence(0)
    for k in range(3):
        mix_in(clacks, wood_clack(rng), k * 0.2)

    return {
        "bell": single,
        "bell_triple": triple,
        "bell_long": long_bell,
        "clack_triple": clacks,
        "beep": beep(),
    }


def write_wav(path, track):
    with wave.open(path, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes(b"".join(struct.pack("<h", int(s * 32767)) for s in track))


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for name, track in sounds().items():
            track = normalize(track)
            wav = os.path.join(tmp, name + ".wav")
            write_wav(wav, track)
            ogg = os.path.abspath(os.path.join(OUT_DIR, name + ".ogg"))
            subprocess.run(
                # ffmpeg's built-in Vorbis encoder is marked experimental and
                # only writes stereo, so the mono signal goes in both channels.
                ["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-af", "pan=stereo|c0=c0|c1=c0",
                 "-c:a", "vorbis", "-strict", "experimental", "-q:a", "8", ogg],
                check=True,
            )
            print(f"{name}.ogg  {len(track) / RATE:.2f} s")


if __name__ == "__main__":
    sys.exit(main())
