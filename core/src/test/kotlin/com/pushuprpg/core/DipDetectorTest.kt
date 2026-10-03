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
 * from a curl; what it has to refuse is the hands coming to the shoulders instead of the shoulders
 * going down to the hands — a wave or a curl. The elbow angle was that witness until a phone showed
 * it reading backwards from the front; the shoulders' own travel in the picture is, now
 * (`ExerciseDescriptor.shoulderTravel`), and `HangingRigTest` defeats it with the fakes.
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
    fun `a dip whose elbow the model reads as straight still counts`() {
        // The phone's report (2026-10-02, SM-A556S): filmed from the front, the model read the elbow
        // straighter at the bottom of honest dips than at the top, and the elbow check refused three
        // of four. The shoulders coming down to the hands witness a dip now; this fixture is that
        // phone's reading — the body sinking, the 3-D elbow locked — and it is a dip.
        val frames = PoseFixtures.dipTrace(count = 6, startMs = 3_600_000L) { t, d ->
            val moving = PoseFixtures.dipFrame(t, d)
            val locked = PoseFixtures.dipFrame(t, 0f)
            moving.copy(worldLandmarks = locked.worldLandmarks)
        }
        assertEquals(6, run(frames).first)
    }

    @Test
    fun `without world landmarks the shoulders still witness a dip`() {
        val frames = PoseFixtures.dipTrace(count = 6, startMs = 3_600_000L) { t, d ->
            PoseFixtures.dipFrame(t, d, world = false)
        }
        assertEquals(6, run(frames).first)
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
    }
}
