package com.pushuprpg.core

import com.pushuprpg.core.math.Geometry
import com.pushuprpg.core.pose.Landmark
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeometryTest {

    private fun lm(x: Float, y: Float) = Landmark(x, y, 0f, 1f, 1f)

    @Test
    fun `right angle is ninety degrees`() {
        val angle = Geometry.angleDeg(lm(0f, 1f), lm(0f, 0f), lm(1f, 0f))
        assertTrue(abs(angle - 90f) < 0.01f, "expected 90, got $angle")
    }

    @Test
    fun `straight arm is one hundred eighty degrees`() {
        val angle = Geometry.angleDeg(lm(0f, 0f), lm(1f, 0f), lm(2f, 0f))
        assertTrue(abs(angle - 180f) < 0.01f, "expected 180, got $angle")
    }

    @Test
    fun `collapsed landmarks yield NaN rather than a plausible angle`() {
        val angle = Geometry.angleDeg(lm(0f, 0f), lm(0f, 0f), lm(1f, 0f))
        assertTrue(angle.isNaN(), "collapsed arms must report unknown, got $angle")
    }

    @Test
    fun `inverseLerp maps into unit range and clamps`() {
        assertEquals(0.5f, Geometry.inverseLerp(0f, 10f, 5f))
        assertEquals(0f, Geometry.inverseLerp(0f, 10f, -3f))
        assertEquals(1f, Geometry.inverseLerp(0f, 10f, 99f))
    }

    @Test
    fun `inverseLerp on a collapsed range reports zero not one`() {
        // A collapsed range means calibration has not separated top from bottom yet.
        // Returning 1.0 here would fire a rep on the first noisy frame of a session.
        assertEquals(0f, Geometry.inverseLerp(5f, 5f, 5f))
        assertEquals(0f, Geometry.inverseLerp(5f, 5f, 99f))
    }

    @Test
    fun `inverseLerp works when the range runs downward`() {
        // The depth signal decreases as the user descends, so `from` > `to` is the normal case.
        assertEquals(0.5f, Geometry.inverseLerp(10f, 0f, 5f))
        assertEquals(1f, Geometry.inverseLerp(10f, 0f, -1f))
    }
}
