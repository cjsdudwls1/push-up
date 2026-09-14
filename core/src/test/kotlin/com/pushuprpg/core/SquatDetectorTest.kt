package com.pushuprpg.core

import com.pushuprpg.core.detect.*
import com.pushuprpg.core.fixtures.PoseFixtures
import com.pushuprpg.core.pose.PoseFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The squat shares the rep state machine with the pushup and differs only in its depth signal, so
 * these tests concentrate on the things that signal could plausibly get wrong.
 */
class SquatDetectorTest {

    private fun run(
        frames: List<PoseFrame>,
        detector: RepDetector = RepDetectorImpl(DetectorConfig.squat()),
    ): Pair<RepDetector, List<RepEvent>> {
        val events = mutableListOf<RepEvent>()
        frames.forEach { events += detector.onFrame(it).events }
        return detector to events
    }

    private inline fun <reified T : RepEvent> List<RepEvent>.of(): List<T> = filterIsInstance<T>()

    @Test
    fun `a clean set of squats counts exactly the reps performed`() {
        val (detector, events) = run(PoseFixtures.squatTrace(count = 10))
        assertEquals(10, events.of<RepEvent.Strike>().size)
        assertEquals(10, detector.sessionSummary().repCount)
    }

    @Test
    fun `the signal falls as the user descends, not rises`() {
        // The body normal is sign-forced toward the hips for a squat and toward the wrists for a
        // pushup. Getting that backwards would not degrade the reading, it would invert it — so a
        // descent would register as standing up.
        val detector = RepDetectorImpl(DetectorConfig.squat())
        var standing = 0f
        var deep = 0f
        PoseFixtures.squatTrace(count = 1, peakDepth = 1.0f).forEach { f ->
            val tick = detector.onFrame(f)
            if (f.timestampMs < 600) standing = tick.depth
            deep = maxOf(deep, tick.depth)
        }
        assertTrue(standing < 15f, "standing should read near zero depth, got $standing")
        assertTrue(deep > 70f, "a parallel squat should read deep, got $deep")
    }

    @Test
    fun `a quarter squat does not count`() {
        val (detector, events) = run(PoseFixtures.squatTrace(count = 8, peakDepth = 0.35f))
        assertEquals(0, events.of<RepEvent.Strike>().size, "quarter squats must not count")
        assertEquals(8, events.of<RepEvent.Shallow>().size)
        assertEquals(0, detector.sessionSummary().repCount)
    }

    @Test
    fun `going below parallel earns the deep grade`() {
        val (_, deep) = run(PoseFixtures.squatTrace(count = 5, peakDepth = 1.08f))
        assertEquals(5, deep.of<RepEvent.Strike>().size)
        assertTrue(deep.of<RepEvent.DeepUpgrade>().isNotEmpty(),
            "hip below knee is the definition of a deep squat")

        val (_, parallel) = run(PoseFixtures.squatTrace(count = 5, peakDepth = 0.80f))
        assertEquals(5, parallel.of<RepEvent.Strike>().size)
        assertEquals(0, parallel.of<RepEvent.DeepUpgrade>().size)
    }

    @Test
    fun `the reading survives the user drifting nearer to or further from the phone`() {
        // The whole reason shoulder width is the normaliser. A squat set in a living room involves
        // the user stepping around between reps.
        fun repsAt(scale: Float): Int {
            val detector = RepDetectorImpl(DetectorConfig.squat())
            val frames = PoseFixtures.squatTrace(count = 6) { t, d ->
                PoseFixtures.squatFrame(
                    t, d,
                    shoulderWidth = PoseFixtures.STANDING_SHOULDER_WIDTH * scale,
                )
            }
            run(frames, detector)
            return detector.sessionSummary().repCount
        }
        assertEquals(6, repsAt(1.0f))
        assertEquals(6, repsAt(0.7f), "further away")
        assertEquals(6, repsAt(1.5f), "closer in")
    }

    @Test
    fun `the reading survives the user standing off-centre`() {
        val detector = RepDetectorImpl(DetectorConfig.squat())
        run(
            PoseFixtures.squatTrace(count = 6) { t, d ->
                PoseFixtures.squatFrame(t, d, centerU = PoseFixtures.ASPECT * 0.32f)
            },
            detector,
        )
        assertEquals(6, detector.sessionSummary().repCount)
    }

    @Test
    fun `knees out of frame stop the count rather than guessing at it`() {
        // Hip-above-knee needs the knee. Without it there is no squat signal at all, and inventing
        // one would be worse than saying nothing.
        val (detector, events) = run(
            PoseFixtures.squatTrace(count = 6) { t, d ->
                PoseFixtures.squatFrame(t, d, legConfidence = 0.1f)
            }
        )
        assertEquals(0, detector.sessionSummary().repCount)
        assertTrue(events.of<RepEvent.QualityChanged>().any { it.quality != PoseQuality.OK })
    }

    @Test
    fun `squats are paced more slowly than pushups`() {
        // A squat cycle is longer, and the config reflects that rather than reusing pushup timings.
        val squat = DetectorConfig.squat()
        val pushup = DetectorConfig.pushup()
        assertTrue(squat.minRepPeriodMs > pushup.minRepPeriodMs)
        assertTrue(squat.maxDescentSpeed < pushup.maxDescentSpeed)
    }

    @Test
    fun `the calibrated range allows going below parallel`() {
        // Clamping the bottom at zero would cap a full-depth squatter at the same reading as
        // someone stopping exactly at parallel.
        assertTrue(DetectorConfig.squat().botClampMin < 0f)
    }

    @Test
    fun `a set of squats never trains the bar down to meet quarter reps`() {
        val (detector, events) = run(PoseFixtures.squatTrace(count = 25, peakDepth = 0.40f))
        assertEquals(0, events.of<RepEvent.Strike>().size,
            "quarter squats must still be quarter squats on rep 25")
    }
}
