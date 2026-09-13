package com.pushuprpg.core

import com.pushuprpg.core.filter.OneEuroFilter
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OneEuroFilterTest {

    @Test
    fun `first sample passes through untouched`() {
        val f = OneEuroFilter()
        assertEquals(42.0, f.filter(42.0, 0L))
    }

    @Test
    fun `noise around a constant is attenuated`() {
        val f = OneEuroFilter()
        val noise = listOf(0.4, -0.6, 0.5, -0.3, 0.6, -0.5, 0.2, -0.4, 0.5, -0.2)
        var t = 0L
        f.filter(50.0, t)
        var worst = 0.0
        noise.forEachIndexed { i, n ->
            t += 33
            val out = f.filter(50.0 + n, t)
            if (i >= 3) worst = maxOf(worst, abs(out - 50.0))
        }
        // Raw excursions reach 0.6; the filter should hold well inside that once settled.
        assertTrue(worst < 0.3, "filter left too much jitter: $worst")
    }

    @Test
    fun `fast motion is tracked without excessive lag`() {
        val f = OneEuroFilter()
        // A 1 Hz descent/ascent over a 0..100 range, the shape of a brisk pushup.
        var t = 0L
        var maxLag = 0.0
        repeat(120) { i ->
            val truth = 50.0 + 50.0 * sin(2.0 * Math.PI * i / 30.0)
            val out = f.filter(truth, t)
            if (i > 30) maxLag = maxOf(maxLag, abs(out - truth))
            t += 33
        }
        assertTrue(maxLag < 6.0, "filter lagged real motion by $maxLag units")
    }

    @Test
    fun `a long gap re-seeds the filter and reports the discontinuity`() {
        val f = OneEuroFilter()
        f.filter(50.0, 0L)
        f.filter(50.0, 33L)
        assertTrue(!f.hadDiscontinuity)

        // App was backgrounded for 10 seconds, then a wildly different sample arrives.
        val out = f.filter(0.0, 10_033L)

        // Blending across that gap would let one stale sample drag the output somewhere the user
        // never was. Re-seeding is the honest answer, and the flag lets the rep detector abandon
        // whatever rep it thought was in flight.
        assertEquals(0.0, out)
        assertEquals(0.0, f.velocity)
        assertTrue(f.hadDiscontinuity)

        // The flag is per-call, not sticky.
        f.filter(1.0, 10_066L)
        assertTrue(!f.hadDiscontinuity)
    }

    @Test
    fun `a short burst of dropped frames is not treated as a gap`() {
        val f = OneEuroFilter()
        f.filter(50.0, 0L)
        f.filter(50.0, 33L)
        // ~9 dropped frames: inside GAP_RESET_MS, so the filter keeps its history and simply
        // tracks. 300 ms is ample time for the user to genuinely have moved, so following the new
        // sample closely is the correct answer here — only the reset flag must stay clear.
        f.filter(0.0, 333L)
        assertTrue(!f.hadDiscontinuity, "a 300ms hiccup must not count as a discontinuity")
    }

    @Test
    fun `a single glitched frame cannot drag the signal`() {
        val f = OneEuroFilter()
        var t = 0L
        repeat(10) { f.filter(50.0, t); t += 33 }

        // One frame where the model mis-places a landmark and reports full depth.
        val during = f.filter(0.0, t)
        t += 33
        val after = f.filter(50.0, t)

        // Without the slew limit this lands near 4; the state machine would read it as a rep.
        assertTrue(during > 35.0, "one bad frame moved the signal to $during")
        assertTrue(abs(after - 50.0) < 10.0, "signal did not recover: $after")
    }

    @Test
    fun `a genuinely fast rep is not clipped by the slew limit`() {
        val f = OneEuroFilter()
        var t = 0L
        f.filter(100.0, t)
        // Full 100-unit descent in 300ms — faster than any real pushup — at 30fps.
        var last = 100.0
        repeat(9) { i ->
            t += 33
            last = f.filter(100.0 - (i + 1) * 100.0 / 9.0, t)
        }
        assertTrue(last < 12.0, "a real fast descent was clipped, ended at $last")
    }

    @Test
    fun `duplicate and out-of-order timestamps return the previous value`() {
        val f = OneEuroFilter()
        f.filter(50.0, 100L)
        val settled = f.filter(51.0, 133L)
        assertEquals(settled, f.filter(99.0, 133L))
        assertEquals(settled, f.filter(99.0, 50L))
    }

    @Test
    fun `reset clears history`() {
        val f = OneEuroFilter()
        f.filter(50.0, 0L)
        f.filter(60.0, 33L)
        f.reset()
        assertEquals(7.0, f.filter(7.0, 66L))
    }
}
