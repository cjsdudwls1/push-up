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

    /** The shoulder line and the limbs the chosen movement actually reads — arms for a pushup, legs for a squat. The default. */
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
 *  2. **[SkeletonMode.MINIMAL] draws by allowlist**, not by threshold: the shoulder line plus the
 *     limbs whose landmarks the movement's signal reads. For a pushup that is the arms and nothing
 *     below, so the far-away legs are not even candidates. For a lunge it is the legs — drawing the
 *     arms there, as a fixed allowlist did, showed a user doing lunges a skeleton of their arms,
 *     and they reasonably concluded the app was watching the wrong limbs.
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
            SkeletonMode.MINIMAL -> minimalSegments
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

    /**
     * The shoulder line, plus every bone of [FULL_SEGMENTS] whose two ends the movement measures.
     * Derived from [ExerciseDescriptor.watchedLandmarks] rather than listed per exercise, so a new
     * movement draws the right limbs without anyone remembering to say so.
     */
    private val minimalSegments: Array<Pair<Int, Int>> = run {
        if (config.descriptor.signal == null) return@run ARM_SEGMENTS
        val watched = config.descriptor.watchedLandmarks
        FULL_SEGMENTS.filter { (a, b) ->
            (a == Lm.LEFT_SHOULDER && b == Lm.RIGHT_SHOULDER) || (a in watched && b in watched)
        }.toTypedArray()
    }

    companion object {
        /** Below this a joint contributes nothing but visual noise. */
        const val MIN_VISIBLE_ALPHA = 0.05f

        /** Arms and the shoulder line: what a hold, which has no signal, draws. */
        val ARM_SEGMENTS: Array<Pair<Int, Int>> = arrayOf(
            Lm.LEFT_SHOULDER to Lm.RIGHT_SHOULDER,
            Lm.LEFT_SHOULDER to Lm.LEFT_ELBOW,
            Lm.LEFT_ELBOW to Lm.LEFT_WRIST,
            Lm.RIGHT_SHOULDER to Lm.RIGHT_ELBOW,
            Lm.RIGHT_ELBOW to Lm.RIGHT_WRIST,
        )

        val FULL_SEGMENTS: Array<Pair<Int, Int>> = ARM_SEGMENTS + arrayOf(
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
