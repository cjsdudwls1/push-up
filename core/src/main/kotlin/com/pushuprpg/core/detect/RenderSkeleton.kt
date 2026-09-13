package com.pushuprpg.core.detect

import com.pushuprpg.core.math.Geometry
import com.pushuprpg.core.pose.PoseFrame
import com.pushuprpg.core.pose.PoseLandmarks as Lm

/** A joint to draw. [alpha] already encodes confidence, so the renderer just uses it. */
data class RenderJoint(val index: Int, val x: Float, val y: Float, val alpha: Float)

/** A limb to draw between two joints. */
data class RenderBone(val a: Int, val b: Int, val alpha: Float)

/**
 * What the overlay should draw this frame.
 *
 * Coordinates are aspect-corrected isotropic units (1.0 = one image height); the UI applies
 * mirroring, because the detection maths is mirror-invariant and must stay that way.
 */
data class RenderSkeleton(
    val joints: List<RenderJoint>,
    val bones: List<RenderBone>,
    /** True while the current rep is past the 깊게 line — the overlay latches its accent colour. */
    val highlight: Boolean,
    val aspect: Float,
) {
    companion object {
        val EMPTY = RenderSkeleton(emptyList(), emptyList(), false, 1f)
    }
}

/** How much of the body the overlay shows. */
enum class SkeletonMode {
    /** Nothing drawn. The camera image is left completely clear. */
    OFF,

    /** Arms and shoulder line only — the part that actually carries the rep. The default. */
    MINIMAL,

    /** Adds the torso and, when genuinely visible, the legs. */
    FULL,
}

/**
 * Builds the overlay geometry.
 *
 * Two rules here are what fix the stray lower-body dots the demo was criticised for, and they are
 * enforced structurally rather than by the renderer remembering to check:
 *
 *  1. **A bone is drawn only when both of its endpoints are confident**, and a joint only when it
 *     belongs to at least one drawn bone. A knee the model is guessing at therefore produces no
 *     line *and* no dot — there is no code path that can draw one.
 *  2. **[SkeletonMode.MINIMAL] excludes the legs by allowlist**, not by threshold. With a phone on
 *     the floor the legs are usually out of frame or far away, so in the default mode they are not
 *     candidates for drawing at all.
 *
 * Alpha fades smoothly with confidence instead of switching on and off, so a joint at the edge of
 * detectability dissolves rather than strobing.
 */
class SkeletonBuilder(private val config: DetectorConfig) {

    fun build(
        frame: PoseFrame,
        confidence: FloatArray,
        mode: SkeletonMode,
        highlight: Boolean,
    ): RenderSkeleton {
        if (mode == SkeletonMode.OFF || !frame.hasPose) return RenderSkeleton.EMPTY

        val segments = when (mode) {
            SkeletonMode.OFF -> return RenderSkeleton.EMPTY
            SkeletonMode.MINIMAL -> MINIMAL_SEGMENTS
            SkeletonMode.FULL -> FULL_SEGMENTS
        }

        val bones = ArrayList<RenderBone>(segments.size)
        val drawn = HashSet<Int>(segments.size * 2)

        for ((a, b) in segments) {
            val alpha = minOf(fade(confidence[a]), fade(confidence[b]))
            if (alpha <= MIN_VISIBLE_ALPHA) continue
            bones.add(RenderBone(a, b, alpha))
            drawn.add(a)
            drawn.add(b)
        }

        val joints = drawn.map { i ->
            RenderJoint(i, frame.u(i), frame.v(i), fade(confidence[i]))
        }.sortedBy { it.index }

        return RenderSkeleton(joints, bones, highlight, frame.aspect)
    }

    private fun fade(c: Float): Float =
        Geometry.smoothstep(config.renderFadeLow, config.renderFadeHigh, c)

    companion object {
        /** Below this a joint contributes nothing but visual noise. */
        const val MIN_VISIBLE_ALPHA = 0.05f

        val MINIMAL_SEGMENTS: Array<Pair<Int, Int>> = arrayOf(
            Lm.LEFT_SHOULDER to Lm.RIGHT_SHOULDER,
            Lm.LEFT_SHOULDER to Lm.LEFT_ELBOW,
            Lm.LEFT_ELBOW to Lm.LEFT_WRIST,
            Lm.RIGHT_SHOULDER to Lm.RIGHT_ELBOW,
            Lm.RIGHT_ELBOW to Lm.RIGHT_WRIST,
        )

        val FULL_SEGMENTS: Array<Pair<Int, Int>> = MINIMAL_SEGMENTS + arrayOf(
            Lm.LEFT_SHOULDER to Lm.LEFT_HIP,
            Lm.RIGHT_SHOULDER to Lm.RIGHT_HIP,
            Lm.LEFT_HIP to Lm.RIGHT_HIP,
            Lm.LEFT_HIP to Lm.LEFT_KNEE,
            Lm.RIGHT_HIP to Lm.RIGHT_KNEE,
            Lm.LEFT_KNEE to Lm.LEFT_ANKLE,
            Lm.RIGHT_KNEE to Lm.RIGHT_ANKLE,
        )
    }
}
