package com.pushuprpg.core

import com.pushuprpg.core.detect.PlankDetector
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.fixtures.Body3d
import com.pushuprpg.core.fixtures.Body3d.Camera
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

    private fun ticks(pose: Body3d.Skeleton, camera: Camera, seconds: Int = 6): Int {
        val d = PlankDetector()
        return (0 until seconds * 30).sumOf { i ->
            d.onFrame(Body3d.frame(i * 33L, pose, camera)).events.count { it is RepEvent.HoldTick }
        }
    }

    private fun assertHolds(name: String, pose: (Body3d.V3) -> Body3d.Skeleton) {
        for (yaw in views) for ((where, cam) in cameras) {
            assertTrue(ticks(pose(heading(yaw)), cam) > 0, "$name at ${yaw.toInt()}° from $where never held")
        }
    }

    private fun assertNeverHolds(name: String, pose: (Body3d.V3) -> Body3d.Skeleton) {
        for (yaw in views) for ((where, cam) in cameras) {
            assertTrue(ticks(pose(heading(yaw)), cam) == 0, "$name at ${yaw.toInt()}° from $where held as a plank")
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
}
