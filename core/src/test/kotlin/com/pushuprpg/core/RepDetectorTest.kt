package com.pushuprpg.core

import com.pushuprpg.core.detect.*
import com.pushuprpg.core.fixtures.PoseFixtures
import com.pushuprpg.core.pose.PoseFrame
import com.pushuprpg.core.pose.PoseLandmarks as Lm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepDetectorTest {

    private fun run(
        frames: List<PoseFrame>,
        detector: RepDetector = RepDetectorImpl(DetectorConfig.pushup()),
    ): Pair<RepDetector, List<RepEvent>> {
        val events = mutableListOf<RepEvent>()
        frames.forEach { events += detector.onFrame(it).events }
        return detector to events
    }

    private inline fun <reified T : RepEvent> List<RepEvent>.of(): List<T> = filterIsInstance<T>()

    @Test
    fun `a clean set counts exactly the reps performed`() {
        val (detector, events) = run(PoseFixtures.trace(count = 10))
        assertEquals(10, events.of<RepEvent.Strike>().size, "strike count")
        assertEquals(10, detector.sessionSummary().repCount)
        assertEquals(10, events.of<RepEvent.Completed>().size, "every rep returned to the top")
    }

    @Test
    fun `shallow reps do not count and are reported as shallow`() {
        // Peaks at 50 on the 0..100 scale — past the top band, nowhere near the 인정 line at 70.
        val (detector, events) = run(PoseFixtures.trace(count = 6, peakDepth = 0.50f))
        assertEquals(0, events.of<RepEvent.Strike>().size, "shallow reps must not count")
        assertEquals(6, events.of<RepEvent.Shallow>().size)
        assertEquals(0, detector.sessionSummary().repCount)
    }

    @Test
    fun `deep reps earn the deep upgrade and shallower ones do not`() {
        val (_, deep) = run(PoseFixtures.trace(count = 5, peakDepth = 0.97f))
        assertEquals(5, deep.of<RepEvent.Strike>().size)
        assertEquals(5, deep.of<RepEvent.DeepUpgrade>().size, "97 is past the 깊게 line at 88")

        val (_, mid) = run(PoseFixtures.trace(count = 5, peakDepth = 0.78f))
        assertEquals(5, mid.of<RepEvent.Strike>().size)
        assertEquals(0, mid.of<RepEvent.DeepUpgrade>().size, "78 is between the two lines")
    }

    @Test
    fun `bouncing at the bottom cannot farm reps`() {
        // Descend once, then bob between the bottom and halfway up forever, never locking out.
        val frames = mutableListOf<PoseFrame>()
        var t = 0L
        repeat(20) { frames += PoseFixtures.frame(t, 0f); t += 33 }
        frames += PoseFixtures.rep(t, descentMs = 800, bottomMs = 0, ascentMs = 0, peakDepth = 0.95f, restMs = 0)
        t = frames.last().timestampMs + 33
        repeat(40) { i ->
            // Oscillate between depth 95 and 60 — never reaching the top band at 20.
            val d = if (i % 2 == 0) 0.60f else 0.95f
            repeat(8) { frames += PoseFixtures.frame(t, d); t += 33 }
        }

        val (_, events) = run(frames)
        assertEquals(1, events.of<RepEvent.Strike>().size,
            "only the one genuine descent should count; a strike needs a real lockout first")
    }

    @Test
    fun `the counter is frame rate independent`() {
        val at30 = run(PoseFixtures.trace(count = 8, fps = 30)).second.of<RepEvent.Strike>().size
        val at15 = run(PoseFixtures.trace(count = 8, fps = 15)).second.of<RepEvent.Strike>().size
        val at12 = run(PoseFixtures.trace(count = 8, fps = 12)).second.of<RepEvent.Strike>().size
        assertEquals(8, at30, "30fps")
        assertEquals(8, at15, "15fps")
        assertEquals(8, at12, "12fps — a cheap or thermally throttled phone must still count right")
    }

    @Test
    fun `no person in frame counts nothing and reports no subject`() {
        val frames = (0 until 120).map { PoseFrame.empty(it * 33L) }
        val (detector, events) = run(frames)
        assertEquals(0, detector.sessionSummary().repCount)
        assertTrue(events.of<RepEvent.QualityChanged>().any { it.quality == PoseQuality.NO_SUBJECT })
    }

    @Test
    fun `combo tracks consecutive reps and breaks after a long rest`() {
        val fast = PoseFixtures.trace(count = 5, restMs = 300)
        val (d1, e1) = run(fast)
        assertEquals(5, e1.of<RepEvent.Strike>().last().combo, "combo should match the rep count")
        assertEquals(5, d1.sessionSummary().maxCombo)

        // Now the same set with a rest longer than the combo timeout between every rep.
        val slow = PoseFixtures.trace(count = 4, restMs = 9_000)
        val (_, e2) = run(slow)
        assertTrue(e2.of<RepEvent.ComboBroken>().isNotEmpty(), "a 9s rest must break the combo")
        assertTrue(e2.of<RepEvent.Strike>().all { it.combo == 1 },
            "each rep after a break should start the combo over")
    }

    @Test
    fun `a set of genuinely shallow reps never trains the bar down to meet them`() {
        // The degenerate case the calibration guards exist for: if the accepted range tracked
        // whatever the user was doing, thirty half-reps would eventually all start counting.
        val (detector, events) = run(PoseFixtures.trace(count = 30, peakDepth = 0.55f))
        assertEquals(0, events.of<RepEvent.Strike>().size,
            "half-depth reps must still be half-depth reps on rep 30")
        assertTrue(detector.snapshotCalibration().bottom > 0f)
    }

    @Test
    fun `fatigue is tolerated but only within the demonstrated range`() {
        val detector = RepDetectorImpl(DetectorConfig.pushup())
        // Ten full reps to establish the range...
        run(PoseFixtures.trace(count = 10, peakDepth = 1.0f), detector)
        val strong = detector.sessionSummary().repCount
        assertEquals(10, strong)

        // ...then ten tired ones at 85% depth, which should still count.
        val t = detector.sessionSummary().durationMs + 1000
        run(PoseFixtures.trace(count = 10, startMs = t, peakDepth = 0.85f), detector)
        assertTrue(detector.sessionSummary().repCount >= 18,
            "a fatiguing user should keep counting, got ${detector.sessionSummary().repCount}")

        // But collapsing to a quarter of the range must not.
        val t2 = t + 60_000
        val before = detector.sessionSummary().repCount
        run(PoseFixtures.trace(count = 10, startMs = t2, peakDepth = 0.35f), detector)
        assertEquals(before, detector.sessionSummary().repCount,
            "quarter-depth reps must not count no matter how tired the user is")
    }
}
