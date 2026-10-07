package com.pushuprpg.core

import com.pushuprpg.core.detect.DetectorFactory
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.PlankDetector
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.fixtures.Body3d
import com.pushuprpg.core.fixtures.Body3d.Camera
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every movement through a wide lens: 설정 → 광각으로 찍기, the camera zoomed out below 1x. The
 * owner's ask (2026-10-07): 집이 좁은데 광각이 안 돼서 불편해요.
 *
 * Through a wider lens the same body fills the picture from closer, by the zoom, and that is where
 * it is filmed from in a small room — so each placement here is the usual one brought that much
 * nearer along the floor, at the same height and tilt. 0.8x, a front camera's wide selfie, is also
 * run from where the phone already stood, where the body is drawn smaller. The detector reads ratios
 * inside the body and the model's 3-D skeleton, so the lens itself changes nothing; what is measured
 * here is the stronger perspective of the shorter distances it allows.
 *
 * What it does not do, found here and not pinned:
 *
 *  - It does not let the phone stay where it was. At 0.5x the body is drawn half the size, and a
 *    lunge from 1.3 m or a squat from 2.2 m falls under the movement's minScale and is not read —
 *    as it would not be at 1x from twice as far. The placement coach says COME_CLOSER for it.
 *  - A squat from the floor 1.1 m away with its feet in the picture counts its first rep and refuses
 *    the rest. That close, the shoulder width changes so much between standing and the bottom that
 *    the tracker's smoothed scale is still catching up at the next top, and the shoulders' travel
 *    toward the ankles reads 7% short. At 1x the feet are past the edge from there and the check is
 *    never asked. From 1.3 m it counts every rep at 0.5x, so the squat is pinned from there.
 *  - A dip filmed from behind on the floor closer than 2.4 m loses reps at 1x as well; the lens
 *    adds nothing to it, and it is pinned from behind at chair height only.
 */
class WideAngleRigTest {

    /** The rig's 62 degrees at 1x, widened by [zoom]: the tangent of the half angle grows by 1/zoom. */
    private fun vfov(zoom: Float): Float =
        (2.0 * atan(tan(31.0 * PI / 180.0) / zoom) * 180.0 / PI).toFloat()

    /** A wide selfie, and the ultra-wides. */
    private val zooms = listOf(0.8f, 0.6f, 0.5f)

    /** [camera] through a lens at [zoom], moved [closer] of the way to the body along the floor. */
    private fun wide(camera: Camera, zoom: Float, closer: Float): Camera {
        val p = camera.position
        val t = camera.target
        return camera.copy(
            position = Body3d.V3(p.x * closer, p.y, p.z * closer),
            target = Body3d.V3(t.x * closer, p.y + (t.y - p.y) * closer, t.z * closer),
            vfovDeg = vfov(zoom),
        )
    }

    /** Each of [base] at each zoom, brought closer by it, and at 0.8x left where it was. */
    private fun placements(base: List<Pair<String, Camera>>) = buildList {
        for (zoom in zooms) for ((where, cam) in base) {
            add("$where brought to ${"%.2f".format(cam.position.z * zoom)}m, at ${zoom}x" to wide(cam, zoom, zoom))
            if (zoom == 0.8f) add("$where, at ${zoom}x" to wide(cam, zoom, 1f))
        }
    }

    private fun facing(yawDeg: Float): Body3d.V3 {
        val yaw = Math.toRadians(yawDeg.toDouble())
        return Body3d.V3(sin(yaw).toFloat(), 0f, cos(yaw).toFloat())
    }

    private fun heading(yawDeg: Float): Body3d.V3 {
        val yaw = Math.toRadians(yawDeg.toDouble())
        return Body3d.V3(-sin(yaw).toFloat(), 0f, cos(yaw).toFloat())
    }

    private fun reps(type: ExerciseType, camera: Camera, pose: (Float) -> Body3d.Skeleton, peakDepth: Float = 0.95f, fps: Int = 30): Pair<Int, String> {
        val detector = DetectorFactory.create(type)
        val events = mutableListOf<RepEvent>()
        Body3d.trace(pose, camera, 6, peakDepth = peakDepth, fps = fps).forEach { events += detector.onFrame(it).events }
        val why = "refused as ${events.filterIsInstance<RepEvent.Abandoned>().map { it.reason }.distinct()}, " +
            "shallow ${events.count { it is RepEvent.Shallow }}"
        return detector.sessionSummary().repCount to why
    }

    /** Every case is run before anything fails, so a failure names all the placements it reaches. */
    private fun assertAll(failures: List<String>) =
        assertTrue(failures.isEmpty(), failures.joinToString("\n", prefix = "${failures.size} failed:\n"))

    private fun MutableList<String>.counts(what: String, type: ExerciseType, camera: Camera, pose: (Float) -> Body3d.Skeleton) {
        for (fps in listOf(30, 15)) {
            val (n, why) = reps(type, camera, pose, fps = fps)
            if (n != 6) add("$what at $fps fps counted $n of 6; $why")
        }
    }

    private fun MutableList<String>.countsNone(what: String, type: ExerciseType, camera: Camera, peakDepth: Float, pose: (Float) -> Body3d.Skeleton) {
        val (n, _) = reps(type, camera, pose, peakDepth = peakDepth)
        if (n != 0) add("$what counted $n")
    }

    @Test
    fun `a pushup counts through a wide lens from the head, a diagonal and the side, and one 40 percent down does not`() {
        val base = listOf(
            "the floor 1.3m away" to Camera.onFloor(1.3f, 12f),
            "the floor 2.0m away" to Camera.onFloor(2.0f, 8f),
        )
        val failures = mutableListOf<String>()
        for ((where, cam) in placements(base)) for (yaw in listOf(0f, 30f, 90f, -90f)) for (onKnees in listOf(false, true)) {
            val pose = { d: Float -> Body3d.pushup(d, heading(yaw), Body3d.V3(0f, 0f, 0f), onKnees) }
            val what = (if (onKnees) "a knee pushup" else "a pushup") + " ${yaw.toInt()}° off the head from $where"
            failures.counts(what, ExerciseType.PUSHUP, cam, pose)
            failures.countsNone("$what, 40% down,", ExerciseType.PUSHUP, cam, 0.4f, pose)
        }
        assertAll(failures)
    }

    @Test
    fun `a squat and a lunge count through a wide lens, and a squat is still not a lunge`() {
        val failures = mutableListOf<String>()
        for ((where, cam) in placements(listOf("the floor 2.6m away, tilted 30°" to Camera.onFloor(2.6f, 30f)))) {
            failures.counts("a squat from $where", ExerciseType.SQUAT, cam) { d -> Body3d.squat(d) }
        }
        for ((where, cam) in placements(listOf("the floor 2.2m away, tilted 30°" to Camera.onFloor(2.2f, 30f)))) {
            for (yaw in listOf(0f, 45f, 90f)) {
                failures.counts("a lunge at ${yaw.toInt()}° from $where", ExerciseType.LUNGE, cam) { d ->
                    Body3d.rotated(Body3d.lunge(d), facing(yaw))
                }
            }
            failures.countsNone("a squat as a lunge from $where", ExerciseType.LUNGE, cam, 0.95f) { d -> Body3d.squat(d) }
        }
        assertAll(failures)
    }

    @Test
    fun `a pull-up and a dip count through a wide lens from every side`() {
        val base = listOf(
            "the floor 3m back, tilted 25°" to Camera.onFloor(3f, 25f),
            "a chair 2.5m back at 1m" to Camera.level(2.5f, 1.0f),
        )
        val failures = mutableListOf<String>()
        for ((where, cam) in placements(base)) for (yaw in listOf(0f, 45f, 90f, 180f)) {
            failures.counts("a pull-up at ${yaw.toInt()}° from $where", ExerciseType.PULL_UP, cam) { d ->
                Body3d.pullUp(d, facing(yaw))
            }
            if (yaw == 180f && cam.position.y < 0.5f) continue
            failures.counts("a dip at ${yaw.toInt()}° from $where", ExerciseType.DIP, cam) { d ->
                Body3d.dipFacing(d, facing(yaw))
            }
        }
        assertAll(failures)
    }

    @Test
    fun `a plank holds through a wide lens, and all fours and standing do not`() {
        val base = listOf(
            "2.2m, tilted 10°" to Camera.onFloor(2.2f, 10f),
            "3m, tilted 20°" to Camera.onFloor(3f, 20f),
            "2m, tilted 30°" to Camera.onFloor(2f, 30f),
        )
        fun ticks(pose: Body3d.Skeleton, cam: Camera): Int {
            val d = PlankDetector()
            return (0 until 6 * 30).sumOf { i ->
                d.onFrame(Body3d.frame(i * 33L, pose, cam)).events.count { it is RepEvent.HoldTick }
            }
        }
        val failures = mutableListOf<String>()
        for ((where, cam) in placements(base)) for (yaw in listOf(0f, 45f, 90f, 180f)) {
            val h = heading(yaw)
            val at = "at ${yaw.toInt()}° from $where"
            if (ticks(Body3d.pushup(0f, h, h * 0.65f), cam) == 0) failures += "a high plank $at never held"
            if (ticks(Body3d.forearmPlank(h, h * 0.65f), cam) == 0) failures += "a forearm plank $at never held"
            if (ticks(Body3d.allFours(h, h * 0.3f), cam) > 0) failures += "all fours $at held"
            if (ticks(Body3d.kneePlank(h, h * 0.4f), cam) > 0) failures += "a knee plank $at held"
            if (ticks(Body3d.standing(), cam) > 0) failures += "standing $at held"
        }
        assertAll(failures)
    }

    // ---------------------------------------------------------------- switched mid-set

    /**
     * The run screen switches 광각 and moves its slider while a set is on, so the zoom can change under
     * the detector between two frames, or across a second while the slider is dragged. The phone does
     * not move; the body is drawn smaller or larger. [zoomAt] is the zoom at a frame's time.
     *
     * Not pinned, because the hand is on the phone for it: a drag spread across a rep's descent loses
     * that rep (and at 15 fps the one after it). The body's size changing on every frame is what a
     * tracking glitch looks like, and the tracker refuses to follow it on one frame by design.
     */
    private fun repsWithZoom(
        type: ExerciseType,
        camera: Camera,
        pose: (Float) -> Body3d.Skeleton,
        fps: Int,
        zoomAt: (Long) -> Float,
    ): Pair<Int, String> {
        val detector = DetectorFactory.create(type)
        val events = mutableListOf<RepEvent>()
        com.pushuprpg.core.fixtures.PoseFixtures.trace(
            count = 8, startMs = START_MS, descentMs = 1000, bottomMs = 200, ascentMs = 1000, restMs = REST_MS,
            fps = fps, settleMs = 800,
            frameOf = { t, d -> Body3d.frame(t, pose(d), camera.copy(vfovDeg = vfov(zoomAt(t)))) },
        ).forEach { events += detector.onFrame(it).events }
        val why = "refused as ${events.filterIsInstance<RepEvent.Abandoned>().map { it.reason }.distinct()}, " +
            "shallow ${events.count { it is RepEvent.Shallow }}"
        return detector.sessionSummary().repCount to why
    }

    @Test
    fun `switching 광각 or dragging its slider in a pause mid-set loses no rep`() {
        // In the pause after the fourth rep, at the top, where someone reaching for the phone is.
        val pauseAfterFourth = START_MS + 800 + 4 * REP_MS - REST_MS
        val changes = listOf<Pair<String, (Long, Float, Float) -> Float>>(
            "switched in a pause" to { t, from, to -> if (t < pauseAfterFourth + REST_MS / 2) from else to },
            "dragged over a second in a pause" to { t, from, to ->
                val f = ((t - pauseAfterFourth - 500) / 1000f).coerceIn(0f, 1f)
                from + (to - from) * f
            },
        )
        val sets = listOf<Triple<String, ExerciseType, (Float) -> Body3d.Skeleton>>(
            Triple("a pushup from the head", ExerciseType.PUSHUP) { d -> Body3d.pushup(d, heading(0f), Body3d.V3(0f, 0f, 0f)) },
            Triple("a pushup side on", ExerciseType.PUSHUP) { d -> Body3d.pushup(d, heading(90f), Body3d.V3(0f, 0f, 0f)) },
            Triple("a squat", ExerciseType.SQUAT) { d -> Body3d.squat(d) },
            Triple("a lunge", ExerciseType.LUNGE) { d -> Body3d.lunge(d) },
        )
        val failures = mutableListOf<String>()
        for ((what, type, pose) in sets) {
            val camera = if (type == ExerciseType.PUSHUP) Camera.onFloor(1.3f, 12f) else Camera.onFloor(1.6f, 30f)
            for ((how, change) in changes) for ((from, to) in listOf(1f to 0.6f, 0.6f to 1f)) for (fps in listOf(30, 15)) {
                val (n, why) = repsWithZoom(type, camera, pose, fps) { t -> change(t, from, to) }
                if (n != 8) failures += "$what, ${from}x to ${to}x $how, at $fps fps: counted $n of 8; $why"
            }
        }
        assertAll(failures)
    }

    private companion object {
        const val START_MS = 3_600_000L
        const val REST_MS = 2500
        const val REP_MS = 1000 + 200 + 1000 + REST_MS
    }
}
