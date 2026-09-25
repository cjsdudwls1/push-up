package com.pushuprpg.core

import com.pushuprpg.core.detect.AbandonReason
import com.pushuprpg.core.detect.BodySide
import com.pushuprpg.core.detect.DetectorFactory
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.fixtures.Body3d
import com.pushuprpg.core.fixtures.Body3d.Camera
import com.pushuprpg.core.pose.PoseFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The lunge on the projected body. The device report: a squat counted as a lunge. On the rig it
 * did, six of six — hips over knees reads the same with the feet split or side by side. The stance
 * is checked now, and the same measurement names the front leg.
 */
class LungeRigTest {

    private val floor = Camera.onFloor(2.2f, 30f)

    /** Reps one after another, each on the leg [legs] says, stepping between them at the top. */
    private fun alternating(legs: List<Boolean>): List<PoseFrame> {
        val frames = mutableListOf<PoseFrame>()
        var t = 3_600_000L
        legs.forEachIndexed { i, leftForward ->
            val rep = Body3d.trace({ d -> Body3d.lunge(d, leftForward) }, floor, 1, startMs = t, settleMs = if (i == 0) 800 else 600)
            frames += rep
            t = rep.last().timestampMs + 33
        }
        return frames
    }

    /**
     * The device report: lunges filmed at an angle counted nothing. Read across the shoulder line, a
     * body turned that far was too narrow to measure; read along the spine, the side is as good as
     * the front, and a squat is still refused from all of them.
     */
    @Test
    fun `a lunge counts from the front, the side and between, and a squat from none of them`() {
        // From the floor, where the placement line sends the phone; a level camera sees the thigh's
        // travel end on, which MovementRigTest records as a fact about the geometry.
        for (yaw in listOf(0f, 45f, 90f, 135f)) for ((where, cam) in listOf(
            "2.2m on the floor" to floor, "3m on the floor" to Camera.onFloor(3f, 20f),
        )) {
            val r = Math.toRadians(yaw.toDouble())
            val facing = Body3d.V3(-kotlin.math.sin(r).toFloat(), 0f, kotlin.math.cos(r).toFloat())
            val lunge = DetectorFactory.create(ExerciseType.LUNGE)
            Body3d.trace({ d -> Body3d.rotated(Body3d.lunge(d, true), facing) }, cam, 6).forEach { lunge.onFrame(it) }
            // From behind, the front knee's travel is along the line of sight and the range's first
            // guess is too deep: two reps fall short, and the bottom watchdog moves the range to them.
            val least = if (yaw > 90f) 4 else 6
            assertTrue(lunge.sessionSummary().repCount in least..6, "lunges at ${yaw.toInt()}° from $where: ${lunge.sessionSummary().repCount}")
            val squat = DetectorFactory.create(ExerciseType.LUNGE)
            Body3d.trace({ d -> Body3d.rotated(Body3d.squat(d), facing) }, cam, 6).forEach { squat.onFrame(it) }
            assertEquals(0, squat.sessionSummary().repCount, "a squat at ${yaw.toInt()}° from $where counted as a lunge")
        }
    }

    @Test
    fun `half lunges never teach the range to meet them, from any side`() {
        // The bottom watchdog moves the range only for reps whose knee bent fully by its own 3-D
        // angle. A half lunge reads 60 on the knee, from wherever it is filmed.
        for (yaw in listOf(0f, 90f, 135f)) {
            val r = Math.toRadians(yaw.toDouble())
            val facing = Body3d.V3(-kotlin.math.sin(r).toFloat(), 0f, kotlin.math.cos(r).toFloat())
            val detector = DetectorFactory.create(ExerciseType.LUNGE)
            Body3d.trace({ d -> Body3d.rotated(Body3d.lunge(d, true), facing) }, floor, 8, peakDepth = 0.5f)
                .forEach { detector.onFrame(it) }
            assertEquals(0, detector.sessionSummary().repCount, "half lunges at ${yaw.toInt()}° counted")
        }
    }

    @Test
    fun `a squat is not a lunge`() {
        val detector = DetectorFactory.create(ExerciseType.LUNGE)
        val events = mutableListOf<RepEvent>()
        Body3d.trace({ d -> Body3d.squat(d) }, floor, 6).forEach { events += detector.onFrame(it).events }
        assertEquals(0, detector.sessionSummary().repCount, "a squat counted as a lunge")
        assertTrue(events.filterIsInstance<RepEvent.Abandoned>().any { it.reason == AbandonReason.NOT_SPLIT })
    }

    @Test
    fun `a squat still counts as a squat`() {
        val detector = DetectorFactory.create(ExerciseType.SQUAT)
        Body3d.trace({ d -> Body3d.squat(d) }, floor, 6).forEach { detector.onFrame(it) }
        assertEquals(6, detector.sessionSummary().repCount)
    }

    @Test
    fun `lunges on alternating legs all count, and each names its front leg`() {
        val detector = DetectorFactory.create(ExerciseType.LUNGE)
        val strikes = mutableListOf<RepEvent.Strike>()
        alternating(listOf(true, false, true, false, true, false)).forEach { f ->
            strikes += detector.onFrame(f).events.filterIsInstance<RepEvent.Strike>()
        }
        assertEquals(6, strikes.size, "counted ${strikes.size} of 6 alternating lunges")
        assertEquals(
            listOf(BodySide.LEFT, BodySide.RIGHT, BodySide.LEFT, BodySide.RIGHT, BodySide.LEFT, BodySide.RIGHT),
            strikes.map { it.front },
        )
    }
}
