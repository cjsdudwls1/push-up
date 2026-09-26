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
    /** Front-to-back distance between the feet in metres, for a [StanceCheck]; NaN otherwise. */
    val stagger: Float = Float.NaN,
    /** The leg in front, for a [StanceCheck]; null otherwise. */
    val front: BodySide? = null,
    /** |h_left − h_right| in h units; large values mean one side is dropping more than the other. */
    val asymmetry: Float,
    /**
     * 0..100 from the 3-D angle at the working joint — the elbow for a pushup or a pull-up, the
     * knee for a squat. NaN when world landmarks are unavailable, or when the exercise declares no
     * [JointAngleCheck]. Read only as a cross-check.
     */
    val jointDepth: Float,
    /**
     * How far an independent part of the body has travelled along the body axis since the top, in
     * h units, defined so that it **increases as the user descends** for every exercise. NaN when
     * the exercise declares no [BodyTravelCheck], or when the landmarks it needs are not visible.
     *
     * See [BodyTravelCheck] for why each exercise watches what it watches — and why a pull-up
     * watches nothing.
     */
    val bodyDrop: Float,
)

/**
 * Computes the depth ratio `h = dot(distal − proximal, n̂) / scale` for whatever movement the
 * config describes.
 *
 * There are no exercise branches here. Everything that differs between a pushup, a squat and a
 * pull-up is a value in [Exercises]; this file is the one implementation those values drive. Per
 * side, so that a user with one limb out of frame still gets a reading, and so that the difference
 * between sides becomes a usable asymmetry signal.
 */
object DepthSignal {

    /** Elbow angle at lockout, in degrees, used to anchor the cross-check. */
    const val ELBOW_TOP_DEG = Exercises.ELBOW_TOP_DEG

    /** Elbow angle with the upper arm parallel to the floor — a standard pushup bottom. */
    const val ELBOW_BOTTOM_DEG = Exercises.ELBOW_BOTTOM_DEG

    /** Knee angle standing. */
    const val KNEE_TOP_DEG = Exercises.KNEE_TOP_DEG

    /** Knee angle with the thigh parallel to the floor. */
    const val KNEE_BOTTOM_DEG = Exercises.KNEE_BOTTOM_DEG

    fun compute(
        frame: PoseFrame,
        body: BodyFrameState,
        confidence: FloatArray,
        config: DetectorConfig,
    ): DepthSample? {
        if (body.scale <= 0f) return null
        // A hold has no depth ratio. Returning null rather than inventing one is what stops the rep
        // state machine from ever being fed a plank.
        val signal = config.descriptor.signal ?: return null

        val wL = confidence[signal.proximal.left] * confidence[signal.distal.left]
        val wR = confidence[signal.proximal.right] * confidence[signal.distal.right]

        val hL = sideRatio(frame, body, signal.proximal.left, signal.distal.left)
        val hR = sideRatio(frame, body, signal.proximal.right, signal.distal.right)

        val usableL = wL > 0f && !hL.isNaN()
        val usableR = wR > 0f && !hR.isNaN()

        val h: Float
        var asymmetry = 0f
        when {
            usableL && usableR -> {
                h = when (signal.sideCombiner) {
                    // A split stance: only the front leg bends, and averaging it with a trailing
                    // leg that barely moves halves the reading so no honest rep reaches the line.
                    // h falls with effort, so the working side is the smaller one.
                    SideCombiner.DEEPER_SIDE -> minOf(hL, hR)
                    // One side far less trusted than the other: take the good one outright rather
                    // than letting a weighted mean quietly import the bad one's error.
                    SideCombiner.CONFIDENCE_WEIGHTED -> when {
                        wL < SIDE_DOMINANCE * wR -> hR
                        wR < SIDE_DOMINANCE * wL -> hL
                        else -> (hL * wL + hR * wR) / (wL + wR)
                    }
                }
                // An asymmetric movement is asymmetric by definition; reporting that as a fault
                // would flag every honest rep.
                asymmetry = if (signal.sideCombiner == SideCombiner.DEEPER_SIDE) 0f else abs(hL - hR)
            }
            usableL -> h = hL
            usableR -> h = hR
            else -> return null
        }

        val joint = signal.jointCheck?.let { jointDepth(frame, confidence, config, it) } ?: Float.NaN
        // Side on, the declared witness is not asked: the side view brings its own. See [SideView].
        val travel = if (body.sideOn) Float.NaN
        else signal.bodyTravel?.let { bodyTravel(frame, body, confidence, it) } ?: Float.NaN

        val source = when {
            maxOf(wL, wR) >= config.minSideWeight -> DepthSource.PRIMARY
            signal.allowJointFallback && !joint.isNaN() -> DepthSource.ELBOW_FALLBACK
            else -> DepthSource.NONE
        }

        if (source == DepthSource.NONE) return null

        val stance = signal.stance?.let { stance(frame) }
        return DepthSample(
            h = h, source = source, asymmetry = asymmetry, jointDepth = joint, bodyDrop = travel,
            stagger = stance?.first ?: Float.NaN, front = stance?.second,
        )
    }

    /**
     * The measured segment projected onto the body normal, in shoulder widths.
     *
     * The normaliser is what makes this worth using: shoulder width is close to perpendicular to
     * the optical axis and physically constant, so `f` and `Z` cancel and the reading survives the
     * user drifting nearer to or further from the phone mid-set.
     *
     * Side on it is the segment's length in the picture instead, over the torso's: the movement is
     * in the picture's own plane there, and the torso's normal tilts with the body through the rep.
     */
    private fun sideRatio(frame: PoseFrame, body: BodyFrameState, proximal: Int, distal: Int): Float {
        val du = frame.u(distal) - frame.u(proximal)
        val dv = frame.v(distal) - frame.v(proximal)
        if (body.scale < Geometry.EPSILON) return Float.NaN
        if (body.sideOn) return Geometry.norm(du, dv) / body.scale
        return Geometry.dot(du, dv, body.nU, body.nV) / body.scale
    }

    /**
     * Independent depth estimate from a 3-D joint angle.
     *
     * This must come from world landmarks, never from the projected 2-D angle: in this camera
     * geometry the working limb swings away from the lens as the user descends, so the projected
     * angle can barely move across a full-depth rep. Useful as a cross-check, unusable as the gauge.
     * The one movement where it is strong rather than marginal is the pull-up, where the subject
     * hangs side-on to the flexing elbow — which is why the pull-up is allowed to require it.
     */
    fun jointDepth(
        frame: PoseFrame,
        confidence: FloatArray,
        config: DetectorConfig,
        check: JointAngleCheck,
    ): Float {
        if (!frame.hasWorld) return Float.NaN
        val w = frame.worldLandmarks

        val cL = confidence[check.vertex.left]
        val cR = confidence[check.vertex.right]
        val aL = if (cL >= config.minCoreConfidence) {
            Geometry.angleDeg3(w[check.proximal.left], w[check.vertex.left], w[check.distal.left])
        } else Float.NaN
        val aR = if (cR >= config.minCoreConfidence) {
            Geometry.angleDeg3(w[check.proximal.right], w[check.vertex.right], w[check.distal.right])
        } else Float.NaN

        val theta = when {
            !aL.isNaN() && !aR.isNaN() -> (aL * cL + aR * cR) / (cL + cR)
            !aL.isNaN() -> aL
            !aR.isNaN() -> aR
            else -> return Float.NaN
        }

        return (100f * (check.topDeg - theta) / (check.topDeg - check.bottomDeg)).coerceIn(0f, 100f)
    }

    /**
     * The feet's front-to-back distance in metres, and which is in front, from world landmarks.
     *
     * "Front" is the way the body faces: square to both the hip line and the spine, pointing the
     * side the nose is on. Not "horizontal": world landmarks are in the camera's frame, and with the
     * phone tilted up 30 degrees a horizontal plane in it runs through the torso, which put the nose
     * behind the shoulders and every lunge on the wrong leg. Null without world landmarks.
     */
    fun stance(frame: PoseFrame): Pair<Float, BodySide>? {
        if (!frame.hasWorld) return null
        val w = frame.worldLandmarks
        fun mid(a: Int, b: Int) = floatArrayOf(
            (w[a].x + w[b].x) / 2f, (w[a].y + w[b].y) / 2f, (w[a].z + w[b].z) / 2f,
        )
        val hip = floatArrayOf(
            w[Lm.LEFT_HIP].x - w[Lm.RIGHT_HIP].x,
            w[Lm.LEFT_HIP].y - w[Lm.RIGHT_HIP].y,
            w[Lm.LEFT_HIP].z - w[Lm.RIGHT_HIP].z,
        )
        val shoulders = mid(Lm.LEFT_SHOULDER, Lm.RIGHT_SHOULDER)
        val hips = mid(Lm.LEFT_HIP, Lm.RIGHT_HIP)
        val spine = floatArrayOf(shoulders[0] - hips[0], shoulders[1] - hips[1], shoulders[2] - hips[2])
        // hip × spine: square to both.
        var fx = hip[1] * spine[2] - hip[2] * spine[1]
        var fy = hip[2] * spine[0] - hip[0] * spine[2]
        var fz = hip[0] * spine[1] - hip[1] * spine[0]
        val len = kotlin.math.sqrt(fx * fx + fy * fy + fz * fz)
        if (len < 1e-5f) return null
        fx /= len; fy /= len; fz /= len
        val nose = w[Lm.NOSE]
        if ((nose.x - shoulders[0]) * fx + (nose.y - shoulders[1]) * fy + (nose.z - shoulders[2]) * fz < 0f) {
            fx = -fx; fy = -fy; fz = -fz
        }
        fun along(i: Int) = w[i].x * fx + w[i].y * fy + w[i].z * fz
        val left = along(Lm.LEFT_ANKLE)
        val right = along(Lm.RIGHT_ANKLE)
        return abs(left - right) to (if (left > right) BodySide.LEFT else BodySide.RIGHT)
    }

    /**
     * How far the watched body part has travelled along the body axis, in h units, signed so it
     * increases with depth for every exercise.
     *
     * Returns NaN when either endpoint is not visible at all, which the caller must treat as
     * "unknown" rather than "no movement" — reading a missing landmark as zero travel would reject
     * every rep the moment the legs left frame.
     */
    fun bodyTravel(
        frame: PoseFrame,
        body: BodyFrameState,
        confidence: FloatArray,
        check: BodyTravelCheck,
    ): Float {
        if (body.scale < Geometry.EPSILON) return Float.NaN
        val fromU = pointU(frame, check.from); val fromV = pointV(frame, check.from)
        val toU = pointU(frame, check.to); val toV = pointV(frame, check.to)
        if (minOf(pointConfidence(confidence, check.from), pointConfidence(confidence, check.to)) <= 0f) {
            return Float.NaN
        }
        val extent = Geometry.dot(toU - fromU, toV - fromV, body.nU, body.nV) / body.scale
        return if (check.invert) -extent else extent
    }

    private fun pointU(frame: PoseFrame, p: BodyPoint): Float = when (p) {
        is BodyPoint.Single -> frame.u(p.index)
        is BodyPoint.Midpoint -> (frame.u(p.pair.left) + frame.u(p.pair.right)) / 2f
    }

    private fun pointV(frame: PoseFrame, p: BodyPoint): Float = when (p) {
        is BodyPoint.Single -> frame.v(p.index)
        is BodyPoint.Midpoint -> (frame.v(p.pair.left) + frame.v(p.pair.right)) / 2f
    }

    private fun pointConfidence(confidence: FloatArray, p: BodyPoint): Float = when (p) {
        is BodyPoint.Single -> confidence[p.index]
        is BodyPoint.Midpoint -> minOf(confidence[p.pair.left], confidence[p.pair.right])
    }

    /** Below this ratio a side is considered so much worse than the other that it is dropped. */
    const val SIDE_DOMINANCE = 0.25f
}
