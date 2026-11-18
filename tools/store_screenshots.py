#!/usr/bin/env python3
"""The Play listing's phone screenshots and feature graphic, from the app's own screens.

scripts/store-screenshots.sh draws the real screens on the JVM (app/src/test/.../StoreScreenshots.kt)
into a folder of raw PNGs; this puts each under a caption on the menus' deep-space ground and writes
docs/store-assets/screenshots/NN.png (1080x1920, 9:16) and docs/store-assets/feature-graphic.png
(1024x500), in the app's own type (Galmuri11).

tools/play.py listing uploads them, with the 512 px icon, to the Play listing.

Usage: python3 tools/store_screenshots.py RAW_DIR
"""
from __future__ import annotations

import pathlib
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
FONT_BOLD = ROOT / "app/src/main/res/font/galmuri11_bold.ttf"
FONT_REGULAR = ROOT / "app/src/main/res/font/galmuri11_regular.ttf"
OUT = ROOT / "docs/store-assets"

# The menus' ground (SpaceBackdrop): black-navy at the top to a violet haze at the foot, in flat bands.
BG0 = (0x04, 0x04, 0x0C)
BG1 = (0x07, 0x08, 0x1A)
NEBULA = (0x2A, 0x12, 0x60)
TEXT = (0xEE, 0xF0, 0xFF)
TEXT_2 = (0xA9, 0xAE, 0xD6)
CYAN = (0x34, 0xE7, 0xFF)
CYAN_EDGE = (0x0E, 0x86, 0xA8)

# raw name, headline, the line under it.
SHOTS = [
    ("01-run", "천장이 내려와요", "팔굽혀펴기로 밀어 올려 주세요"),
    ("02-push", "한 번에 천장이 쑥", "카메라가 개수를 세요"),
    ("03-rest", "목숨 10개, 세트 10번", "쉬고 나서 다음 세트로 이어 가요"),
    ("04-hub", "맨몸운동 다섯 가지", "고르고 바로 시작해요"),
    ("05-wardrobe", "고양이가 선물을 찾아와요", "츄르로 꾸밀 수도 있어요"),
]

W, H = 1080, 1920


def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def backdrop(w: int, h: int, bands: int = 12) -> Image.Image:
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for b in range(bands):
        t = b / (bands - 1)
        c = lerp(BG0, BG1, t / 0.5) if t < 0.5 else lerp(BG1, NEBULA, (t - 0.5) / 0.5 * 0.7)
        d.rectangle([0, h * b // bands, w, h * (b + 1) // bands], fill=c)
    return img


def stairs_mask(size, step: int) -> Image.Image:
    w, h = size
    m = Image.new("L", size, 255)
    d = ImageDraw.Draw(m)
    for (x0, y0, dx, dy) in [(0, 0, 1, 1), (w, 0, -1, 1), (0, h, 1, -1), (w, h, -1, -1)]:
        # Two stairs: a 2x1 and a 1x1 block of `step` cut off each corner.
        for (bx, by) in [(0, 0), (1, 0), (0, 1)]:
            ax, ay = x0 + dx * bx * step, y0 + dy * by * step
            d.rectangle([min(ax, ax + dx * step), min(ay, ay + dy * step),
                         max(ax, ax + dx * step) - 1, max(ay, ay + dy * step) - 1], fill=0)
    return m


def centered(d: ImageDraw.ImageDraw, y: int, text: str, font, fill, w: int = W):
    tw = d.textlength(text, font=font)
    d.text(((w - tw) / 2, y), text, font=font, fill=fill)


def shot(raw: pathlib.Path, headline: str, sub: str) -> Image.Image:
    img = backdrop(W, H)
    d = ImageDraw.Draw(img)
    big = ImageFont.truetype(str(FONT_BOLD), 76)
    small = ImageFont.truetype(str(FONT_REGULAR), 44)
    centered(d, 120, headline, big, TEXT)
    centered(d, 230, sub, small, CYAN)

    screen = Image.open(raw).convert("RGB")
    top = 340
    sh = H - top - 70
    sw = round(screen.width * sh / screen.height)
    screen = screen.resize((sw, sh), Image.NEAREST if sh % screen.height == 0 else Image.LANCZOS)
    x = (W - sw) // 2
    step = 12
    pad = 10
    frame = Image.new("RGB", (sw + pad * 2, sh + pad * 2), CYAN_EDGE)
    img.paste(frame, (x - pad, top - pad), stairs_mask(frame.size, step + 4))
    inner = Image.new("RGB", (sw + 4, sh + 4), CYAN)
    img.paste(inner, (x - 2, top - 2), stairs_mask(inner.size, step))
    img.paste(screen, (x, top), stairs_mask(screen.size, step))
    return img


def feature(raw_dir: pathlib.Path) -> Image.Image:
    fw, fh = 1024, 500
    img = backdrop(fw, fh, bands=8)
    d = ImageDraw.Draw(img)
    title = ImageFont.truetype(str(FONT_BOLD), 40)
    big = ImageFont.truetype(str(FONT_BOLD), 58)
    small = ImageFont.truetype(str(FONT_REGULAR), 26)
    d.text((64, 96), "고양이 지켜줘", font=title, fill=CYAN)
    d.text((64, 170), "팔굽혀펴기 한 번에", font=big, fill=TEXT)
    d.text((64, 246), "천장이 올라가요", font=big, fill=TEXT)
    d.text((64, 360), "카메라가 세는 홈트 · 영상은 저장되지 않아요", font=small, fill=TEXT_2)

    # The run's room, cut from the ceiling to the floor, framed as the screenshots are.
    run = Image.open(raw_dir / "01-run.png").convert("RGB")
    crop = run.crop((0, round(run.height * 0.26), run.width, round(run.height * 0.92)))
    ch = 420
    cw = round(crop.width * ch / crop.height)
    crop = crop.resize((cw, ch), Image.LANCZOS)
    x, y = fw - cw - 56, (fh - ch) // 2
    frame = Image.new("RGB", (cw + 16, ch + 16), CYAN_EDGE)
    img.paste(frame, (x - 8, y - 8), stairs_mask(frame.size, 12))
    inner = Image.new("RGB", (cw + 4, ch + 4), CYAN)
    img.paste(inner, (x - 2, y - 2), stairs_mask(inner.size, 8))
    img.paste(crop, (x, y), stairs_mask(crop.size, 8))
    return img


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    raw_dir = pathlib.Path(sys.argv[1])
    out = OUT / "screenshots"
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.png"):
        old.unlink()
    for i, (name, headline, sub) in enumerate(SHOTS, 1):
        shot(raw_dir / f"{name}.png", headline, sub).save(out / f"{i:02d}.png", optimize=True)
        print(out / f"{i:02d}.png")
    feature(raw_dir).save(OUT / "feature-graphic.png", optimize=True)
    print(OUT / "feature-graphic.png")


if __name__ == "__main__":
    main()
