#!/usr/bin/env python3
"""
Every line the game speaks aloud, for recording or generating voice files.

The app speaks through GameVoice. For each line it first looks for a pre-rendered clip at
app/src/main/assets/voice/<id>.ogg, where <id> is the first 12 hex digits of the SHA-1 of the line's
exact text (UTF-8), and falls back to the phone's own text-to-speech when there is none. Keying on
the text means a reworded line simply stops matching its old clip — it is never spoken in words the
app no longer uses — and a line with no clip still gets said.

Usage:
  tools/voice_lines.py           rewrite tools/voice/lines.json from strings.xml
  tools/voice_lines.py --check   also report which lines have a clip, which do not, and stale clips

lines.json is a list of {"file", "text", "style", "key"}:
  style  URGENT  the ultimate winding up and its outcome: fast, high, pressing — someone two metres
                 away mid-rep has to act on it now
         COACH   a training partner's plain encouragement, 해요체
         CAT     고냥이, the survival mode's cat: small, cute, frightened or relieved
  key    the string resource and argument it came from, for people
"""
import hashlib
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STRINGS = os.path.join(ROOT, "app/src/main/res/values/strings.xml")
CLIPS = os.path.join(ROOT, "app/src/main/assets/voice")
OUT = os.path.join(ROOT, "tools/voice/lines.json")
# Where lines are chosen. Every R.string these name must be listed below or be a fragment.
SPEAKERS = [
    os.path.join(ROOT, "app/src/main/kotlin/com/pushuprpg/app/audio/GameVoice.kt"),
]

# The ultimate is blocked by three answers: said as "세 번", and the count down as "두 번 더!", "한 번 더!".
ANSWERS_TO_BLOCK = 3
# Battle combo callouts every 10; the cat's every 10; the cat's time callouts every 30 s. Lines past
# these ranges are rare and fall back to text-to-speech.
COMBOS = range(10, 101, 10)
SECONDS = range(30, 301, 30)

# Pieces of other lines, never spoken alone.
FRAGMENTS = {f"voice_times_{n}" for n in range(1, 6)}


def load_strings():
    root = ET.parse(STRINGS).getroot()
    out = {}
    for e in root.findall("string"):
        # The voice lines carry no markup or escapes; refuse any that do rather than guess how
        # Android would unescape them, since the hash has to match the text the phone produces.
        text = "".join(e.itertext())
        if "\\" in text or (text.startswith('"') and text.endswith('"')):
            out[e.get("name")] = None
        else:
            out[e.get("name")] = text
    return out


def fmt(template, *args):
    """Android's positional %1$s / %1$d, which is all these lines use."""
    def sub(m):
        return str(args[int(m.group(1)) - 1])
    return re.sub(r"%(\d+)\$[sd]", sub, template)


def lines(s):
    times = {n: s[f"voice_times_{n}"] for n in range(1, 6)}
    out = []

    def add(name, style, *args, key=None):
        t = s[name]
        if t is None:
            sys.exit(f"{name} has escapes or quotes; the clip hash cannot be matched reliably")
        out.append({"text": fmt(t, *args), "style": style, "key": key or name})

    # The ultimate, and its outcome.
    for name in ("voice_ultimate_knight", "voice_ultimate_archer"):
        add(name, "URGENT", times[ANSWERS_TO_BLOCK], key=f"{name}:{ANSWERS_TO_BLOCK}")
    add("voice_ultimate_hold", "URGENT")
    for left in range(1, ANSWERS_TO_BLOCK):
        add("voice_answers_left", "URGENT", times[left], key=f"voice_answers_left:{left}")
    add("voice_ultimate_blocked", "URGENT")
    add("voice_ultimate_hit", "URGENT")

    # Coaching in battle.
    for name in ("voice_boss_low", "voice_shallow", "battle_not_split", "voice_style_too_quick",
                 "voice_style_not_full", "voice_style_lagging", "voice_leg_left", "voice_leg_right",
                 "voice_ready", "voice_rest_ten", "voice_rest_go"):
        add(name, "COACH")
    for n in COMBOS:
        add("voice_combo", "COACH", n, key=f"voice_combo:{n}")

    # Where to move: the placement coach.
    for name in ("placement_step_into_view", "placement_come_closer", "placement_move_back",
                 "placement_show_below", "placement_show_above", "placement_center",
                 "placement_face_floor", "placement_face_standing", "placement_clearer",
                 "quality_unstable_camera", "quality_subject_switch", "quality_implausible_rate",
                 "placement_start_pushup", "placement_start_plank", "placement_start_squat",
                 "placement_start_lunge", "placement_start_pull_up", "placement_start_dip"):
        add(name, "COACH")

    # 고냥이.
    for name in sorted(k for k in s if k.startswith("cat_line_")):
        if "milestone" in name:
            for sec in SECONDS:
                add(name, "CAT", sec, key=f"{name}:{sec}")
        elif "combo" in name:
            for n in COMBOS:
                add(name, "CAT", n, key=f"{name}:{n}")
        else:
            add(name, "CAT")

    seen = set()
    unique = []
    for line in out:
        line["file"] = hashlib.sha1(line["text"].encode("utf-8")).hexdigest()[:12] + ".ogg"
        if line["file"] not in seen:
            seen.add(line["file"])
            unique.append({k: line[k] for k in ("file", "text", "style", "key")})
    return unique


def check_speakers(listed_keys):
    """Every string GameVoice can speak must be listed here, or the manifest silently misses it."""
    listed = {k.split(":")[0] for k in listed_keys}
    missing = []
    for path in SPEAKERS:
        for name in sorted(set(re.findall(r"R\.string\.(\w+)", open(path, encoding="utf-8").read()))):
            if name not in listed and name not in FRAGMENTS:
                missing.append(f"{os.path.relpath(path, ROOT)}: R.string.{name}")
    if missing:
        sys.exit("spoken but not listed in tools/voice_lines.py:\n  " + "\n  ".join(missing))


def main():
    s = load_strings()
    out = lines(s)
    check_speakers([l["key"] for l in out])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"{len(out)} lines -> {os.path.relpath(OUT, ROOT)}")

    if "--check" in sys.argv:
        have = set(os.listdir(CLIPS)) if os.path.isdir(CLIPS) else set()
        wanted = {l["file"] for l in out}
        missing = [l for l in out if l["file"] not in have]
        stale = sorted(f for f in have if f.endswith(".ogg") and f not in wanted)
        print(f"clips: {len(wanted) - len(missing)} of {len(wanted)} present")
        for l in missing:
            print(f"  missing {l['file']}  {l['style']:6}  {l['text']}")
        for f in stale:
            print(f"  stale   {f}  (no line has this text any more; delete it)")


if __name__ == "__main__":
    main()
