#!/usr/bin/env python3
"""Turns voice recordings into the bundled-voice clips in app/src/main/assets/voice.

Record one file per word, named after the word, in any format ffmpeg reads
(wav, m4a, aiff, mp3, flac, ogg), for example `recordings/three.m4a`. Then:

    python3 tools/voice/prepare_voice.py recordings/

Each clip is trimmed of the silence before and after the word (so stitched
combos sound tight), peak-normalized, and encoded as Ogg Vorbis. The script
lists any word still missing; the app offers the bundled voice only once
every word has a clip.

For trying the feature before recording, `--say` makes clips with the Mac's
built-in voice instead. Those are for local testing only: Apple's voices may
not be redistributed, so don't commit or ship them.

Needs Python 3 and ffmpeg.
"""

import argparse
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import wave

# Every word a call can contain. Keep in sync with VoiceScript.WORDS.
WORDS = ["one", "two", "three", "four", "five", "six", "body",
         "jab", "cross", "left", "right", "hook", "uppercut"]
EXTENSIONS = ["wav", "m4a", "aiff", "aif", "mp3", "flac", "ogg"]
RATE = 44_100
PEAK = 0.9  # About -1 dBFS, like the timer sounds.
OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "assets", "voice")

# Trims quiet audio from both ends: anything under -45 dBFS counts as silence.
TRIM = ("silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.01,"
        "areverse,silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.01,areverse")


def ffmpeg(*args):
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", *args], check=True)


def find_recording(folder, word):
    for ext in EXTENSIONS:
        path = os.path.join(folder, f"{word}.{ext}")
        if os.path.exists(path):
            return path
    return None


def normalize(wav_in, wav_out):
    with wave.open(wav_in, "rb") as f:
        frames = f.readframes(f.getnframes())
    samples = struct.unpack(f"<{len(frames) // 2}h", frames)
    peak = max((abs(s) for s in samples), default=0) or 1
    gain = PEAK * 32767 / peak
    out = b"".join(struct.pack("<h", max(-32768, min(32767, int(s * gain)))) for s in samples)
    with wave.open(wav_out, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes(out)


def prepare(source, word, tmp):
    trimmed = os.path.join(tmp, f"{word}-trimmed.wav")
    ffmpeg("-i", source, "-af", TRIM, "-ac", "1", "-ar", str(RATE), "-sample_fmt", "s16", trimmed)
    loud = os.path.join(tmp, f"{word}.wav")
    normalize(trimmed, loud)
    ogg = os.path.abspath(os.path.join(OUT_DIR, f"{word}.ogg"))
    # ffmpeg's built-in Vorbis encoder only writes stereo, so the mono
    # signal goes in both channels.
    ffmpeg("-i", loud, "-af", "pan=stereo|c0=c0|c1=c0", "-c:a", "vorbis", "-strict", "experimental", "-q:a", "6", ogg)
    with wave.open(loud, "rb") as f:
        return f.getnframes() / RATE


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("recordings", nargs="?", help="folder with one recording per word, e.g. three.m4a")
    group.add_argument("--say", action="store_true", help="make test-only clips with the Mac's built-in voice")
    parser.add_argument("--voice", default="Samantha", help="voice for --say (see `say -v ?`)")
    args = parser.parse_args()

    if shutil.which("ffmpeg") is None:
        sys.exit("ffmpeg is needed: brew install ffmpeg")
    os.makedirs(OUT_DIR, exist_ok=True)

    missing = []
    with tempfile.TemporaryDirectory() as tmp:
        for word in WORDS:
            if args.say:
                source = os.path.join(tmp, f"{word}.aiff")
                subprocess.run(["say", "-v", args.voice, "-o", source, word], check=True)
            else:
                source = find_recording(args.recordings, word)
                if source is None:
                    missing.append(word)
                    continue
            seconds = prepare(source, word, tmp)
            print(f"voice/{word}.ogg  {seconds:.2f} s")

    if missing:
        print(f"\nStill missing: {', '.join(missing)}. The app needs every word before it offers the bundled voice.")
    if args.say:
        print("\nThese clips use Apple's voice: test with them locally, but don't commit or ship them.")


if __name__ == "__main__":
    sys.exit(main())
