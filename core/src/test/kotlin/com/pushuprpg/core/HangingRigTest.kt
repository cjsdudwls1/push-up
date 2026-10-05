package com.pushuprpg.core

import com.pushuprpg.core.detect.DetectorFactory
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.fixtures.Body3d
import com.pushuprpg.core.fixtures.Body3d.Camera
import com.pushuprpg.core.pose.Landmark
import com.pushuprpg.core.pose.PoseFrame
import com.pushuprpg.core.pose.PoseLandmarks as Lm
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

    private fun assertCounts(
        type: ExerciseType,
        seen: (PoseFrame) -> PoseFrame = { it },
        pose: (Float, Body3d.V3) -> Body3d.Skeleton,
    ) {
        for (yaw in views) for ((where, cam) in cameras) {
            val detector = DetectorFactory.create(type)
            val events = mutableListOf<RepEvent>()
            Body3d.trace({ d -> pose(d, facing(yaw)) }, cam, 6).forEach { events += detector.onFrame(seen(it)).events }
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

    /**
     * Filmed from the front the elbows point at the lens, the one direction the model has to guess:
     * a dip's elbow read straighter at the bottom than at the top there, and refused three of four.
     * A pull-up is witnessed by the shoulders against the bar, so an elbow the model reads straight
     * through every pull — the worst it could do — costs none of them.
     */
    @Test
    fun `a pull-up counts with the elbows read straight all the way`() =
        assertCounts(ExerciseType.PULL_UP, seen = ::elbowsReadStraight) { d, f -> Body3d.pullUp(d, f) }

    /** The frame with each world elbow put on the line from its shoulder to its wrist. */
    private fun elbowsReadStraight(frame: PoseFrame): PoseFrame {
        if (!frame.hasWorld) return frame
        val w = frame.worldLandmarks.toMutableList()
        for ((s, e, h) in listOf(
            Triple(Lm.LEFT_SHOULDER, Lm.LEFT_ELBOW, Lm.LEFT_WRIST),
            Triple(Lm.RIGHT_SHOULDER, Lm.RIGHT_ELBOW, Lm.RIGHT_WRIST),
        )) {
            w[e] = Landmark((w[s].x + w[h].x) / 2f, (w[s].y + w[h].y) / 2f, (w[s].z + w[h].z) / 2f)
        }
        return frame.copy(worldLandmarks = w)
    }

    @Test
    fun `a dip counts from the front, the side, the back and between`() =
        assertCounts(ExerciseType.DIP) { d, f -> Body3d.dipFacing(d, f) }

    private fun assertCountsNone(what: String, type: ExerciseType = ExerciseType.PULL_UP, pose: (Float, Body3d.V3) -> Body3d.Skeleton) {
        for (yaw in views) for ((where, cam) in cameras) {
            val detector = DetectorFactory.create(type)
            Body3d.trace({ d -> pose(d, facing(yaw)) }, cam, 6).forEach { detector.onFrame(it) }
            assertEquals(0, detector.sessionSummary().repCount, "$what as a $type at ${yaw.toInt()}° from $where counted")
        }
    }

    /** The device report: standing and bending the arms overhead counted a pull-up every time. */
    @Test
    fun `bending the arms overhead while standing is not a pull-up`() =
        assertCountsNone("arms bent overhead") { d, f -> Body3d.overheadArmBend(d, f) }

    /** The device report: coming down off the bar, the arms folding on the way, counted one more. */
    @Test
    fun `folding the arms on the way down off the bar is not a pull-up`() =
        assertCountsNone("folding on the way down") { d, f -> Body3d.overheadArmBend(d, f, sinkM = 0.15f) }

    /**
     * The device report (2026-10-04): reaching up to the bar from standing counted the first pull of
     * a set. Read along the spine, the arms by the sides read as a dead hang. On the phone the user
     * also stepped in under the bar, which moved the shoulders in the picture; that set is replayed
     * in [RealTraceTest].
     */
    @Test
    fun `reaching up to the bar is not a pull-up`() =
        assertCountsNone("reaching up to the bar") { d, f -> Body3d.reachUp(d, f) }

    /**
     * The same report: letting go of the bar and lowering the arms counted one more at the end of
     * every set — from a dead hang, and after a last pull given up half way.
     */
    @Test
    fun `letting go of the bar is not a pull-up, from a hang or half way up`() {
        assertCountsNone("letting go from a hang") { d, f -> Body3d.letGo(d, f) }
        assertCountsNone("letting go half way up") { d, f -> Body3d.letGo(d, f, pull = 0.5f) }
    }

    /**
     * A dip is witnessed by the shoulders coming down to the hands since the elbow's 3-D angle was
     * seen reading backwards on a phone; the fakes move the hands and leave the shoulders.
     */
    @Test
    fun `waving or bending the arms while standing is not a dip`() {
        assertCountsNone("arms waved at the lens", ExerciseType.DIP) { d, f -> Body3d.armWave(d, f) }
        assertCountsNone("arms bent overhead", ExerciseType.DIP) { d, f -> Body3d.overheadArmBend(d, f) }
    }
}
