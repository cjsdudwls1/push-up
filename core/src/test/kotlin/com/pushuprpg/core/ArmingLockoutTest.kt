package com.pushuprpg.core

import com.pushuprpg.core.detect.DetectorConfig
import com.pushuprpg.core.detect.RepDetectorImpl
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.detect.PoseQuality
import com.pushuprpg.core.fixtures.PoseFixtures
import com.pushuprpg.core.pose.PoseFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The lockout that no other test in this suite could see.
 *
 * Arming a rep requires reaching the top band, `depth <= topEnter`. `depth` comes from the
 * calibrator, which starts from a population prior. And the calibrator only ever learns from a
 * **completed** rep. Put those three together and there is a loop that closes on itself: a user
 * whose resting position sits further from the prior than `topEnter` never enters the top band,
 * so no rep ever completes, so the calibrator is never taught, so the prior is never corrected.
 * Zero reps, forever, with the gauge sweeping its full travel on every rep and no error reported —
 * the silent-zero failure this project has already shipped twice.
 *
 * Every existing test missed it because [PoseFixtures] builds its body from `H_TOP = 1.35f`, which
 * is `DetectorConfig.pushup().hTopPrior` exactly. The fixture's user is the prior. Nobody else is.
 *
 * The tolerance is `topEnter` percent of the prior's range — 0.13 h units for a pushup — while `h`
 * at lockout is arm length over shoulder width, which across real builds spans roughly 1.28 to
 * 1.78. So the prior covers a slice of the population and silently refuses the rest.
 */
class ArmingLockoutTest {

    /**
     * A user whose lockout `h` is [hAtRest] instead of the fixture's 1.35, with the same range.
     *
     * Scaled, not offset. `h` is arm length over shoulder width, so a different build reads
     * proportionally higher or lower at every depth. The world landmarks are untouched, because the
     * elbow closes to the same angle at the bottom of a pushup whatever the limb lengths — which is
     * what keeps the joint cross-check agreeing, and what makes the failure silent rather than an
     * INCONSISTENT.
     */
    private fun bodyWith(hAtRest: Float): (Long, Float) -> PoseFrame {
        val scale = hAtRest / PoseFixtures.H_TOP
        return { t, d -> PoseFixtures.frame(t, d, hScale = scale) }
    }

    private fun run(hAtRest: Float, count: Int = 10): Triple<Int, List<RepEvent>, PoseQuality> {
        val detector = RepDetectorImpl(DetectorConfig.pushup())
        val events = mutableListOf<RepEvent>()
        var quality = PoseQuality.NO_SUBJECT
        PoseFixtures.trace(count = count, startMs = 3_600_000L, frameOf = bodyWith(hAtRest))
            .forEach { f ->
                val tick = detector.onFrame(f)
                events += tick.events
                quality = tick.quality
            }
        return Triple(detector.sessionSummary().repCount, events, quality)
    }

    @Test
    fun `the fixture body is the prior, which is why nothing else catches this`() {
        assertEquals(
            DetectorConfig.pushup().hTopPrior, PoseFixtures.H_TOP,
            "if these ever diverge, the rest of this file is measuring something else",
        )
    }

    @Test
    fun `a broader build than the prior still counts its reps`() {
        // 1.20 at lockout: shorter arms over wider shoulders. A perfectly ordinary build, and
        // 0.15 h units from the prior — just past the 0.13 the top band allows.
        val (reps, events, quality) = run(hAtRest = 1.20f)

        assertEquals(
            PoseQuality.OK, quality,
            "the tracker is seeing this user perfectly well, which is what makes a zero here silent",
        )
        assertTrue(
            reps > 0,
            "ten honest pushups counted $reps. Events: ${events.map { it::class.simpleName }.distinct()}",
        )
    }

    @Test
    fun `a lockout would report nothing at all, so it can never be diagnosed from events`() {
        // The property that makes this class of bug so expensive: there is no error to look at.
        // Asserted so that if a future change makes the detector complain instead, that is noticed.
        val (_, events, _) = run(hAtRest = 1.20f)
        val complaints = events.filterNot {
            it is RepEvent.QualityChanged || it is RepEvent.Strike ||
                it is RepEvent.Completed || it is RepEvent.DeepUpgrade || it is RepEvent.Coach
        }
        assertTrue(
            complaints.isEmpty() || events.any { it is RepEvent.Strike },
            "the detector produced complaints without counting anything: " +
                "${complaints.map { it::class.simpleName }.distinct()}",
        )
    }

    @Test
    fun `the whole plausible range of builds counts`() {
        // h at lockout is arm length over biacromial width. This span is the population, and the
        // detector has to hold across all of it rather than across the slice the prior sits in.
        val failures = (100..180 step 10)
            .map { it / 100f }
            .filter { run(hAtRest = it, count = 8).first == 0 }

        assertTrue(failures.isEmpty(), "these builds counted nothing at all: $failures")
    }

    @Test
    fun `correcting the offset does not hand out reps for half a rep`() {
        // The fix must move where the range sits without widening it, or it would buy arming at
        // the cost of the anti-farming property that half reps never count.
        val detector = RepDetectorImpl(DetectorConfig.pushup())
        PoseFixtures.trace(
            count = 20,
            startMs = 3_600_000L,
            peakDepth = 0.45f,
            frameOf = bodyWith(1.20f),
        ).forEach(detector::onFrame)

        assertEquals(
            0, detector.sessionSummary().repCount,
            "half reps counted on rep 20 — the range was widened, not shifted",
        )
    }
}
