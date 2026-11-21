#!/usr/bin/env python3
"""A pixel-art person doing each of the app's five movements, for the store pictures and the icon.

One body with fixed segment lengths, posed by its joints and drawn limb by limb at one pixel per art
pixel: far limbs first in a shaded tone, then the torso, the near limbs with a dark line where they
cross the body, the head with a sporty headband, and an outline round the whole. Every pose is the
same person, so the five read as one set. Callers enlarge the sprites without smoothing.

    python3 tools/pixel_people.py OUT.png   # a sheet of every pose, for looking at
"""
from __future__ import annotations

import math
import sys

from PIL import Image

OUTLINE = (0x1A, 0x10, 0x30)
SKIN = (0xF6, 0xC9, 0xA3)
SKIN_FAR = (0xD8, 0x9E, 0x7C)
HAIR = (0x3B, 0x26, 0x1A)
BAND = (0xFF, 0x5A, 0x8C)
SHIRT = (0x34, 0xE7, 0xFF)
SHIRT_FAR = (0x1A, 0xA2, 0xC4)
SHORTS = (0x6E, 0x5E, 0xE8)
SHORTS_FAR = (0x4C, 0x40, 0xB0)
SHOE = (0xF4, 0xF6, 0xFF)
SHOE_FAR = (0xB4, 0xB8, 0xD8)
STEEL = (0xA8, 0xB2, 0xD6)
STEEL_DARK = (0x62, 0x6A, 0x94)

UPPER_ARM, FOREARM, THIGH, SHIN, TORSO = 10.0, 9.0, 13.0, 13.0, 17.0
HEAD_R = 5.7


class proportions:
    """Draws inside the block with other segment lengths: the icon's person is shorter and
    bigger-headed, so the whole of them fits an icon and still reads at 48 dp.

        with proportions(torso=13, thigh=10, shin=10, upper_arm=8, forearm=7, head_r=6.4):
            img, floor = pushup()
    """

    def __init__(self, **kw):
        self.kw = {k.upper(): v for k, v in kw.items()}

    def __enter__(self):
        g = globals()
        self.saved = {k: g[k] for k in self.kw}
        g.update(self.kw)

    def __exit__(self, *exc):
        globals().update(self.saved)


def add(p, q):
    return (p[0] + q[0], p[1] + q[1])


def mul(v, k):
    return (v[0] * k, v[1] * k)


def unit(deg):
    """A direction on screen: 0 is right, 90 is down."""
    r = math.radians(deg)
    return (math.cos(r), math.sin(r))


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def ik(shoulder, hand, a, b, pick):
    """The elbow of a two-segment limb from [shoulder] to [hand]; [pick] chooses between the two."""
    dx, dy = hand[0] - shoulder[0], hand[1] - shoulder[1]
    d = max(1e-6, min(math.hypot(dx, dy), a + b - 1e-6))
    base = math.atan2(dy, dx)
    alpha = math.acos(max(-1.0, min(1.0, (a * a + d * d - b * b) / (2 * a * d))))
    options = [add(shoulder, (a * math.cos(base + s * alpha), a * math.sin(base + s * alpha))) for s in (1, -1)]
    return min(options, key=pick)


class Sprite:
    def __init__(self, w: int, h: int):
        self.w, self.h = w, h
        self.px = [[None] * w for _ in range(h)]
        self.part = [[None] * w for _ in range(h)]
        self.current = None

    def _seg(self, a, b, r, color, ring: bool):
        x0, x1 = int(min(a[0], b[0]) - r - 2), int(max(a[0], b[0]) + r + 2)
        y0, y1 = int(min(a[1], b[1]) - r - 2), int(max(a[1], b[1]) + r + 2)
        ax, ay = a
        vx, vy = b[0] - ax, b[1] - ay
        vv = vx * vx + vy * vy
        for y in range(max(0, y0), min(self.h, y1 + 1)):
            for x in range(max(0, x0), min(self.w, x1 + 1)):
                cx, cy = x + 0.5, y + 0.5
                t = 0.0 if vv == 0 else max(0.0, min(1.0, ((cx - ax) * vx + (cy - ay) * vy) / vv))
                d = math.hypot(cx - (ax + vx * t), cy - (ay + vy * t))
                if d <= r:
                    self.px[y][x] = color
                    self.part[y][x] = self.current
                elif (ring and d <= r + 1.0 and self.px[y][x] not in (None, OUTLINE)
                      and self.part[y][x] != self.current):
                    self.px[y][x] = OUTLINE

    def limb(self, a, b, r, color, ring=False):
        self._seg(a, b, r, color, ring)

    def begin(self, part):
        """Everything drawn until the next begin() is one part: no line is drawn inside it."""
        self.current = part

    def polygon(self, pts, color):
        for y in range(self.h):
            for x in range(self.w):
                if _inside((x + 0.5, y + 0.5), pts):
                    self.px[y][x] = color

    def rect(self, x, y, w, h, color):
        for yy in range(max(0, int(y)), min(self.h, int(y + h))):
            for xx in range(max(0, int(x)), min(self.w, int(x + w))):
                self.px[yy][xx] = color

    def head(self, c, facing: int):
        """[facing] 1 is right, -1 left, 0 the front."""
        self.begin("head")
        cx, cy = c
        for y in range(int(cy - HEAD_R - 2), int(cy + HEAD_R + 2)):
            for x in range(int(cx - HEAD_R - 2), int(cx + HEAD_R + 2)):
                if not (0 <= x < self.w and 0 <= y < self.h):
                    continue
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                if dx * dx + dy * dy > HEAD_R * HEAD_R:
                    continue
                if -2.2 <= dy < -1.0:
                    col = BAND
                elif dy < -2.2:
                    col = HAIR
                elif facing and dx * facing < -1.6 and dy < 2.6:
                    col = HAIR
                elif not facing and abs(dx) > 3.9 and dy < 1.5:
                    col = HAIR
                else:
                    col = SKIN
                self.px[y][x] = col
                self.part[y][x] = "head"
        if facing:
            self._dot(cx + 2.6 * facing, cy + 0.6, OUTLINE)
        else:
            self._dot(cx - 2.0, cy + 0.6, OUTLINE)
            self._dot(cx + 2.0, cy + 0.6, OUTLINE)

    def _dot(self, x, y, color):
        xi, yi = int(x), int(y)
        if 0 <= xi < self.w and 0 <= yi < self.h:
            self.px[yi][xi] = color

    def outline(self):
        filled = [[c is not None for c in row] for row in self.px]
        for y in range(self.h):
            for x in range(self.w):
                if filled[y][x]:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < self.w and 0 <= yy < self.h and filled[yy][xx]:
                        self.px[y][x] = OUTLINE
                        break

    def image(self) -> Image.Image:
        img = Image.new("RGBA", (self.w, self.h))
        for y in range(self.h):
            for x in range(self.w):
                c = self.px[y][x]
                if c is not None:
                    img.putpixel((x, y), c + (255,))
        return img


def _inside(p, pts):
    x, y = p
    inside = False
    j = len(pts) - 1
    for i in range(len(pts)):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi:
            inside = not inside
        j = i
    return inside


# ---------------------------------------------------------------- body parts, side on

def arm(s: Sprite, shoulder, elbow, wrist, hand_to, near: bool, thick: bool = False):
    skin, shirt = (SKIN, SHIRT) if near else (SKIN_FAR, SHIRT_FAR)
    k = 0.35 if thick else 0.0
    s.begin(("arm", near, shoulder))
    s.limb(shoulder, lerp(shoulder, elbow, 0.45), 2.4 + k, shirt, ring=near)
    s.limb(lerp(shoulder, elbow, 0.45), elbow, 2.0 + k, skin, ring=near)
    s.limb(elbow, wrist, 1.85 + k, skin, ring=near)
    s.limb(wrist, hand_to, 1.6 + k, skin, ring=near)


def leg(s: Sprite, hip, knee, ankle, toe, near: bool):
    skin, shorts, shoe = (SKIN, SHORTS, SHOE) if near else (SKIN_FAR, SHORTS_FAR, SHOE_FAR)
    s.begin(("leg", near, hip))
    s.limb(hip, lerp(hip, knee, 0.55), 3.1, shorts, ring=near)
    s.limb(lerp(hip, knee, 0.55), knee, 2.5, skin, ring=near)
    s.limb(knee, ankle, 2.25, skin, ring=near)
    s.limb(ankle, toe, 1.9, shoe, ring=near)


def torso(s: Sprite, shoulder, hip):
    s.begin("torso")
    s.limb(shoulder, lerp(shoulder, hip, 0.78), 4.0, SHIRT)
    s.limb(lerp(shoulder, hip, 0.72), hip, 3.8, SHORTS)


def body_line(shoulder, decline_deg, floor_y, toe_len=4.0):
    """Shoulder to toes in one straight line, falling [decline_deg] towards the feet (to the left)."""
    d = (-math.cos(math.radians(decline_deg)), math.sin(math.radians(decline_deg)))
    hip = add(shoulder, mul(d, TORSO))
    knee = add(hip, mul(d, THIGH))
    ankle = add(knee, mul(d, SHIN))
    toe = add(ankle, mul((math.sin(math.radians(decline_deg)), math.cos(math.radians(decline_deg))), toe_len))
    return d, hip, knee, ankle, toe


def solve_decline(shoulder_y, floor_y):
    """The decline that puts the toes on the floor from a shoulder at [shoulder_y]."""
    lo, hi = 0.0, 80.0
    for _ in range(40):
        mid = (lo + hi) / 2
        _, _, _, _, toe = body_line((0, shoulder_y), mid, floor_y)
        if toe[1] < floor_y - 0.6:
            lo = mid
        else:
            hi = mid
    return (lo + hi) / 2


# ---------------------------------------------------------------- the five movements

def pushup(down: bool = False, mid: bool = True) -> tuple[Image.Image, int]:
    """Side on. [mid] (the default) catches it halfway, elbows bent back: with straight arms it read
    as a high plank in the blind test, and all the way down as a forearm plank."""
    s = Sprite(78, 36)
    floor = s.h - 3
    wrist = (60.0, floor - 1.4)
    height = 9.5 if down else (15.0 if mid else 20.0)
    shoulder = (wrist[0] - (1.0 if down or mid else 0.0), floor - height)
    dec = solve_decline(shoulder[1], floor)
    d, hip, knee, ankle, toe = body_line(shoulder, dec, floor)
    if down or mid:
        elbow = ik(shoulder, wrist, UPPER_ARM, FOREARM, pick=lambda e: e[0] + e[1] * 0.3)
    else:
        elbow = lerp(shoulder, wrist, UPPER_ARM / (UPPER_ARM + FOREARM))
    back = (-1.8, 0.0)
    arm(s, add(shoulder, back), add(elbow, back), add(wrist, back), add(wrist, (1.4, 0)), near=False, thick=True)
    leg(s, add(hip, (0, -0.9)), add(knee, (0.6, -0.9)), add(ankle, (0.8, -0.9)), add(toe, (0.8, -0.9)), near=False)
    torso(s, shoulder, hip)
    leg(s, hip, knee, ankle, toe, near=True)
    s.head(add(shoulder, add(mul(d, -7.6), (0.0, -1.8))), facing=1)
    arm(s, shoulder, elbow, wrist, add(wrist, (3.2, 0.2)), near=True, thick=True)
    s.outline()
    return s.image(), floor


def squat() -> tuple[Image.Image, int]:
    s = Sprite(52, 48)
    floor = s.h - 3
    ankle = (20.0, floor - 2.2)
    knee = add(ankle, mul(unit(-62), SHIN))
    hip = add(knee, mul(unit(190), THIGH))
    shoulder = add(hip, mul(unit(-52), TORSO))
    wrist = add(shoulder, mul(unit(4), UPPER_ARM + FOREARM))
    elbow = lerp(shoulder, wrist, UPPER_ARM / (UPPER_ARM + FOREARM))
    toe = (ankle[0] + 5.0, floor - 1.4)
    heel = (ankle[0] - 1.0, floor - 1.4)
    back = (-1.4, 0.0)
    legback = (-3.2, -0.6)
    arm(s, add(shoulder, back), add(elbow, back), add(wrist, back), add(wrist, (0.6, 0)), near=False)
    leg(s, add(hip, legback), add(knee, legback), add(ankle, (-3.2, 0.0)), add(toe, (-3.2, 0.0)), near=False)
    s.limb(add(heel, (-3.2, 0.0)), add(toe, (-3.2, 0.0)), 1.5, SHOE_FAR)
    torso(s, shoulder, hip)
    leg(s, hip, knee, ankle, toe, near=True)
    s.limb(heel, toe, 1.6, SHOE)
    s.head(add(shoulder, add(mul(unit(-52), 7.4), (1.8, -0.4))), facing=1)
    arm(s, shoulder, elbow, wrist, add(wrist, (2.0, 0)), near=True)
    s.outline()
    return s.image(), floor


def plank() -> tuple[Image.Image, int]:
    s = Sprite(80, 26)
    floor = s.h - 3
    elbow = (56.0, floor - 2.0)
    wrist = (elbow[0] + 10.0, elbow[1])
    shoulder = (elbow[0], elbow[1] - UPPER_ARM)
    dec = solve_decline(shoulder[1], floor)
    d, hip, knee, ankle, toe = body_line(shoulder, dec, floor)
    back = (-1.8, 0.0)
    arm(s, add(shoulder, back), add(elbow, back), add(wrist, back), add(wrist, (1.0, 0)), near=False)
    leg(s, add(hip, (0, -0.8)), add(knee, (0.6, -0.8)), add(ankle, (0.8, -0.8)), add(toe, (0.8, -0.8)), near=False)
    torso(s, shoulder, hip)
    leg(s, hip, knee, ankle, toe, near=True)
    s.head(add(shoulder, add(mul(d, -7.4), (0.0, -1.8))), facing=1)
    arm(s, shoulder, elbow, wrist, add(wrist, (2.4, 0)), near=True)
    s.outline()
    return s.image(), floor


def pullup() -> tuple[Image.Image, int]:
    """From the front, chin over the bar, on a bar between two posts."""
    s = Sprite(56, 74)
    floor = s.h - 3
    cx, bar = s.w / 2, 9.0
    # The station: posts down to the floor, the bar across, drawn first so the hands close over it.
    s.begin("bar")
    s.limb((7.0, bar), (7.0, floor), 1.4, STEEL_DARK)
    s.limb((s.w - 7.0, bar), (s.w - 7.0, floor), 1.4, STEEL_DARK)
    s.limb((5.5, bar), (s.w - 5.5, bar), 1.5, STEEL)
    head_c = (cx, bar - 6.4)
    sy = bar + 3.4
    shoulders = ((cx - 6.5, sy), (cx + 6.5, sy))
    hips_y = sy + TORSO
    # Torso from the front: wider at the shoulders.
    s.begin("torso")
    s.polygon([(cx - 7.5, sy - 1.6), (cx + 7.5, sy - 1.6), (cx + 5.6, hips_y - 3.5), (cx - 5.6, hips_y - 3.5)], SHIRT)
    s.polygon([(cx - 5.8, hips_y - 4.5), (cx + 5.8, hips_y - 4.5), (cx + 6.2, hips_y + 2.0), (cx - 6.2, hips_y + 2.0)], SHORTS)
    for side in (-1, 1):
        s.begin(("leg", side))
        hip = (cx + side * 3.0, hips_y)
        knee = (cx + side * 3.4, hips_y + THIGH - 1.0)
        ankle = (cx + side * 2.2, knee[1] + SHIN - 3.0)
        toe = (ankle[0] + side * 0.2, ankle[1] + 3.0)
        s.limb(hip, lerp(hip, knee, 0.5), 3.0, SHORTS)
        s.limb(lerp(hip, knee, 0.5), knee, 2.5, SKIN)
        s.limb(knee, ankle, 2.2, SKIN)
        s.limb(ankle, toe, 1.9, SHOE)
    # A line down between the legs, so two legs read as two.
    for y in range(int(hips_y + 2), int(hips_y + THIGH + SHIN + 3)):
        if s.px[y][int(cx)] is not None:
            s.px[y][int(cx)] = OUTLINE
    s.head(head_c, facing=0)
    for side in (-1, 1):
        s.begin(("arm", side))
        shoulder = shoulders[0 if side < 0 else 1]
        hand = (cx + side * 14.0, bar)
        # At the top of a pull-up the elbows point down and out, beside the ribs.
        elbow = ik(shoulder, hand, UPPER_ARM, FOREARM, pick=lambda e, side=side: -side * e[0] * 0.5 - e[1])
        s.limb(shoulder, lerp(shoulder, elbow, 0.4), 2.4, SHIRT, ring=True)
        s.limb(lerp(shoulder, elbow, 0.4), elbow, 2.0, SKIN, ring=True)
        s.limb(elbow, hand, 1.85, SKIN, ring=True)
        s.limb(hand, add(hand, (0, -0.3)), 2.3, SKIN, ring=True)
    s.outline()
    return s.image(), floor


def dip() -> tuple[Image.Image, int]:
    """Side on, low between the parallel bars, as the classic pictogram draws it: the torso nearly
    upright, the hand on the bar just behind the hip, the forearm straight up to an elbow that sticks
    out behind the back, the upper arm forward to the shoulder, the knees bent and the feet hanging
    well off the floor. In the blind test of 2026-11-21 an arm laid over the leaning torso read as
    sitting, and a front view with the feet near the floor as standing between two posts."""
    s = Sprite(62, 66)
    floor = s.h - 3
    bar = floor - 33.0
    hip = (30.0, bar + 7.0)
    shoulder = add(hip, mul(unit(-75), TORSO))
    hand = (hip[0] - 7.5, bar - 1.0)
    elbow = ik(shoulder, hand, UPPER_ARM, FOREARM, pick=lambda e: e[0] + e[1])
    knee = add(hip, mul(unit(80), THIGH))
    ankle = add(knee, mul(unit(165), SHIN))
    toe = add(ankle, (-2.6, 2.0))
    # The bars behind the body, on two posts, long enough to show on both sides of it.
    s.begin("bar")
    s.limb((10.0, bar), (10.0, floor), 1.5, STEEL_DARK)
    s.limb((s.w - 10.0, bar), (s.w - 10.0, floor), 1.5, STEEL_DARK)
    s.limb((8.0, bar), (s.w - 8.0, bar), 1.7, STEEL)
    back = (-1.8, 0.0)
    leg(s, add(hip, back), add(knee, back), add(ankle, (0.8, -1.4)), add(toe, (0.8, -1.4)), near=False)
    arm(s, add(shoulder, (1.2, 0.0)), add(elbow, (1.6, -0.6)), add(hand, (1.6, 0.0)), add(hand, (2.6, 0)), near=False, thick=True)
    torso(s, shoulder, hip)
    leg(s, hip, knee, ankle, toe, near=True)
    s.head(add(shoulder, add(mul(unit(-72), 7.9), (0.8, 0.0))), facing=1)
    arm(s, shoulder, elbow, hand, add(hand, (-1.4, 0.2)), near=True, thick=True)
    s.outline()
    return s.image(), floor


POSES = {
    "pushup": lambda: pushup(),
    "pushup_up": lambda: pushup(mid=False),
    "pushup_down": lambda: pushup(True),
    "squat": squat,
    "plank": plank,
    "pullup": pullup,
    "dip": dip,
}

# The five, in the app's own order (the hub's chips), with the names the app uses.
FIVE = [("pushup", "푸쉬업"), ("squat", "스쿼트"), ("plank", "플랭크"), ("pullup", "턱걸이"), ("dip", "딥스")]


def sheet(scale: int = 6, labels: bool = False) -> Image.Image:
    imgs = [(name, POSES[name]()) for name in POSES]
    w = sum(i.width for _, (i, _) in imgs) * scale + 40 * (len(imgs) + 1)
    h = max(i.height for _, (i, _) in imgs) * scale + 80
    out = Image.new("RGBA", (w, h), (0x07, 0x08, 0x1A, 255))
    x = 40
    for name, (img, floor) in imgs:
        big = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
        y = h - 40 - big.height
        out.alpha_composite(big, (x, y))
        x += big.width + 40
    return out


if __name__ == "__main__":
    sheet().save(sys.argv[1] if len(sys.argv) > 1 else "people.png")
