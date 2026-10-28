# Working in this repo

## Layout

- `core/` — pure Kotlin/JVM. Rep detection, calibration, 고양이 지켜줘 (the ceiling, the session
  of sets, the cat), progression (streaks, calories, records, gifts). **No Android imports,
  ever.** If something can be expressed as a rule rather than a screen, it belongs here.
- `app/` — Android. CameraX, MediaPipe, Compose, Room, DataStore.

## Running the tests

```bash
scripts/test-core.sh          # no Android SDK needed; ~20s
scripts/check-resources.sh    # duplicate/missing/misused <string>s; <1s
scripts/replay-trace.sh f.json  # a run recorded on a phone, replayed rep by rep; no SDK needed
scripts/render-cat.sh out/ [ITEM...]  # the cat and its things as PNG, Compose's own Skia; no SDK needed
scripts/install-android-sdk.sh [dir]  # the SDK :app needs, if Google's servers are reachable
./gradlew :app:assembleDebug  # needs the SDK
```

When a device report says reps are not counting, ask for a trace rather than guessing: debug builds
have 설정 → 테스트 → 동작 기록 남기기 / 방금 한 운동 기록 보내기, which shares the latest run's landmarks as
JSON. `replay-trace.sh` shows which check refused each rep, and the file can become a test. A screen
recording of the app works too: `tools/video_to_trace.py` runs the app's own pose model (full, since 2026-10-01) over it and
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
an `atMs` parameter, and nothing in it is random; anything that ever needs to be takes its
randomness as a parameter. This is what makes a whole session replayable from a recorded landmark
trace in a plain JVM test. Breaking it breaks every
test in the module.

**One component owns whether a rep counts.** The detector decides, using its calibrated range and
its anti-cheat checks, and hands the verdict down as a `RepGrade`. The ceiling maps an accepted rep
to a push and never re-tests the depth (`CeilingSurvival.onRep`). Two components applying their own
thresholds is exactly how a counter once ticked up while nothing happened, and how the cat's first
minute was once all near misses.

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
**A plank may be filmed with the phone on its side**, by the owner's decision (2026-10-02): side on,
the upright picture needed the phone too far away, and the wide one holds the body from 1.5-2 m. For
a plank (and a pushup, below), the run screen turns with the phone whatever the rotation lock says
(`FollowPhoneRotation`; the manifest keeps everything else upright), and the camera's use cases
follow the display's rotation (`CameraPreview`), so the model is always handed an upright picture.
`PlankRigTest` and `PlacementCoachTest` pin it from a phone on its side (`Body3d.Camera.landscape`).
**A pushup may be too**, by the owner's request (2026-10-28), for the same reason; the turning
screens are `FOLLOWS_PHONE_ROTATION`. Nothing in the detector changed for it — the side view and the
head-on reading count on the wide picture as on the upright one — and `MovementRigTest` and
`PlacementCoachTest` pin it from a phone on its side, side on and from the head.

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
(`JointAngleCheck.confirmsFullDepth`: dip, pull-up) is asked — a pushup's elbow and a squat's
knee are fully bent at half depth and cannot tell a half rep from a whole one.

**Real sets are replayed at a phone's frame rates.** `RealTraceTest` plays two sets recorded on a
phone — from nothing, from the stuck calibration the phone was left with, and from a stale profile —
at the recording's rate and at every second and third frame. A detector change that passes the rig
and fails there is not done. New recordings go in `core/src/test/resources/traces/`.

**광각으로 찍기 zooms the camera out; it never changes the detector**, by the owner's request
(2026-10-07, for a small room). It picks the widest camera facing that way (`intrinsicZoomRatio`) and
zooms out — as far as it goes unless a ratio was picked — which CameraX applies before any stream,
so the preview, the analysis and the overlay see the same wider picture. A phone with nothing under 1x films as before; it is off by
default. It is switched from the run screen's top right as well as the settings
(`WideCameraControl`): one tap turns it on or off, by the owner's request (2026-10-09 — a button
that opened a panel with a switch and a slider was one step too many). On is as wide as the camera
goes; there is no slider, and turning it on clears any ratio an earlier build saved.
`WideAngleRigTest` runs every movement at 0.8x, 0.6x and 0.5x from the usual placements
brought closer by the zoom, and a set whose zoom is switched or dragged in a pause mid-set. A wider
lens draws the body smaller from where it stood, and under a movement's `minScale` it is not read —
the coach says COME_CLOSER. Its KDoc lists the limits found.

**Pull-ups and dips are read along the spine** (`AxisSource.TORSO`), not across the shoulder line:
at the distance a pull-up needs, the shoulder line is barely over the minimum scale from the front
and gone from the side. `HangingRigTest` pins front, side, back and diagonal.
The model's spine is longer against the limbs than the rig's (people stand at 0.7-0.8 of the rig's
`h`), so a prior that only fits the rig is a prior that never arms on a phone.
A pull-up counts when the shoulders reach the count line (`ExerciseDescriptor.hangsFromBar`), read
as their own rise in the picture since the top — as if the hands had stayed on the bar, which for a
pull they do. The gap and the elbow close the same whether the body rises to the bar or the hands
come down to the shoulders: arms bent overhead, folding them off the bar, reaching up to the bar and
letting go of it all counted, the last two at the start and end of every set on a phone
(2026-10-04). The strike waits for the shoulders rather than refusing at the line — from behind, the
wrists are lost behind the head at the top and read as falling — and a pull-up arms only with the
hands past the head (`BodyFrameState.farPastShoulders`), since by the sides they read as a dead hang.
With the hands held, the shoulders cannot rise without the elbows bending, so the elbow's 3-D angle
does not veto a pull-up (`RepSignal.jointVetoes`): filmed from the front it is the reading the model
has to guess. `HangingRigTest` pins the fakes from every view and a pull-up counting with the elbows
read straight; `RealTraceTest` the recorded sets (`pullup-reach-and-let-go-*.json.gz`). From the front
it is validated on the rig only: until a real set filmed from the front is in
`core/src/test/resources/traces`, ask the owner for one.
A dip is witnessed by the shoulders too, coming down to the hands on the bars
(`ExerciseDescriptor.shoulderTravel`), and its elbow's 3-D angle may not refuse a rep either: on a
phone with the full model, filmed from the front, the elbow read straighter at the bottom of honest
dips than at the top and refused three of four (`dip-front-full-model.json.gz` in `RealTraceTest`).

**A pushup filmed from the side is read in its side view** (`SideView`), by the owner's request.
Side on the shoulder line projects onto itself and has no scale, so once the shoulders have been
narrow against the torso for a second — never on one frame; from the head the lite model collapses
the shoulder line for single frames — the tracker reads the torso of the better-seen side instead,
and goes back once they open again. A switch starts the range over and the rep arms again. Side on
the range starts from the prior, never the stored profile, and a side-on set does not write the
profile: it is `h` from the head, and seeded from it a side-on set went 깊게 on as few as one rep in
eight and wrote its range over the head's. Side on, the reading is the shoulder-to-wrist distance
over the torso, not the separation along the torso's normal, which tilts with the body and read a
40% knee pushup as 22%; the witness is the shoulders coming down in the picture, because with the
head in line the nose never moves and a wave moves only the hands; and the range is anchored where
the body rests even inside the head-on prior's top band.
`MovementRigTest` pins it from the floor, waist and chest height, from either side and 60-75 degrees
off, on the toes and the knees, at 30 and 15 fps, with waves, holds and a turn mid-set. The view
turns at shoulders 0.55 of the torso, 55-60 degrees off from the floor, where the head-on reading
still counts; at 0.42 the band from 61 to 70 degrees fell between the views and a set there counted
one rep in eight. It is validated on the rig only: until a real side-on set is in
`core/src/test/resources/traces`, ask the owner for one (설정 → 테스트 → 동작 기록 남기기, then 방금 한
운동 기록 보내기).

**A rep's depth is how deep it went, not where it counted.** A rep strikes at the 인정 line on its way
down, so the strike's depth is always about 70. The ceiling's push and the 깊게 tally take each rep's
depth from its deep upgrade and its finished record (`CeilingSurvival.onTick`); read at the strike, a
set with the chest on the floor was once graded barely deep enough. `RealTraceTest` pins it on a real
set.

**Line crossings are placed between frames.** A brisk rep crosses the whole count band in one or
two frames, and timing it frame to frame undercounted the descent by up to a frame — a 1-second
squat read as 760 points a second and was refused as TOO_FAST, more often the higher the frame
rate. `RepDetectorImpl.crossedAt` interpolates; any new timing taken off a line must use it.

**Bodyweight movements only**, by the owner's decision: pushup, squat, plank, pull-up, dip.
The weighted ones were removed, and the lunge with its split-stance check by the owner's decision
(2026-10-09); stored names that point at them read back tolerantly.

**A line drawn for the user to aim at reads its value from `DetectorConfig`.** It must be the same
value the rep counter uses. Never hardcode 70 or 88 in a composable.

**Reps survive a loss.** A session's reps are banked however it ends — the close button, back, or
the screen going (`SurvivalViewModel.save` and `leave`) — and the records, the
calories, the streak and the gifts are all worked out from what was banked.

**Never punish a tracking failure.** Nothing counts while `PoseQuality != OK`, and nothing may be
lost for it either. 고양이 지켜줘's ceiling does not pause for it, by the owner's decision: being in
position only *starts* a set, and after that the ceiling never stops — resting, standing up and
stepping out of view all let it keep coming. It is a sprint, and a sprint you can pause by sitting
up is not one. Do not "fix" it back to pausing. What keeps the rule instead is that there is nothing
to lose: the ceiling winning ends the set and keeps its reps, and the next set comes after the rest.
(A life the tracker lost was once given back once a session; since the lives came back on
2026-10-09 it is not — the set's reps are kept either way.)

A session of 고양이 지켜줘 is **ten lives, one set each, with a rest between them**, by the owner's
decision (2026-10-09, putting back the lives that 2026-10-04 took out). A set is one run of the
ceiling — a life, in `CeilingSurvival`'s words — and the ceiling winning costs one, shown as the row
of hearts under the score and on the rest card (`CatSessionState.livesLeft`). The rest (a minute by
default; the settings offer 90 s and 2 min) runs its length unless the user skips it — the rest card's
one button, added by the owner's decision of 2026-10-02 (`CatSession.skipRest`); there is no extend —
and the next set's ceiling does not come back until it is over or skipped, and then waits, as the
first did, for the user to be in position. Each set is a fresh `CeilingSurvival` and keeps the sprint
rule above. The session is over when the last life is lost, or earlier when the user ends it with the
close button or back; either way it shows its ending. No life is given back for a tracking failure:
the set's reps are kept, which is what the rule above asks. The hearts that float up from the cat on a
push are the cheer, not the lives. The tutorial is one set and no rest (`CatSession.oneSet`). Ten sets
in one session is a full one, which a gift is for (`Gifts.FULL_SESSION_SETS`). `CatSessionTest` pins
all of it.

**The cat is the whole app; the dungeons were taken out** (2026-09-30), by the owner's decision.
The hub's one big button is 고양이 지키기, and it starts the movement it names at once — the picker
is the 운동 바꾸기 link under it. There are no classes, levels, XP, difficulty or dungeon unlocks any
more. What they stored is left where it was and not read, so a downgrade still finds it; rows played
in a dungeon stay in the session table and count everywhere (`SessionRecord.dungeonIndex`), and the
table kept its columns so there is no migration. Whether tracking works on real phones (H2) is read
from `cat_session_finished`, which carries what `run_finished` used to (`TrackingDrops`).

**Each set is played against its own best, with the numbers on screen**, by the owner's decision
(2026-10-01, reversing the earlier "counts only at the end"): 3세트 · 지금 9 / 최고 12 over the
camera, the next set's best on the rest card, and the cat says it once when the best is passed
(`CatCompanion`, `CatLine.RECORD`). A set's best is per movement and per place in the session
(`SetBests`), only grows, and is written when the session is banked. A rep still has no judgment
sound of its own — the ding a counted rep makes is the feedback.

**Growth is shown as things that only go up.** The calories (`Growth.kt`) turn every rep into the
energy it burned — MET × 65 kg × a movement's seconds per rep, an estimate the screen says is one,
since the app does not ask anyone's weight — and name the foods it would take to put back; each
movement's record is its best one go against its first, and each movement keeps its own best score,
by the owner's decision, since a pull-up is not a pushup. None of them ever falls, and a set the
ceiling won or a week away subtracts nothing. The hub's cat greets by how long it has been (`Welcome`), glad
whatever the answer, and a broken streak is never said — by the owner's decision, pointing at what
was lost is how a return becomes a goodbye. The week is one card, its days named 월 to 일 rather
than drawn as dots, with last week summed up at the start of the next (`Weeks.recap`). There are no
push notifications, by the owner's decision.

**The cat's things are gifts it finds, and beside them a shop for 츄르**, by the owner's decisions.
The shop came later (2026-10-01): a set that beats its own best pays 츄르 (`SetOutcome.churu`), and
`Shop` sells things no gift brings — never one a gift brings, and nothing bought is taken back. The
gifts themselves stay as they were: a
reward promised for doing something is one people do it for and stop when it stops, and one that
arrives as a surprise adds to why they came. `Gifts.earned` works each gift out from what only
grows — the runs, the best set of a counted movement, the longest streak, returns after three days
away, records broken, the calories burned — so nothing is stored but which were seen
(`PlayerProgress.giftsSeen`) and what is worn (`AppSettings.catWear`), and no gift is ever taken
back. A run says what it found (`RunGrowth.gifts`, read after the streak is written) and puts it on
where nothing is worn; something the user chose is never taken off for it. 꾸미기 shows what each
found gift was for, after the fact, and nothing about the ones still wrapped. `GiftsTest` pins it.
There are 32 gifts and 20 things on the shelf, more than twice the 15 and 8 there were, by the owner's
request (2026-10-03); runs with something done in them and movements tried are ways to find one too.
A toy (`WearSlot.TOY`) is not worn: it sits on the floor at the cat's left, `TOY_OFFSET` heads from
its middle, and every picture that draws it leaves room there — the one that does not (the name
card's) passes `toy = false`.

The cat's fear, its lines and its sounds are decided by `CatCompanion` in `:core`, from the run's
state and events — the rest's lines included; the screen draws the `CatView` it is given and plays
the sounds, and decides nothing about how the cat feels. It never changes the game —
`CatCompanionTest` pins when it speaks.

## Copy

Korean is the default locale, not a translation. Voice is 해요체 — an encouraging training partner,
never a drill instructor. 실패 does not appear anywhere in the app; a session ends as
고양이를 지켰어요, however it ended.
Every user-visible string lives in `app/src/main/res/values/strings.xml`.

Spoken lines play from `app/src/main/assets/voice/<id>.ogg` when a clip exists for the exact text
(id = first 12 hex of its SHA-1); a line with no clip is not spoken at all — the phone's TTS was
taken out by the owner's decision (2026-10-01), since a machine voice beside the cat's is worse than
silence. `tools/voice_lines.py`
lists every line with its file and delivery, and fails if `GameVoice` speaks a string it does not
list — add new voiced strings there. After rewording a voiced string, `--check` shows the clip that
went stale. Outside audio and its licence go in `docs/AUDIO_CREDITS.md`.

## Balance

The ceiling's curve is written in pushups — `CeilingSurvival`'s header does the arithmetic — and every
other movement is converted into pushups by its `sessionVolumeScale` rather than given a curve of its
own. If a movement keeps the cat alive too easily or too hard, change its `sessionVolumeScale`; if the
whole mode does, the curve's constants.

## Before claiming something works

`:app` compiles here only when the environment's network reaches Google's servers:
`scripts/install-android-sdk.sh` then installs the SDK and points `local.properties` at it, and
`./gradlew :app:assembleDebug` runs (about four minutes the first time; Maven Central sometimes
answers 429 to the first burst, and a second try goes through). Under a policy that blocks Google's
Maven it cannot, and a change to `:app` is unverified until CI runs. Either way a build is a compile
check, not a phone: say which you have — a local build, CI, or neither — rather than implying more.

`scripts/render-art.sh` renders the share cards and the Play feature graphic on a plain JVM against a
Java2D shim, so artwork can be reviewed by looking at it here. Layout, clipping and hierarchy are
real; fonts and hinting are not. `scripts/render-cat.sh` does the same for the cat and each of its
things, and more faithfully: the cat is Compose drawing, and Compose's desktop build draws it through
the same Skia a phone does. A new or changed thing for the cat is not done until its sheet has been
looked at — the wardrobe tile on a light and a dark coat, the hub, and the run's cat in a panic, when
the eyes are widest and the ceiling is on its head. The pictures the screens frame are plain drawing
in `CatPictures.kt` for this reason: the renderer has the UI graphics and nothing above them.
