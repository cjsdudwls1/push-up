package com.pushuprpg.core.math

import com.pushuprpg.core.pose.Landmark
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.sqrt

/** Small geometric helpers shared by the exercise detectors. All angles are in degrees. */
object Geometry {

    const val EPSILON = 1e-6f

    fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float = hypot(ax - bx, ay - by)

    fun dot(ax: Float, ay: Float, bx: Float, by: Float): Float = ax * bx + ay * by

    fun norm(ax: Float, ay: Float): Float = sqrt(ax * ax + ay * ay)

    /** Rotates a 2-D vector 90° counter-clockwise. */
    fun rot90(ax: Float, ay: Float): Pair<Float, Float> = -ay to ax

    /**
     * Interior angle at `vertex` formed by `a`-`vertex`-`b`, in degrees (0..180).
     *
     * Returns [Float.NaN] when either arm has zero length — which happens when the model collapses
     * two landmarks onto the same pixel. Callers must treat NaN as "unknown", never as 0.
     */
    fun angleDeg(
        ax: Float, ay: Float,
        vx: Float, vy: Float,
        bx: Float, by: Float,
    ): Float {
        val v1x = ax - vx
        val v1y = ay - vy
        val v2x = bx - vx
        val v2y = by - vy
        val len1 = norm(v1x, v1y)
        val len2 = norm(v2x, v2y)
        if (len1 < EPSILON || len2 < EPSILON) return Float.NaN
        val cos = (dot(v1x, v1y, v2x, v2y) / (len1 * len2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble()).toFloat()
    }

    /** 3-D angle at [vertex], used on world landmarks where the hip origin cancels out. */
    fun angleDeg3(a: Landmark, vertex: Landmark, b: Landmark): Float {
        val v1x = a.x - vertex.x; val v1y = a.y - vertex.y; val v1z = a.z - vertex.z
        val v2x = b.x - vertex.x; val v2y = b.y - vertex.y; val v2z = b.z - vertex.z
        val len1 = sqrt(v1x * v1x + v1y * v1y + v1z * v1z)
        val len2 = sqrt(v2x * v2x + v2y * v2y + v2z * v2z)
        if (len1 < EPSILON || len2 < EPSILON) return Float.NaN
        val cos = ((v1x * v2x + v1y * v2y + v1z * v2z) / (len1 * len2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble()).toFloat()
    }

    /**
     * Where [value] sits between [from] and [to], as 0..1, clamped.
     *
     * Returns 0 when the range has collapsed. That default matters: a collapsed range means
     * calibration has not separated the user's top and bottom yet, and reporting "no depth" is the
     * safe answer — reporting 1.0 would fire a rep on the first noisy frame of a session.
     */
    fun inverseLerp(from: Float, to: Float, value: Float): Float {
        val span = to - from
        if (abs(span) < EPSILON) return 0f
        return ((value - from) / span).coerceIn(0f, 1f)
    }

    fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

    /** Hermite fade between two edges — used so joints dissolve instead of blinking. */
    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (abs(edge1 - edge0) < EPSILON) return if (x < edge0) 0f else 1f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Cosine similarity of two 2-D vectors; NaN when either is degenerate. */
    fun cosineSimilarity(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val la = norm(ax, ay)
        val lb = norm(bx, by)
        if (la < EPSILON || lb < EPSILON) return Float.NaN
        return (dot(ax, ay, bx, by) / (la * lb)).coerceIn(-1f, 1f)
    }
}
