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
