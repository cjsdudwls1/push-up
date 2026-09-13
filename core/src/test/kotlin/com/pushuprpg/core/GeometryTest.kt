package com.pushuprpg.core

import com.pushuprpg.core.math.Geometry
import com.pushuprpg.core.pose.Landmark
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeometryTest {

    @Test
    fun `right angle is ninety degrees`() {
        val angle = Geometry.angleDeg(0f, 1f, 0f, 0f, 1f, 0f)
        assertTrue(abs(angle - 90f) < 0.01f, "expected 90, got $angle")
    }

    @Test
    fun `straight arm is one hundred eighty degrees`() {
        val angle = Geometry.angleDeg(0f, 0f, 1f, 0f, 2f, 0f)
        assertTrue(abs(angle - 180f) < 0.01f, "expected 180, got $angle")
    }

    @Test
    fun `collapsed landmarks yield NaN rather than a plausible angle`() {
        val angle = Geometry.angleDeg(0f, 0f, 0f, 0f, 1f, 0f)
        assertTrue(angle.isNaN(), "collapsed arms must report unknown, got $angle")
    }

    @Test
    fun `three dimensional elbow angle uses all axes`() {
        // A forearm that swings away from the camera is flat in 2D but bent in 3D. This is exactly
        // the pushup geometry, and why the elbow cross-check reads world landmarks.
        val shoulder = Landmark(0f, 0f, 0f)
        val elbow = Landmark(0f, 1f, 0f)
        val wrist = Landmark(0f, 2f, 1f)

        // Really bent 45 degrees short of lockout...
        val a = Geometry.angleDeg3(shoulder, elbow, wrist)
        assertTrue(abs(a - 135f) < 0.01f, "expected 135 in 3D, got $a")

        // ...but the camera sees a perfectly straight arm. Reading the projected angle would
        // report a locked-out elbow for an arm that is nowhere near locked out.
        val flat2d = Geometry.angleDeg(shoulder.x, shoulder.y, elbow.x, elbow.y, wrist.x, wrist.y)
        assertTrue(abs(flat2d - 180f) < 0.01f, "projected angle should look straight, got $flat2d")
    }

    @Test
    fun `inverseLerp maps into unit range and clamps`() {
        assertEquals(0.5f, Geometry.inverseLerp(0f, 10f, 5f))
        assertEquals(0f, Geometry.inverseLerp(0f, 10f, -3f))
        assertEquals(1f, Geometry.inverseLerp(0f, 10f, 99f))
    }

    @Test
    fun `inverseLerp on a collapsed range reports zero not one`() {
        assertEquals(0f, Geometry.inverseLerp(5f, 5f, 5f))
        assertEquals(0f, Geometry.inverseLerp(5f, 5f, 99f))
    }

    @Test
    fun `inverseLerp works when the range runs downward`() {
        // The depth ratio decreases as the user descends, so from > to is the normal case.
        assertEquals(0.5f, Geometry.inverseLerp(10f, 0f, 5f))
        assertEquals(1f, Geometry.inverseLerp(10f, 0f, -1f))
    }

    @Test
    fun `rot90 is perpendicular and length preserving`() {
        val (px, py) = Geometry.rot90(3f, 4f)
        assertEquals(0f, Geometry.dot(3f, 4f, px, py))
        assertEquals(5f, Geometry.norm(px, py))
    }

    @Test
    fun `smoothstep is clamped and monotonic`() {
        assertEquals(0f, Geometry.smoothstep(0.5f, 0.75f, 0.2f))
        assertEquals(1f, Geometry.smoothstep(0.5f, 0.75f, 0.9f))
        assertEquals(0.5f, Geometry.smoothstep(0.5f, 0.75f, 0.625f))
        assertTrue(Geometry.smoothstep(0.5f, 0.75f, 0.6f) < Geometry.smoothstep(0.5f, 0.75f, 0.7f))
    }

    @Test
    fun `cosineSimilarity spots vectors moving together`() {
        assertTrue(Geometry.cosineSimilarity(1f, 0f, 2f, 0f) > 0.99f)
        assertTrue(Geometry.cosineSimilarity(1f, 0f, -1f, 0f) < -0.99f)
        assertTrue(Geometry.cosineSimilarity(0f, 0f, 1f, 0f).isNaN())
    }
}
