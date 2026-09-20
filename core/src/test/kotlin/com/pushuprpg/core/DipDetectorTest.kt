package com.pushuprpg.core

import com.pushuprpg.core.detect.*
import com.pushuprpg.core.fixtures.PoseFixtures
import com.pushuprpg.core.pose.PoseLandmarks as Lm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The tenth movement, and the first added after the project learned how these fail.
 *
 * A dip shares its signal pair AND its normal with a curl — stand still, curl a dumbbell from a
 * hanging arm, and the wrist closes the same gap with the same elbow angle. What separates them is
 * that a dip's elbow rises relative to the shoulder line as the body sinks past the bar, and a
 * curl's does not. So both witnesses are required, and both are tested here by being defeated.
 */
class DipDetectorTest {

    private fun run(frames: List<com.pushuprpg.core.pose.PoseFrame>): Pair<Int, List<RepEvent>> {
        val detector = DetectorFactory.create(ExerciseType.DIP, profile = UserProfile.empty())
        val events = mutableListOf<RepEvent>()
        frames.forEach { events += detector.onFrame(it).events }
        return detector.sessionSummary().repCount to events
    }

    @Test
    fun `a clean set of dips counts exactly the reps performed`() {
        val (reps, _) = run(PoseFixtures.dipTrace(count = 8, startMs = 3_600_000L))
        assertEquals(8, reps)
    }

    @Test
    fun `the signal falls as the body sinks, and never crosses zero`() {
        // The rule an author must get right, and the one this project has broken twice.
        val samples = (0..10).map { PoseFixtures.hForDip(it / 10f) }
        samples.zipWithNext { a, b -> assertTrue(b < a, "h rose from $a to $b on the way down") }
        assertTrue(samples.all { it > 0f }, "h crossed zero: $samples")
    }

    @Test
    fun `a half dip does not count, even on rep twenty`() {
        val (reps, _) = run(PoseFixtures.dipTrace(count = 20, startMs = 3_600_000L, peakDepth = 0.45f))
        assertEquals(0, reps, "half reps trained the bar down to meet them")
    }

    @Test
    fun `a standing curl is refused, because the elbow never travels`() {
        // The fake the travel witness exists for, built the way the movement actually is: the body
        // does not move at all and the WRIST rises to meet a fixed shoulder. The shoulder-to-wrist
        // gap closes exactly as in a dip and the elbow angle closes with it, so the primary signal
        // and the joint check both pass. What a curl cannot do is move the elbow relative to the
        // shoulder line, because the shoulder line is not going anywhere.
        val frames = PoseFixtures.dipTrace(count = 6, startMs = 3_600_000L) { t, d ->
            val top = PoseFixtures.dipFrame(t, 0f)
            val moving = PoseFixtures.dipFrame(t, d)
            val lm = top.landmarks.toMutableList()
            // Only the wrists travel, up toward the stationary shoulders.
            listOf(Lm.LEFT_WRIST, Lm.RIGHT_WRIST).forEach { i ->
                val closed = top.landmarks[i].y -
                    (top.landmarks[i].y - top.landmarks[Lm.LEFT_SHOULDER].y) * d
                lm[i] = top.landmarks[i].copy(y = closed)
            }
            // The elbow angle closes honestly, so the joint check has nothing to object to.
            top.copy(landmarks = lm, worldLandmarks = moving.worldLandmarks)
        }
        val (reps, events) = run(frames)
        assertEquals(0, reps, "a curl was counted as a dip")
        assertTrue(
            events.any { it is RepEvent.Abandoned },
            "the curl was refused silently — it must surface as an Abandoned",
        )
    }

    @Test
    fun `no world landmarks means a loud refusal, not a silent zero`() {
        // JOINT_REQUIRED: without the elbow angle there is no second opinion, and the primary
        // signal alone is exactly what a curl fakes.
        val frames = PoseFixtures.dipTrace(count = 6, startMs = 3_600_000L) { t, d ->
            PoseFixtures.dipFrame(t, d, world = false)
        }
        val (reps, events) = run(frames)
        assertEquals(0, reps)
        assertTrue(
            events.filterIsInstance<RepEvent.Abandoned>().any { it.reason == AbandonReason.INCONSISTENT },
            "a missing joint check must refuse out loud",
        )
    }

    @Test
    fun `the reading survives the user being nearer or further from the phone`() {
        // f and Z cancel in the depth ratio, so the count must not move with apparent size.
        listOf(0.09f, 0.13f, 0.18f).forEach { width ->
            val (reps, _) = run(
                PoseFixtures.dipTrace(count = 6, startMs = 3_600_000L) { t, d ->
                    PoseFixtures.dipFrame(t, d, shoulderWidth = width)
                }
            )
            assertEquals(6, reps, "shoulder width $width counted $reps")
        }
    }

    @Test
    fun `every plausible build counts, not just the one the prior sits on`() {
        // The lockout this project shipped twice: h at rest is arm length over shoulder width, so a
        // prior covers a slice of the population and silently refuses the rest unless the calibrator
        // re-anchors. Real builds span roughly 1.1 to 1.8.
        val failures = (110..180 step 10).map { it / 100f }.filter { hTop ->
            val (reps, _) = run(
                PoseFixtures.dipTrace(
                    count = 6,
                    startMs = 3_600_000L,
                    hScale = hTop / PoseFixtures.DIP_H_TOP,
                )
            )
            reps == 0
        }
        assertTrue(failures.isEmpty(), "these builds counted nothing: $failures")
    }

    @Test
    fun `a dip is priced as its own session, not a pushup's`() {
        val d = Exercises.of(ExerciseType.DIP)
        assertTrue(d.sessionVolumeScale < 0.5f, "a dip session is near-max, not 150 reps")
        assertTrue(d.damageCoefficient > 1f, "a dip is worth more than a pushup per rep")
        assertTrue(!d.validatedOnDevice, "no real body has done this yet, and the UI must say so")
    }
}
