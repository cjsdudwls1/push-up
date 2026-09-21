package com.pushuprpg.core

import com.pushuprpg.core.detect.AbandonReason
import com.pushuprpg.core.detect.BodyFrameTracker
import com.pushuprpg.core.detect.ConfidenceEstimator
import com.pushuprpg.core.detect.DepthSignal
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.RepDetectorImpl
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.fixtures.Body3d
import com.pushuprpg.core.fixtures.Body3d.Camera
import com.pushuprpg.core.pose.PoseLandmarks as Lm
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

    private fun run(type: ExerciseType, pose: (Float) -> Body3d.Skeleton, camera: Camera, count: Int = 8): Run {
        val detector = RepDetectorImpl(Exercises.of(type).config)
        val events = mutableListOf<RepEvent>()
        Body3d.trace(pose, camera, count).forEach { events += detector.onFrame(it).events }
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

    // ---------------------------------------------------------------- the shared detector rule

    /**
     * The regression this file exists for. The travel witness used to be referenced from the last
     * armed frame — a third of the way down — instead of from rest, so a witness geared under 1:1
     * with the primary could never travel 30% of the range in the window it was given. A curl from
     * a phone on the floor measured 0.3271 against 0.3277 and counted 1 of 8, the first rep only,
     * which is exactly the "counts one then stalls" the device showed.
     */
    @Test
    fun `a curl from a phone on the floor counts every rep, not just the first`() {
        assertCounts(ExerciseType.CURL, Body3d::curl, floor30, "the floor")
    }

    // ---------------------------------------------------------------- per movement

    @Test
    fun `a curl counts from the floor, from waist height and from chest height`() {
        assertCounts(ExerciseType.CURL, Body3d::curl, Camera.onFloor(2.2f, 45f), "the floor at 45°")
        assertCounts(ExerciseType.CURL, Body3d::curl, waist, "waist height")
        assertCounts(ExerciseType.CURL, Body3d::curl, chest, "chest height")
    }

    @Test
    fun `an overhead press counts from the floor, from waist height and from chest height`() {
        assertCounts(ExerciseType.OVERHEAD_PRESS, Body3d::press, floor30, "the floor")
        assertCounts(ExerciseType.OVERHEAD_PRESS, Body3d::press, waist, "waist height")
        assertCounts(ExerciseType.OVERHEAD_PRESS, Body3d::press, chest, "chest height")
    }

    @Test
    fun `a hinge counts from the floor, from waist height and from chest height`() {
        assertCounts(ExerciseType.HINGE, Body3d::hinge, floor30, "the floor")
        assertCounts(ExerciseType.HINGE, Body3d::hinge, waist, "waist height")
        assertCounts(ExerciseType.HINGE, Body3d::hinge, chest, "chest height")
    }

    @Test
    fun `a dip counts from the floor — where the phone was — as well as from bar height`() {
        assertCounts(ExerciseType.DIP, { Body3d.dip(it) }, Camera.onFloor(2.5f, 35f), "the floor at 35°")
        assertCounts(ExerciseType.DIP, { Body3d.dip(it) }, Camera.onFloor(2.5f, 20f), "the floor at 20°")
        assertCounts(ExerciseType.DIP, { Body3d.dip(it) }, Camera.level(2.5f, 1.2f), "bar height")
        assertCounts(ExerciseType.DIP, { Body3d.dip(it) }, Camera.level(2.5f, 0.9f), "waist height")
    }

    @Test
    fun `a lunge counts from the floor, which is where the placement line sends the phone`() {
        assertCounts(ExerciseType.LUNGE, Body3d::lunge, floor30, "the floor")
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
        val r = run(ExerciseType.LUNGE, Body3d::lunge, waist)
        assertTrue(r.refusals.isEmpty(), "a level camera refused lunges as ${r.refusals.distinct()}")
        assertTrue(r.shallow > 0 || r.reps == 8, "no Shallow event and no reps: a silent zero")
    }

    // ---------------------------------------------------------------- the sign, per movement

    /**
     * The one rule a descriptor author must get right, measured rather than assumed. The hinge had
     * it backwards: wrists from knees reads −0.9 standing and rises to +0.4 at the bottom, against
     * a prior of +1.40 that no camera produces, and never armed.
     */
    @Test
    fun `h falls with effort from every camera, for every standing movement`() {
        val movements = listOf<Pair<ExerciseType, (Float) -> Body3d.Skeleton>>(
            ExerciseType.CURL to Body3d::curl,
            ExerciseType.OVERHEAD_PRESS to Body3d::press,
            ExerciseType.HINGE to Body3d::hinge,
            ExerciseType.LUNGE to Body3d::lunge,
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

    /**
     * At the bottom of a hinge the hips sit level with the shoulders in the image, so a normal
     * re-decided every frame flips on the strike frame. Latched, it holds — and this asserts the
     * bottom reading keeps its sign from chest height, the placement where it used to flip.
     */
    @Test
    fun `the hinge's normal does not flip at the bottom from chest height`() {
        val r = run(ExerciseType.HINGE, Body3d::hinge, chest)
        assertTrue(AbandonReason.INCONSISTENT !in r.refusals, "the normal flipped mid-rep: ${r.refusals.distinct()}")
        assertEquals(8, r.reps)
    }
}
