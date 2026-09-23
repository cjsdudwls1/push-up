#!/usr/bin/env python3
"""
Generates the game's sound effects.

They are synthesised rather than recorded or licensed for two reasons. There is no audio budget,
and — more usefully — the design calls for the sounds to sit in two separate spectral bands so they
never mask each other: form feedback dry and high (1.5-4 kHz), combat impacts wet and low
(60-250 Hz). Synthesising them is the only way to guarantee that precisely, and it makes the whole
palette a diff rather than a folder of binaries nobody can adjust.

Run:  python3 tools/generate_sfx.py [sfx_name ...]
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


# --- 고냥이 and its room ---------------------------------------------------------------------
#
# The cat's band sits between the other two (roughly 300 Hz-1.4 kHz) because that is where a voice
# lives, and because a phone speaker reproduces almost nothing below 200 Hz: a purr synthesised at
# its true 25 Hz would be silence on the device it is for. So the purr is heard as its pulse
# rather than its pitch, and even the heartbeat carries a little upper harmonic to survive.


def resonate(x, freq, q):
    """Two-pole resonator: rings at freq, which is what turns a click into wood or a buzz into a vowel."""
    w = 2 * math.pi * freq / SR
    r = math.exp(-w / (2 * q))
    a1, a2 = -2 * r * math.cos(w), r * r
    y = np.zeros_like(x)
    y1 = y2 = 0.0
    for i, v in enumerate(x):
        y0 = v - a1 * y1 - a2 * y2
        y[i] = y0
        y2, y1 = y1, y0
    return y * (1 - r)


def voice(f0_points, formant_points, dur, vibrato_hz=5.5, vibrato_depth=0.02, breath=0.05, seed=1):
    """
    A cat's voice: a harmonic source whose pitch follows f0_points, shaped by formants that move
    between vowels as formant_points says. Both are lists of (fraction of dur, value) pairs; the
    formant values are (F1, F2). Moving formants are what make "mi-a-ow" rather than a whistle.
    """
    n = secs(dur)
    t = np.arange(n) / SR
    frac = t / dur
    fx, fy = zip(*f0_points)
    f0 = np.interp(frac, fx, fy) * (1 + vibrato_depth * np.sin(2 * np.pi * vibrato_hz * t))
    phase = 2 * np.pi * np.cumsum(f0) / SR
    px, pf = zip(*formant_points)
    f1 = np.interp(frac, px, [f[0] for f in pf])
    f2 = np.interp(frac, px, [f[1] for f in pf])
    out = np.zeros(n)
    for k in range(1, 14):
        fk = k * f0
        weight = np.exp(-((fk - f1) / 260.0) ** 2) + 0.6 * np.exp(-((fk - f2) / 420.0) ** 2) + 0.08 / k
        out += np.sin(k * phase) * weight
    rng = np.random.default_rng(seed)
    out += highpass(rng.uniform(-1, 1, n), 1_500) * breath
    a, r = secs(0.035), secs(0.12)
    shape = np.ones(n)
    shape[:a] = np.linspace(0, 1, a)
    shape[-r:] = np.linspace(1, 0, r) ** 1.5
    return out * shape


def cat_meow():
    """A worried "mi-a-ow" — the ceiling has started to come down and the cat has noticed."""
    x = voice(
        f0_points=[(0, 540), (0.3, 720), (0.7, 620), (1, 430)],
        formant_points=[(0, (380, 2000)), (0.35, (820, 1350)), (0.75, (650, 950)), (1, (500, 850))],
        dur=0.55,
    )
    return normalise(x, 0.8)


def cat_cry():
    """The frightened cry: higher, longer, shakier. Played faster still when it is close to the end."""
    x = voice(
        f0_points=[(0, 640), (0.25, 930), (0.6, 880), (1, 600)],
        formant_points=[(0, (420, 1900)), (0.3, (900, 1450)), (0.8, (700, 1000)), (1, (520, 900))],
        dur=0.8,
        vibrato_hz=8.5,
        vibrato_depth=0.045,
        breath=0.12,
        seed=2,
    )
    return normalise(x, 0.9)


def cat_happy():
    """살았다냥 — a short rising chirp with a rolled start, the sound a cat makes greeting you."""
    n = secs(0.3)
    x = voice(
        f0_points=[(0, 430), (0.45, 560), (1, 820)],
        formant_points=[(0, (500, 1200)), (0.5, (700, 1500)), (1, (450, 2100))],
        dur=0.3,
        vibrato_depth=0.0,
        breath=0.03,
        seed=3,
    )
    t = np.arange(n) / SR
    trill = np.where(t < 0.13, 0.55 + 0.45 * np.abs(np.sin(2 * np.pi * 14 * t)), 1.0)
    return normalise(x * trill, 0.8)


def cat_purr():
    """
    A contented purr: two breaths, the out-breath louder, each a train of pulses at the purr's own
    ~25 Hz. Filtered noise rather than a tone, so it is heard as texture on a speaker that cannot
    play the fundamental.
    """
    n = secs(1.3)
    t = np.arange(n) / SR
    rng = np.random.default_rng(4)
    grain = resonate(rng.uniform(-1, 1, n), 420, 1.6) + 0.5 * resonate(rng.uniform(-1, 1, n), 780, 2.0)
    pulse = np.abs(np.sin(np.pi * 25 * t)) ** 3
    breath = np.zeros(n)
    for start, end, gain in ((0.0, 0.58, 0.65), (0.66, 1.3, 1.0)):
        a, b = secs(start), secs(end)
        m = b - a
        breath[a:b] = np.sin(np.linspace(0, np.pi, m)) ** 0.8 * gain
    return normalise(grain * pulse * breath, 0.7)


def ceiling_creak():
    """
    The ceiling: a wooden creak, as stick-slip friction — clicks at an uneven, rising rate, each
    ringing the same two wood resonances. It has to sound heavy and slow, never like a door.
    """
    n = secs(0.75)
    rng = np.random.default_rng(5)
    clicks = np.zeros(n)
    pos = 0.0
    while True:
        frac = pos / n
        rate = 38 + 70 * frac  # the creak climbs as the joint gives
        pos += SR / rate * (1 + rng.uniform(-0.25, 0.25))
        if pos >= n:
            break
        clicks[int(pos)] = rng.uniform(0.6, 1.0)
    wood = resonate(clicks, 360, 9) + 0.6 * resonate(clicks, 910, 12) + 0.25 * resonate(clicks, 1_450, 14)
    swell = np.sin(np.linspace(0, np.pi, n)) ** 0.6
    return normalise(wood * swell, 0.8)


def heartbeat():
    """
    Lub-dub. The pulse itself is below what a phone speaker plays, so each beat also carries a
    short knock a couple of octaves up: on the device that knock is the heartbeat, and the sub-bass
    is only there for headphones.
    """
    n = secs(0.46)
    out = np.zeros(n)
    for offset, f_from, f_to, gain in ((0.0, 72, 44, 1.0), (0.17, 86, 52, 0.7)):
        start = secs(offset)
        m = secs(0.2)
        low = np.tanh(sweep(f_from, f_to, m) * 3.0) * env(m, attack=0.004, decay=0.05) * 0.55
        k = secs(0.09)
        knock = sweep(f_from * 4.4, f_from * 2.2, k) * env(k, attack=0.002, decay=0.022)
        out[start:start + m] += low * gain
        out[start:start + k] += knock * gain
    return normalise(out, 0.9)


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
    "sfx_heartbeat": heartbeat,
    "sfx_cat_purr": cat_purr,
    "sfx_cat_meow": cat_meow,
    "sfx_cat_cry": cat_cry,
    "sfx_cat_happy": cat_happy,
    "sfx_ceiling_creak": ceiling_creak,
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
    # Names on the command line regenerate only those, so adding a sound does not re-encode (and
    # churn the bytes of) every other one.
    wanted = set(sys.argv[1:])
    unknown = wanted - SOUNDS.keys()
    if unknown:
        sys.exit(f"no such sound: {', '.join(sorted(unknown))}")
    for name, fn in SOUNDS.items():
        if wanted and name not in wanted:
            continue
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
