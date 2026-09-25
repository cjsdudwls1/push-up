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
 * The movement is declared on the way into the run, so the detector no longer has to tell a dip
 * from a curl; what it has to refuse is a wave — shoulders dropping toward the hands with the
 * elbows never bending — and the elbow angle is the one witness that does that from any camera
 * position. It is mandatory, and it is tested here by being defeated.
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
    fun `a wave with straight arms is refused, because the elbow never bends`() {
        // The fake the mandatory joint check exists for: the shoulders sink toward the hands as in
        // a dip — the primary signal reads a textbook rep — but the elbow angle stays locked out.
        // There is no travel witness any more (the elbow's projected travel reverses from a phone
        // on the floor, and refused every honest dip from there), so this is the only second
        // opinion, and it has to be enough on its own.
        val frames = PoseFixtures.dipTrace(count = 6, startMs = 3_600_000L) { t, d ->
            val moving = PoseFixtures.dipFrame(t, d)
            val locked = PoseFixtures.dipFrame(t, 0f)
            moving.copy(worldLandmarks = locked.worldLandmarks)
        }
        val (reps, events) = run(frames)
        assertEquals(0, reps, "a straight-armed wave was counted as a dip")
        assertTrue(
            events.filterIsInstance<RepEvent.Abandoned>().any { it.reason == AbandonReason.INCONSISTENT },
            "the wave was refused silently — it must surface as INCONSISTENT",
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
    }
}
