#!/usr/bin/env python3
"""The launcher icon as pixel art: a person doing a push-up, the cheese cat riding on their back.

By the owner's request (2026-11-21): the cat holding the ceiling up did not say what the app is for, and
an icon has to say "bodyweight workout" at a glance. The push-up is the movement everyone knows, the
cat is the app's own, and the deep-space room is the run's (PixelRoom.drawRoom): a navy wall with stars
and violet deck plates.

Writes the adaptive icon's three layers (app/src/main/res/drawable/ic_launcher_{background,foreground,
monochrome}.xml) and the Play listing's docs/store-assets/icon-512.png from one grid, so they cannot
drift apart. One icon pixel is one dp of the adaptive icon's 108; the person is tools/pixel_people.py's,
the cat the run's sprite. The store icon is the middle 64 dp at eight pixels each, so its pixels stay
square and sharp; everything that matters sits inside the 66 dp safe zone, and the script says how
far out it reaches.

Usage: python3 tools/pixel_icon.py [--preview DIR]   (--preview also writes the store icon and how
launchers' circle and squircle masks show it, for looking at)
"""
from __future__ import annotations

import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import pixel_people as pp  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parent.parent
G = 108  # icon pixels across the adaptive icon: one a dp
CROP = 64  # the store icon is the middle 64 of them, eight pixels each
SAFE = 33.0

ROOM = (0x0A, 0x0B, 0x22)
ROOM_STRIPE = (0x13, 0x12, 0x36)
ROOM_STAR = (0xB8, 0xC2, 0xF0)
GLOW = (0x1C, 0x1A, 0x4C)
FLOOR_EDGE = (0x3A, 0x34, 0x70)
FLOOR = (0x0F, 0x0E, 0x2A)
FLOOR_BOARD = (0x1A, 0x18, 0x40)

OUTLINE = (0x2B, 0x1A, 0x12)
FUR = (0xFF, 0x9A, 0x2E)
STRIPE = (0xE0, 0x76, 0x1A)
LIGHT = (0xFF, 0xD9, 0xA8)
EAR = (0xFF, 0x8F, 0xAB)
EYE = (0x1E, 0x1A, 0x2E)
GLINT = (0xFF, 0xFF, 0xFF)

# The run's cat, sitting: one icon pixel a cell.
CAT = [
    "..KK............KK..",
    ".KOOK..........KOOK.",
    ".KOPOK........KOPOK.",
    "KOOPOOKKKKKKKKOOPOOK",
    "KOOOOOOOSSSSOOOOOOOK",
    "KOOOOOOOOSSOOOOOOOOK",
    "KOOOOOOOOOOOOOOOOOOK",
    "KOOOOOOOOOOOOOOOOOOK",
    "KOOOOOOOOOOOOOOOOOOK",
    "KOOOOOOOOPPOOOOOOOOK",
    "KOPPOOOOKOOKOOOOPPOK",
    "KOOOOOOOOKKOOOOOOOOK",
    ".KOOOOOOOOOOOOOOOOK.",
    "..KKKOOOOOOOOOOKKK..",
    "....KOLLLLLLLLOK....",
    "...KOOLLLLLLLLOOK...",
    "...KOOLLLLLLLLOOK...",
    "...KOOOLLLLLLOOOK...",
    "...KOOOOOOOOOOOOK...",
    "...KLLLKKKKKKLLLK...",
    "....KKK......KKK....",
]
CAT_COLORS = {"K": OUTLINE, "O": FUR, "S": STRIPE, "L": LIGHT, "P": EAR}


def grid():
    return [[None] * G for _ in range(G)]


def put(g, x, y, c):
    if 0 <= x < G and 0 <= y < G:
        g[y][x] = c


def rect(g, x, y, w, h, c):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            put(g, xx, yy, c)


def sprite(g, img: Image.Image, x0: int, y0: int):
    for y in range(img.height):
        for x in range(img.width):
            r, gg, b, a = img.getpixel((x, y))
            if a:
                put(g, x0 + x, y0 + y, (r, gg, b))


def cat(g, x0: int, y0: int, scale: int = 1):
    for r, line in enumerate(CAT):
        for c, ch in enumerate(line):
            if ch in CAT_COLORS:
                rect(g, x0 + c * scale, y0 + r * scale, scale, scale, CAT_COLORS[ch])
    for start in (3, 14):
        rect(g, x0 + start * scale, y0 + 6 * scale, 3 * scale, 3 * scale, EYE)
        rect(g, x0 + (start + 2) * scale, y0 + 6 * scale, scale, scale, GLINT)


def background(floor_y: int):
    g = grid()
    rect(g, 0, 0, G, G, ROOM)
    for x in range(4, G, 12):
        rect(g, x, 0, 1, floor_y, ROOM_STRIPE)
    # A soft round glow behind the subject, in two flat steps.
    for y in range(G):
        for x in range(G):
            d = math.hypot(x + 0.5 - 54, y + 0.5 - 50)
            if y < floor_y and d < 30:
                g[y][x] = GLOW if d < 24 else (0x14, 0x13, 0x3A)
    for j in range(0, floor_y - 2, 4):
        for i in range(0, G, 4):
            h = (i * 374761393 + j * 668265263) & 0xFFFFFFFF
            h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
            h = (h ^ (h >> 16)) & 0x7FFFFFFF
            if h % 9 == 0:
                put(g, i + h % 3, j + h // 7 % 3, ROOM_STAR)
    rect(g, 0, floor_y, G, 2, FLOOR_EDGE)
    rect(g, 0, floor_y + 2, G, G, FLOOR)
    for y in range(floor_y + 6, G, 5):
        rect(g, 0, y, G, 1, FLOOR_BOARD)
    return g


# ---------------------------------------------------------------- the variants

# Long in the body and short in the arm, so the push-up's straight line from head to heels lies
# low: with longer arms it stood steep and read as crawling at 48 dp (the blind review, 2026-11-21).
CHIBI = dict(torso=15.0, thigh=12.0, shin=12.0, upper_arm=7.0, forearm=6.5, head_r=6.6)


def pushup_cat():
    floor_y = 72
    g = background(floor_y)
    fg = grid()
    # Straight arms: at 48 dp the bent elbow of a rep caught halfway read as a lump. The cat on the
    # back is what says it is not a plank.
    with pp.proportions(**CHIBI):
        img, floor = pp.pushup(mid=False)
    # The figure spans most of its sprite; centre what is drawn, not the canvas.
    bbox = img.getbbox()
    x0 = 54 - (bbox[0] + bbox[2]) // 2
    y0 = floor_y - floor
    sprite(fg, img, x0, y0)
    # The cat on the back, sitting up, at the middle of what is drawn.
    back_x = x0 + (bbox[0] + bbox[2]) // 2 - 2
    top = next(y for y in range(G) if fg[y][back_x] is not None)
    cat(fg, back_x - 10, top - 20)
    return g, fg


# Tried on 2026-11-21 and dropped: the push-up caught at the bottom with an arrow (the cat floated off
# the lowered back) and a pull-up with the cat on the bar (too tall for the mask at one pixel a dp).
VARIANTS = {"pushup-cat": pushup_cat}
CHOSEN = "pushup-cat"


# ---------------------------------------------------------------- output

def hexc(c):
    return "#FF%02X%02X%02X" % c


def runs(g):
    """Each colour's horizontal runs, merged down into rectangles where they repeat row after row.
    Sorted throughout, so the same picture always writes the same XML (sets alone came out in a
    different order on every run, which churned the diff)."""
    by_color: dict[str, list[tuple[int, int, int, int]]] = {}
    open_rects: dict[tuple, list[int]] = {}
    for y in range(G + 1):
        row_runs = set()
        if y < G:
            x = 0
            while x < G:
                c = g[y][x]
                if c is None:
                    x += 1
                    continue
                s = x
                while x < G and g[y][x] == c:
                    x += 1
                row_runs.add((s, x, c))
        for key in sorted(open_rects):
            if key not in row_runs:
                s, e, c = key
                top = open_rects.pop(key)[0]
                by_color.setdefault(hexc(c) if isinstance(c, tuple) else c, []).append((s, top, e - s, y - top))
        for key in sorted(row_runs):
            if key not in open_rects:
                open_rects[key] = [y]
    return {c: sorted(r, key=lambda t: (t[1], t[0])) for c, r in sorted(by_color.items())}


def vector(g, comment, tint=None, mono=False):
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f"<!-- {comment}",
        "     Generated by tools/pixel_icon.py; edit that, not this. -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="108dp"',
        '    android:height="108dp"',
        '    android:viewportWidth="108"',
        '    android:viewportHeight="108"' + (f'\n    android:tint="{tint}">' if tint else ">"),
    ]
    for color, rects in runs(g).items():
        d = "".join(f"M{x},{y}h{w}v{h}h-{w}z" for x, y, w, h in rects)
        lines.append("    <path")
        lines.append(f'        android:pathData="{d}"')
        lines.append(f'        android:fillColor="{"#FFFFFFFF" if mono else color}" />')
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def flat(bg, fg) -> Image.Image:
    img = Image.new("RGB", (G, G))
    for y in range(G):
        for x in range(G):
            c = fg[y][x] if fg[y][x] is not None else bg[y][x]
            img.putpixel((x, y), c)
    return img


def store_icon(bg, fg) -> Image.Image:
    lo = (G - CROP) // 2
    return flat(bg, fg).crop((lo, lo, lo + CROP, lo + CROP)).resize((512, 512), Image.NEAREST)


def reach(fg):
    return max(math.hypot(x + 0.5 - 54, y + 0.5 - 54) for y in range(G) for x in range(G) if fg[y][x] is not None)


def preview(dir_: pathlib.Path):
    """Each variant as the store shows it, and as launchers' circle and squircle masks show it."""
    dir_.mkdir(parents=True, exist_ok=True)
    for name, make in VARIANTS.items():
        bg, fg = make()
        print(f"{name}: reaches {reach(fg):.1f} dp from the middle (safe zone {SAFE:g})")
        store = store_icon(bg, fg)
        store.save(dir_ / f"{name}-store.png")
        full = flat(bg, fg).resize((G * 4, G * 4), Image.NEAREST)
        lo = 18 * 4
        launcher = full.crop((lo, lo, lo + 72 * 4, lo + 72 * 4))
        for shape in ("circle", "squircle"):
            m = Image.new("L", launcher.size, 0)
            d = ImageDraw.Draw(m)
            if shape == "circle":
                d.ellipse([0, 0, launcher.width - 1, launcher.height - 1], fill=255)
            else:
                d.rounded_rectangle([0, 0, launcher.width - 1, launcher.height - 1], radius=launcher.width // 3, fill=255)
            out = Image.new("RGB", launcher.size, (0xF2, 0xF2, 0xF6))
            out.paste(launcher, (0, 0), m)
            out.save(dir_ / f"{name}-{shape}.png")


def main():
    if "--preview" in sys.argv:
        preview(pathlib.Path(sys.argv[sys.argv.index("--preview") + 1]))
    bg, fg = VARIANTS[CHOSEN]()
    print(f"{CHOSEN}: reaches {reach(fg):.1f} dp from the middle (safe zone {SAFE:g})")
    mono = [[("#FFFFFFFF" if c is not None and c not in (EYE, GLINT, pp.OUTLINE) else None) for c in row] for row in fg]
    res = ROOT / "app/src/main/res/drawable"
    (res / "ic_launcher_background.xml").write_text(
        vector(bg, "The run's room: a navy wall with stars, a glow behind the figure, violet deck plates."),
        encoding="utf-8")
    (res / "ic_launcher_foreground.xml").write_text(
        vector(fg, "A person doing a push-up with the cheese cat sitting on their back, in pixels:\n"
                   "     what the app is (a bodyweight workout) and whose it is (the cat's) at a glance."),
        encoding="utf-8")
    (res / "ic_launcher_monochrome.xml").write_text(
        vector(mono, "Themed icons: the person and the cat as one silhouette, the eyes and the outlines\n"
                     "     left out so the shapes still read in a single colour.", tint="#FFFFFFFF", mono=True),
        encoding="utf-8")
    out = ROOT / "docs/store-assets/icon-512.png"
    store_icon(bg, fg).save(out)
    print(f"wrote the three layers and {out.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
