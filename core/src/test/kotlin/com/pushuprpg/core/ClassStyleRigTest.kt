package com.pushuprpg.core

import com.pushuprpg.core.detect.DetectorFactory
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.fixtures.Body3d
import com.pushuprpg.core.fixtures.Body3d.Camera
import com.pushuprpg.core.game.ClassStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two classes' thresholds, measured on the projected body rather than asserted.
 *
 * A 기사's rep is whole when the way down is slow: [ClassStyle.SLOW_LOWERING_MS] from leaving the
 * top band to the deep line. A 궁수's is whole when it follows the last inside
 * [ClassStyle.briskCycleMs]. Both have to be something a body does and the camera sees.
 */
class ClassStyleRigTest {

    private val front = Body3d.V3(0f, 0f, 1f)

    private class Move(val type: ExerciseType, val camera: Camera, val peak: Float, val pose: (Float) -> Body3d.Skeleton)

    private val moves = listOf(
        Move(ExerciseType.PUSHUP, Camera.onFloor(1.3f, 12f), 0.95f) { d -> Body3d.pushup(d, front, Body3d.V3(0f, 0f, 0f)) },
        Move(ExerciseType.SQUAT, Camera.onFloor(2.2f, 30f), 0.95f) { d -> Body3d.squat(d) },
        Move(ExerciseType.LUNGE, Camera.onFloor(2.2f, 30f), 0.95f) { d -> Body3d.lunge(d, true) },
        // A pull-up's full range is the chin over the bar; short of it, it never reaches 깊게.
        Move(ExerciseType.PULL_UP, Camera.onFloor(3f, 25f), 1.0f) { d -> Body3d.pullUp(d, front) },
        Move(ExerciseType.DIP, Camera.onFloor(3f, 25f), 0.95f) { d -> Body3d.dipFacing(d, front) },
    )

    private fun lowerings(m: Move, descentMs: Int, fps: Int): List<Int> {
        val detector = DetectorFactory.create(m.type)
        val out = mutableListOf<Int>()
        Body3d.trace(m.pose, m.camera, 6, peakDepth = m.peak, descentMs = descentMs, fps = fps).forEach { f ->
            out += detector.onFrame(f).events.filterIsInstance<RepEvent.DeepUpgrade>().map { it.loweringMs }
        }
        return out
    }

    @Test
    fun `a two-second lowering is a whole rep for a 기사, from the first rep, at any frame rate`() {
        for (m in moves) for (fps in listOf(15, 30)) {
            val l = lowerings(m, descentMs = 2000, fps = fps)
            assertEquals(6, l.size, "${m.type} at $fps fps reached 깊게 on ${l.size} of 6")
            assertTrue(l.all { ClassStyle.knightSlowEnough(it) }, "${m.type} at $fps fps: a 2 s lowering read $l")
        }
    }

    @Test
    fun `an ordinary one-second lowering is half for a 기사`() {
        for (m in moves) for (fps in listOf(15, 30)) {
            val l = lowerings(m, descentMs = 1000, fps = fps)
            assertTrue(l.isNotEmpty() && l.none { ClassStyle.knightSlowEnough(it) }, "${m.type} at $fps fps: a 1 s lowering read $l")
        }
    }

    @Test
    fun `a 궁수's brisk pace is well inside what the camera counts`() {
        // At 80% of the brisk limit, every rep must still count: the class asks for pace, and the
        // detector must never be what stops it. FastRepRigTest pins the detector's own floor.
        for (m in moves) for (fps in listOf(15, 30)) {
            val cycle = ClassStyle.briskCycleMs(m.type) * 8 / 10
            val detector = DetectorFactory.create(m.type)
            val events = mutableListOf<RepEvent>()
            Body3d.trace(
                m.pose, m.camera, 10, peakDepth = m.peak,
                descentMs = cycle * 42 / 100, bottomMs = cycle * 6 / 100,
                ascentMs = cycle * 42 / 100, restMs = cycle * 10 / 100, fps = fps,
            ).forEach { events += detector.onFrame(it).events }
            assertEquals(
                10, detector.sessionSummary().repCount,
                "${m.type} at $cycle ms a rep, $fps fps: refused as " +
                    events.filterIsInstance<RepEvent.Abandoned>().map { it.reason }.distinct(),
            )
        }
    }
}
