#!/usr/bin/env python3
"""The launcher icon as pixel art: the cheese cat holding the hull-steel ceiling up on its ship's deck.

Deep space since 2026-11-11 (the owner's "아주 짙은 남색, 아주 짙은 보라, 검은색"): the run's room
(PixelRoom.drawRoom) - a navy wall with stars through it, violet deck plates, a steel ceiling with a
neon-cyan lit edge.

Writes the adaptive icon's three layers (app/src/main/res/drawable/ic_launcher_{background,foreground,
monochrome}.xml) and the Play listing's docs/store-assets/icon-512.png from one raster, so they cannot
drift apart. The picture is the share card's (ShareCardRenderer.drawCeilingAndCat) ported cell for cell:
an art pixel is half a dp of the icon's 108, the cat 20 cells of 4 of them.

The paws and everything below stay inside the 66 dp safe zone (a circle of radius 33 around 54,54) to
within a dp; the ceiling and the room run off the mask on purpose. The script prints how far out the cat
reaches.

Usage: python tools/pixel_icon.py
"""
from __future__ import annotations

import math
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
N = 216  # art pixels across the icon's 108 dp

ROOM = "#FF0A0B22"
ROOM_STRIPE = "#FF15143A"
ROOM_STAR = "#FFB8C2F0"
FLOOR_EDGE = "#FF3A3470"
FLOOR = "#FF0F0E2A"
FLOOR_BOARD = "#FF1A1840"
BRICK = "#FF4A4870"
BRICK_DARK = "#FF33324E"
BRICK_LIT = "#FF34E7FF"
OUTLINE = "#FF2B1A12"
FUR = "#FFFF9A2E"
STRIPE = "#FFE0761A"
LIGHT = "#FFFFD9A8"
EAR = "#FFFF8FAB"
EYE = "#FF1E1A2E"
GLINT = "#FFFFFFFF"

SPRITE = [
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
SPRITE_COLORS = {"K": OUTLINE, "O": FUR, "S": STRIPE, "L": LIGHT, "P": EAR}


def layer():
    return [[None] * N for _ in range(N)]


def px(g, c, r, w, h, color):
    for y in range(max(r, 0), min(r + h, N)):
        for x in range(max(c, 0), min(c + w, N)):
            g[y][x] = color


def background():
    g = layer()
    floor_row = FLOOR_ROW
    px(g, 0, 0, N, N, ROOM)
    for x in range(8, N, 24):
        px(g, x, 0, 2, floor_row, ROOM_STRIPE)
    # A fixed scatter of stars through the wall, from a hash of the cell, as the run's room has.
    for j in range(0, floor_row - 4, 8):
        for i in range(0, N, 8):
            h = (i * 374761393 + j * 668265263) & 0xFFFFFFFF
            h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
            h = (h ^ (h >> 16)) & 0x7FFFFFFF
            if h % 11 == 0:
                big = 2 if h % 5 == 0 else 1
                px(g, i + h % 6, j + h // 7 % 6, big, big, ROOM_STAR)
    px(g, 0, floor_row, N, 4, FLOOR_EDGE)
    px(g, 0, floor_row + 4, N, N, FLOOR)
    for y in range(floor_row + 12, N, 10):
        px(g, 0, y, N, 2, FLOOR_BOARD)
    return g


CELL = 4
CAT_LEFT = N // 2 - len(SPRITE[0]) * CELL // 2
REACH = -4
# The paws' tops 21 dp above the middle, so their outer corners stay near the safe zone.
PAW_TOP = 66
CAT_TOP = PAW_TOP - (REACH - 1) * CELL - 2
FLOOR_ROW = CAT_TOP + len(SPRITE) * CELL
# Holding it up: the teeth's tips rest on the paws.
CEILING_BOTTOM = PAW_TOP - 11


def foreground():
    g = layer()
    cat = set()
    for a in (-2, 19):
        px(g, CAT_LEFT + a * CELL - 1, CAT_TOP + REACH * CELL, 3 * CELL + 2, (11 - REACH) * CELL, OUTLINE)
        px(g, CAT_LEFT + a * CELL + 1, CAT_TOP + REACH * CELL + 2, 3 * CELL - 2, (11 - REACH) * CELL - 2, FUR)
        px(g, CAT_LEFT + a * CELL - 2, CAT_TOP + (REACH - 1) * CELL + 2, 4 * CELL, 2 * CELL, OUTLINE)
        px(g, CAT_LEFT + a * CELL, CAT_TOP + REACH * CELL, 3 * CELL, CELL, LIGHT)
        px(g, CAT_LEFT + (a + 1) * CELL, CAT_TOP + REACH * CELL + 1, CELL, 2, EAR)
    for r, line in enumerate(SPRITE):
        for c, ch in enumerate(line):
            if ch in SPRITE_COLORS:
                px(g, CAT_LEFT + c * CELL, CAT_TOP + r * CELL, CELL, CELL, SPRITE_COLORS[ch])
    for start in (3, 14):
        px(g, CAT_LEFT + start * CELL, CAT_TOP + 6 * CELL, 3 * CELL, 3 * CELL, EYE)
        px(g, CAT_LEFT + (start + 2) * CELL, CAT_TOP + 6 * CELL, CELL, CELL, GLINT)
    for y in range(N):
        for x in range(N):
            if g[y][x] is not None:
                cat.add((x, y))

    px(g, 0, 0, N, CEILING_BOTTOM, BRICK)
    by, row = CEILING_BOTTOM - 12, 0
    while by > -12:
        px(g, 0, by, N, 2, BRICK_DARK)
        bx = (row % 2) * 16
        while bx < N:
            px(g, bx, by, 2, 12, BRICK_DARK)
            bx += 32
        by -= 12
        row += 1
    px(g, 0, CEILING_BOTTOM - 4, N, 2, BRICK_LIT)
    for tx in range(0, N, 16):
        px(g, tx + 2, CEILING_BOTTOM, 12, 4, BRICK)
        px(g, tx + 4, CEILING_BOTTOM + 4, 8, 4, BRICK)
        px(g, tx + 6, CEILING_BOTTOM + 8, 4, 2, BRICK)
        px(g, tx + 6, CEILING_BOTTOM + 10, 4, 1, BRICK_DARK)
    return g, cat


def runs(g):
    """Each colour's horizontal runs, merged down into rectangles where they repeat row after row."""
    by_color: dict[str, list[tuple[int, int, int, int]]] = {}
    open_rects: dict[tuple[int, int, str], list[int]] = {}
    for y in range(N + 1):
        row_runs = set()
        if y < N:
            x = 0
            while x < N:
                c = g[y][x]
                if c is None:
                    x += 1
                    continue
                s = x
                while x < N and g[y][x] == c:
                    x += 1
                row_runs.add((s, x, c))
        for key in list(open_rects):
            if key not in row_runs:
                s, e, c = key
                top = open_rects.pop(key)[0]
                by_color.setdefault(c, []).append((s, top, e - s, y - top))
        for key in row_runs:
            if key not in open_rects:
                open_rects[key] = [y]
    return by_color


def fmt(v):
    return f"{v / 2:g}"


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
        d = "".join(f"M{fmt(x)},{fmt(y)}h{fmt(w)}v{fmt(h)}h-{fmt(w)}z" for x, y, w, h in rects)
        lines.append("    <path")
        lines.append(f'        android:pathData="{d}"')
        lines.append(f'        android:fillColor="{"#FFFFFFFF" if mono else color}" />')
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def main():
    bg = background()
    fg, cat = foreground()
    reach = max(math.hypot((x + 0.5) / 2 - 54, (y + 0.5) / 2 - 54) for x, y in cat)
    print(f"the cat reaches {reach:.1f} dp from the middle (safe zone 33)")

    mono = [[("#FFFFFFFF" if c is not None and c not in (EYE, GLINT) else None) for c in row] for row in fg]
    res = ROOT / "app/src/main/res/drawable"
    (res / "ic_launcher_background.xml").write_text(
        vector(bg, "The ship's deck: the navy wall, its ribs and stars, and the violet deck plates."), encoding="utf-8")
    (res / "ic_launcher_foreground.xml").write_text(
        vector(fg, "The cheese cat holding the hull-steel ceiling up over its head, in pixels:\n"
                   "     the whole app in one picture. The ceiling runs off the mask on purpose."),
        encoding="utf-8")
    (res / "ic_launcher_monochrome.xml").write_text(
        vector(mono, "Themed icons: the cat and the ceiling as one silhouette, the eyes left out\n"
                     "     so the face still reads in a single colour.", tint="#FFFFFFFF", mono=True),
        encoding="utf-8")

    from PIL import Image, ImageDraw

    size = 512
    img = Image.new("RGB", (size, size))
    draw = ImageDraw.Draw(img)
    for grid in (bg, fg):
        for color, rects in runs(grid).items():
            rgb = "#" + color[3:]
            for x, y, w, h in rects:
                x0, y0 = round(x * size / N), round(y * size / N)
                x1, y1 = round((x + w) * size / N), round((y + h) * size / N)
                if x1 > x0 and y1 > y0:
                    draw.rectangle([x0, y0, x1 - 1, y1 - 1], fill=rgb)
    out = ROOT / "docs/store-assets/icon-512.png"
    img.save(out)
    print(f"wrote the three layers and {out.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
