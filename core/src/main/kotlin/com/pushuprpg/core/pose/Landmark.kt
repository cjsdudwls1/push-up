package com.pushuprpg.core.pose

/**
 * One body landmark, in the coordinate space MediaPipe hands back for *normalized* landmarks:
 * [x] and [y] are fractions of the image width/height (0..1, origin top-left), and [z] is depth
 * relative to the hip midpoint.
 *
 * [visibility] and [presence] are the model's own confidence scores (0..1), or [UNKNOWN] when the
 * Android Tasks API did not populate them — it returns them as absent often enough that no code
 * here may assume they exist. [com.pushuprpg.core.detect.ConfidenceEstimator] derives a confidence
 * from geometry when that happens.
 *
 * Never read [z] for pushup detection. It is anchored to the hip midpoint, which with a phone on
 * the floor is the farthest and most-occluded point in the scene — the single least reliable
 * reference available. Real 3-D comes from world landmarks, where the hip origin cancels in the
 * difference vectors.
 *
 * This type is deliberately plain: `:core` never sees a MediaPipe class, so every rule in this
 * module is testable from a plain JVM against hand-written fixtures.
 */
data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val visibility: Float = UNKNOWN,
    val presence: Float = UNKNOWN,
) {
    /** True when the model actually reported confidence for this point. */
    val hasModelConfidence: Boolean
        get() = visibility >= 0f && presence >= 0f

    /**
     * The model's own confidence, or [UNKNOWN] if it did not report one.
     * A point needs both scores: `visibility` asks whether it is unoccluded and in frame,
     * `presence` whether the body part is in the image at all.
     */
    val modelConfidence: Float
        get() = if (hasModelConfidence) minOf(visibility, presence) else UNKNOWN

    companion object {
        /** Sentinel for "the model did not report this score". */
        const val UNKNOWN = -1f

        val ZERO = Landmark(0f, 0f, 0f, UNKNOWN, UNKNOWN)
    }
}
