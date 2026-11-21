#!/usr/bin/env python3
"""The Play listing's phone screenshots and feature graphic.

Third pass (2026-11-21), by the owner's note: the pictures must say "a bodyweight-workout app that
does push-ups, squats, planks, pull-ups and dips" at a glance, and be understood without reading.
So each picture carries one idea in a short caption, and the picture does the explaining: a person
(tools/pixel_people.py) doing each movement, a phone on the floor counting, the ceiling low before
and high after. The app's own screens (drawn on the JVM by app/src/test/.../StoreScreenshots.kt,
through scripts/store-screenshots.sh) appear where they show what happens; text in them is not meant
to be read.

1. The five movements, named, each with its mark: arrows for counted reps, a stopwatch for the plank.
2. How: a person doing push-ups facing a phone on the floor, its screen called out, +1.
3. The game: the ceiling low if you stay still, pushed up if you move.
4. Each set against its best.
5. Dressing the cat.
6. Free, and no video kept.

One deep-space strip runs behind all six, so side by side in the store they read as one. Writes
docs/store-assets/screenshots/NN.png (1080x1920) and docs/store-assets/feature-graphic.png (1024x500).
tools/play.py listing uploads them with the 512 px icon (tools/pixel_icon.py).

Usage: python3 tools/store_screenshots.py RAW_DIR
"""
from __future__ import annotations

import math
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import pixel_people as pp  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parent.parent
FONT_BOLD = str(ROOT / "app/src/main/res/font/galmuri11_bold.ttf")
FONT_REGULAR = str(ROOT / "app/src/main/res/font/galmuri11_regular.ttf")
OUT = ROOT / "docs/store-assets"

W, H = 1080, 1920
N = 6
PX = 6  # one decoration pixel on the canvas

BG0 = (0x04, 0x04, 0x0C)
BG1 = (0x07, 0x08, 0x1A)
NEBULA = (0x2A, 0x12, 0x60)
TEXT = (0xEE, 0xF0, 0xFF)
TEXT_2 = (0xA9, 0xAE, 0xD6)
CYAN = (0x34, 0xE7, 0xFF)
CYAN_EDGE = (0x0E, 0x86, 0xA8)
YELLOW = (0xFF, 0xC5, 0x3D)
YELLOW_DARK = (0xB0, 0x6A, 0x10)
PINK = (0xFF, 0x6B, 0x9A)
SHADOW = (0x12, 0x08, 0x30)
STAR = (0xDD, 0xE4, 0xFF)
INK = (0x0B, 0x0D, 0x12)
CARD = (0x13, 0x13, 0x34)
CARD_EDGE = (0x2A, 0x28, 0x60)
FLOOR_LINE = (0x3A, 0x34, 0x70)
PHONE = (0x24, 0x22, 0x52)


def font(path, size):
    return ImageFont.truetype(path, size)


def rnd(seed: int) -> int:
    """A fixed hash: the same stars every run, so a regenerated set does not churn the diff."""
    x = (seed * 0x9E3779B1 + 0x7F4A7C15) & 0xFFFFFFFF
    x ^= x >> 15
    x = (x * 0x85EBCA6B) & 0xFFFFFFFF
    x ^= x >> 13
    return x


def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def up(img: Image.Image, k: int = PX) -> Image.Image:
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


# ---------------------------------------------------------------- the strip behind all six

def panorama(n: int = N) -> Image.Image:
    w, h = W * n // PX, H // PX
    img = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(img)
    bands = 14
    for b in range(bands):
        t = b / (bands - 1)
        c = lerp(BG0, BG1, t / 0.45) if t < 0.45 else lerp(BG1, NEBULA, (t - 0.45) / 0.55 * 0.75)
        d.rectangle([0, h * b // bands, w, h * (b + 1) // bands], fill=c + (255,))
    for x in range(w):
        mid = h * (0.64 + 0.09 * math.sin(x / 61.0) + 0.04 * math.sin(x / 23.0))
        thick = 24 + 10 * math.sin(x / 47.0 + 1.3)
        for y in range(int(mid - thick), int(mid + thick)):
            if not 0 <= y < h:
                continue
            k = 1 - abs(y - mid) / thick
            r = rnd(x * 7919 + y) % 100
            if k > 0.55 or r < k * 120 - 25:
                base = img.getpixel((x, y))[:3]
                tint = (0x3A, 0x1C, 0x86) if math.sin(x / 90.0) > -0.2 else (0x0E, 0x3A, 0x5E)
                img.putpixel((x, y), lerp(base, tint, 0.30 + 0.20 * k) + (255,))
    for i in range(1100):
        x, y = rnd(i * 2 + 1) % w, rnd(i * 2 + 2) % h
        kind = rnd(i + 99991) % 40
        if kind < 30:
            img.putpixel((x, y), lerp(BG1, STAR, 0.4) + (255,))
        elif kind < 38:
            img.putpixel((x, y), STAR + (255,))
        else:
            for dx, dy in [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]:
                if 0 <= x + dx < w and 0 <= y + dy < h:
                    img.putpixel((x + dx, y + dy), (CYAN if kind == 39 else STAR) + (255,))
    return img


# ---------------------------------------------------------------- pixel pieces

def stairs_mask(size, step: int) -> Image.Image:
    """The corners cut in two stairs, as PixelShape cuts them."""
    w, h = size
    m = Image.new("L", size, 255)
    d = ImageDraw.Draw(m)
    for (x0, y0, dx, dy) in [(0, 0, 1, 1), (w, 0, -1, 1), (0, h, 1, -1), (w, h, -1, -1)]:
        for (bx, by) in [(0, 0), (1, 0), (0, 1)]:
            ax, ay = x0 + dx * bx * step, y0 + dy * by * step
            d.rectangle([min(ax, ax + dx * step), min(ay, ay + dy * step),
                         max(ax, ax + dx * step) - 1, max(ay, ay + dy * step) - 1], fill=0)
    return m


def panel(canvas: Image.Image, x: int, y: int, w: int, h: int, fill=CARD, edge=CARD_EDGE, step: int = 12):
    o = Image.new("RGB", (w, h), edge)
    canvas.paste(o, (x, y), stairs_mask(o.size, step))
    i = Image.new("RGB", (w - 8, h - 8), fill)
    canvas.paste(i, (x + 4, y + 4), stairs_mask(i.size, step - 2))


def framed(canvas: Image.Image, pic: Image.Image, x: int, y: int, step: int = 14, edge=CYAN, outer=CYAN_EDGE):
    """A screen in the app's pixel frame: a dark drop shadow, a deep edge, a lit edge."""
    w, h = pic.size
    sh = Image.new("RGBA", (w + 28, h + 28), SHADOW + (200,))
    canvas.paste(sh, (x - 14 + 18, y - 14 + 24), stairs_mask(sh.size, step + 6))
    o = Image.new("RGB", (w + 28, h + 28), outer)
    canvas.paste(o, (x - 14, y - 14), stairs_mask(o.size, step + 6))
    i = Image.new("RGB", (w + 10, h + 10), edge)
    canvas.paste(i, (x - 5, y - 5), stairs_mask(i.size, step + 2))
    canvas.paste(pic, (x, y), stairs_mask(pic.size, step))


def from_rows(rows, colors, outline=SHADOW) -> Image.Image:
    """A sprite from rows of characters, with a dark outline round it."""
    h, w = len(rows), max(len(r) for r in rows)
    img = Image.new("RGBA", (w + 2, h + 2))
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in colors:
                img.putpixel((i + 1, j + 1), colors[ch] + (255,))
    out = img.copy()
    for y in range(img.height):
        for x in range(img.width):
            if img.getpixel((x, y))[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < img.width and 0 <= yy < img.height and img.getpixel((xx, yy))[3]:
                    out.putpixel((x, y), outline + (255,))
                    break
    return out


UPDOWN = from_rows([
    "..X..",
    ".XXX.",
    "XXXXX",
    "..X..",
    "..X..",
    "..X..",
    "XXXXX",
    ".XXX.",
    "..X..",
], {"X": YELLOW})

STOPWATCH = from_rows([
    "...XXX...",
    "....X....",
    "..XXXXX..",
    ".XWWWWWX.",
    "XWWWRWWWX",
    "XWWWRWWWX",
    "XWWWRRRWX",
    "XWWWWWWWX",
    ".XWWWWWX.",
    "..XXXXX..",
], {"X": YELLOW, "W": TEXT, "R": INK})

ARROW_RIGHT = from_rows([
    "......X.....",
    "......XX....",
    "XXXXXXXXX...",
    "XXXXXXXXXX..",
    "XXXXXXXXX...",
    "......XX....",
    "......X.....",
], {"X": YELLOW})

CHECK = from_rows([
    "......X",
    ".....XX",
    "X...XX.",
    "XX.XX..",
    ".XXX...",
    "..X....",
], {"X": CYAN})


def sparkle(canvas: Image.Image, x: int, y: int, size: int = 3, color=STAR):
    s = Image.new("RGBA", (size * 2 + 1, size * 2 + 1))
    for k in range(-size, size + 1):
        s.putpixel((size + k, size), color + (255,))
        s.putpixel((size, size + k), color + (255,))
    if size >= 3:
        for dx, dy in [(1, 1), (-1, 1), (1, -1), (-1, -1)]:
            s.putpixel((size + dx, size + dy), color + (255,))
    s = up(s)
    canvas.alpha_composite(s, (x - s.width // 2, y - s.height // 2))


def heart(canvas: Image.Image, x: int, y: int, color=PINK, k: int = PX):
    rows = [".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."]
    s = Image.new("RGBA", (7, 6))
    for j, row in enumerate(rows):
        for i, c in enumerate(row):
            if c == "X":
                s.putpixel((i, j), color + (255,))
    s.putpixel((1, 1), (255, 255, 255, 255))
    s = up(s, k)
    canvas.alpha_composite(s, (x - s.width // 2, y - s.height // 2))


def burst(canvas: Image.Image, cx: int, cy: int, text: str, size: int = 80, fill=YELLOW, ink=INK, r: int = 0):
    """A pixel burst with a word on it, the way the app marks a record."""
    f = font(FONT_BOLD, size)
    lines = text.split("\n")
    tw = max(ImageDraw.Draw(canvas).textlength(t, font=f) for t in lines)
    rad = r or int(max(tw / 2 + 40, len(lines) * size * 0.72 + 34))
    k = rad // PX + 3
    b = Image.new("RGBA", (k * 2 + 1, k * 2 + 1))
    spikes = 14
    for yy in range(-k, k + 1):
        for xx in range(-k, k + 1):
            a = math.atan2(yy, xx)
            edge = (rad / PX) * (0.86 + 0.14 * (1 if int((a + math.pi) / (2 * math.pi) * spikes * 2) % 2 else -1))
            dist = math.hypot(xx, yy)
            if dist <= edge:
                b.putpixel((k + xx, k + yy), (fill if dist < edge - 1.2 else lerp(fill, YELLOW_DARK, 0.6)) + (255,))
    shadow = Image.new("RGBA", b.size, SHADOW + (170,))
    shadow.putalpha(b.getchannel("A").point(lambda a: 170 if a else 0))
    canvas.alpha_composite(up(shadow), (cx - k * PX + 12, cy - k * PX + 16))
    canvas.alpha_composite(up(b), (cx - k * PX, cy - k * PX))
    d = ImageDraw.Draw(canvas)
    total = len(lines) * size * 1.1
    for j, t in enumerate(lines):
        w = d.textlength(t, font=f)
        d.text((cx - w / 2, cy - total / 2 + j * size * 1.1 - size * 0.06), t, font=f, fill=ink)


def text_center(canvas, y, text, size, color, bold=True, shadow=True, x0=0, width=W):
    d = ImageDraw.Draw(canvas)
    f = font(FONT_BOLD if bold else FONT_REGULAR, size)
    w = d.textlength(text, font=f)
    x = x0 + (width - w) / 2
    if shadow:
        d.text((x + max(3, size // 14), y + max(4, size // 11)), text, font=f, fill=SHADOW)
    d.text((x, y), text, font=f, fill=color)


def caption(canvas: Image.Image, top: int, lines, size: int = 104):
    """Big lines; words in [brackets] are the record yellow. A hard pixel shadow under all of it."""
    d = ImageDraw.Draw(canvas)
    f = font(FONT_BOLD, size)
    y = top
    for line in lines:
        parts = [(t[1:-1], True) if t.startswith("[") else (t, False)
                 for t in re.split(r"(\[[^\]]*\])", line) if t]
        total = sum(d.textlength(t, font=f) for t, _ in parts)
        x = (W - total) / 2
        for t, hot in parts:
            d.text((x + 8, y + 10), t, font=f, fill=SHADOW)
            d.text((x, y), t, font=f, fill=YELLOW if hot else TEXT)
            x += d.textlength(t, font=f)
        y += int(size * 1.2)
    return y


def figure(name: str, scale: int) -> tuple[Image.Image, int]:
    """A pose enlarged, cropped to what is drawn; the second value is the floor's row in it."""
    img, floor = pp.POSES[name]()
    box = img.getbbox()
    crop = img.crop((box[0], box[1], box[2], max(box[3], floor + 1)))
    return up(crop, scale), (floor - box[1]) * scale


def screen(raw_dir: pathlib.Path, name: str, width: int) -> Image.Image:
    s = Image.open(raw_dir / f"{name}.png").convert("RGB")
    return s.resize((width, round(s.height * width / s.width)), Image.LANCZOS)


def floor_band(canvas: Image.Image, y: int):
    d = ImageDraw.Draw(canvas)
    d.rectangle([0, y, W, y + 12], fill=FLOOR_LINE)
    d.rectangle([0, y + 12, W, H], fill=(0x0F, 0x0E, 0x2A))
    for yy in range(y + 40, H, 34):
        d.rectangle([0, yy, W, yy + 6], fill=(0x1A, 0x18, 0x40))


def small_phone(raw_dir: pathlib.Path, k: int = 8) -> Image.Image:
    """A phone standing on the floor, its screen the run's: 11 by 20 pixels at [k]."""
    body = Image.new("RGBA", (13, 22))
    d = ImageDraw.Draw(body)
    d.rectangle([1, 1, 11, 20], fill=PHONE + (255,))
    d.rectangle([0, 2, 0, 19], fill=SHADOW + (255,))
    d.rectangle([12, 2, 12, 19], fill=SHADOW + (255,))
    d.rectangle([2, 0, 10, 0], fill=SHADOW + (255,))
    d.rectangle([2, 21, 10, 21], fill=SHADOW + (255,))
    big = up(body, k)
    scr = screen(raw_dir, "02-push", 9 * k)
    scr = scr.crop((0, 0, 9 * k, 16 * k))
    big.paste(scr, (2 * k, 3 * k))
    d = ImageDraw.Draw(big)
    d.rectangle([5 * k, 1 * k + k // 2, 8 * k - 1, 2 * k + k // 4], fill=(0x10, 0x10, 0x28, 255))
    return big


# ---------------------------------------------------------------- the six

def shot_movements(c: Image.Image):
    caption(c, 84, ["[맨몸운동 5가지]", "카메라가 세 줘요"])
    s = 6
    label = font(FONT_BOLD, 76)
    d = ImageDraw.Draw(c)

    def card(x, y, w, h, name, word, mark, wide=False):
        panel(c, x, y, w, h)
        fig, floor = figure(name, s)
        fy = y + h - (44 if wide else 150)
        d.rectangle([x + 24, fy, x + w - 24, fy + 5], fill=FLOOR_LINE)
        fx = x + 46 if wide else x + (w - fig.width) // 2
        c.alpha_composite(fig, (fx, fy - floor))
        tw = d.textlength(word, font=label)
        tx, ty = (x + w - 56 - tw, y + 50) if wide else (x + (w - tw) / 2, y + h - 124)
        d.text((tx + 6, ty + 7), word, font=label, fill=SHADOW)
        d.text((tx, ty), word, font=label, fill=TEXT)
        m = up(mark, 7)
        c.alpha_composite(m, (int(tx - m.width - 24), y + 44) if wide else (x + w - m.width - 22, y + 22))

    card(40, 376, 1000, 350, "pushup", "푸쉬업", UPDOWN, wide=True)
    w3 = (1000 - 2 * 20) // 3
    for i, (name, word) in enumerate([("squat", "스쿼트"), ("pullup", "턱걸이"), ("dip", "딥스")]):
        card(40 + i * (w3 + 20), 750, w3, 720, name, word, UPDOWN)
    card(40, 1494, 1000, 340, "plank", "플랭크", STOPWATCH, wide=True)


def shot_how(c: Image.Image, raw_dir: pathlib.Path):
    caption(c, 84, ["폰만 세워 두면", "[개수를 세 줘요]"])
    floor_y = 1700
    floor_band(c, floor_y)
    fig, floor = figure("pushup", 10)
    fx = 20
    c.alpha_composite(fig, (fx, floor_y - floor))
    ph = small_phone(raw_dir, 8)
    px_, py_ = 880, floor_y - ph.height + 8
    c.alpha_composite(ph, (px_, py_))
    # The phone's screen, called out above it.
    scr = screen(raw_dir, "02-push", 430)
    sx, sy = 560, 420
    framed(c, scr, sx, sy)
    # The callout's tail, down to the phone on the floor.
    d = ImageDraw.Draw(c)
    tip = (px_ + ph.width // 2, py_ - 18)
    base_y = sy + scr.height + 14
    d.polygon([(tip[0] - 60, base_y), (tip[0] + 30, base_y), tip], fill=CYAN_EDGE)
    d.polygon([(tip[0] - 44, base_y), (tip[0] + 18, base_y), (tip[0], tip[1] - 22)], fill=CYAN)
    burst(c, 270, 900, "+1", size=150, r=170)
    sparkle(c, 120, 640, 4, YELLOW)
    sparkle(c, 470, 1180, 3)
    heart(c, 470, 760, k=8)


def shot_game(c: Image.Image, raw_dir: pathlib.Path):
    caption(c, 84, ["1개 할 때마다", "천장이 [쑥!]"])
    w = 440
    left = screen(raw_dir, "game-low", w)
    right = screen(raw_dir, "game-high", w)
    y = 560
    text_center(c, 440, "가만히 있으면", 56, TEXT_2, x0=50, width=w + 20)
    text_center(c, 440, "운동하면!", 56, YELLOW, x0=590, width=w + 20)
    framed(c, left, 60, y, edge=PINK, outer=(0x8A, 0x20, 0x48))
    framed(c, right, 600, y)
    a = up(ARROW_RIGHT, 10)
    c.alpha_composite(a, (540 - a.width // 2, y + left.height // 2 - a.height // 2))
    text_center(c, y + left.height + 70, "천장에 눌리지 않게", 60, TEXT)
    text_center(c, y + left.height + 150, "고양이를 지켜 주세요", 60, CYAN)
    heart(c, 980, 470, k=7)


def shot_records(c: Image.Image, raw_dir: pathlib.Path):
    caption(c, 84, ["세트마다", "[내 기록]을 깨요"])
    # What the run says as a set passes its best, big: now 13, best 12.
    hud = Image.open(raw_dir / "02-push.png").convert("RGB").crop((205, 336, 875, 404))
    hud = hud.resize((980, round(hud.height * 980 / hud.width)), Image.LANCZOS)
    framed(c, hud, 50, 430)
    burst(c, 540, 770, "기록\n경신!", size=84)
    # The ending: every set of the session, the ones that beat their best starred.
    s = Image.open(raw_dir / "07-ending.png").convert("RGB")
    chart = s.crop((120, 650, 960, 1120))
    chart = chart.resize((980, round(chart.height * 980 / chart.width)), Image.LANCZOS)
    framed(c, chart, 50, 1000, edge=YELLOW, outer=YELLOW_DARK)
    sparkle(c, 110, 700, 4, YELLOW)
    sparkle(c, 980, 640, 3)
    heart(c, 140, 900, k=7)
    heart(c, 950, 900, k=7)


def shot_cats(c: Image.Image, raw_dir: pathlib.Path):
    caption(c, 84, ["[고양이]를 꾸며요"])
    text_center(c, 230, "선물 32가지 · 츄르 상점 20가지", 46, CYAN, bold=False)
    size = 470
    for i in range(4):
        p = Image.open(raw_dir / f"cat-{i + 1}.png").convert("RGB")
        side = min(p.width, p.height)
        p = p.crop(((p.width - side) // 2, (p.height - side) // 2, (p.width + side) // 2, (p.height + side) // 2))
        p = p.resize((size, size), Image.LANCZOS)
        x = 55 + (i % 2) * (size + 30)
        y = 400 + (i // 2) * (size + 40)
        framed(c, p, x, y, edge=CYAN if i % 3 == 0 else PINK, outer=CYAN_EDGE if i % 3 == 0 else (0x8A, 0x20, 0x48))
    for hx, hy in [(120, 1460), (960, 1500), (540, 1560)]:
        heart(c, hx, hy, k=8)
    text_center(c, 1620, "운동할수록 선물이 와요", 60, TEXT)


def shot_free(c: Image.Image, raw_dir: pathlib.Path):
    caption(c, 84, ["[전부 무료]예요"])
    rows = [
        "모든 운동 · 모든 세트",
        "운동 중엔 광고 없어요",
        "영상은 저장하지 않아요",
    ]
    f = font(FONT_BOLD, 62)
    d = ImageDraw.Draw(c)
    y = 360
    for t in rows:
        panel(c, 70, y, 940, 170)
        chk = up(CHECK, 9)
        c.alpha_composite(chk, (120, y + (170 - chk.height) // 2))
        d.text((230 + 5, y + 52 + 6), t, font=f, fill=SHADOW)
        d.text((230, y + 52), t, font=f, fill=TEXT)
        y += 200
    # The cat, glad.
    run = Image.open(raw_dir / "02-push.png").convert("RGB").crop((150, 1380, 800, 1960))
    run = run.resize((run.width * 13 // 10, run.height * 13 // 10), Image.LANCZOS)
    framed(c, run, (W - run.width) // 2, 1000)
    for hx, hy in [(130, 1180), (950, 1120), (980, 1500)]:
        heart(c, hx, hy, k=8)


def compose(raw_dir: pathlib.Path):
    strip = up(panorama())
    shots = []
    makers = [
        lambda c: shot_movements(c),
        lambda c: shot_how(c, raw_dir),
        lambda c: shot_game(c, raw_dir),
        lambda c: shot_records(c, raw_dir),
        lambda c: shot_cats(c, raw_dir),
        lambda c: shot_free(c, raw_dir),
    ]
    for i, make in enumerate(makers):
        c = strip.crop((W * i, 0, W * (i + 1), H)).copy()
        make(c)
        shots.append(c)
    return shots


def feature(raw_dir: pathlib.Path) -> Image.Image:
    fw, fh = 1024, 500
    strip = up(panorama())
    img = strip.crop((W * 1, 300, W * 1 + fw, 300 + fh)).copy()
    d = ImageDraw.Draw(img)
    for (t, f, col, x, y) in [
        ("고양이 지켜줘", font(FONT_BOLD, 40), CYAN, 56, 40),
        ("맨몸운동 5가지,", font(FONT_BOLD, 64), TEXT, 56, 98),
        ("카메라가 세 줘요", font(FONT_BOLD, 64), YELLOW, 56, 176),
    ]:
        d.text((x + 5, y + 7), t, font=f, fill=SHADOW)
        d.text((x, y), t, font=f, fill=col)
    floor_y = 470
    d.rectangle([0, floor_y, fw, floor_y + 6], fill=FLOOR_LINE)
    d.rectangle([0, floor_y + 6, fw, fh], fill=(0x0F, 0x0E, 0x2A))
    s = 3
    figs = [figure(n, s) for n in ("pushup", "squat", "pullup", "dip", "plank")]
    gap = 22
    total = sum(f.width for f, _ in figs) + gap * (len(figs) - 1)
    # The two tall ones stand on the right, under no text; the row runs right to left from there.
    x = fw - 30 - total
    for f, floor in figs:
        img.alpha_composite(f, (x, floor_y - floor))
        x += f.width + gap
    return img.convert("RGB")


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    raw_dir = pathlib.Path(sys.argv[1])
    out = OUT / "screenshots"
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.png"):
        old.unlink()
    for i, img in enumerate(compose(raw_dir), 1):
        path = out / f"{i:02d}.png"
        img.convert("RGB").save(path, optimize=True)
        print(path)
    feature(raw_dir).save(OUT / "feature-graphic.png", optimize=True)
    print(OUT / "feature-graphic.png")


if __name__ == "__main__":
    main()
