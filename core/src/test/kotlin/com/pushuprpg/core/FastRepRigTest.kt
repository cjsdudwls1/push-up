package com.pushuprpg.core

import com.pushuprpg.core.detect.DetectorFactory
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.fixtures.Body3d
import com.pushuprpg.core.fixtures.Body3d.Camera
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Brisk reps, which the 궁수 is built around, on the projected body at the frame rates a phone
 * actually delivers.
 *
 * Before the band crossings were interpolated, a 1-second squat was refused as TOO_FAST at 20 and
 * 30 fps and counted at 15 — the frame times undercut the descent by up to a frame, and a smoother
 * camera undercut it more often. Squats, lunges, pull-ups and dips needed 1.2-2 seconds a rep.
 */
class FastRepRigTest {

    private val front = Body3d.V3(0f, 0f, 1f)

    private class Brisk(
        val type: ExerciseType,
        val cycleMs: Int,
        val camera: Camera,
        val pose: (Float) -> Body3d.Skeleton,
        val name: String = type.toString(),
    )

    /** Each movement at the briskest cadence it must still count at, in ms per rep. */
    private val brisk = listOf(
        Brisk(ExerciseType.PUSHUP, 800, Camera.onFloor(1.3f, 12f), { d: Float -> Body3d.pushup(d, front, Body3d.V3(0f, 0f, 0f)) }),
        Brisk(ExerciseType.PUSHUP, 800, Camera.onFloor(2.0f, 8f), { d: Float -> Body3d.pushup(d, Body3d.V3(-1f, 0f, 0f), Body3d.V3(0f, 0f, 0f)) }, "PUSHUP side on"),
        Brisk(ExerciseType.SQUAT, 1000, Camera.onFloor(2.2f, 30f), { d: Float -> Body3d.squat(d) }),
        Brisk(ExerciseType.LUNGE, 1000, Camera.onFloor(2.2f, 30f), { d: Float -> Body3d.lunge(d, true) }),
        Brisk(ExerciseType.PULL_UP, 1000, Camera.onFloor(3f, 25f), { d: Float -> Body3d.pullUp(d, front) }),
        Brisk(ExerciseType.DIP, 1200, Camera.onFloor(3f, 25f), { d: Float -> Body3d.dipFacing(d, front) }),
    )

    @Test
    fun `a brisk rep counts at every frame rate a phone gives`() {
        for (b in brisk) for (fps in listOf(12, 15, 20, 30)) {
            val (type, cycleMs) = b.type to b.cycleMs
            val detector = DetectorFactory.create(type)
            val events = mutableListOf<RepEvent>()
            Body3d.trace(
                b.pose, b.camera, 10,
                descentMs = cycleMs * 42 / 100, bottomMs = cycleMs * 6 / 100,
                ascentMs = cycleMs * 42 / 100, restMs = cycleMs * 10 / 100, fps = fps,
            ).forEach { events += detector.onFrame(it).events }
            assertEquals(
                10, detector.sessionSummary().repCount,
                "${b.name} at $cycleMs ms a rep, $fps fps: refused as " +
                    events.filterIsInstance<RepEvent.Abandoned>().map { it.reason }.distinct(),
            )
        }
    }
}
