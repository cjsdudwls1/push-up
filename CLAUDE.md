# Working in this repo

## Layout

- `core/` — pure Kotlin/JVM. Rep detection, calibration, combat, progression, dungeon content,
  survival mode, run orchestration. **No Android imports, ever.** If something can be expressed as
  a rule rather than a screen, it belongs here.
- `app/` — Android. CameraX, MediaPipe, Compose, Room, DataStore, Play Billing.

## Running the tests

```bash
scripts/test-core.sh          # no Android SDK needed; ~20s
scripts/check-resources.sh    # duplicate/missing/misused <string>s; <1s
scripts/replay-trace.sh f.json  # a run recorded on a phone, replayed rep by rep; no SDK needed
./gradlew :app:assembleDebug  # needs the SDK
```

When a device report says reps are not counting, ask for a trace rather than guessing: debug builds
have 설정 → 테스트 → 동작 기록 남기기 / 방금 한 운동 기록 보내기, which shares the latest run's landmarks as
JSON. `replay-trace.sh` shows which check refused each rep, and the file can become a test. A screen
recording of the app works too: `tools/video_to_trace.py` runs the app's own lite model over it and
writes the same JSON. It runs at the recording's frame rate, not the phone's, so replay every second
and third frame as well — a fix that held at 40 fps once failed at 20.

Run `check-resources.sh` before every push that touches `strings.xml`. A duplicated or missing
string is not a Kotlin error, so `test-core.sh` cannot see it and it surfaces four minutes later as
an aapt failure in CI. It has already cost one round.

`scripts/test-core.sh` mirrors `core/src` into a standalone Maven-Central-only Gradle build. Use it
constantly — it is the only fast feedback loop in the project. `./gradlew :core:test` does *not*
work without the Android SDK, because the root build declares AGP and Gradle configures every
project.

## Rules that are load-bearing

**`:core` reads no clock and owns no randomness.** All time arrives as `PoseFrame.timestampMs` or
an `atMs` parameter; randomness comes through the injected `Rng`. This is what makes a whole
session replayable from a recorded landmark trace in a plain JVM test. Breaking it breaks every
test in the module.

**One component owns whether a rep counts.** The detector decides, using its calibrated range and
its anti-cheat checks, and hands the verdict down as a `RepGrade`. Combat maps an accepted rep to
damage and never re-tests the depth. Two components applying their own thresholds is exactly how
the counter ended up incrementing without dealing damage.

**The overlay draws only what `:core` gives it.** `RenderSkeleton` contains bones whose endpoints
are both confidently visible, and joints that anchor a drawn bone. The renderer has no path to a
stray dot because it is never handed one. Do not add confidence checks in the UI; fix them in
`SkeletonBuilder`.

**A descriptor is validated on the rig, not on a fixture drawn to match it.** `Body3d` (core tests)
is one skeleton with real segment lengths, posed by joint angles and projected through a pinhole
camera; the primary signal, the joint check and the travel witness all come from that one body.
Run a new or changed movement through `MovementRigTest` from the floor, waist height and chest
height before believing its priors. Four descriptors shipped with the wrong sign or a witness that
could not move, and every hand-placed fixture agreed with them.

**The placement coach never contradicts the detector.** `PlacementCoach` says 좋아요 only when the
detector's own quality is OK and it has armed; every other line is geometry measured on the frame
the detector refused. Placement advice lives there and in one line per movement on the picker —
not in paragraphs.

**A plank is read from the model's 3-D skeleton, not the picture.** From the head, all fours,
a knee plank and plain standing all line up down the image the way a plank does, and all of them
used to hold; side on, the picture-based frame collapsed and a real plank never held.
`PlankRigTest` pins every pose from the front, side, back and diagonal. World landmarks are
camera-aligned, so the phone's tilt is in them; `Body3d` reproduces that. The legs are taken from the
skeleton whether the camera sees them or not — from the head they are behind the body, from a phone
close by the side past the edge of the picture — and requiring them is why a real plank never held
on a phone. Unseen legs still gate the pose (a folded knee is not a plank) but are left out of the
form score, and the placement coach does not ask for them. With the feet past the edge and the
knees in the picture, the knees' height off the floor (elbow to shoulder is up; the lower of elbow and
wrist is the floor) can pass the legs instead of the model's guess at the shins, which read a real
side-on plank as bent. The arm check that tells a plank from standing is not asked of a torso past
level: from the head on the forearms the model put the elbow behind the shoulder half the time.

**A single frame never moves the calibration.** The lite model misplaces a landmark for a frame —
a shoulder on the neck, the shoulders swapped — often enough to matter. A scale jump is a new subject
only once it has held (`BodyFrameTracker.SWITCH_CONFIRM_FRAMES`/`_MS`), a short shoulder line is a
turned torso only on its second frame, and the rest anchor is the median of a held stretch, never the
largest value seen. One stray frame once set a pushup's top at 2.38 against a real 1.5-1.8, and the
set stopped at one rep.

**A range whose top the body never reaches must be able to recover.** Arming needs the top band and
only a completed rep teaches the calibrator, so a top set too high — by a stray frame, a stale profile
or a prior that does not fit — is a trap that holds while the tracker says OK. Two ways out, both
guarded by the working joint's own 3-D angle so half reps cannot use them: a joint-confirmed lockout
completes the rep and re-arms, and the arming watchdog moves the top to where the user actually
turns around after two swings in `h` short of the band. The watchdog follows `h`, not the gauge;
read through the count line of the very range that was wrong, it missed at a phone's frame rate.
The bottom has the same trap and the same guard: two Shallow reps with the working joint fully bent
move the bottom up to them, and a rep refused as too fast that went all the way may *widen* the
range, never narrow it. Only a joint whose angle keeps closing to the bottom
(`JointAngleCheck.confirmsFullDepth`: lunge, dip, pull-up) is asked — a pushup's elbow and a squat's
knee are fully bent at half depth and cannot tell a half rep from a whole one.

**Real sets are replayed at a phone's frame rates.** `RealTraceTest` plays two sets recorded on a
phone — from nothing, from the stuck calibration the phone was left with, and from a stale profile —
at the recording's rate and at every second and third frame. A detector change that passes the rig
and fails there is not done. New recordings go in `core/src/test/resources/traces/`.

**Pull-ups, dips and lunges are read along the spine** (`AxisSource.TORSO`), not across the
shoulder line: at the distance a pull-up needs, the shoulder line is barely over the minimum scale
from the front and gone from the side, and a lunge filmed at an angle — as people film it — was too
narrow to measure at all. `HangingRigTest` and `LungeRigTest` pin front, side, back and diagonal.
The model's spine is longer against the limbs than the rig's (people stand at 0.7-0.8 of the rig's
`h`), so a prior that only fits the rig is a prior that never arms on a phone.

**A rep's depth is how deep it went, not where it counted.** A rep strikes at the 인정 line on its way
down, so the strike's depth is always about 70. The stars and the 깊게 tally take each rep's depth
from its deep upgrade and its finished record; averaged at the strike, a set with the chest on the
floor graded one star and the result screen said 다음엔 더 깊게.

**A lunge needs a split stance** (`StanceCheck`, from world landmarks): the depth signal reads a
squat exactly like a lunge. "Forward" is square to the hip line and the spine, never "horizontal" —
world landmarks are in the camera's tilted frame. The game does not tell the user which leg to put
forward, by the owner's decision: either leg counts, and the same leg twice is not remarked on.

**A class is a way of training, and it decides what a rep is worth**, by the owner's decision: two
classes, no more. 기사 is 근비대 — a rep whose lowering (top band to the 깊게 line) takes at least
`ClassStyle.SLOW_LOWERING_MS` and reaches 깊게 is a whole rep off the monster, anything else half.
궁수 is 수행능력 — a rep inside `ClassStyle.briskCycleMs` of the last, or the first of a set, is whole,
anything else half. The detector still decides whether a rep counts; the class only prices it, from
the detector's own timings. Both thresholds are measured on the rig (`ClassStyleRigTest`), and the
brisk pace must stay well above what the detector can count (`FastRepRigTest`). 법사 was folded
into 기사; a stored `MAGE` reads back as the default class.

**Line crossings are placed between frames.** A brisk rep crosses the whole count band in one or
two frames, and timing it frame to frame undercounted the descent by up to a frame — a 1-second
squat read as 760 points a second and was refused as TOO_FAST, more often the higher the frame
rate. `RepDetectorImpl.crossedAt` interpolates; any new timing taken off a line must use it.

**Bodyweight movements only**, by the owner's decision: pushup, squat, plank, pull-up, lunge, dip.
The weighted ones were removed; stored names that point at them read back tolerantly.

**The gauge reads its thresholds from `DetectorConfig`.** The line the user aims at must be the
same value the rep counter uses. Never hardcode 70 or 88 in a composable.

**Reps survive a loss.** XP is earned per rep rather than on victory; progress is written whether
the run was cleared or not. `BattleEngineTest` asserts it. The clear screen makes this promise in
Korean and the code has to keep it.

**Never punish a tracking failure.** When `PoseQuality != OK` the game clock stops and the boss
stops attacking. A user must never lose health because the tracker blinked.

고냥이 지켜줘 is the one deliberate exception, by the owner's decision: being in position only
*starts* the run, and after that the ceiling never stops — resting, standing up and stepping out of
view all let it keep coming. It is a sprint, and a sprint you can pause by sitting up is not one. Do
not "fix" it back to pausing.

The cat's fear, its lines and its sounds are decided by `CatCompanion` in `:core`, from the run's
state and events; the screen draws the `CatView` it is given and plays the sounds, and decides
nothing about how the cat feels. It never changes the game — `CatCompanionTest` pins when it speaks.

## Copy

Korean is the default locale, not a translation. Voice is 해요체 — an encouraging training partner,
never a drill instructor. 실패 does not appear anywhere in the app; a lost run is `다음엔 잡아요`.
Every user-visible string lives in `app/src/main/res/values/strings.xml`.

Spoken lines play from `app/src/main/assets/voice/<id>.ogg` when a clip exists for the exact text
(id = first 12 hex of its SHA-1) and fall back to the phone's TTS otherwise. `tools/voice_lines.py`
lists every line with its file and delivery, and fails if `GameVoice` speaks a string it does not
list — add new voiced strings there. After rewording a voiced string, `--check` shows the clip that
went stale. Outside audio and its licence go in `docs/AUDIO_CREDITS.md`.

## Balance

Enemy HP is never authored. Content declares a *rep cost* and HP is derived at spawn: the rep cost
× difficulty × the movement's session volume × the class's `repCostScale` (fewer reps for a 기사,
more for a 궁수; a hold is the same for both). If a fight feels wrong, change its `standardRepCost`
or a class's `repCostScale` — never an HP number, because there isn't one. Every screen that quotes
a count passes the player's class to `Dungeon.repCost`, or it quotes a number the run won't ask for.

## Before claiming something works

`:app` cannot be compiled in this environment — Google's Maven is unreachable here — so any change
to it is unverified until CI runs. CI does build it and is green; that is the verification, not a
local check. Say which of the two you have, rather than implying otherwise.

`scripts/render-art.sh` is the exception that proves it: it renders the share cards and the Play
feature graphic on a plain JVM against a Java2D shim, so artwork can be reviewed by looking at it
here. Layout, clipping and hierarchy are real; fonts and hinting are not.
