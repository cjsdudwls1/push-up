package com.pushuprpg.core.fixtures

import com.pushuprpg.core.detect.DepthSignal
import com.pushuprpg.core.pose.Landmark
import com.pushuprpg.core.pose.PoseFrame
import com.pushuprpg.core.pose.PoseLandmarks as Lm
import kotlin.math.cos
import kotlin.math.PI

/**
 * Synthetic landmark traces for a person doing pushups face-on to a phone lying on the floor.
 *
 * These exist because the alternative — testing rep detection only against real recordings — makes
 * every edge case (a user with one arm out of frame, a glitched frame, a 12 fps device, someone
 * waving a hand at the lens) expensive to produce and impossible to produce deterministically.
 * The geometry here is deliberately simple but *self-consistent*: shoulders descend toward fixed
 * hands, the head descends with them, and the world-space elbow angle closes as they do, so the
 * primary signal and both cross-checks agree exactly the way they would on a real rep.
 *
 * `depthFraction` runs 0 (locked out) to 1 (deepest calibrated position) and, because the default
 * calibration priors are the same values used here, maps directly onto the 0..100 depth scale.
 */
object PoseFixtures {

    const val WIDTH = 640
    const val HEIGHT = 480
    const val ASPECT = WIDTH.toFloat() / HEIGHT.toFloat()

    /** Shoulder width in isotropic units (1.0 = one image height). */
    const val SHOULDER_WIDTH = 0.25f

    /** Where the hands rest. They do not move during a pushup. */
    const val WRIST_V = 0.85f

    const val H_TOP = 1.35f
    const val H_BOTTOM = 0.70f

    private const val CONFIDENT = 0.95f

    /** Legs are usually far from a floor-level phone; this is what the model actually reports. */
    private const val LEG_CONFIDENCE = 0.25f

    fun hFor(depthFraction: Float): Float = H_TOP - depthFraction * (H_TOP - H_BOTTOM)

    /**
     * One frame at a given depth.
     *
     * @param legConfidence set high to simulate a user whose legs genuinely are in frame.
     * @param armsVisible set false to simulate wrists leaving the frame.
     * @param world include world landmarks (the elbow cross-check needs them).
     */
    fun frame(
        tMs: Long,
        depthFraction: Float,
        legConfidence: Float = LEG_CONFIDENCE,
        armsVisible: Boolean = true,
        world: Boolean = true,
        shoulderWidth: Float = SHOULDER_WIDTH,
        centerU: Float = ASPECT / 2f,
        noiseU: Float = 0f,
        noiseV: Float = 0f,
    ): PoseFrame {
        val h = hFor(depthFraction)
        val shoulderV = WRIST_V - h * shoulderWidth
        val halfW = shoulderWidth / 2f

        val lm = MutableList(Lm.COUNT) { Landmark.ZERO }

        fun put(index: Int, u: Float, v: Float, conf: Float) {
            lm[index] = Landmark(
                x = (u + noiseU) / ASPECT,
                y = v + noiseV,
                z = 0f,
                visibility = conf,
                presence = conf,
            )
        }

        // Head: descends further than the shoulders, as it does in a real rep, so the nose
        // cross-check sees genuine motion.
        put(Lm.NOSE, centerU, shoulderV + shoulderWidth * (0.30f + 0.90f * depthFraction), CONFIDENT)

        put(Lm.LEFT_SHOULDER, centerU + halfW, shoulderV, CONFIDENT)
        put(Lm.RIGHT_SHOULDER, centerU - halfW, shoulderV, CONFIDENT)

        val armConf = if (armsVisible) CONFIDENT else 0.10f
        // Elbows bow outward as the body descends.
        val elbowOut = halfW * (1.0f + 0.55f * depthFraction)
        val elbowV = (shoulderV + WRIST_V) / 2f
        put(Lm.LEFT_ELBOW, centerU + elbowOut, elbowV, armConf)
        put(Lm.RIGHT_ELBOW, centerU - elbowOut, elbowV, armConf)
        put(Lm.LEFT_WRIST, centerU + halfW, WRIST_V, armConf)
        put(Lm.RIGHT_WRIST, centerU - halfW, WRIST_V, armConf)

        // Hips and below sit far up the frame, away from the camera.
        val hipV = shoulderV - shoulderWidth * 1.1f
        put(Lm.LEFT_HIP, centerU + halfW * 0.8f, hipV, legConfidence)
        put(Lm.RIGHT_HIP, centerU - halfW * 0.8f, hipV, legConfidence)
        put(Lm.LEFT_KNEE, centerU + halfW * 0.7f, hipV - shoulderWidth * 0.9f, legConfidence)
        put(Lm.RIGHT_KNEE, centerU - halfW * 0.7f, hipV - shoulderWidth * 0.9f, legConfidence)
        put(Lm.LEFT_ANKLE, centerU + halfW * 0.6f, hipV - shoulderWidth * 1.8f, legConfidence)
        put(Lm.RIGHT_ANKLE, centerU - halfW * 0.6f, hipV - shoulderWidth * 1.8f, legConfidence)

        val worldLm = if (world) worldFor(depthFraction, armConf) else emptyList()
        return PoseFrame(tMs, WIDTH, HEIGHT, lm, worldLm)
    }

    /**
     * World landmarks placed so the 3-D elbow angle closes from lockout to a standard bottom
     * exactly in step with the primary signal, which is what the agreement check compares.
     */
    private fun worldFor(depthFraction: Float, armConf: Float): List<Landmark> {
        val theta = DepthSignal.ELBOW_TOP_DEG -
            depthFraction * (DepthSignal.ELBOW_TOP_DEG - DepthSignal.ELBOW_BOTTOM_DEG)
        val rad = theta * PI.toFloat() / 180f

        val lm = MutableList(Lm.COUNT) { Landmark.ZERO }
        val upperArm = 0.33f
        val forearm = 0.26f

        // Elbow at the origin of each arm, shoulder straight above it, wrist swung round by theta,
        // so the enclosed 3-D angle is exactly theta.
        for (side in listOf(1, -1)) {
            val shoulder = if (side > 0) Lm.LEFT_SHOULDER else Lm.RIGHT_SHOULDER
            val elbow = if (side > 0) Lm.LEFT_ELBOW else Lm.RIGHT_ELBOW
            val wrist = if (side > 0) Lm.LEFT_WRIST else Lm.RIGHT_WRIST
            val ex = side * 0.20f
            lm[elbow] = Landmark(ex, 0f, 0f)
            lm[shoulder] = Landmark(ex, upperArm, 0f)
            lm[wrist] = Landmark(
                ex + side * forearm * kotlin.math.sin(rad),
                forearm * cos(rad),
                0f,
            )
        }
        return lm
    }

    /**
     * A plank, viewed face-on from a phone on the floor.
     *
     * [sagDegrees] bends the shoulder-hip-knee line: 0 is a straight plank, positive sags the hips
     * toward the floor, negative pikes them up. [drift] moves the hips away from where they started,
     * in shoulder widths. [wander] displaces the whole body, which is what the stability term sees.
     */
    fun plankFrame(
        tMs: Long,
        sagDegrees: Float = 0f,
        drift: Float = 0f,
        wander: Float = 0f,
        legConfidence: Float = CONFIDENT,
        shoulderWidth: Float = SHOULDER_WIDTH,
        centerU: Float = ASPECT / 2f,
    ): PoseFrame {
        val lm = MutableList(Lm.COUNT) { Landmark.ZERO }
        val halfW = shoulderWidth / 2f
        val shoulderV = WRIST_V - shoulderWidth * 1.15f
        val cu = centerU + wander

        fun put(index: Int, u: Float, v: Float, conf: Float) {
            lm[index] = Landmark(x = u / ASPECT, y = v + wander * 0.4f, z = 0f, visibility = conf, presence = conf)
        }

        // Segment lengths along the body, running up the frame away from the camera. They are
        // shorter than anatomy because a floor-level camera foreshortens everything pointing away
        // from it — and they have to keep the knee inside the frame, since a landmark outside it is
        // one the detector will (correctly) refuse to use.
        val torso = shoulderWidth * 0.90f
        val thigh = shoulderWidth * 0.80f

        val hipV = shoulderV - torso + drift * shoulderWidth
        // The sag angle bends the thigh segment relative to the torso.
        val rad = sagDegrees * PI.toFloat() / 180f
        val kneeV = hipV - thigh * cos(rad)
        val kneeOffsetU = thigh * kotlin.math.sin(rad)

        put(Lm.NOSE, cu, shoulderV + shoulderWidth * 0.5f, CONFIDENT)
        put(Lm.LEFT_SHOULDER, cu + halfW, shoulderV, CONFIDENT)
        put(Lm.RIGHT_SHOULDER, cu - halfW, shoulderV, CONFIDENT)
        put(Lm.LEFT_ELBOW, cu + halfW, (shoulderV + WRIST_V) / 2f, CONFIDENT)
        put(Lm.RIGHT_ELBOW, cu - halfW, (shoulderV + WRIST_V) / 2f, CONFIDENT)
        put(Lm.LEFT_WRIST, cu + halfW, WRIST_V, CONFIDENT)
        put(Lm.RIGHT_WRIST, cu - halfW, WRIST_V, CONFIDENT)
        put(Lm.LEFT_HIP, cu + halfW * 0.85f, hipV, CONFIDENT)
        put(Lm.RIGHT_HIP, cu - halfW * 0.85f, hipV, CONFIDENT)
        put(Lm.LEFT_KNEE, cu + halfW * 0.8f + kneeOffsetU, kneeV, legConfidence)
        put(Lm.RIGHT_KNEE, cu - halfW * 0.8f + kneeOffsetU, kneeV, legConfidence)
        put(Lm.LEFT_ANKLE, cu + halfW * 0.75f + kneeOffsetU * 2f, kneeV - thigh, legConfidence)
        put(Lm.RIGHT_ANKLE, cu - halfW * 0.75f + kneeOffsetU * 2f, kneeV - thigh, legConfidence)

        return PoseFrame(tMs, WIDTH, HEIGHT, lm, emptyList())
    }

    /** [durationMs] of plank frames at [fps]. */
    fun plankTrace(
        durationMs: Int,
        startMs: Long = 0L,
        fps: Int = 30,
        frameOf: (Long) -> PoseFrame = { plankFrame(it) },
    ): List<PoseFrame> {
        val step = (1000 / fps).toLong()
        val frames = mutableListOf<PoseFrame>()
        var t = startMs
        while (t < startMs + durationMs) {
            frames += frameOf(t)
            t += step
        }
        return frames
    }

    /**
     * A full rep as a list of frames at [fps].
     *
     * [peakDepth] below the count line produces a shallow rep; the phase durations let a test make
     * a rep too fast, too slow, or a hover that never commits.
     */
    fun rep(
        startMs: Long,
        descentMs: Int = 800,
        bottomMs: Int = 150,
        ascentMs: Int = 800,
        peakDepth: Float = 0.95f,
        restMs: Int = 300,
        fps: Int = 30,
        frameOf: (Long, Float) -> PoseFrame = { t, d -> frame(t, d) },
    ): List<PoseFrame> {
        val step = (1000 / fps).toLong()
        val frames = mutableListOf<PoseFrame>()
        var t = startMs

        fun ramp(durationMs: Int, from: Float, to: Float) {
            if (durationMs <= 0) return
            val end = t + durationMs
            while (t < end) {
                val progress = ((t - (end - durationMs)).toFloat() / durationMs).coerceIn(0f, 1f)
                // Smooth ease so velocity is never a step function, as in a real movement.
                val eased = (1f - cos(progress * PI.toFloat())) / 2f
                frames += frameOf(t, from + (to - from) * eased)
                t += step
            }
        }

        ramp(descentMs, 0f, peakDepth)
        val bottomEnd = t + bottomMs
        while (t < bottomEnd) {
            frames += frameOf(t, peakDepth)
            t += step
        }
        ramp(ascentMs, peakDepth, 0f)
        val restEnd = t + restMs
        while (t < restEnd) {
            frames += frameOf(t, 0f)
            t += step
        }
        return frames
    }

    /** [count] identical reps back to back, preceded by enough still frames for the detector to arm. */
    fun trace(
        count: Int,
        startMs: Long = 0L,
        peakDepth: Float = 0.95f,
        descentMs: Int = 800,
        bottomMs: Int = 150,
        ascentMs: Int = 800,
        restMs: Int = 300,
        fps: Int = 30,
        settleMs: Int = 600,
        frameOf: (Long, Float) -> PoseFrame = { t, d -> frame(t, d) },
    ): List<PoseFrame> {
        val step = (1000 / fps).toLong()
        val frames = mutableListOf<PoseFrame>()
        var t = startMs
        val settleEnd = t + settleMs
        while (t < settleEnd) {
            frames += frameOf(t, 0f)
            t += step
        }
        repeat(count) {
            val r = rep(t, descentMs, bottomMs, ascentMs, peakDepth, restMs, fps, frameOf)
            frames += r
            t = (r.lastOrNull()?.timestampMs ?: t) + step
        }
        return frames
    }
}
