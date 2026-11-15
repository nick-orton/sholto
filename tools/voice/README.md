# Bundled voice

The bundled voice says calls with recorded clips instead of the phone's
text-to-speech. Clips start instantly and sound more like a coach in combos.
The app offers it in **Settings → Punch Caller → Voice** once there is a clip
for every word.

## Words to record

One file per word, named after the word:

```
one  two  three  four  five  six  body
jab  cross  left  right  hook  uppercut
```

Combos are stitched from these, e.g. `1 · 2 · 3B` is *one · two · three body*
and, with names, *jab · cross · left hook body*. A southpaw's "right hook"
uses the same clips.

## Recording tips

- Say each word on its own, briskly, as you would call it on the pads.
- Keep the same distance and level for every word, so stitched combos sound
  even. Quiet room; no music.
- Any format ffmpeg reads is fine (Voice Memos' `.m4a` works). Silence before
  and after is trimmed for you.

## Making the clips

```bash
python3 tools/voice/prepare_voice.py path/to/recordings/
```

This writes `app/src/main/assets/voice/<word>.ogg` and lists any word still
missing. Commit the `.ogg` files once you're happy with them; only use
recordings you have the rights to, since the repository is public.

To try the feature before recording, `--say` makes clips with the Mac's
built-in voice. Apple's voices may not be redistributed, so keep those local:
delete `app/src/main/assets/voice/` before committing.
