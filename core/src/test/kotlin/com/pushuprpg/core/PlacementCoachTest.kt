package com.pushuprpg.core

import com.pushuprpg.core.detect.DetectorFactory
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Placement
import com.pushuprpg.core.detect.PlacementAdvice
import com.pushuprpg.core.detect.PlacementCoach
import com.pushuprpg.core.fixtures.Body3d
import com.pushuprpg.core.fixtures.Body3d.Camera
import com.pushuprpg.core.pose.PoseFrame
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The live placement coach, on the projected rig: every piece of advice is checked against a body
 * and a camera that actually produce it, and READY against a detector that actually counts.
 */
class PlacementCoachTest {

    /** A body lying head-first toward the lens ([yawDeg] 0) or across it (90). */
    private fun floorBody(yawDeg: Float): (Float) -> Body3d.Skeleton {
        val yaw = Math.toRadians(yawDeg.toDouble())
        val heading = Body3d.V3(-sin(yaw).toFloat(), 0f, cos(yaw).toFloat())
        return { depth -> Body3d.pushup(depth, heading, Body3d.V3(0f, 0f, 0f)) }
    }

    /** Everything the coach said, in order, over a trace of [reps] reps; and whether it counted. */
    private class Heard(val said: List<PlacementAdvice?>, val reps: Int) {
        val last get() = said.last()
        val distinct get() = said.distinct()
    }

    private fun listen(type: ExerciseType, frames: List<PoseFrame>): Heard {
        val detector = DetectorFactory.create(type)
        val coach = PlacementCoach(type)
        val said = frames.map { f -> coach.update(f, detector.onFrame(f)).advice }
        return Heard(said, detector.sessionSummary().repCount)
    }

    private fun still(pose: Body3d.Skeleton, camera: Camera, seconds: Int = 3): List<PoseFrame> =
        (0 until seconds * 30).map { i -> Body3d.frame(3_600_000L + i * 33L, pose, camera) }

    @Test
    fun `nobody in the picture is asked to step in`() {
        val coach = PlacementCoach(ExerciseType.PUSHUP)
        val detector = DetectorFactory.create(ExerciseType.PUSHUP)
        val f = PoseFrame.empty(1_000L)
        assertEquals(PlacementAdvice.STEP_INTO_VIEW, coach.update(f, detector.onFrame(f)).advice)
    }

    @Test
    fun `a pushup filmed from the side is told to face the phone, not to come closer`() {
        // The side view the old placement line asked for: shoulders 0.04 apart, never counts.
        val heard = listen(ExerciseType.PUSHUP, Body3d.trace(floorBody(90f), Camera.onFloor(2.0f, 10f), 4))
        assertEquals(0, heard.reps)
        assertEquals(PlacementAdvice.FACE_CAMERA, heard.last)
    }

    @Test
    fun `a plank filmed from the side holds when centred, and with its feet off the edge`() {
        // The plank used to need the shoulders apart in the picture and never held side on; it
        // reads the 3-D skeleton now, so the side is as good as the front.
        val side = floorBody(90f)
        val centred = listen(ExerciseType.PLANK, still(Body3d.pushup(0f, Body3d.V3(-1f, 0f, 0f), Body3d.V3(-0.65f, 0f, 0f)), Camera.onFloor(2.2f, 10f), seconds = 6))
        assertTrue(centred.reps > 0, "a centred side-on plank never held")
        assertTrue(PlacementAdvice.READY in centred.said, "never said ready: ${centred.distinct}")

        // Shoulders on the middle of the picture, feet off its edge: the legs come from the model's
        // 3-D skeleton, so it holds, and the coach must not ask for feet the detector does not need.
        val offCentre = listen(ExerciseType.PLANK, still(side(0f), Camera.onFloor(2.0f, 10f), seconds = 6))
        assertTrue(offCentre.reps > 0, "a side-on plank with its feet off the edge never held")
        assertTrue(PlacementAdvice.CENTER !in offCentre.said, "asked to centre a plank that held: ${offCentre.distinct}")
    }

    @Test
    fun `a plank from in front of the head holds, and the coach says so and goes quiet`() {
        val heard = listen(ExerciseType.PLANK, still(floorBody(0f)(0f), Camera.onFloor(2.0f, 10f), seconds = 6))
        assertTrue(heard.reps > 0, "the plank never held")
        assertTrue(PlacementAdvice.READY in heard.said, "never said ready: ${heard.distinct}")
        assertNull(heard.last, "still talking after ready: ${heard.distinct}")
    }

    @Test
    fun `a pushup from in front of the head counts, and the coach never objects while it does`() {
        val heard = listen(ExerciseType.PUSHUP, Body3d.trace(floorBody(0f), Camera.onFloor(2.0f, 10f), 6))
        assertEquals(6, heard.reps)
        val objections = heard.said.filterNotNull().filter {
            it != PlacementAdvice.READY && it != PlacementAdvice.GET_IN_POSITION
        }
        assertTrue(objections.isEmpty(), "objected to a set that counted: ${objections.distinct()}")
    }

    @Test
    fun `too far away is told to come closer`() {
        val heard = listen(ExerciseType.LUNGE, Body3d.trace({ d -> Body3d.lunge(d) }, Camera.onFloor(5f, 30f), 3))
        assertEquals(0, heard.reps)
        assertEquals(PlacementAdvice.COME_CLOSER, heard.last)
    }

    @Test
    fun `feet below the frame are named, and the edge they left by`() {
        // Level with the chest and close: the knees and ankles the lunge reads are under the frame.
        val coach = PlacementCoach(ExerciseType.LUNGE)
        val detector = DetectorFactory.create(ExerciseType.LUNGE)
        var last = Placement()
        still(Body3d.lunge(0f), Camera.level(1.3f, 1.3f)).forEach { last = coach.update(it, detector.onFrame(it)) }
        assertTrue(
            last.advice == PlacementAdvice.SHOW_BELOW || last.advice == PlacementAdvice.MOVE_PHONE_BACK,
            "advice was ${last.advice}",
        )
        assertTrue(last.offFrame.isNotEmpty(), "named no body part")
    }

    @Test
    fun `standing in view for a pushup is asked to get into position, once, not after resting`() {
        val coach = PlacementCoach(ExerciseType.PUSHUP)
        val detector = DetectorFactory.create(ExerciseType.PUSHUP)
        val said = mutableListOf<PlacementAdvice?>()
        // In position and counting first, then standing up to rest.
        Body3d.trace(floorBody(0f), Camera.onFloor(2.0f, 10f), 3).forEach { said += coach.update(it, detector.onFrame(it)).advice }
        val restStart = said.size
        still(Body3d.lunge(0f), Camera.onFloor(2.0f, 10f)).forEach { f ->
            said += coach.update(f.copy(timestampMs = f.timestampMs + 60_000), detector.onFrame(f.copy(timestampMs = f.timestampMs + 60_000))).advice
        }
        assertTrue(PlacementAdvice.GET_IN_POSITION !in said.drop(restStart), "nagged a resting user: ${said.drop(restStart).distinct()}")
    }

    @Test
    fun `advice that flickers every frame does not flicker on screen`() {
        val coach = PlacementCoach(ExerciseType.PUSHUP)
        val detector = DetectorFactory.create(ExerciseType.PUSHUP)
        val side = still(floorBody(90f)(0f), Camera.onFloor(2.0f, 10f), seconds = 2)
        // Alternate a frame with nobody and a frame side on, for two seconds.
        val shown = side.mapIndexed { i, f ->
            val frame = if (i % 2 == 0) PoseFrame.empty(f.timestampMs, f.imageWidth, f.imageHeight) else f
            coach.update(frame, detector.onFrame(frame)).advice
        }
        assertEquals(1, shown.distinct().size, "the advice flickered: ${shown.distinct()}")
    }
}
