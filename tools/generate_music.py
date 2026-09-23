#!/usr/bin/env python3
"""
Generates the background music: a handful of seamless loops the user picks between in settings.

Synthesised for the same reasons as the sound effects (tools/generate_sfx.py): there is no music
budget, and a score kept as notes in a script is a diff that can be changed, not a folder of
binaries nobody can adjust. Each track is written as chords, a bass line, a melody and a drum
pattern on a beat grid, rendered additively with numpy, and folded so its tail rings into its own
beginning — so the loop point is inaudible.

Music sits under the game's sounds rather than competing with them: every track is mixed well
below full scale, is played quieter still (MusicPlayer), and keeps its energy between about 80 Hz
and 2 kHz, which leaves the form chimes above it and the impacts below it their own room.

Run:  python3 tools/generate_music.py [bgm_name ...]
Needs: numpy, and ffmpeg on PATH (or FFMPEG env var) to encode OGG.
"""

import os
import subprocess
import sys
import wave

import numpy as np

SR = 44_100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
TAIL_S = 2.5
RNG = np.random.default_rng(20260923)


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12.0)


NOTE = {n: i for i, n in enumerate(["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"])}


def m(name):
    """'C5' -> 72, 'F#3' -> 54."""
    pitch, octave = name[:-1], int(name[-1])
    return 12 * (octave + 1) + NOTE[pitch]


# --- voices -----------------------------------------------------------------------------------

def adsr(n, a=0.01, d=0.1, s=0.7, r=0.08):
    """Attack, decay to sustain, and a release inside the note's own length."""
    e = np.full(n, s)
    na, nd, nr = int(a * SR), int(d * SR), int(r * SR)
    na = min(na, n)
    e[:na] = np.linspace(0, 1, na, endpoint=False) if na else e[:na]
    nd = min(nd, n - na)
    if nd > 0:
        e[na:na + nd] = np.linspace(1, s, nd, endpoint=False)
    nr = min(nr, n)
    if nr > 0:
        e[-nr:] *= np.linspace(1, 0, nr)
    return e


def additive(freq, n, partials, detune=0.0):
    """Sum of harmonics [(multiple, amplitude)], dropping any above 12 kHz to stay alias-free."""
    t = np.arange(n) / SR
    out = np.zeros(n)
    for mult, amp in partials:
        f = freq * mult
        if f >= 12_000:
            continue
        out += amp * np.sin(2 * np.pi * f * t + RNG.uniform(0, 2 * np.pi))
        if detune:
            out += amp * 0.7 * np.sin(2 * np.pi * f * (1 + detune) * t + RNG.uniform(0, 2 * np.pi))
    return out


def square(freq, n, bright=1.0):
    return additive(freq, n, [(k, bright ** (k - 1) / k) for k in range(1, 26, 2)])


def triangle(freq, n):
    return additive(freq, n, [(k, (1 if (k // 2) % 2 == 0 else -1) / (k * k)) for k in range(1, 12, 2)])


def saw_pad(freq, n):
    return additive(freq, n, [(k, 0.7 ** k / k) for k in range(1, 9)], detune=0.004)


def epiano(freq, n):
    """Two-operator FM with a decaying index: the bark at the attack, a sine by the tail."""
    t = np.arange(n) / SR
    index = 1.6 * np.exp(-t / 0.35)
    mod = np.sin(2 * np.pi * freq * t)
    return np.sin(2 * np.pi * freq * t + index * mod) * np.exp(-t / 1.4)


def bell(freq, n):
    t = np.arange(n) / SR
    out = np.zeros(n)
    for mult, amp, tau in ((1, 1.0, 0.9), (2.0, 0.35, 0.5), (3.98, 0.18, 0.25), (9.1, 0.06, 0.08)):
        if freq * mult < 12_000:
            out += amp * np.sin(2 * np.pi * freq * mult * t) * np.exp(-t / tau)
    return out


def marimba(freq, n):
    t = np.arange(n) / SR
    return (np.sin(2 * np.pi * freq * t) * np.exp(-t / 0.35)
            + 0.25 * np.sin(2 * np.pi * freq * 4 * t) * np.exp(-t / 0.05))


def kick(n=None, punch=1.0):
    n = n or int(0.35 * SR)
    t = np.arange(n) / SR
    f = 55 + 110 * np.exp(-t / 0.03)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.11)
    click = RNG.uniform(-1, 1, n) * np.exp(-t / 0.003) * 0.3
    # A little second harmonic, so the kick is felt on a phone speaker and not only heard on buds.
    return (np.tanh(body * 1.6 * punch) + click) * 0.9


def snare(n=None, tone=190):
    n = n or int(0.25 * SR)
    t = np.arange(n) / SR
    noise = RNG.uniform(-1, 1, n)
    noise = np.diff(noise, prepend=0)  # a first difference is a gentle high-pass
    return noise * np.exp(-t / 0.07) * 0.55 + np.sin(2 * np.pi * tone * t) * np.exp(-t / 0.05) * 0.5


def hat(open_=False):
    n = int((0.22 if open_ else 0.05) * SR)
    t = np.arange(n) / SR
    noise = np.diff(np.diff(RNG.uniform(-1, 1, n + 2)))
    return noise * np.exp(-t / (0.08 if open_ else 0.015)) * 0.28


def shaker():
    n = int(0.09 * SR)
    t = np.arange(n) / SR
    noise = np.diff(RNG.uniform(-1, 1, n + 1))
    return noise * np.sin(np.pi * t / t[-1]) ** 2 * 0.16


# --- arrangement ------------------------------------------------------------------------------

class Track:
    """A beat grid that notes are laid onto; rendered once, then folded into a seamless loop."""

    def __init__(self, bpm, bars, beats_per_bar=4):
        self.bpm = bpm
        self.beat = 60.0 / bpm
        self.length = int(round(bars * beats_per_bar * self.beat * SR))
        self.buf = np.zeros(self.length + int(TAIL_S * SR))

    def at(self, beat):
        return int(round(beat * self.beat * SR))

    def add(self, sound, beat, gain=1.0):
        start = self.at(beat)
        end = min(start + len(sound), len(self.buf))
        self.buf[start:end] += sound[: end - start] * gain

    def note(self, voice, midi, beat, dur, gain=1.0, env=None, release=0.08):
        n = int((dur * self.beat + release) * SR)
        x = voice(hz(midi), n)
        if env is not None:
            x = x * env(n)
        self.add(x, beat, gain)

    def echo(self, delay_beats, feedback, mix):
        """A tempo-synced echo, done on the whole buffer so it wraps with everything else."""
        d = self.at(delay_beats)
        wet = np.zeros_like(self.buf)
        tap = self.buf.copy()
        g = mix
        for _ in range(4):
            tap = np.concatenate([np.zeros(d), tap[:-d]])
            wet += tap * g
            g *= feedback
        self.buf += wet

    def render(self, peak=0.72):
        out = self.buf[: self.length].copy()
        tail = self.buf[self.length:]
        out[: len(tail)] += tail  # the loop's tail rings into its own start
        # High-pass below ~70 Hz, in the frequency domain. A phone speaker plays almost nothing
        # down there, and left in, it is most of the energy and sets the level of everything
        # else. Done on the whole loop at once, the filter is circular, so it cannot open a seam.
        spectrum = np.fft.rfft(out)
        f = np.fft.rfftfreq(len(out), 1 / SR)
        spectrum *= np.clip((f - 30) / 50, 0.08, 1.0)
        out = np.fft.irfft(spectrum, len(out))
        out = np.tanh(out / (np.max(np.abs(out)) or 1) * 1.3)
        return out * (peak / np.max(np.abs(out)))


def chord_tones(root_midi, quality):
    shape = {"maj": (0, 4, 7), "min": (0, 3, 7), "maj7": (0, 4, 7, 11), "min7": (0, 3, 7, 10),
             "dom7": (0, 4, 7, 10)}[quality]
    return [root_midi + s for s in shape]


def lay_drums(tr, bars, pattern, gains=None):
    """pattern: {'k': [beats in bar], 's': [...], 'h': [...], 'o': [...], 'sh': [...]}"""
    gains = gains or {}
    for bar in range(bars):
        base = bar * 4
        for b in pattern.get("k", []):
            tr.add(kick(), base + b, gains.get("k", 0.9))
        for b in pattern.get("s", []):
            tr.add(snare(), base + b, gains.get("s", 0.6))
        for b in pattern.get("h", []):
            tr.add(hat(), base + b, gains.get("h", 0.5))
        for b in pattern.get("o", []):
            tr.add(hat(open_=True), base + b, gains.get("o", 0.4))
        for b in pattern.get("sh", []):
            tr.add(shaker(), base + b, gains.get("sh", 0.6))


# --- the tracks -------------------------------------------------------------------------------

def adventure():
    """모험. C major, 124 BPM: a bright square-wave tune over a bouncing bass — setting out."""
    tr = Track(124, 16)
    prog = [("C3", "maj"), ("G2", "maj"), ("A2", "min"), ("F2", "maj"),
            ("C3", "maj"), ("G2", "maj"), ("F2", "maj"), ("G2", "maj"),
            ("A2", "min"), ("F2", "maj"), ("C3", "maj"), ("G2", "maj"),
            ("A2", "min"), ("F2", "maj"), ("G2", "maj"), ("G2", "maj")]
    for bar, (root, q) in enumerate(prog):
        r = m(root)
        base = bar * 4
        for i, off in enumerate((0, 0, 12, 0, 7, 0, 12, 7)):
            tr.note(lambda f, n: square(f, n, 0.35), r + off, base + i * 0.5, 0.45, 0.22,
                    env=lambda n: adsr(n, 0.005, 0.1, 0.8, 0.03))
        for tone in chord_tones(r + 12, q):
            tr.note(saw_pad, tone, base, 4, 0.10, env=lambda n: adsr(n, 0.25, 0.4, 0.8, 0.5), release=0.5)
        # A soft sixteenth-note arpeggio: motion without clutter.
        tones = chord_tones(r + 24, q)
        for i in range(16):
            tr.note(lambda f, n: square(f, n, 0.6), tones[i % len(tones)], base + i * 0.25, 0.2, 0.035,
                    env=lambda n: adsr(n, 0.003, 0.05, 0.3, 0.02))
    melody = [
        ("E5", 0, 1), ("G5", 1, .5), ("C6", 1.5, 1), ("G5", 2.5, .5), ("E5", 3, 1),
        ("D5", 4, 1), ("G5", 5, .5), ("B5", 5.5, 1), ("D6", 6.5, .5), ("B5", 7, 1),
        ("C6", 8, 1.5), ("B5", 9.5, .5), ("A5", 10, 1), ("E5", 11, 1),
        ("F5", 12, 1), ("A5", 13, 1), ("G5", 14, 1.5),
        ("E5", 16, 1), ("G5", 17, .5), ("C6", 17.5, 1), ("G5", 18.5, .5), ("E5", 19, 1),
        ("D5", 20, 1), ("G5", 21, .5), ("B5", 21.5, 1), ("D6", 22.5, .5), ("E6", 23, 1),
        ("F6", 24, 1.5), ("E6", 25.5, .5), ("D6", 26, 1), ("C6", 27, 1),
        ("D6", 28, 2), ("B5", 30, 1), ("G5", 31, 1),
        ("A5", 32, 1.5), ("C6", 33.5, .5), ("E6", 34, 2),
        ("F5", 36, 1.5), ("A5", 37.5, .5), ("C6", 38, 2),
        ("G5", 40, 1.5), ("C6", 41.5, .5), ("E6", 42, 2),
        ("D6", 44, 1), ("B5", 45, 1), ("G5", 46, 2),
        ("A5", 48, 1), ("C6", 49, 1), ("E6", 50, 1), ("D6", 51, 1),
        ("C6", 52, 1), ("A5", 53, 1), ("F5", 54, 2),
        ("G5", 56, 1), ("B5", 57, 1), ("D6", 58, 1), ("F6", 59, 1),
        ("E6", 60, 2), ("D6", 62, 1), ("B5", 63, 1),
    ]
    for name, beat, dur in melody:
        tr.note(lambda f, n: square(f, n, 0.7), m(name), beat, dur * 0.92, 0.2,
                env=lambda n: adsr(n, 0.008, 0.12, 0.65, 0.06))
    lay_drums(tr, 16, {"k": [0, 2, 2.5], "s": [1, 3], "h": [x * 0.5 for x in range(8)]},
              {"k": 0.35, "s": 0.3, "h": 0.3})
    tr.echo(0.75, 0.35, 0.18)
    return tr.render()


def battle():
    """전투. A minor, 140 BPM: driving eighth-note bass, four on the floor, a hard-edged riff."""
    tr = Track(140, 16)
    prog = [("A2", "min"), ("A2", "min"), ("F2", "maj"), ("C3", "maj"),
            ("G2", "maj"), ("A2", "min"), ("F2", "maj"), ("E2", "maj")] * 2
    for bar, (root, q) in enumerate(prog):
        r = m(root)
        base = bar * 4
        for i in range(8):
            off = 12 if i in (3, 7) else 0
            tr.note(lambda f, n: square(f, n, 0.45), r + off, base + i * 0.5, 0.4, 0.16,
                    env=lambda n: adsr(n, 0.003, 0.08, 0.6, 0.02))
        for tone in chord_tones(r + 12, q):
            tr.note(saw_pad, tone, base, 4, 0.09, env=lambda n: adsr(n, 0.05, 0.3, 0.7, 0.3), release=0.3)
    riff = [
        ("E5", 0, .5), ("E5", .5, .5), ("A5", 1, 1), ("G5", 2, .5), ("E5", 2.5, .5), ("D5", 3, 1),
        ("C5", 4, 1), ("D5", 5, .5), ("E5", 5.5, 1.5),
        ("F5", 8, 1), ("E5", 9, .5), ("D5", 9.5, .5), ("C5", 10, 1), ("E5", 11, 1),
        ("G5", 12, 1.5), ("E5", 13.5, .5), ("C5", 14, 2),
        ("D5", 16, .5), ("D5", 16.5, .5), ("G5", 17, 1), ("F5", 18, .5), ("E5", 18.5, .5), ("D5", 19, 1),
        ("E5", 20, 1), ("A5", 21, 1), ("C6", 22, 2),
        ("B5", 24, 1), ("A5", 25, .5), ("G#5", 25.5, .5), ("A5", 26, 1), ("B5", 27, 1),
        ("G#5", 28, 2), ("E5", 30, 2),
    ]
    for rep in range(2):
        for name, beat, dur in riff:
            b = beat + rep * 32
            tr.note(lambda f, n: square(f, n, 0.8), m(name), b, dur * 0.85, 0.2,
                    env=lambda n: adsr(n, 0.004, 0.08, 0.55, 0.04))
            if rep == 1:
                # The second time round, a harmony a third under: the same fight, harder.
                tr.note(lambda f, n: square(f, n, 0.6), m(name) - 4, b, dur * 0.85, 0.09,
                        env=lambda n: adsr(n, 0.004, 0.08, 0.55, 0.04))
    lay_drums(tr, 16, {"k": [0, 1, 2, 3], "s": [1, 3], "h": [x * 0.25 for x in range(16)], "o": [3.5]},
              {"k": 0.4, "s": 0.4, "h": 0.2, "o": 0.25})
    for bar in (7, 15):  # a fill into each half
        for i, b in enumerate((3.0, 3.25, 3.5, 3.75)):
            tr.add(snare(tone=210 + 20 * i), bar * 4 + b, 0.35 + 0.08 * i)
    tr.echo(0.5, 0.3, 0.12)
    return tr.render(0.75)


def focus():
    """집중. Lo-fi, 86 BPM: swung electric-piano sevenths, a soft boom-bap, a little dust."""
    tr = Track(86, 16)
    swing = 0.08
    prog = [("F3", "maj7"), ("E3", "min7"), ("D3", "min7"), ("C3", "maj7")] * 4
    for bar, (root, q) in enumerate(prog):
        r = m(root)
        base = bar * 4
        for hit in (0, 2.5 + swing):
            for tone in chord_tones(r + 12, q):
                tr.note(epiano, tone, base + hit, 1.6, 0.13, release=0.6)
        tr.note(lambda f, n: square(f, n, 0.3), r, base, 1.5, 0.12, env=lambda n: adsr(n, 0.01, 0.3, 0.6, 0.1))
        tr.note(lambda f, n: square(f, n, 0.3), r + 7, base + 2 + swing, 1.0, 0.09,
                env=lambda n: adsr(n, 0.01, 0.3, 0.6, 0.1))
    tune = [("A4", 1, 1), ("G4", 2.5, .5), ("E4", 3, 1), ("D4", 6, 2),
            ("C5", 9, 1), ("B4", 10.5, .5), ("G4", 11, 2), ("E4", 14, 2)]
    for rep in (1, 3):
        for name, beat, dur in tune:
            tr.note(bell, m(name) + 12, rep * 16 + beat, dur, 0.14, release=0.8)
    lay_drums(tr, 16, {"k": [0, 1.75 + swing, 2.5], "s": [1, 3],
                       "h": [0, 0.5 + swing, 1, 1.5 + swing, 2, 2.5 + swing, 3, 3.5 + swing]},
              {"k": 0.35, "s": 0.3, "h": 0.2})
    # Dust: sparse soft clicks across the loop.
    for _ in range(90):
        tr.add(RNG.uniform(-1, 1, 40) * np.linspace(1, 0, 40), RNG.uniform(0, 64), 0.05)
    tr.echo(0.75, 0.25, 0.1)
    return tr.render(0.65)


def calm():
    """잔잔. F major pentatonic, 104 BPM: a marimba tune over a soft pad — 고냥이's room."""
    tr = Track(104, 16)
    prog = [("F3", "maj"), ("D3", "min"), ("A#2", "maj"), ("C3", "maj"),
            ("F3", "maj"), ("D3", "min"), ("A#2", "maj"), ("C3", "maj"),
            ("G2", "min"), ("C3", "maj"), ("F3", "maj"), ("D3", "min"),
            ("G2", "min"), ("C3", "maj"), ("F3", "maj"), ("F3", "maj")]
    for bar, (root, q) in enumerate(prog):
        r = m(root)
        base = bar * 4
        for tone in chord_tones(r + 12, q):
            tr.note(saw_pad, tone, base, 4, 0.07, env=lambda n: adsr(n, 0.5, 0.5, 0.8, 0.6), release=0.6)
        tr.note(triangle, r, base, 1.8, 0.3, env=lambda n: adsr(n, 0.02, 0.3, 0.6, 0.1))
        tr.note(triangle, r + 7, base + 2, 1.8, 0.22, env=lambda n: adsr(n, 0.02, 0.3, 0.6, 0.1))
    tune = [
        ("C5", 0, .5), ("D5", .5, .5), ("F5", 1, 1), ("A5", 2, 1), ("G5", 3, 1),
        ("F5", 4, 1.5), ("D5", 5.5, .5), ("C5", 6, 2),
        ("D5", 8, .5), ("F5", 8.5, .5), ("G5", 9, 1), ("A#5", 10, 1), ("A5", 11, 1),
        ("G5", 12, 2), ("E5", 14, 1), ("C5", 15, 1),
        ("C5", 16, .5), ("D5", 16.5, .5), ("F5", 17, 1), ("A5", 18, 1), ("C6", 19, 1),
        ("A5", 20, 1.5), ("G5", 21.5, .5), ("F5", 22, 2),
        ("D5", 24, 1), ("F5", 25, 1), ("G5", 26, 1), ("A5", 27, 1),
        ("G5", 28, 3),
        ("A#5", 32, 1), ("A5", 33, .5), ("G5", 33.5, .5), ("F5", 34, 2),
        ("G5", 36, 1), ("A5", 37, 1), ("C6", 38, 2),
        ("A5", 40, 1), ("G5", 41, 1), ("F5", 42, 1), ("D5", 43, 1),
        ("F5", 44, 2), ("A5", 46, 2),
        ("A#5", 48, 1), ("A5", 49, .5), ("G5", 49.5, .5), ("F5", 50, 2),
        ("E5", 52, 1), ("G5", 53, 1), ("C6", 54, 2),
        ("A5", 56, 1.5), ("G5", 57.5, .5), ("F5", 58, 2),
        ("F5", 60, 3),
    ]
    for name, beat, dur in tune:
        tr.note(marimba, m(name), beat, dur, 0.34, release=0.4)
        tr.note(bell, m(name) + 12, beat, dur, 0.04, release=0.4)
    lay_drums(tr, 16, {"k": [0, 2.5], "sh": [x * 0.5 for x in range(8)], "s": [3]},
              {"k": 0.25, "sh": 0.5, "s": 0.15})
    tr.echo(0.75, 0.4, 0.16)
    return tr.render(0.62)


TRACKS = {
    "bgm_adventure": adventure,
    "bgm_battle": battle,
    "bgm_focus": focus,
    "bgm_calm": calm,
}


def write_wav(path, samples):
    pcm = (np.clip(samples, -1, 1) * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main():
    ffmpeg = os.environ.get("FFMPEG", "ffmpeg")
    os.makedirs(OUT, exist_ok=True)
    wanted = set(sys.argv[1:])
    unknown = wanted - TRACKS.keys()
    if unknown:
        sys.exit(f"no such track: {', '.join(sorted(unknown))}")
    for name, fn in TRACKS.items():
        if wanted and name not in wanted:
            continue
        wav = os.path.join(OUT, name + ".wav")
        ogg = os.path.join(OUT, name + ".ogg")
        samples = fn()
        write_wav(wav, samples)
        # ANDROID_LOOP tells the platform player the file is a loop, which it then plays without
        # the gap setLooping alone leaves on some devices.
        subprocess.run(
            [ffmpeg, "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "3",
             "-ar", str(SR), "-ac", "1", "-metadata", "ANDROID_LOOP=true", ogg],
            check=True,
        )
        os.remove(wav)
        print(f"  {name}.ogg  {len(samples) / SR:.1f} s  {os.path.getsize(ogg) / 1024:.0f} KB")


if __name__ == "__main__":
    main()
