package com.pushuprpg.core.math

import com.pushuprpg.core.pose.Landmark
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.sqrt

/** Small 2-D helpers shared by the exercise detectors. All angles are in degrees. */
object Geometry {

    /** Straight-line distance in normalized image units. */
    fun distance(a: Landmark, b: Landmark): Float = hypot(a.x - b.x, a.y - b.y)

    /**
     * Interior angle at [vertex] formed by [a]-[vertex]-[b], in degrees (0..180).
     *
     * Returns [Float.NaN] when either arm has zero length, which happens when the model collapses
     * two landmarks onto the same pixel. Callers must treat NaN as "unknown", never as 0.
     */
    fun angleDeg(a: Landmark, vertex: Landmark, b: Landmark): Float {
        val v1x = a.x - vertex.x
        val v1y = a.y - vertex.y
        val v2x = b.x - vertex.x
        val v2y = b.y - vertex.y

        val len1 = sqrt(v1x * v1x + v1y * v1y)
        val len2 = sqrt(v2x * v2x + v2y * v2y)
        if (len1 < EPSILON || len2 < EPSILON) return Float.NaN

        val cos = ((v1x * v2x + v1y * v2y) / (len1 * len2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble()).toFloat()
    }

    /**
     * Where [value] sits between [from] and [to], as 0..1, clamped.
     *
     * Returns 0 when the range has collapsed. That default matters: a collapsed range means
     * calibration has not separated the user's top and bottom positions yet, and reporting
     * "no depth" is the safe answer — reporting 1.0 would fire a rep on the first noisy frame.
     */
    fun inverseLerp(from: Float, to: Float, value: Float): Float {
        val span = to - from
        if (kotlin.math.abs(span) < EPSILON) return 0f
        return ((value - from) / span).coerceIn(0f, 1f)
    }

    fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

    const val EPSILON = 1e-6f
}
