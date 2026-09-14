# 푸쉬업 RPG

An Android fitness RPG. The phone's camera watches you do pushups; every rep is an attack on a
monster. Rest and the boss hits back.

Korean-language, on-device, camera-based. Nothing you record ever leaves the phone.

---

## How it works

The camera feed goes to MediaPipe's Pose Landmarker, which returns 33 body landmarks per frame.
Those are converted into a single depth value, 0 to 100, which drives both the gauge on screen and
the rep counter. Each counted rep resolves into damage against the current enemy.

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
then every rep counts forever and the gauge becomes decoration. Three guards prevent it: a minimum
range anchored at the lockout, a cap on how much range fatigue may take (30% of what the user has
themselves demonstrated), and absolute anthropometric clamps. `RepDetectorTest` pins the degenerate
case directly — thirty half-depth reps still count zero on rep thirty.

### Why the rep counts at the bottom

The strike fires the moment depth crosses the accept line, not on the return to the top, so the hit
lands when the effort is *felt*. Firing at lockout would put the damage number several hundred
milliseconds after the exertion and the game would read as disconnected from the body.

Farming is prevented structurally rather than by validation: a strike is only reachable from a
re-armed top, so a second rep requires a genuine lockout. A bouncing user gets exactly one rep, and
there is a test for it.

### Why enemy HP is never authored

A beginner does 8 pushups and an athlete does 100. Any fixed HP number either walls the first or is
trivial for the second, so content declares a *rep cost* — "this boss should take about fourteen
pushups" — and HP is derived at spawn from the player's measured capacity and the attack of a
player at the dungeon's recommended level. Capacity scaling is deliberately compressive, so a
twelvefold capacity spread becomes a sixfold volume spread; what compression gives away, the
difficulty tier gives back as a choice the player makes.

### "이기든 지든 사라지지 않아요"

The clear screen promises that your reps are recorded whether you win or lose. That is implemented,
not just written: XP is earned per rep rather than on victory, so defeat cannot cost more than the
completion bonus, and losing still banks a fraction of the damage dealt so the retry is measurably
shorter. `BattleEngineTest` asserts it.

Two related rules hold everywhere: the game clock and the boss both pause whenever tracking is lost,
so nobody ever takes damage because the tracker blinked; and a lost run appears in the records
exactly as prominently as a won one.

---

## Project layout

```
core/     Pure Kotlin/JVM. Rep detection, calibration, combat, progression, dungeon content,
          survival mode, and the run orchestration. No Android imports anywhere.
app/      Android. CameraX, MediaPipe, Compose UI, Room, DataStore, Play Billing.
```

Everything that can be a rule rather than a screen lives in `:core`, which is why 88 unit tests can
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

`docs/RELEASE.md` has the checklist. The short version: a signing keystore, the Play Console
subscription products from `BillingProducts.kt`, GitHub Pages switched on so the privacy policy URL
resolves, a Data Safety declaration, store screenshots taken on a real device, and a content rating.

Three more documents cover the half of this that is not code:

- `docs/STORE_LISTING.md` — the Console fields, written to Play's limits, plus what each of the
  eight screenshots has to do.
- `docs/LAUNCH.md` — the plan for getting the first users, the hypotheses it tests, and the
  numbers that decide whether to build anything else.
- `docs/DECISIONS.md` — the product decisions and why.

`scripts/render-art.sh` renders the share cards and the Play feature graphic to PNG on a plain JVM,
so the artwork can be reviewed without an Android device. Same idea as `test-core.sh`, applied to
pixels.
