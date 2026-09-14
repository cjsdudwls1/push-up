#!/usr/bin/env python3
"""
Generates the game's sound effects.

They are synthesised rather than recorded or licensed for two reasons. There is no audio budget,
and — more usefully — the design calls for the sounds to sit in two separate spectral bands so they
never mask each other: form feedback dry and high (1.5-4 kHz), combat impacts wet and low
(60-250 Hz). Synthesising them is the only way to guarantee that precisely, and it makes the whole
palette a diff rather than a folder of binaries nobody can adjust.

Run:  python3 tools/generate_sfx.py
Needs: numpy, and ffmpeg on PATH (or FFMPEG env var) to encode OGG.
"""

import math
import os
import struct
import subprocess
import sys
import wave

import numpy as np

SR = 48_000
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


def env(n, attack=0.002, decay=0.2, power=2.0):
    """Percussive envelope: near-instant attack, exponential-ish decay."""
    a = int(attack * SR)
    t = np.arange(n)
    e = np.ones(n)
    if a > 0:
        e[:a] = np.linspace(0, 1, a)
    d = np.exp(-t / (decay * SR)) ** (1.0 / power)
    return e * d


def tone(freq, n, kind="sine"):
    t = np.arange(n) / SR
    if kind == "sine":
        return np.sin(2 * np.pi * freq * t)
    if kind == "tri":
        return 2 * np.abs(2 * ((freq * t) % 1) - 1) - 1
    if kind == "square":
        return np.sign(np.sin(2 * np.pi * freq * t))
    raise ValueError(kind)


def sweep(f0, f1, n, kind="sine"):
    t = np.arange(n) / SR
    k = (f1 - f0) / (n / SR)
    phase = 2 * np.pi * (f0 * t + 0.5 * k * t * t)
    return np.sin(phase) if kind == "sine" else np.sign(np.sin(phase))


def noise(n):
    rng = np.random.default_rng(20260914)
    return rng.uniform(-1, 1, n)


def lowpass(x, cutoff):
    """One-pole; enough for shaping a transient."""
    a = math.exp(-2 * math.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def normalise(x, peak=0.92):
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def secs(s):
    return int(s * SR)


# --- the palette ------------------------------------------------------------------------------

def rep_accept():
    """인정. Dry, high, short — it has to cut through without competing with the impact."""
    n = secs(0.075)
    body = tone(2_450, n, "tri") * env(n, decay=0.018)
    click = highpass(noise(n), 2_000) * env(n, decay=0.008) * 0.5
    return normalise(body + click, 0.75)


def rep_deep():
    """깊게. The same family as accept, a fifth up and with a second chime so it reads as 'more'."""
    n = secs(0.16)
    a = tone(2_450, n, "tri") * env(n, decay=0.020)
    b = tone(3_680, n, "tri") * env(n, decay=0.055) * 0.7
    shimmer = highpass(noise(n), 4_000) * env(n, decay=0.03) * 0.25
    return normalise(a + b + shimmer, 0.85)


def hit(pitch_semitones=0.0, heavy=False):
    """
    Combat impact. Low and wet, so it never masks the form cues.

    The pitch argument is what makes a combo audible with the eyes closed: the impact climbs a
    semitone per rep, so a long chain is heard rising even by someone who cannot look at the screen.
    """
    n = secs(0.34 if heavy else 0.24)
    ratio = 2 ** (pitch_semitones / 12.0)
    f0 = (120 if heavy else 150) * ratio
    thump = sweep(f0, f0 * 0.42, n) * env(n, decay=0.085 if heavy else 0.06)
    body = tone(f0 * 0.5, n) * env(n, decay=0.12) * (0.8 if heavy else 0.5)
    crack = lowpass(noise(n), 900) * env(n, decay=0.02) * (0.5 if heavy else 0.32)
    return normalise(thump + body + crack, 0.95 if heavy else 0.85)


def crit():
    n = secs(0.5)
    base = hit(0, heavy=True)
    base = np.pad(base, (0, n - len(base)))
    ring = tone(880, n) * env(n, decay=0.12) * 0.3
    ring += tone(1_320, n) * env(n, decay=0.09) * 0.22
    return normalise(base + ring, 0.98)


def player_hurt():
    """The boss landing one. Dull and unpleasant, deliberately not satisfying."""
    n = secs(0.42)
    thud = sweep(90, 45, n) * env(n, decay=0.11)
    grit = lowpass(noise(n), 400) * env(n, decay=0.09) * 0.6
    return normalise(thud + grit, 0.9)


def telegraph():
    """
    필살기 온다. Three accelerating beats at -3.0s, -1.6s, -0.7s.

    Timed so the warning can be read by ear alone — the user is mid-rep and not looking at the
    screen, which is the whole reason the audio design exists.
    """
    n = secs(3.2)
    out = np.zeros(n)
    for offset, gain in ((0.0, 0.55), (1.4, 0.75), (2.3, 1.0)):
        start = secs(offset)
        m = secs(0.22)
        if start + m > n:
            m = n - start
        beat = sweep(210, 120, m) * env(m, decay=0.06) * gain
        out[start:start + m] += beat
    return normalise(out, 0.9)


def combo_up():
    n = secs(0.22)
    a = tone(880, n, "tri") * env(n, decay=0.05)
    b = tone(1_320, n, "tri") * env(n, decay=0.07) * 0.6
    return normalise(a + b, 0.7)


def combo_break():
    n = secs(0.3)
    return normalise(sweep(700, 260, n) * env(n, decay=0.09), 0.6)


def enemy_down():
    n = secs(0.7)
    crumble = lowpass(noise(n), 1_200) * env(n, decay=0.16) * 0.8
    drop = sweep(320, 70, n) * env(n, decay=0.2)
    return normalise(crumble + drop, 0.95)


def victory():
    n = secs(1.1)
    out = np.zeros(n)
    for i, f in enumerate((523.25, 659.25, 783.99, 1046.50)):
        start = secs(0.10 * i)
        m = n - start
        out[start:] += tone(f, m, "tri") * env(m, decay=0.30) * (0.5 + 0.12 * i)
    return normalise(out, 0.85)


def defeat():
    """탈진, not 패배. Falls, but resolves — the run ended, the work still counted."""
    n = secs(1.2)
    out = np.zeros(n)
    for i, f in enumerate((523.25, 440.00, 349.23)):
        start = secs(0.16 * i)
        m = n - start
        out[start:] += tone(f, m, "sine") * env(m, decay=0.34) * 0.55
    return normalise(out, 0.6)


def countdown():
    n = secs(0.18)
    return normalise(tone(1_046, n, "tri") * env(n, decay=0.05), 0.7)


def go():
    n = secs(0.45)
    a = tone(1_568, n, "tri") * env(n, decay=0.12)
    b = tone(2_093, n, "tri") * env(n, decay=0.16) * 0.6
    return normalise(a + b, 0.9)


def ceiling_push():
    """고냥이. Warmer and rounder than the dungeon's impacts — a different register on purpose."""
    n = secs(0.26)
    a = sweep(240, 460, n) * env(n, decay=0.08)
    b = tone(700, n, "sine") * env(n, decay=0.05) * 0.4
    return normalise(a + b, 0.8)


SOUNDS = {
    "sfx_rep_accept": rep_accept,
    "sfx_rep_deep": rep_deep,
    "sfx_hit": lambda: hit(0),
    "sfx_hit_heavy": lambda: hit(0, heavy=True),
    "sfx_crit": crit,
    "sfx_player_hurt": player_hurt,
    "sfx_telegraph": telegraph,
    "sfx_combo_up": combo_up,
    "sfx_combo_break": combo_break,
    "sfx_enemy_down": enemy_down,
    "sfx_victory": victory,
    "sfx_defeat": defeat,
    "sfx_countdown": countdown,
    "sfx_go": go,
    "sfx_ceiling_push": ceiling_push,
}


def write_wav(path, samples):
    data = np.clip(samples, -1.0, 1.0)
    pcm = (data * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main():
    ffmpeg = os.environ.get("FFMPEG", "ffmpeg")
    os.makedirs(OUT, exist_ok=True)
    for name, fn in SOUNDS.items():
        wav = os.path.join(OUT, name + ".wav")
        ogg = os.path.join(OUT, name + ".ogg")
        write_wav(wav, fn())
        try:
            subprocess.run(
                [ffmpeg, "-y", "-loglevel", "error", "-i", wav,
                 "-c:a", "libvorbis", "-q:a", "4", "-ar", str(SR), "-ac", "1", ogg],
                check=True,
            )
            os.remove(wav)
            size = os.path.getsize(ogg)
            print(f"  {name}.ogg  {size/1024:.1f} KB")
        except (subprocess.CalledProcessError, FileNotFoundError) as e:
            print(f"  {name}: kept WAV, ffmpeg unavailable ({e})", file=sys.stderr)


if __name__ == "__main__":
    main()
