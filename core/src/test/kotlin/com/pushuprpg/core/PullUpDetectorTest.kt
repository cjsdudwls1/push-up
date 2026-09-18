package com.pushuprpg.core

import com.pushuprpg.core.detect.*
import com.pushuprpg.core.fixtures.PoseFixtures
import com.pushuprpg.core.pose.PoseFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A pull-up is the first exercise added purely as data, so these tests are as much about the
 * descriptor abstraction holding up as about the movement.
 *
 * The things that could plausibly go wrong are all directional, and each has bitten this project
 * before: the normal pointing the wrong way, a signal that rises where it should fall, and a
 * cross-check that cannot move.
 */
class PullUpDetectorTest {

    private fun run(
        frames: List<PoseFrame>,
        detector: RepDetector = RepDetectorImpl(DetectorConfig.pullUp()),
    ): Pair<RepDetector, List<RepEvent>> {
        val events = mutableListOf<RepEvent>()
        frames.forEach { events += detector.onFrame(it).events }
        return detector to events
    }

    private inline fun <reified T : RepEvent> List<RepEvent>.of(): List<T> = filterIsInstance<T>()

    @Test
    fun `a clean set of pull-ups counts exactly the reps performed`() {
        val (detector, events) = run(PoseFixtures.pullUpTrace(count = 8))
        assertEquals(8, events.of<RepEvent.Strike>().size)
        assertEquals(8, detector.sessionSummary().repCount)
        assertEquals(8, events.of<RepEvent.Completed>().size, "every rep returned to a dead hang")
    }

    @Test
    fun `the signal falls as the user pulls up, not rises`() {
        // The hands are above the shoulders here and below them in a pushup, so the body normal is
        // sign-forced in opposite image directions for the two movements. If that flip were not
        // handled, a pull would register as a descent to a dead hang and nothing would ever count.
        val detector = RepDetectorImpl(DetectorConfig.pullUp())
        var hanging = 0f
        var pulled = 0f
        PoseFixtures.pullUpTrace(count = 1, peakDepth = 1.0f).forEach { f ->
            val tick = detector.onFrame(f)
            if (f.timestampMs < 700) hanging = tick.depth
            pulled = maxOf(pulled, tick.depth)
        }
        assertTrue(hanging < 15f, "a dead hang should read near zero depth, got $hanging")
        assertTrue(pulled > 70f, "chin over bar should read deep, got $pulled")
    }

    @Test
    fun `a half rep does not count`() {
        val (detector, events) = run(PoseFixtures.pullUpTrace(count = 6, peakDepth = 0.45f))
        assertEquals(0, events.of<RepEvent.Strike>().size, "chin nowhere near the bar")
        assertEquals(6, events.of<RepEvent.Shallow>().size)
        assertEquals(0, detector.sessionSummary().repCount)
    }

    @Test
    fun `chin well over the bar earns the deep grade and a bare rep does not`() {
        val (_, deep) = run(PoseFixtures.pullUpTrace(count = 5, peakDepth = 0.97f))
        assertEquals(5, deep.of<RepEvent.Strike>().size)
        assertTrue(deep.of<RepEvent.DeepUpgrade>().isNotEmpty())

        val (_, bare) = run(PoseFixtures.pullUpTrace(count = 5, peakDepth = 0.78f))
        assertEquals(5, bare.of<RepEvent.Strike>().size)
        assertEquals(0, bare.of<RepEvent.DeepUpgrade>().size)
    }

    @Test
    fun `the reading survives the user hanging nearer to or further from the phone`() {
        fun repsAt(scale: Float): Int {
            val detector = RepDetectorImpl(DetectorConfig.pullUp())
            run(
                PoseFixtures.pullUpTrace(count = 5) { t, d ->
                    PoseFixtures.pullUpFrame(
                        t, d,
                        shoulderWidth = PoseFixtures.STANDING_SHOULDER_WIDTH * scale,
                    )
                },
                detector,
            )
            return detector.sessionSummary().repCount
        }
        assertEquals(5, repsAt(1.0f))
        assertEquals(5, repsAt(0.75f), "further away")
        assertEquals(5, repsAt(1.4f), "closer in")
    }

    @Test
    fun `the elbow angle is required, and its absence rejects out loud rather than in silence`() {
        // A hanging body is rigid below the shoulders: the nose, hips and ankles all move exactly
        // with the shoulders, so no body-travel check can ever disagree with the primary signal.
        // The descriptor therefore makes the elbow angle mandatory. Without world landmarks the
        // reps must be refused — and must SAY they were refused, because "counted nothing and said
        // nothing" is the failure mode this project has shipped twice.
        val (detector, events) = run(
            PoseFixtures.pullUpTrace(count = 6) { t, d -> PoseFixtures.pullUpFrame(t, d, world = false) }
        )
        assertEquals(0, detector.sessionSummary().repCount)
        assertTrue(
            events.of<RepEvent.Abandoned>().any { it.reason == AbandonReason.INCONSISTENT },
            "a refused pull-up must surface as an Abandoned event, not as silence",
        )
    }

    @Test
    fun `the nose would have been a dead cross-check for this movement`() {
        // Pinning the reasoning, not the code: if someone later "improves" the pull-up by copying
        // the pushup's nose check across, this states why the copy is wrong.
        assertEquals(null, Exercises.PULL_UP.signal?.bodyTravel)
        assertEquals(CrossCheckPolicy.JOINT_REQUIRED, Exercises.PULL_UP.signal?.crossCheck)

        val hang = PoseFixtures.pullUpFrame(0L, 0f)
        val top = PoseFixtures.pullUpFrame(1000L, 1f)
        fun noseOffset(f: PoseFrame): Float =
            f.v(com.pushuprpg.core.pose.PoseLandmarks.NOSE) -
                (f.v(com.pushuprpg.core.pose.PoseLandmarks.LEFT_SHOULDER) +
                    f.v(com.pushuprpg.core.pose.PoseLandmarks.RIGHT_SHOULDER)) / 2f
        assertEquals(
            noseOffset(hang), noseOffset(top), 1e-6f,
            "the head does not move relative to the shoulders in a pull-up",
        )
    }

    @Test
    fun `pull-ups are paced far more slowly than pushups and are worth more`() {
        val pullUp = DetectorConfig.pullUp()
        val pushup = DetectorConfig.pushup()
        assertTrue(pullUp.minRepPeriodMs > pushup.minRepPeriodMs)
        assertTrue(pullUp.maxDescentSpeed < pushup.maxDescentSpeed)
        assertTrue(
            Exercises.PULL_UP.damageCoefficient > 2f * Exercises.PUSHUP.damageCoefficient,
            "a floor costed in pushups must not ask for the same number of pull-ups",
        )
        assertTrue(Exercises.PULL_UP.streakBar < Exercises.PUSHUP.streakBar)
    }

    @Test
    fun `a set of half reps never trains the bar down to meet them`() {
        val (_, events) = run(PoseFixtures.pullUpTrace(count = 20, peakDepth = 0.45f))
        assertEquals(
            0, events.of<RepEvent.Strike>().size,
            "half reps must still be half reps on rep 20",
        )
    }
}
