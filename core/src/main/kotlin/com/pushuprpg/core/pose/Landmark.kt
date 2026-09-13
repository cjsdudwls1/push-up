package com.pushuprpg.core.pose

/**
 * One body landmark, in the coordinate space MediaPipe hands back for *normalized* landmarks:
 * [x] and [y] are fractions of the image width/height (0..1, origin top-left), and [z] is depth
 * relative to the hip midpoint in roughly the same scale as [x].
 *
 * [visibility] and [presence] are the model's own confidence scores (0..1). They are the only
 * defence against the classic failure of this app: the legs are usually far from a phone lying on
 * the floor, frequently out of frame, and the model will still emit *coordinates* for them —
 * confidently wrong ones. Nothing downstream may use a landmark without checking [isReliable].
 *
 * This type is deliberately plain: `:core` never sees a MediaPipe class, so every rule in this
 * module is testable from a plain JVM with hand-written fixtures.
 */
data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val visibility: Float = 0f,
    val presence: Float = 0f,
) {
    /**
     * Whether this landmark may be used for maths or drawn on screen.
     *
     * MediaPipe reports `visibility` (is the point inside the frame and unoccluded) and
     * `presence` (is the body part in the image at all) separately; a point needs both.
     */
    fun isReliable(threshold: Float = DEFAULT_RELIABILITY): Boolean =
        visibility >= threshold && presence >= threshold

    companion object {
        /**
         * Chosen empirically-conservative rather than permissive. At 0.5 the model still emits
         * plausible-looking ankles for a user whose feet are a metre behind the frame edge, which
         * is exactly the "stray leg dots" artefact the demo was criticised for.
         */
        const val DEFAULT_RELIABILITY = 0.65f

        val ZERO = Landmark(0f, 0f, 0f, 0f, 0f)
    }
}
