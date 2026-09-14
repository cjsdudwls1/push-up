package com.pushuprpg.core.detect

import com.pushuprpg.core.math.Geometry
import com.pushuprpg.core.pose.PoseFrame
import com.pushuprpg.core.pose.PoseLandmarks as Lm
import kotlin.math.abs

/**
 * The raw depth reading for one frame, before smoothing and calibration.
 *
 * [h] is the dimensionless ratio the whole pipeline is built on; [jointDepth] is an independent
 * estimate on the 0..100 scale used only to cross-check it.
 */
data class DepthSample(
    val h: Float,
    val source: DepthSource,
    /** |h_left − h_right| in h units; large values mean one side is dropping more than the other. */
    val asymmetry: Float,
    /**
     * 0..100 from the 3-D angle at the working joint — the elbow for a pushup, the knee for a
     * squat. NaN when world landmarks are unavailable. Read only as a cross-check.
     */
    val jointDepth: Float,
    /**
     * How far an independent part of the body has travelled along the body axis since the top, in
     * h units, defined so that it **increases as the user descends** for every exercise.
     *
     * The point is to watch something the primary signal does not: a pushup can be faked by moving
     * the wrists alone, a squat by tilting the pelvis. Which part is useful differs by movement —
     * the head drops relative to the shoulders in a pushup but is rigid against them in a squat, so
     * the squat watches the shoulders against the ankles instead.
     */
    val bodyDrop: Float,
)

/**
 * Computes the depth ratio `h = dot(wrist − shoulder, n̂) / scale`.
 *
 * Per side, so that a user with one arm out of frame still gets a reading, and so that the
 * difference between sides becomes a usable asymmetry signal.
 */
object DepthSignal {

    /** Elbow angle at lockout, in degrees, used to anchor the cross-check. */
    const val ELBOW_TOP_DEG = 172f

    /** Elbow angle with the upper arm parallel to the floor — a standard pushup bottom. */
    const val ELBOW_BOTTOM_DEG = 82f

    /** Knee angle standing. */
    const val KNEE_TOP_DEG = 172f

    /** Knee angle with the thigh parallel to the floor. */
    const val KNEE_BOTTOM_DEG = 88f

    fun compute(
        frame: PoseFrame,
        body: BodyFrameState,
        confidence: FloatArray,
        config: DetectorConfig,
    ): DepthSample? {
        if (body.scale <= 0f) return null
        if (config.exercise == ExerciseType.SQUAT) return computeSquat(frame, body, confidence, config)

        val wL = confidence[Lm.LEFT_SHOULDER] * confidence[Lm.LEFT_WRIST]
        val wR = confidence[Lm.RIGHT_SHOULDER] * confidence[Lm.RIGHT_WRIST]

        val hL = sideRatio(frame, body, Lm.LEFT_SHOULDER, Lm.LEFT_WRIST)
        val hR = sideRatio(frame, body, Lm.RIGHT_SHOULDER, Lm.RIGHT_WRIST)

        val usableL = wL > 0f && !hL.isNaN()
        val usableR = wR > 0f && !hR.isNaN()

        val h: Float
        var asymmetry = 0f
        when {
            usableL && usableR -> {
                // One side far less trusted than the other: take the good one outright rather than
                // letting a weighted mean quietly import the bad one's error.
                h = when {
                    wL < SIDE_DOMINANCE * wR -> hR
                    wR < SIDE_DOMINANCE * wL -> hL
                    else -> (hL * wL + hR * wR) / (wL + wR)
                }
                asymmetry = abs(hL - hR)
            }
            usableL -> h = hL
            usableR -> h = hR
            else -> return null
        }

        val elbow = elbowDepth(frame, confidence, config)
        val drop = noseDrop(frame, body, confidence)

        val source = if (maxOf(wL, wR) >= config.minSideWeight) {
            DepthSource.PRIMARY
        } else if (!elbow.isNaN()) {
            DepthSource.ELBOW_FALLBACK
        } else {
            DepthSource.NONE
        }

        if (source == DepthSource.NONE) return null

        return DepthSample(h = h, source = source, asymmetry = asymmetry, jointDepth = elbow, bodyDrop = drop)
    }

    /**
     * Squat depth: how far the hip sits above the knee, along the body axis, in shoulder widths.
     *
     * This is the anatomical definition of squat depth — "hip crease below the knee" — expressed
     * directly, which is why it is worth using rather than reaching for the obvious alternatives.
     * The knee *angle* is unusable for the same reason the elbow angle is unusable for a pushup: a
     * user faces the camera, so the joint flexes in the plane pointing away from it and the
     * projection barely moves. Hip height above the *ankle* would work, but ankles leave the frame
     * far more often than knees do.
     *
     * Shoulder width is the normaliser again, and for the same reason: it is close to perpendicular
     * to the optical axis and physically constant, so `f` and `Z` cancel and the reading survives
     * the user drifting nearer to or further from the phone mid-set.
     *
     * The scale runs from about +1.05 standing, through 0 at parallel — hip level with knee — to
     * negative below parallel. It decreases as the user descends, exactly like the pushup ratio, so
     * the calibrator and the state machine need no special case at all.
     */
    private fun computeSquat(
        frame: PoseFrame,
        body: BodyFrameState,
        confidence: FloatArray,
        config: DetectorConfig,
    ): DepthSample? {
        val wL = confidence[Lm.LEFT_HIP] * confidence[Lm.LEFT_KNEE]
        val wR = confidence[Lm.RIGHT_HIP] * confidence[Lm.RIGHT_KNEE]

        val hL = squatSideRatio(frame, body, Lm.LEFT_HIP, Lm.LEFT_KNEE)
        val hR = squatSideRatio(frame, body, Lm.RIGHT_HIP, Lm.RIGHT_KNEE)

        val usableL = wL > 0f && !hL.isNaN()
        val usableR = wR > 0f && !hR.isNaN()

        val h: Float
        var asymmetry = 0f
        when {
            usableL && usableR -> {
                h = when {
                    wL < SIDE_DOMINANCE * wR -> hR
                    wR < SIDE_DOMINANCE * wL -> hL
                    else -> (hL * wL + hR * wR) / (wL + wR)
                }
                asymmetry = abs(hL - hR)
            }
            usableL -> h = hL
            usableR -> h = hR
            else -> return null
        }

        if (maxOf(wL, wR) < config.minSideWeight) return null

        return DepthSample(
            h = h,
            source = DepthSource.PRIMARY,
            asymmetry = asymmetry,
            jointDepth = kneeDepth(frame, confidence, config),
            bodyDrop = shoulderDropOverAnkles(frame, body, confidence),
        )
    }

    /** Hip-above-knee along the body normal, in shoulder widths. Positive while the hip is higher. */
    private fun squatSideRatio(frame: PoseFrame, body: BodyFrameState, hip: Int, knee: Int): Float {
        val du = frame.u(knee) - frame.u(hip)
        val dv = frame.v(knee) - frame.v(hip)
        if (body.scale < Geometry.EPSILON) return Float.NaN
        return Geometry.dot(du, dv, body.nU, body.nV) / body.scale
    }

    private fun sideRatio(frame: PoseFrame, body: BodyFrameState, shoulder: Int, wrist: Int): Float {
        val du = frame.u(wrist) - frame.u(shoulder)
        val dv = frame.v(wrist) - frame.v(shoulder)
        if (body.scale < Geometry.EPSILON) return Float.NaN
        return Geometry.dot(du, dv, body.nU, body.nV) / body.scale
    }

    /**
     * Independent depth estimate from the 3-D elbow angle.
     *
     * This must come from world landmarks, never from the projected 2-D angle: in this camera
     * geometry the forearm swings away from the lens as the user descends, so the projected angle
     * can barely move across a full-depth rep. Useful as a cross-check, unusable as the gauge.
     */
    fun elbowDepth(frame: PoseFrame, confidence: FloatArray, config: DetectorConfig): Float {
        if (!frame.hasWorld) return Float.NaN
        val w = frame.worldLandmarks

        val cL = confidence[Lm.LEFT_ELBOW]
        val cR = confidence[Lm.RIGHT_ELBOW]
        val aL = if (cL >= config.minCoreConfidence) {
            Geometry.angleDeg3(w[Lm.LEFT_SHOULDER], w[Lm.LEFT_ELBOW], w[Lm.LEFT_WRIST])
        } else Float.NaN
        val aR = if (cR >= config.minCoreConfidence) {
            Geometry.angleDeg3(w[Lm.RIGHT_SHOULDER], w[Lm.RIGHT_ELBOW], w[Lm.RIGHT_WRIST])
        } else Float.NaN

        val theta = when {
            !aL.isNaN() && !aR.isNaN() -> (aL * cL + aR * cR) / (cL + cR)
            !aL.isNaN() -> aL
            !aR.isNaN() -> aR
            else -> return Float.NaN
        }

        return (100f * (ELBOW_TOP_DEG - theta) / (ELBOW_TOP_DEG - ELBOW_BOTTOM_DEG)).coerceIn(0f, 100f)
    }

    /**
     * Independent depth estimate from the 3-D knee angle — the squat's counterpart to the elbow.
     *
     * Same reasoning: a user faces the camera, so the knee flexes in the plane pointing away from
     * it and the projected angle barely moves across a full squat. Only world landmarks see it.
     */
    fun kneeDepth(frame: PoseFrame, confidence: FloatArray, config: DetectorConfig): Float {
        if (!frame.hasWorld) return Float.NaN
        val w = frame.worldLandmarks

        val cL = confidence[Lm.LEFT_KNEE]
        val cR = confidence[Lm.RIGHT_KNEE]
        val aL = if (cL >= config.minCoreConfidence) {
            Geometry.angleDeg3(w[Lm.LEFT_HIP], w[Lm.LEFT_KNEE], w[Lm.LEFT_ANKLE])
        } else Float.NaN
        val aR = if (cR >= config.minCoreConfidence) {
            Geometry.angleDeg3(w[Lm.RIGHT_HIP], w[Lm.RIGHT_KNEE], w[Lm.RIGHT_ANKLE])
        } else Float.NaN

        val theta = when {
            !aL.isNaN() && !aR.isNaN() -> (aL * cL + aR * cR) / (cL + cR)
            !aL.isNaN() -> aL
            !aR.isNaN() -> aR
            else -> return Float.NaN
        }
        return (100f * (KNEE_TOP_DEG - theta) / (KNEE_TOP_DEG - KNEE_BOTTOM_DEG)).coerceIn(0f, 100f)
    }

    /**
     * How far the shoulders have come down toward the ankles, in h units, negated so it increases
     * with depth like every other [DepthSample.bodyDrop].
     *
     * A squat lowers the whole upper body; tilting the pelvis at the camera does not. That is the
     * distinction this exists to draw.
     */
    fun shoulderDropOverAnkles(
        frame: PoseFrame,
        body: BodyFrameState,
        confidence: FloatArray,
    ): Float {
        val ankleConf = minOf(confidence[Lm.LEFT_ANKLE], confidence[Lm.RIGHT_ANKLE])
        if (ankleConf <= 0f || body.scale < Geometry.EPSILON) return Float.NaN
        val ankleU = (frame.u(Lm.LEFT_ANKLE) + frame.u(Lm.RIGHT_ANKLE)) / 2f
        val ankleV = (frame.v(Lm.LEFT_ANKLE) + frame.v(Lm.RIGHT_ANKLE)) / 2f
        val extent = Geometry.dot(
            ankleU - body.shoulderU, ankleV - body.shoulderV,
            body.nU, body.nV,
        ) / body.scale
        return -extent
    }

    /**
     * How far the nose sits below the shoulder line, along n̂, in h units.
     *
     * The head descends with the chest in a real pushup but stays put when someone waves an arm at
     * the phone, which is what makes this a useful second opinion when world landmarks are absent.
     */
    fun noseDrop(frame: PoseFrame, body: BodyFrameState, confidence: FloatArray): Float {
        if (confidence[Lm.NOSE] <= 0f || body.scale < Geometry.EPSILON) return Float.NaN
        val du = frame.u(Lm.NOSE) - body.shoulderU
        val dv = frame.v(Lm.NOSE) - body.shoulderV
        return Geometry.dot(du, dv, body.nU, body.nV) / body.scale
    }

    /** Below this ratio a side is considered so much worse than the other that it is dropped. */
    const val SIDE_DOMINANCE = 0.25f
}
