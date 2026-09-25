package com.pushuprpg.core

import com.pushuprpg.core.detect.PlankDetector
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.fixtures.Body3d
import com.pushuprpg.core.fixtures.Body3d.Camera
import com.pushuprpg.core.pose.PoseFrame
import com.pushuprpg.core.pose.PoseLandmarks as Lm
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The plank on the projected body, from every side a phone gets put.
 *
 * The device report: on the knees on all fours, the plank held and dealt damage — and a real
 * plank from the side never held at all. On the rig it was worse: standing still in front of the
 * phone held too. The detector read the pose off the picture, where from the head any of those
 * line up the same; it reads the model's 3-D skeleton now.
 */
class PlankRigTest {

    private fun heading(yawDeg: Float): Body3d.V3 {
        val yaw = Math.toRadians(yawDeg.toDouble())
        return Body3d.V3(-sin(yaw).toFloat(), 0f, cos(yaw).toFloat())
    }

    /** Head toward the lens (0), diagonal, side on, feet toward the lens (180). */
    private val views = listOf(0f, 45f, 90f, 180f)

    /** Propped on the floor at the tilts and distances people use. */
    private val cameras = listOf(
        "2.2m, tilted 10°" to Camera.onFloor(2.2f, 10f),
        "3m, tilted 20°" to Camera.onFloor(3f, 20f),
        "2m, tilted 30°" to Camera.onFloor(2f, 30f),
    )

    private fun ticks(pose: Body3d.Skeleton, camera: Camera, legsSeen: Boolean, seconds: Int = 6, feetOut: Boolean = false): Int {
        val d = PlankDetector()
        return (0 until seconds * 30).sumOf { i ->
            val frame = Body3d.frame(i * 33L, pose, camera)
            val seen = when {
                feetOut -> feetOffFrame(frame)
                legsSeen -> frame
                else -> legsHidden(frame)
            }
            d.onFrame(seen).events.count { it is RepEvent.HoldTick }
        }
    }

    /**
     * The feet past the edge of the picture and the knees in it: a phone close by the side. The
     * model still places the feet, where the rig says they are; ConfidenceEstimator gives a point
     * off the edge nothing, and that is what a phone reported.
     */
    private fun feetOffFrame(frame: PoseFrame): PoseFrame = frame.copy(
        landmarks = frame.landmarks.mapIndexed { i, lm ->
            if (i >= Lm.LEFT_ANKLE) lm.copy(visibility = 0f, presence = 0f) else lm
        },
    )

    /**
     * The legs as a phone gets them from the head end: behind the body, so the model reports them
     * barely visible — 0.05-0.3 for the ankles over a device recording — and places them in its
     * 3-D skeleton anyway. The placement here is the true one; how well the model guesses it is
     * what a recording shows, not this.
     */
    private fun legsHidden(frame: PoseFrame): PoseFrame = frame.copy(
        landmarks = frame.landmarks.mapIndexed { i, lm ->
            if (i >= Lm.LEFT_KNEE && lm.visibility > 0f) lm.copy(visibility = 0.15f, presence = 0.15f) else lm
        },
    )

    private fun assertHolds(name: String, legsSeen: Boolean = true, pose: (Body3d.V3) -> Body3d.Skeleton) {
        for (yaw in views) for ((where, cam) in cameras) {
            assertTrue(ticks(pose(heading(yaw)), cam, legsSeen) > 0, "$name at ${yaw.toInt()}° from $where never held")
        }
    }

    private fun assertNeverHolds(name: String, legsSeen: Boolean = true, pose: (Body3d.V3) -> Body3d.Skeleton) {
        for (yaw in views) for ((where, cam) in cameras) {
            assertTrue(ticks(pose(heading(yaw)), cam, legsSeen) == 0, "$name at ${yaw.toInt()}° from $where held as a plank")
        }
    }

    @Test
    fun `a plank on the hands holds from the front, the side, the back and between`() =
        assertHolds("a high plank") { h -> Body3d.pushup(0f, h, h * 0.65f) }

    @Test
    fun `a plank on the forearms holds from every side too`() =
        assertHolds("a forearm plank") { h -> Body3d.forearmPlank(h, h * 0.65f) }

    @Test
    fun `on all fours is not a plank, from any side`() =
        assertNeverHolds("all fours") { h -> Body3d.allFours(h, h * 0.3f) }

    @Test
    fun `a plank from the knees is not a plank`() =
        assertNeverHolds("a knee plank") { h -> Body3d.kneePlank(h, h * 0.4f) }

    @Test
    fun `standing still in front of the phone is not a plank`() =
        assertNeverHolds("standing") { _ -> Body3d.standing() }

    @Test
    fun `hips sagging out of the line break a plank`() =
        assertNeverHolds("a sagging forearm plank") { h -> Body3d.forearmPlank(h, h * 0.65f, sagDeg = 35f) }

    // The device report: from the head end the plank never held at all. The ankles were behind
    // the body, and the detector refused to judge a plank without seeing them.

    @Test
    fun `a plank holds when the camera cannot see the legs`() {
        assertHolds("a high plank, legs hidden", legsSeen = false) { h -> Body3d.pushup(0f, h, h * 0.65f) }
        assertHolds("a forearm plank, legs hidden", legsSeen = false) { h -> Body3d.forearmPlank(h, h * 0.65f) }
    }

    @Test
    fun `side on with the feet past the edge, a plank holds and the knees down do not`() {
        // The device report: a side plank with the feet out of the picture never held. The model's
        // guess at the shins read 139-145, a knee plank's angle; the knees' height off the floor
        // tells them apart instead.
        val side = heading(90f)
        for ((where, cam) in cameras) {
            assertTrue(ticks(Body3d.forearmPlank(side, side * 0.65f), cam, legsSeen = true, feetOut = true) > 0,
                "a forearm plank with the feet out of frame from $where never held")
            assertTrue(ticks(Body3d.pushup(0f, side, side * 0.65f), cam, legsSeen = true, feetOut = true) > 0,
                "a high plank with the feet out of frame from $where never held")
            assertTrue(ticks(Body3d.kneePlank(side, side * 0.4f), cam, legsSeen = true, feetOut = true) == 0,
                "a knee plank with the feet out of frame from $where held")
            assertTrue(ticks(Body3d.allFours(side, side * 0.3f), cam, legsSeen = true, feetOut = true) == 0,
                "all fours with the feet out of frame from $where held")
        }
    }

    @Test
    fun `hidden legs do not turn all fours or the knees into a plank`() {
        assertNeverHolds("all fours, legs hidden", legsSeen = false) { h -> Body3d.allFours(h, h * 0.3f) }
        assertNeverHolds("a knee plank, legs hidden", legsSeen = false) { h -> Body3d.kneePlank(h, h * 0.4f) }
        assertNeverHolds("standing, legs hidden", legsSeen = false) { _ -> Body3d.standing() }
    }
}
