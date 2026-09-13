package com.pushuprpg.core.detect

import com.pushuprpg.core.math.Geometry
import com.pushuprpg.core.pose.PoseFrame
import com.pushuprpg.core.pose.PoseLandmarks as Lm
import kotlin.math.abs

/**
 * The raw depth reading for one frame, before smoothing and calibration.
 *
 * [h] is the dimensionless ratio the whole pipeline is built on; [elbowDepth] is an independent
 * estimate on the 0..100 scale used only to cross-check it.
 */
data class DepthSample(
    val h: Float,
    val source: DepthSource,
    /** |h_left − h_right| in h units; large values mean one side is dropping more than the other. */
    val asymmetry: Float,
    /** 0..100 from the 3-D elbow angle, or NaN when world landmarks are unavailable. */
    val elbowDepth: Float,
    /** How far the nose has descended, in h units. The fallback cross-check. */
    val noseDrop: Float,
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

    fun compute(
        frame: PoseFrame,
        body: BodyFrameState,
        confidence: FloatArray,
        config: DetectorConfig,
    ): DepthSample? {
        if (body.scale <= 0f) return null

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
        val nose = noseDrop(frame, body, confidence)

        val source = if (maxOf(wL, wR) >= config.minSideWeight) {
            DepthSource.PRIMARY
        } else if (!elbow.isNaN()) {
            DepthSource.ELBOW_FALLBACK
        } else {
            DepthSource.NONE
        }

        if (source == DepthSource.NONE) return null

        return DepthSample(h = h, source = source, asymmetry = asymmetry, elbowDepth = elbow, noseDrop = nose)
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
