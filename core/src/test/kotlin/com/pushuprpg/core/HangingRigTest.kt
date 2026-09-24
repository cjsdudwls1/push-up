package com.pushuprpg.core

import com.pushuprpg.core.detect.DetectorFactory
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.fixtures.Body3d
import com.pushuprpg.core.fixtures.Body3d.Camera
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pull-ups and dips on the projected body, from every side and from where the phone goes.
 *
 * The device report: neither counted from the front, the side or the back. The rig showed why.
 * The depth was measured across the shoulder line and in shoulder widths; at the three metres a
 * pull-up needs to fit the bar and the feet, a shoulder line square to the lens is barely over the
 * minimum scale, 45 degrees off it falls under, and side on it is gone. Both are read along the
 * spine now, which is its full length from every side.
 */
class HangingRigTest {

    private fun facing(yawDeg: Float): Body3d.V3 {
        val yaw = Math.toRadians(yawDeg.toDouble())
        return Body3d.V3(sin(yaw).toFloat(), 0f, cos(yaw).toFloat())
    }

    /** Chest to the lens, 45 degrees, side on, back to the lens. */
    private val views = listOf(0f, 45f, 90f, 180f)

    private val cameras = listOf(
        "the floor 3m back, tilted 25°" to Camera.onFloor(3f, 25f),
        "the floor 3.5m back, tilted 30°" to Camera.onFloor(3.5f, 30f),
        "a shelf 3m back at 1.3m" to Camera.level(3f, 1.3f),
        "a chair 2.5m back at 1m" to Camera.level(2.5f, 1.0f),
    )

    private fun assertCounts(type: ExerciseType, pose: (Float, Body3d.V3) -> Body3d.Skeleton) {
        for (yaw in views) for ((where, cam) in cameras) {
            val detector = DetectorFactory.create(type)
            val events = mutableListOf<RepEvent>()
            Body3d.trace({ d -> pose(d, facing(yaw)) }, cam, 6).forEach { events += detector.onFrame(it).events }
            assertEquals(
                6, detector.sessionSummary().repCount,
                "$type at ${yaw.toInt()}° from $where: refused as " +
                    events.filterIsInstance<RepEvent.Abandoned>().map { it.reason }.distinct(),
            )
        }
    }

    @Test
    fun `a pull-up counts from the front, the side, the back and between`() =
        assertCounts(ExerciseType.PULL_UP) { d, f -> Body3d.pullUp(d, f) }

    @Test
    fun `a dip counts from the front, the side, the back and between`() =
        assertCounts(ExerciseType.DIP) { d, f -> Body3d.dipFacing(d, f) }
}
