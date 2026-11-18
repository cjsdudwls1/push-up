#!/usr/bin/env python3
"""The Play listing's phone screenshots and feature graphic, from the app's own screens.

scripts/store-screenshots.sh draws the real screens on the JVM (app/src/test/.../StoreScreenshots.kt)
into a folder of raw PNGs; this lays them out as the listing's eight screenshots and its feature
graphic, in the app's own type (Galmuri11) and its pixel look:

- one deep-space backdrop runs across all eight, so side by side in the store they read as one
  strip: the stars, the nebula and a shooting star cross from one picture into the next;
- each has a two-line caption with its key words in the record yellow, a badge, and on some a part
  of the screen zoomed out of the phone;
- decorations are drawn at one sixth of the size and enlarged without smoothing, so they are pixel
  art like the app.

Writes docs/store-assets/screenshots/NN.png (1080x1920, 9:16) and docs/store-assets/feature-graphic.png
(1024x500). tools/play.py listing uploads them, with the 512 px icon.

Usage: python3 tools/store_screenshots.py RAW_DIR
"""
from __future__ import annotations

import pathlib
import re
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
FONT_BOLD = str(ROOT / "app/src/main/res/font/galmuri11_bold.ttf")
FONT_REGULAR = str(ROOT / "app/src/main/res/font/galmuri11_regular.ttf")
OUT = ROOT / "docs/store-assets"

W, H = 1080, 1920
N = 8
PX = 6  # one decoration pixel on the canvas

# The app's dark palette (ThemeColors.Dark, PixelRoom).
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
VIOLET = (0x8A, 0x5C, 0xFF)
SHADOW = (0x12, 0x08, 0x30)
STAR = (0xDD, 0xE4, 0xFF)
INK = (0x0B, 0x0D, 0x12)


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


# ---------------------------------------------------------------- the strip behind all eight

def panorama() -> Image.Image:
    """The deep space behind every screenshot, one piece across all eight, at decoration scale."""
    w, h = W * N // PX, H // PX
    img = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(img)
    bands = 14
    for b in range(bands):
        t = b / (bands - 1)
        c = lerp(BG0, BG1, t / 0.45) if t < 0.45 else lerp(BG1, NEBULA, (t - 0.45) / 0.55 * 0.75)
        d.rectangle([0, h * b // bands, w, h * (b + 1) // bands], fill=c + (255,))

    # A nebula: a slow wave of violet and teal cells along the strip, dithered at its edge.
    import math
    for x in range(w):
        mid = h * (0.62 + 0.10 * math.sin(x / 61.0) + 0.05 * math.sin(x / 23.0))
        thick = 26 + 12 * math.sin(x / 47.0 + 1.3)
        for y in range(int(mid - thick), int(mid + thick)):
            if not 0 <= y < h:
                continue
            k = 1 - abs(y - mid) / thick
            r = rnd(x * 7919 + y) % 100
            if k > 0.55 or r < k * 120 - 25:
                base = img.getpixel((x, y))[:3]
                tint = (0x3A, 0x1C, 0x86) if math.sin(x / 90.0) > -0.2 else (0x0E, 0x3A, 0x5E)
                img.putpixel((x, y), lerp(base, tint, 0.35 + 0.25 * k) + (255,))

    # Stars: mostly one pixel, some brighter, a few twinkling crosses.
    for i in range(1500):
        x, y = rnd(i * 2 + 1) % w, rnd(i * 2 + 2) % h
        kind = rnd(i + 99991) % 40
        if kind < 30:
            img.putpixel((x, y), lerp(BG1, STAR, 0.45) + (255,))
        elif kind < 38:
            img.putpixel((x, y), STAR + (255,))
        else:
            for dx, dy in [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]:
                if 0 <= x + dx < w and 0 <= y + dy < h:
                    img.putpixel((x + dx, y + dy), (CYAN if kind == 39 else STAR) + (255,))

    # A ringed planet sitting on the seam between the fourth and fifth pictures.
    px, py, r = W * 4 // PX, int(h * 0.30), 22
    for yy in range(-r, r + 1):
        for xx in range(-r, r + 1):
            if xx * xx + yy * yy <= r * r:
                band = (yy + r) // 6 % 2
                c = (0xE8, 0x8A, 0x4A) if band else (0xF2, 0xB0, 0x6A)
                if xx + yy > r * 0.6:
                    c = lerp(c, (0x5A, 0x24, 0x40), 0.45)
                img.putpixel((px + xx, py + yy), c + (255,))
    for xx in range(-r - 16, r + 17):
        yy = round(xx * 0.28)
        for t in (0, 1):
            x, y = px + xx, py + yy + t
            if (xx * xx + (yy + t) * (yy + t) > r * r) or (yy + t) > 0:
                img.putpixel((x, y), (0xFF, 0xE0, 0xA8, 255))

    # A shooting star from the first picture into the second.
    sx, sy = W // PX - 70, 46
    for k in range(60):
        a = max(0, 255 - k * 5)
        c = lerp(CYAN, BG1, k / 60)
        img.putpixel((sx - k * 2, sy - k), c + (a,))
        img.putpixel((sx - k * 2 - 1, sy - k), c + (a,))
    for dx, dy in [(0, 0), (1, 0), (0, 1), (1, 1), (2, 1), (1, 2)]:
        img.putpixel((sx + dx, sy + dy), (255, 255, 255, 255))
    return img


def up(img: Image.Image, k: int = PX) -> Image.Image:
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


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


def framed(canvas: Image.Image, pic: Image.Image, x: int, y: int, step: int = 14, edge=CYAN, outer=CYAN_EDGE,
           shadow: bool = True):
    """A picture in the app's pixel frame: a dark drop shadow, a deep edge, a lit edge."""
    w, h = pic.size
    if shadow:
        sh = Image.new("RGBA", (w + 28, h + 28), SHADOW + (200,))
        canvas.paste(sh, (x - 14 + 18, y - 14 + 24), stairs_mask(sh.size, step + 6))
    o = Image.new("RGB", (w + 28, h + 28), outer)
    canvas.paste(o, (x - 14, y - 14), stairs_mask(o.size, step + 6))
    i = Image.new("RGB", (w + 10, h + 10), edge)
    canvas.paste(i, (x - 5, y - 5), stairs_mask(i.size, step + 2))
    canvas.paste(pic, (x, y), stairs_mask(pic.size, step))


def sparkle(canvas: Image.Image, x: int, y: int, size: int = 3, color=STAR):
    """A four-pointed pixel star, [size] decoration pixels from its middle to a tip."""
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


def badge(canvas: Image.Image, cx: int, cy: int, text: str, fill=YELLOW, ink=INK, size: int = 46, r: int = 0):
    """A pixel burst with a word or two on it, the way the app marks a record."""
    import math
    f = font(FONT_BOLD, size)
    lines = text.split("\n")
    tw = max(ImageDraw.Draw(canvas).textlength(t, font=f) for t in lines)
    rad = r or int(max(tw / 2 + 46, len(lines) * size * 0.75 + 40))
    k = rad // PX + 3
    burst = Image.new("RGBA", (k * 2 + 1, k * 2 + 1))
    spikes = 14
    for yy in range(-k, k + 1):
        for xx in range(-k, k + 1):
            a = math.atan2(yy, xx)
            edge = (rad / PX) * (0.86 + 0.14 * (1 if int((a + math.pi) / (2 * math.pi) * spikes * 2) % 2 else -1))
            dist = math.hypot(xx, yy)
            if dist <= edge:
                c = fill if dist < edge - 1.2 else lerp(fill, YELLOW_DARK, 0.6)
                burst.putpixel((k + xx, k + yy), c + (255,))
    shadow = Image.new("RGBA", burst.size)
    shadow.putalpha(burst.getchannel("A").point(lambda a: 170 if a else 0))
    shadow = Image.composite(Image.new("RGBA", burst.size, SHADOW + (170,)), shadow, burst.getchannel("A"))
    canvas.alpha_composite(up(shadow), (cx - k * PX + 12, cy - k * PX + 16))
    canvas.alpha_composite(up(burst), (cx - k * PX, cy - k * PX))
    d = ImageDraw.Draw(canvas)
    total = len(lines) * size * 1.12
    for j, t in enumerate(lines):
        w = d.textlength(t, font=f)
        d.text((cx - w / 2, cy - total / 2 + j * size * 1.12 - size * 0.08), t, font=f, fill=ink)


def bubble(canvas: Image.Image, x: int, y: int, text: str, size: int = 40, tail_left: bool = True):
    """The cat's speech bubble from the run, in pixels: white, stair corners, a tail under it."""
    d = ImageDraw.Draw(canvas)
    f = font(FONT_BOLD, size)
    w = int(d.textlength(text, font=f)) + 64
    h = size + 52
    sh = Image.new("RGB", (w, h), SHADOW)
    canvas.paste(sh, (x + 10, y + 12), stairs_mask(sh.size, 10))
    b = Image.new("RGB", (w, h), TEXT)
    canvas.paste(b, (x, y), stairs_mask(b.size, 10))
    tx = x + (40 if tail_left else w - 76)
    for k in range(3):
        d.rectangle([tx + k * PX, y + h + k * PX, tx + 6 * PX - k * PX, y + h + (k + 1) * PX - 1], fill=TEXT)
    d.text((x + 32, y + 22), text, font=f, fill=INK)


def caption(canvas: Image.Image, top: int, headline: str, sub: str, x0: int = 0, width: int = W,
            size: int = 86, sub_size: int = 44, align: str = "center"):
    """Up to two lines; words in [brackets] are the record yellow. A hard pixel shadow under all of it."""
    d = ImageDraw.Draw(canvas)
    f = font(FONT_BOLD, size)
    fs = font(FONT_REGULAR, sub_size)
    y = top
    for line in headline.split("\n"):
        parts = [(t[1:-1], True) if t.startswith("[") else (t, False)
                 for t in re.split(r"(\[[^\]]*\])", line) if t]
        total = sum(d.textlength(t, font=f) for t, _ in parts)
        x = x0 + (width - total) / 2 if align == "center" else x0
        for t, h in parts:
            d.text((x + 7, y + 9), t, font=f, fill=SHADOW)
            d.text((x, y), t, font=f, fill=YELLOW if h else TEXT)
            x += d.textlength(t, font=f)
        y += int(size * 1.22)
    if sub:
        w = d.textlength(sub, font=fs)
        x = x0 + (width - w) / 2 if align == "center" else x0
        d.text((x + 4, y + 18 + 5), sub, font=fs, fill=SHADOW)
        d.text((x, y + 18), sub, font=fs, fill=CYAN)
        y += 18 + sub_size
    return y


def phone_shot(raw: pathlib.Path, height: int = 1440) -> Image.Image:
    """A raw screen (three pixels a dp) at [height]: 1440 is two pixels a dp, still crisp."""
    s = Image.open(raw).convert("RGB")
    w = round(s.width * height / s.height)
    return s.resize((w, height), Image.LANCZOS)


def zoom(raw: pathlib.Path, box, scale: float) -> Image.Image:
    s = Image.open(raw).convert("RGB").crop(box)
    return s.resize((round(s.width * scale), round(s.height * scale)), Image.LANCZOS)


def tall_window(raw: pathlib.Path, anchor_from_bottom: float = 0.0, height: int = 2160) -> Image.Image:
    """The bottom [height] rows of a tall render, above where its content ends."""
    s = Image.open(raw).convert("RGB")
    # The content ends where the rows stop differing from the plain ground under it.
    last = s.height - 1
    probe = s.resize((s.width // 8, s.height // 8))
    for y in range(probe.height - 1, 0, -1):
        row = [probe.getpixel((x, y)) for x in range(probe.width)]
        if max(sum(c) for c in row) - min(sum(c) for c in row) > 60:
            last = y * 8 + 8
            break
    bottom = min(s.height, last + 60)
    top = max(0, bottom - height - int(anchor_from_bottom))
    return s.crop((0, top, s.width, top + height))


# ---------------------------------------------------------------- the eight

def compose(raw_dir: pathlib.Path):
    strip = up(panorama())
    shots = []

    def canvas(i):
        return strip.crop((W * i, 0, W * (i + 1), H)).copy()

    PHONE_TOP = 430

    # 1. The hook.
    c = canvas(0)
    caption(c, 96, "[천장]이 내려와요!", "팔굽혀펴기로 밀어 올려 고양이를 지켜요")
    p = phone_shot(raw_dir / "01-run.png")
    framed(c, p, (W - p.width) // 2 + 70, PHONE_TOP)
    cat = zoom(raw_dir / "01-run.png", (150, 1380, 800, 1960), 0.82)
    framed(c, cat, 40, 1340, step=12, edge=PINK, outer=(0x8A, 0x20, 0x48))
    bubble(c, 50, 1180, "살려 줘요, 냥!!", size=40)
    sparkle(c, 96, 560, 4, YELLOW)
    sparkle(c, 980, 1210, 3)
    shots.append(c)

    # 2. One rep, the ceiling goes up.
    c = canvas(1)
    caption(c, 96, "한 번에 천장이 [쑥!]", "카메라가 개수를 세요. 손으로 셀 필요 없어요")
    p = phone_shot(raw_dir / "02-push.png")
    framed(c, p, (W - p.width) // 2 - 60, PHONE_TOP)
    badge(c, 880, 620, "기록\n경신!", size=50)
    for hx, hy, k in [(900, 1180, 8), (960, 1330, 6), (870, 1450, 5), (150, 980, 6)]:
        heart(c, hx, hy, k=k)
    sparkle(c, 980, 880, 4, YELLOW)
    shots.append(c)

    # 3. Depth.
    c = canvas(2)
    caption(c, 96, "[깊게] 내려갈수록\n더 높이 올라가요", "")
    p = phone_shot(raw_dir / "06-deep.png")
    framed(c, p, 60, PHONE_TOP + 70)
    gauge = zoom(raw_dir / "06-deep.png", (860, 640, 1075, 1720), 0.92)
    framed(c, gauge, W - gauge.width - 70, PHONE_TOP + 180, step=12, edge=YELLOW, outer=YELLOW_DARK)
    badge(c, 860, PHONE_TOP + 140, "깊게!", size=50)
    sparkle(c, 1010, 1560, 4, YELLOW)
    shots.append(c)

    # 4. Lives and rest.
    c = canvas(3)
    caption(c, 96, "목숨 [10개], 세트 [10번]", "쉬는 시간까지가 운동이에요")
    p = phone_shot(raw_dir / "03-rest.png")
    framed(c, p, (W - p.width) // 2, PHONE_TOP)
    for i in range(5):
        heart(c, 110, 760 + i * 150, k=7)
    badge(c, 930, 1560, "1분\n휴식", size=46)
    shots.append(c)

    # 5. The ending, sets against their bests.
    c = canvas(4)
    caption(c, 96, "세트마다 [내 기록]을\n깨 봐요", "")
    p = phone_shot(raw_dir / "07-ending.png")
    framed(c, p, (W - p.width) // 2 + 40, PHONE_TOP + 70)
    chart = zoom(raw_dir / "07-ending.png", (130, 700, 950, 1320), 0.8)
    framed(c, chart, 40, 1340, step=12, edge=YELLOW, outer=YELLOW_DARK)
    badge(c, 150, 640, "츄르\n+18", size=46)
    sparkle(c, 1000, 600, 4, YELLOW)
    shots.append(c)

    # 6. Five movements.
    c = canvas(5)
    caption(c, 96, "맨몸운동 [다섯 가지]", "푸쉬업 · 스쿼트 · 플랭크 · 턱걸이 · 딥스")
    p = phone_shot(raw_dir / "04-hub.png")
    framed(c, p, (W - p.width) // 2, PHONE_TOP)
    chips = zoom(raw_dir / "04-hub.png", (40, 1530, 1040, 1700), 0.98)
    framed(c, chips, (W - chips.width) // 2, 1240, step=10, edge=CYAN, outer=CYAN_EDGE)
    badge(c, 920, 560, "폰만\n있으면", size=40)
    shots.append(c)

    # 7. Gifts and the shop.
    c = canvas(6)
    caption(c, 96, "선물 [32가지], 상점 [20가지]", "내 고양이를 마음대로 꾸며요")
    p = tall_window(raw_dir / "08-shop-tall.png").resize((720, 1440), Image.LANCZOS)
    framed(c, p, (W - p.width) // 2 + 60, PHONE_TOP)
    top = zoom(raw_dir / "08-shop-tall.png", (60, 200, 1020, 830), 0.62)
    framed(c, top, 36, 520, step=12, edge=PINK, outer=(0x8A, 0x20, 0x48))
    badge(c, 140, 1520, "츄르\n상점", size=44)
    sparkle(c, 1010, 1300, 4, YELLOW)
    shots.append(c)

    # 8. The promises.
    c = canvas(7)
    caption(c, 96, "[전부 무료]예요", "회원가입도, 결제도 없어요")
    hero = zoom(raw_dir / "01-run.png", (150, 1380, 800, 1960), 1.3)
    framed(c, hero, (W - hero.width) // 2, 400, step=14)
    items = [
        ("모든 운동, 모든 세트 무료", CYAN),
        ("운동하는 중에는 광고 없음", CYAN),
        ("카메라 영상은 저장되지 않아요", YELLOW),
        ("동작 인식은 폰 안에서만 해요", YELLOW),
    ]
    d = ImageDraw.Draw(c)
    f = font(FONT_BOLD, 46)
    y = 400 + hero.height + 70
    for text, col in items:
        box = Image.new("RGB", (900, 120), (0x11, 0x11, 0x2E))
        c.paste(box, (90, y), stairs_mask(box.size, 12))
        chk = Image.new("RGBA", (7, 6))
        for (i, j) in [(0, 3), (1, 4), (2, 5), (3, 4), (4, 3), (5, 2), (6, 1), (1, 3), (2, 4), (3, 3), (4, 2), (5, 1), (6, 0)]:
            chk.putpixel((i, j), col + (255,))
        c.alpha_composite(up(chk), (130, y + 42))
        d.text((200, y + 34), text, font=f, fill=TEXT)
        y += 150
    sparkle(c, 120, 520, 4, YELLOW)
    sparkle(c, 960, 700, 3)
    for hx, hy in [(980, 460), (100, 860)]:
        heart(c, hx, hy, k=7)
    shots.append(c)
    return shots


def feature(raw_dir: pathlib.Path) -> Image.Image:
    fw, fh = 1024, 500
    strip = up(panorama())
    img = strip.crop((W * 2, 380, W * 2 + fw, 380 + fh)).copy()
    d = ImageDraw.Draw(img)
    for (t, f, col, y) in [
        ("고양이 지켜줘", font(FONT_BOLD, 40), CYAN, 86),
        ("팔굽혀펴기 한 번에", font(FONT_BOLD, 58), TEXT, 160),
        ("천장이 쑥!", font(FONT_BOLD, 58), YELLOW, 236),
    ]:
        d.text((64 + 5, y + 7), t, font=f, fill=SHADOW)
        d.text((64, y), t, font=f, fill=col)
    small = font(FONT_REGULAR, 26)
    d.text((64, 350), "카메라가 세는 홈트 · 영상은 저장되지 않아요", font=small, fill=TEXT_2)

    run = Image.open(raw_dir / "01-run.png").convert("RGB")
    crop = run.crop((0, round(run.height * 0.26), run.width, round(run.height * 0.92)))
    ch = 400
    crop = crop.resize((round(crop.width * ch / crop.height), ch), Image.LANCZOS)
    framed(img, crop, fw - crop.width - 60, (fh - ch) // 2, step=10)
    sparkle(img, 600, 110, 3, YELLOW)
    heart(img, 640, 400, k=5)
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
