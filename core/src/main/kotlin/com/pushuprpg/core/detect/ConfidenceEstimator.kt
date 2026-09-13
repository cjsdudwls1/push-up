package com.pushuprpg.core.detect

import com.pushuprpg.core.math.Geometry
import com.pushuprpg.core.pose.PoseFrame
import com.pushuprpg.core.pose.PoseLandmarks as Lm
import kotlin.math.abs

/**
 * Per-landmark confidence, 0..1.
 *
 * MediaPipe documents `visibility` and `presence`, but the Android Tasks API does not reliably
 * populate them, so this cannot be the only line of defence. When the model reports nothing we
 * derive confidence from geometry instead, using three independent signals:
 *
 *  1. **Frame bounds.** The model happily extrapolates landmarks past the edge of the image. With
 *     a phone on the floor the user's legs are usually outside the frame, so this alone removes
 *     most of the stray lower-body points the demo was criticised for.
 *  2. **Temporal plausibility.** A point that jumps further in one frame than a body can move is
 *     a mis-detection, not motion.
 *  3. **Bone over-length.** Projection can only ever make a limb look *shorter* than it is, never
 *     longer. A bone that measures longer than anatomy allows proves at least one endpoint is
 *     wrong — a one-sided test, which is what makes it trustworthy.
 */
class ConfidenceEstimator {

    private var prevU = FloatArray(Lm.COUNT)
    private var prevV = FloatArray(Lm.COUNT)
    private var prevTMs = 0L
    private var hasPrev = false

    fun reset() {
        hasPrev = false
        prevTMs = 0L
    }

    /**
     * Fills [out] with a confidence per landmark.
     *
     * [scale] is the current shoulder-width estimate in isotropic units; pass a non-positive value
     * before it is known, which disables the bone-length test rather than guessing at it.
     */
    fun compute(frame: PoseFrame, scale: Float, out: FloatArray) {
        require(out.size == Lm.COUNT) { "out must hold ${Lm.COUNT} values" }

        if (!frame.hasPose) {
            out.fill(0f)
            hasPrev = false
            return
        }

        val aspect = frame.aspect
        val dtMs = if (hasPrev) frame.timestampMs - prevTMs else 0L

        for (i in 0 until Lm.COUNT) {
            val lm = frame.landmarks[i]

            // 1. Frame bounds. A small tolerance allows a landmark to sit exactly on the edge,
            //    but anything meaningfully outside the image was invented by the model.
            if (lm.x < -BOUNDS_TOLERANCE || lm.x > 1f + BOUNDS_TOLERANCE ||
                lm.y < -BOUNDS_TOLERANCE || lm.y > 1f + BOUNDS_TOLERANCE
            ) {
                out[i] = 0f
                continue
            }

            var c = if (lm.hasModelConfidence) lm.modelConfidence.coerceIn(0f, 1f) else 1f

            // 2. Temporal plausibility, only over a usable interval.
            if (hasPrev && dtMs in 1..MAX_PREDICT_GAP_MS) {
                val du = lm.x * aspect - prevU[i]
                val dv = lm.y - prevV[i]
                val speed = Geometry.norm(du, dv) * 1000f / dtMs
                if (speed > MAX_LANDMARK_SPEED) {
                    c *= (MAX_LANDMARK_SPEED / speed).coerceIn(0f, 1f)
                }
            }

            out[i] = c
        }

        // 3. Bone over-length, applied to both endpoints of any impossible bone.
        if (scale > 0f) {
            for ((bone, maxRatio) in BONE_LIMITS) {
                val (a, b) = bone
                if (out[a] <= 0f && out[b] <= 0f) continue
                val len = Geometry.norm(
                    frame.landmarks[a].x * aspect - frame.landmarks[b].x * aspect,
                    frame.landmarks[a].y - frame.landmarks[b].y,
                )
                val limit = maxRatio * scale * OVER_LENGTH_TOLERANCE
                if (limit > 0f && len > limit) {
                    val penalty = (limit / len).coerceIn(0f, 1f)
                    out[a] *= penalty
                    out[b] *= penalty
                }
            }
        }

        for (i in 0 until Lm.COUNT) {
            prevU[i] = frame.landmarks[i].x * aspect
            prevV[i] = frame.landmarks[i].y
        }
        prevTMs = frame.timestampMs
        hasPrev = true
    }

    companion object {
        const val BOUNDS_TOLERANCE = 0.01f

        /**
         * Isotropic units per second. One image height in a third of a second is already far
         * beyond what a torso does; anything faster is the model changing its mind.
         */
        const val MAX_LANDMARK_SPEED = 3.0f

        /** Beyond this the previous frame says nothing useful about this one. */
        const val MAX_PREDICT_GAP_MS = 400L

        /** Perspective and landmark noise both inflate measured length; leave headroom. */
        const val OVER_LENGTH_TOLERANCE = 1.35f

        /**
         * Maximum bone length as a multiple of biacromial (shoulder) width, from standard
         * anthropometry: shoulders ≈ 0.39 m, upper arm ≈ 0.33 m, forearm ≈ 0.26 m,
         * shoulder→hip ≈ 0.50 m, thigh and shank ≈ 0.43 m each.
         */
        private val BONE_LIMITS: List<Pair<Pair<Int, Int>, Float>> = listOf(
            (Lm.LEFT_SHOULDER to Lm.LEFT_ELBOW) to 0.85f,
            (Lm.RIGHT_SHOULDER to Lm.RIGHT_ELBOW) to 0.85f,
            (Lm.LEFT_ELBOW to Lm.LEFT_WRIST) to 0.67f,
            (Lm.RIGHT_ELBOW to Lm.RIGHT_WRIST) to 0.67f,
            (Lm.LEFT_SHOULDER to Lm.LEFT_HIP) to 1.28f,
            (Lm.RIGHT_SHOULDER to Lm.RIGHT_HIP) to 1.28f,
            (Lm.LEFT_HIP to Lm.LEFT_KNEE) to 1.10f,
            (Lm.RIGHT_HIP to Lm.RIGHT_KNEE) to 1.10f,
            (Lm.LEFT_KNEE to Lm.LEFT_ANKLE) to 1.10f,
            (Lm.RIGHT_KNEE to Lm.RIGHT_ANKLE) to 1.10f,
        )
    }
}
