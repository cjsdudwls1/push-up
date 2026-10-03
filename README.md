# 고양이 지켜줘

An Android fitness game. The phone's camera watches you do pushups; every rep pushes up a ceiling
that is coming down on a cat. Rest, and it keeps coming.

Korean-language, on-device, camera-based. Nothing you record ever leaves the phone. It was called
푸쉬업 RPG until 2026-09-30, when the dungeons were taken out and the cat became the whole app.

---

## How it works

The camera feed goes to MediaPipe's Pose Landmarker, which returns 33 body landmarks per frame.
Those are converted into a single depth value, 0 to 100, which drives the rep counter. Each counted
rep pushes the ceiling back up, further the deeper it went.

### The depth signal

The measurement is

```
h = dot(wrist − shoulder, n̂) / shoulderWidth
```

taken along `n̂`, the normal to the shoulder axis.

Under pinhole projection a vertical world separation images as `(f/Z)·ΔY` and the shoulder width as
`(f/Z)·S`. In a pushup the hands sit beside the chest, at essentially the same distance from the
lens as the shoulders, and the shoulder axis is close to perpendicular to the optical axis — so
dividing one by the other cancels `f` and `Z` exactly. The reading is therefore invariant to how far
away the phone is, which phone it is, where in the frame the user is, and how the phone is rolled.

Normalising by torso length instead — the obvious alternative — is broken in this camera geometry:
with the phone on the floor the torso points away from the lens, so its projected length is both
short *and changes during the rep*. Dividing a moving signal by a moving normaliser destroys it.

### Per-user calibration

There is no universal correct depth. A tall user, a short user, someone on their knees and someone
doing incline pushups all produce different ratios for the same honest effort, so the detector
learns each user's own top and bottom rather than judging against a constant. That is also why it
needs no special cases for exercise variants — they simply shift the learned range.

The hard part is stopping that range collapsing onto whatever the user is doing right now, because
then every rep counts forever and the gauge becomes decoration. Three guards limit it: a minimum
range anchored at the lockout, a cap on how much range fatigue may take (30% of what the user has
themselves demonstrated), and absolute anthropometric clamps. They keep a set that stays well short
from ever counting, not every short rep: `MovementRigTest` pins that thirty pushups stopping 40% of
the way down count zero from every placement on the rig. Reps past about half depth can count in a
first session, while the range is still being learned: on the rig a pushup 60% of the way down does,
from most placements.

### Why the rep counts at the bottom

The strike fires the moment depth crosses the accept line, not on the return to the top, so the hit
lands when the effort is *felt*. Firing at lockout would put the push several hundred milliseconds
after the exertion and the game would read as disconnected from the body.

Farming is prevented structurally rather than by validation: a strike is only reachable from a
re-armed top, so a second rep requires a genuine lockout. A bouncing user gets exactly one rep, and
there is a test for it.

### Why every movement is counted in pushups

A pull-up is not a pushup, and a plank is not counted at all. Rather than a ceiling tuned per
movement, the ceiling's curve is written once, in pushups, and every other movement is converted into
them by how many of it make up the same session: a pull-up, which a person manages about a third as
many of, moves the ceiling about three times as far, and a second of a plank counts like a rep. The
best score is kept per movement for the same reason.

### "Your reps are kept"

A session's reps are banked however it ends — every life lost, the close button, back, or the app
closed mid-set — and the records, the calories, the streak and the gifts are worked out from what was
banked, so nothing is ever taken back.

Nothing counts while tracking is lost. The ceiling does not pause for it, on purpose: a sprint you can
pause by sitting up is not one. What keeps the user from paying for the tracker instead is that a
life that ends while the camera had lost them is given back, once a session.

---

## Project layout

```
core/     Pure Kotlin/JVM. Rep detection, calibration, 고양이 지켜줘 (the ceiling, the session of
          ten lives, the cat), and progression. No Android imports anywhere.
app/      Android. CameraX, MediaPipe, Compose UI, Room, DataStore.
```

Everything that can be a rule rather than a screen lives in `:core`, which is why 260 unit tests can
cover the parts of this app most likely to be wrong without an emulator, a camera, or a human.

## Building

```bash
./gradlew :app:assembleDebug      # needs the Android SDK
./gradlew :core:test              # needs the Android SDK too, because the root build declares AGP
scripts/test-core.sh              # game logic only — no Android SDK required at all
```

`scripts/test-core.sh` mirrors `core/src` into a throwaway standalone Gradle build that resolves
from Maven Central only. It exists because this project was developed in an environment with no
access to Google's Maven, and it turns out to be useful anyway: game-logic regressions surface in
about twenty seconds instead of a full Android build.

CI runs both. See `.github/workflows/ci.yml`.

## Before shipping

`docs/RELEASE.md` has the checklist. The short version: a signing keystore, a Data Safety
declaration, store screenshots taken on a real device, and a content rating. GitHub Pages is on, so
the privacy policy URL already resolves. There is nothing to sell: the app is free for the whole test period.

Three more documents cover the half of this that is not code:

- `docs/STORE_LISTING.md` — the Console fields, written to Play's limits, plus what each of the
  eight screenshots has to do.
- `docs/LAUNCH.md` — the plan for getting the first users, the hypotheses it tests, and the
  numbers that decide whether to build anything else.
- `docs/DECISIONS.md` — the product decisions and why.

`scripts/render-art.sh` renders the share cards and the Play feature graphic to PNG on a plain JVM,
so the artwork can be reviewed without an Android device. Same idea as `test-core.sh`, applied to
pixels.
