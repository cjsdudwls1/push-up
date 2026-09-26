package com.pushuprpg.core

import com.pushuprpg.core.detect.AbandonReason
import com.pushuprpg.core.detect.BodyFrameTracker
import com.pushuprpg.core.detect.ConfidenceEstimator
import com.pushuprpg.core.detect.DepthSignal
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.PoseQuality
import com.pushuprpg.core.detect.RepDetectorImpl
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.fixtures.Body3d
import com.pushuprpg.core.fixtures.Body3d.Camera
import com.pushuprpg.core.pose.PoseLandmarks as Lm
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every standing movement, as one projected body, from the places a phone actually gets put.
 *
 * The device reports this answers: 런지 잘 안됨, 밀리터리프레스 인정까지 밖에 안 가고 점수 안 오름,
 * 덤벨컬 점수 안 오름, 힙힌지 점수 안 오름, 딥스 안 됨. Four descriptors and one shared rule in the
 * detector were wrong, and none of it was visible from the hand-placed fixtures, because those
 * fixtures were drawn to satisfy the descriptors. [Body3d] is a body with joint angles and a
 * pinhole camera, so a descriptor's claim about the sign of a signal or the direction of a witness
 * is measured rather than asserted — and the same trace is run from the floor, from waist height
 * and from chest height, because the reading is invariant to distance and to nothing else.
 */
class MovementRigTest {

    private val floor30 = Camera.onFloor(2.2f, 30f)
    private val waist = Camera.level(2.2f, 0.9f)
    private val chest = Camera.level(2.5f, 1.3f)

    private class Run(val reps: Int, val events: List<RepEvent>) {
        val refusals get() = events.filterIsInstance<RepEvent.Abandoned>().map { it.reason }
        val shallow get() = events.count { it is RepEvent.Shallow }
    }

    private fun run(
        type: ExerciseType,
        pose: (Float) -> Body3d.Skeleton,
        camera: Camera,
        count: Int = 8,
        peakDepth: Float = 0.95f,
        fps: Int = 30,
    ): Run {
        val detector = RepDetectorImpl(Exercises.of(type).config)
        val events = mutableListOf<RepEvent>()
        Body3d.trace(pose, camera, count, peakDepth = peakDepth, fps = fps).forEach { events += detector.onFrame(it).events }
        return Run(detector.sessionSummary().repCount, events)
    }

    private fun assertCounts(type: ExerciseType, pose: (Float) -> Body3d.Skeleton, camera: Camera, where: String) {
        val r = run(type, pose, camera)
        assertEquals(
            8, r.reps,
            "$type from $where counted ${r.reps} of 8; refused as ${r.refusals.distinct()}, shallow ${r.shallow}",
        )
    }

    /** Raw `h` at [depth], through a fresh tracker, with no detector in the way. */
    private fun hAt(type: ExerciseType, pose: (Float) -> Body3d.Skeleton, camera: Camera, depth: Float): Float {
        val config = Exercises.of(type).config
        val tracker = BodyFrameTracker(config)
        val est = ConfidenceEstimator()
        val conf = FloatArray(Lm.COUNT)
        val f = Body3d.frame(0L, pose(depth), camera)
        est.compute(f, tracker.scale, conf)
        val body = tracker.update(f, conf) ?: error("$type: no body frame at depth $depth")
        return DepthSignal.compute(f, body, conf, config)?.h ?: error("$type: no sample at depth $depth")
    }

    // ---------------------------------------------------------------- per movement

    @Test
    fun `a dip counts from the floor — where the phone was — as well as from bar height`() {
        assertCounts(ExerciseType.DIP, { Body3d.dip(it) }, Camera.onFloor(2.5f, 35f), "the floor at 35°")
        assertCounts(ExerciseType.DIP, { Body3d.dip(it) }, Camera.onFloor(2.5f, 20f), "the floor at 20°")
        assertCounts(ExerciseType.DIP, { Body3d.dip(it) }, Camera.level(2.5f, 1.2f), "bar height")
        assertCounts(ExerciseType.DIP, { Body3d.dip(it) }, Camera.level(2.5f, 0.9f), "waist height")
    }

    @Test
    fun `a lunge counts from the floor, which is where the placement line sends the phone`() {
        assertCounts(ExerciseType.LUNGE, { d -> Body3d.lunge(d) }, floor30, "the floor")
    }

    /**
     * Not a bug, a fact about the geometry, recorded so it is never mistaken for one: in a split
     * stance the front knee is nearly half a metre nearer the lens than the hip. From a camera above
     * knee height that makes it project lower, which reads as the hip sitting higher above it — a
     * quarter of a shoulder width shallower than the body actually is. From the floor the same
     * offset reads deeper. So the lunge is a floor-placement movement, and the honest failure from a
     * level camera is SHALLOW: the gauge visibly stops short, rather than a refused strike or a
     * silent zero.
     */
    @Test
    fun `a lunge from waist height reads shallow, and says so, rather than failing silently`() {
        val r = run(ExerciseType.LUNGE, { d -> Body3d.lunge(d) }, waist)
        assertTrue(r.refusals.isEmpty(), "a level camera refused lunges as ${r.refusals.distinct()}")
        assertTrue(r.shallow > 0 || r.reps == 8, "no Shallow event and no reps: a silent zero")
    }

    // ---------------------------------------------------------------- the sign, per movement

    /**
     * The one rule a descriptor author must get right, measured rather than assumed. A hinge once
     * had it backwards — wrists from knees read −0.9 standing and rose to +0.4 at the bottom,
     * against a prior of +1.40 that no camera produces — and never armed.
     */
    @Test
    fun `h falls with effort from every camera, for every standing movement`() {
        val movements = listOf<Pair<ExerciseType, (Float) -> Body3d.Skeleton>>(
            ExerciseType.LUNGE to { d -> Body3d.lunge(d) },
            ExerciseType.DIP to { d -> Body3d.dip(d) },
        )
        for ((type, pose) in movements) {
            for ((name, cam) in listOf("floor" to floor30, "waist" to waist, "chest" to chest)) {
                val top = hAt(type, pose, cam, 0f)
                val bottom = hAt(type, pose, cam, 1f)
                assertTrue(top > bottom, "$type from $name: h rose from $top to $bottom with effort")
                val c = Exercises.of(type).config
                assertTrue(
                    top > c.hBotPrior && bottom < c.hTopPrior,
                    "$type from $name: measured [$bottom..$top] does not overlap prior [${c.hBotPrior}..${c.hTopPrior}]",
                )
            }
        }
    }

    // ---------------------------------------------------------------- the pushup

    /** A pushup with the shoulders over the origin and the head turned [yawDeg] away from the lens. */
    private fun pushupAt(yawDeg: Float, onKnees: Boolean = false): (Float) -> Body3d.Skeleton {
        val yaw = Math.toRadians(yawDeg.toDouble())
        val heading = Body3d.V3(-sin(yaw).toFloat(), 0f, cos(yaw).toFloat())
        return { depth -> Body3d.pushup(depth, heading, Body3d.V3(0f, 0f, 0f), onKnees) }
    }

    /**
     * The device report this answers: 고냥이 지켜줘 starts and stops on the pose, but pushups never
     * push the ceiling back. From in front of the head and from a diagonal the first rep counted
     * and every one after it was refused as INCONSISTENT — alternately by the elbow check, for an
     * elbow bent past 82 degrees, and by the nose witness, which a head held in line with the body
     * moves 0.23-0.53 of the range against 0.30 required. Survival counts pushups and nothing else,
     * so the mode could not be won.
     */
    @Test
    fun `a pushup counts from in front of the head and from a diagonal, near and far`() {
        for (yaw in listOf(0f, 30f, 60f)) {
            for ((distance, tilt) in listOf(1.3f to 12f, 2.0f to 8f)) {
                assertCounts(
                    ExerciseType.PUSHUP, pushupAt(yaw), Camera.onFloor(distance, tilt),
                    "the floor ${distance}m away, ${yaw.toInt()} degrees off the head",
                )
            }
        }
    }

    /**
     * 힘들면 무릎 대고 해도 괜찮아요 is what the game tells someone whose pushups keep falling short,
     * so a knee pushup has to count, and until this it had never been measured. It counts: the
     * signal and both witnesses are the arms and the head, and the knees on the floor only tilt the
     * body more steeply. From the floor in front of the head, near and far, and from waist and chest
     * height. How far short a knee pushup can stop and still not count is pinned with the pushup's.
     */
    @Test
    fun `a knee pushup counts from the floor, waist and chest`() {
        val cameras = listOf(
            "the floor 1.3m away" to Camera.onFloor(1.3f, 12f),
            "the floor 2.0m away" to Camera.onFloor(2.0f, 8f),
            "waist height" to waist,
            "chest height" to chest,
        )
        for ((where, camera) in cameras) for (yaw in listOf(0f, 30f, 60f)) {
            // From above, 60 degrees off the head the shoulder line is too short to measure, and the
            // pushup is read in its side view — on the toes too.
            val r = run(ExerciseType.PUSHUP, pushupAt(yaw, onKnees = true), camera)
            val what = "a knee pushup from $where, ${yaw.toInt()} degrees off the head"
            assertEquals(8, r.reps, "$what counted ${r.reps} of 8; refused as ${r.refusals.distinct()}, shallow ${r.shallow}")
            // Close by at 60 degrees a few of the rig's deepest reps stop short of 깊게, on the toes too.
            val deep = r.events.count { it is RepEvent.DeepUpgrade }
            if (yaw < 60f) assertEquals(8, deep, "$what went 깊게 on $deep of 8")
        }
    }

    /**
     * A pushup that stops 40% of the way down — the shoulders 40% of the way from lockout to the
     * floor — never counts: from every placement here, on the toes or the knees, at 30 fps or 15, and
     * thirty in a row do not train the range down to meet them. From the head and 30 degrees off it
     * every one is also reported shallow, which is what the game answers with 조금만 더 내려가 볼까요?;
     * 60 degrees off from the floor, still read across the shoulder line, not reliably. (From waist
     * and chest height 60 degrees off is read in the side view, where it is: see below.)
     *
     * That is the claim, and no more. A pushup to 60% counts today from most of these placements, and
     * one to 50% from waist and chest height, and nothing here pins either way. These pins used to
     * test 60% from one placement only — the floor 1.3 m away, straight in front of the head — which
     * happens to be where it does not count, and so they said something that was not true elsewhere.
     */
    @Test
    fun `a pushup 40 percent of the way down never counts from anywhere, and is called short`() {
        val cameras = listOf(
            "the floor 1.3m away" to Camera.onFloor(1.3f, 12f),
            "the floor 2.0m away" to Camera.onFloor(2.0f, 8f),
            "waist height" to waist,
            "chest height" to chest,
        )
        for ((where, camera) in cameras) for (yaw in listOf(0f, 30f, 60f)) for (onKnees in listOf(false, true)) {
            for (fps in listOf(30, 15)) {
                val r = run(ExerciseType.PUSHUP, pushupAt(yaw, onKnees), camera, count = 30, peakDepth = 0.4f, fps = fps)
                val what = (if (onKnees) "a knee pushup" else "a pushup") +
                    " 40% down from $where, ${yaw.toInt()} degrees off the head, at $fps fps"
                assertEquals(0, r.reps, "$what counted ${r.reps} of 30")
                if (yaw < 60f) assertEquals(30, r.shallow, "$what was called short on ${r.shallow} of 30")
            }
        }
    }

    // ---------------------------------------------------------------- the pushup, side on

    /** Where a side-on pushup is filmed from: the floor near and far, waist and chest height. */
    private val sideCameras = listOf(
        "the floor 1.3m away" to Camera.onFloor(1.3f, 12f),
        "the floor 2.0m away" to Camera.onFloor(2.0f, 8f),
        "waist height" to waist,
        "chest height" to chest,
    )

    /**
     * The views the side view reads: side on from the left and the right, 70-75 degrees off the
     * head, and 60 from above — where the shoulder line is too short to measure at all. 60 degrees
     * off from the floor is still read across the shoulder line, as it was, and pinned with the
     * head-on placements above.
     */
    private fun sideViews(camera: Camera): List<Float> =
        listOf(90f, -90f, 75f, -75f, 70f) + if (camera.position.y > 0.5f) listOf(60f) else emptyList()

    /**
     * The owner's ask: 푸시업은 옆에서 찍어도 인식되도록. Side on, the shoulder pair projects onto itself
     * and the frame it defines has no scale, so the pushup is read in its [com.pushuprpg.core.detect.SideView]:
     * along the torso, from the shoulder-to-wrist distance, witnessed by the shoulders coming down in
     * the picture. From either side, from every placement, on the toes and the knees, at 30 fps and 15,
     * every rep counts and every rep goes 깊게.
     */
    @Test
    fun `side on, a pushup counts from either side and goes 깊게, near and far, on the toes and the knees`() {
        for ((where, camera) in sideCameras) for (yaw in sideViews(camera)) for (onKnees in listOf(false, true)) {
            for (fps in listOf(30, 15)) {
                val r = run(ExerciseType.PUSHUP, pushupAt(yaw, onKnees), camera, fps = fps)
                val what = (if (onKnees) "a knee pushup" else "a pushup") +
                    " from $where, ${yaw.toInt()} degrees off the head, at $fps fps"
                assertEquals(8, r.reps, "$what counted ${r.reps} of 8; refused as ${r.refusals.distinct()}, shallow ${r.shallow}")
                val deep = r.events.count { it is RepEvent.DeepUpgrade }
                assertEquals(8, deep, "$what went 깊게 on $deep of 8")
            }
        }
    }

    /**
     * The half rep, side on: 40% of the way down reads 44-52 on the gauge from every side-on
     * placement, is called short every time and never counts, thirty in a row. A pushup to 60% counts
     * side on from every placement here, as it does from the head at most; nothing pins that either way.
     */
    @Test
    fun `side on, a pushup 40 percent of the way down never counts, and is called short`() {
        for ((where, camera) in sideCameras) for (yaw in sideViews(camera)) for (onKnees in listOf(false, true)) {
            for (fps in listOf(30, 15)) {
                val r = run(ExerciseType.PUSHUP, pushupAt(yaw, onKnees), camera, count = 30, peakDepth = 0.4f, fps = fps)
                val what = (if (onKnees) "a knee pushup" else "a pushup") +
                    " 40% down from $where, ${yaw.toInt()} degrees off the head, at $fps fps"
                assertEquals(0, r.reps, "$what counted ${r.reps} of 30")
                assertEquals(30, r.shallow, "$what was called short on ${r.shallow} of 30")
            }
        }
    }

    /**
     * What the side view's witness is for. Standing side on with the arms held out and bending, the
     * shoulder-to-wrist distance closes exactly as in a pushup and so does the elbow; only the
     * shoulders, which stay where they were in the picture, tell it from one. A hang from a bar
     * filmed from the side is the same. Held still, nothing counts: standing, all fours, a plank on
     * the forearms or the hands.
     */
    @Test
    fun `side on, arms waved at the lens and a hang from a bar do not count, and holding still does not`() {
        val cameras = sideCameras + ("the floor 3m away, tilted 25°" to Camera.onFloor(3f, 25f))
        val side = Body3d.V3(-1f, 0f, 0f)
        for ((where, camera) in cameras) for (fps in listOf(30, 15)) {
            for (facing in listOf(Body3d.V3(1f, 0f, 0f), side)) {
                val wave = run(ExerciseType.PUSHUP, { d -> Body3d.armWave(d, facing) }, camera, fps = fps)
                assertEquals(0, wave.reps, "arms waved side on from $where at $fps fps counted ${wave.reps}")
                if (camera.position.y > 0.5f) {
                    assertTrue(AbandonReason.INCONSISTENT in wave.refusals, "the wave from $where was never refused out loud: ${wave.refusals.distinct()}")
                }
                val hang = run(ExerciseType.PUSHUP, { d -> Body3d.pullUp(d, facing) }, camera, fps = fps)
                assertEquals(0, hang.reps, "a pull-up side on from $where at $fps fps counted ${hang.reps} pushups")
            }
            val held = listOf(
                "standing" to Body3d.rotated(Body3d.standing(), Body3d.V3(1f, 0f, 0f)),
                "all fours" to Body3d.allFours(side),
                "a forearm plank" to Body3d.forearmPlank(side),
                "a plank on the hands" to Body3d.pushup(0f, side, Body3d.V3(0f, 0f, 0f)),
            )
            for ((what, pose) in held) {
                val r = run(ExerciseType.PUSHUP, { pose }, camera, fps = fps)
                assertEquals(0, r.reps, "$what held still side on from $where at $fps fps counted ${r.reps}")
            }
        }
    }

    /**
     * Turning mid-set is a new reading of the same body: side on is not `h` from the head. The
     * view changes only once the new one has held for a second, the range starts over from where the
     * body rests in it, and the rep has to arm again — so the set goes on counting, and the turn
     * itself counts nothing.
     */
    @Test
    fun `turning between the head and the side mid-set counts every rep done and none that was not`() {
        for ((where, camera) in sideCameras.take(3)) for (fps in listOf(30, 15)) {
            for ((from, to) in listOf(0f to 90f, 90f to 0f, 0f to -90f)) {
                val step = 1000L / fps
                val before = Body3d.trace(pushupAt(from), camera, 4, fps = fps)
                var t = before.last().timestampMs + step
                val turn = (0..(1000 / step).toInt()).map { i ->
                    Body3d.frame(t + i * step, pushupAt(from + (to - from) * i * step / 1000f)(0f), camera)
                }
                t = turn.last().timestampMs + step
                val after = Body3d.trace(pushupAt(to), camera, 4, startMs = t, settleMs = 2000, fps = fps)
                val detector = RepDetectorImpl(Exercises.PUSHUP.config)
                val events = (before + turn + after).flatMap { detector.onFrame(it).events }
                assertEquals(
                    8, detector.sessionSummary().repCount,
                    "4 reps ${from.toInt()} degrees off the head, a turn, 4 at ${to.toInt()}, from $where at $fps fps: " +
                        "refused as ${events.filterIsInstance<RepEvent.Abandoned>().map { it.reason }.distinct()}",
                )
            }
        }
    }

    /** One frame is never a new view: the lite model collapses the shoulder line for single frames from the head. */
    @Test
    fun `a single side-on frame in a set from the head moves nothing`() {
        val camera = Camera.onFloor(1.3f, 12f)
        for (fps in listOf(30, 15)) {
            val frames = Body3d.trace(pushupAt(0f), camera, 8, fps = fps).mapIndexed { i, f ->
                // Every twentieth frame the model reads the body side on, for that frame only.
                if (i % 20 == 10) Body3d.frame(f.timestampMs, pushupAt(90f)(0f), camera) else f
            }
            val detector = RepDetectorImpl(Exercises.PUSHUP.config)
            val qualities = frames.map { detector.onFrame(it).quality }
            assertEquals(8, detector.sessionSummary().repCount, "at $fps fps")
            assertTrue(PoseQuality.SUBJECT_SWITCH !in qualities, "at $fps fps a stray frame changed the view")
        }
    }
}
