package com.pushuprpg.core

import com.pushuprpg.core.detect.PoseQuality
import com.pushuprpg.core.detect.PoseQuality.LOW_CONFIDENCE
import com.pushuprpg.core.detect.PoseQuality.NO_SUBJECT
import com.pushuprpg.core.detect.PoseQuality.OK
import com.pushuprpg.core.detect.PoseQuality.OUT_OF_FRAME
import com.pushuprpg.core.detect.RepPhase
import com.pushuprpg.core.run.TrackingDrops
import com.pushuprpg.core.run.TrackingDrops.Companion.TAIL_MS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What run_finished says about tracking: H2 in docs/LAUNCH.md is read from these four numbers, so
 * a drop that is really the user setting up, standing up to quit or changing movement must not be in
 * them, and a blink the tracker recovered from must be.
 */
class TrackingDropsTest {

    /** Feeds [quality] at 30 fps from [fromMs] until [toMs], with the detector in [phase]. */
    private fun TrackingDrops.hold(
        quality: PoseQuality, fromMs: Long, toMs: Long, phase: RepPhase = RepPhase.READY_TOP,
    ) {
        var t = fromMs
        while (t < toMs) {
            onFrame(t, quality, phase)
            t += 33
        }
    }

    @Test
    fun `nothing is lost before the detector has armed`() {
        val d = TrackingDrops()
        // Walking into shot, found, lost, found again — in position, but not yet armed.
        d.hold(NO_SUBJECT, 0, 2_000, RepPhase.IDLE)
        d.hold(OK, 2_000, 3_000, RepPhase.IDLE)
        d.hold(OUT_OF_FRAME, 3_000, 3_500, RepPhase.LOST)
        d.hold(OK, 3_500, 40_000, RepPhase.IDLE)
        assertEquals(TrackingDrops.Summary(armed = false, drops = 0, recovered = 0, lostMs = 0), d.summary())
    }

    @Test
    fun `a blink the tracker recovers from is one recovered drop, however many reasons it went through`() {
        val d = TrackingDrops()
        d.hold(OK, 0, 5_000)
        d.hold(LOW_CONFIDENCE, 5_000, 5_100)
        d.hold(OUT_OF_FRAME, 5_100, 5_300)
        d.hold(OK, 5_300, 5_000 + TAIL_MS + 1_000)
        val s = d.summary()
        assertTrue(s.armed)
        assertEquals(1, s.drops, "a change of reason while lost was counted as a second drop")
        assertEquals(1, s.recovered)
        // From the first frame lost to the first frame found, to the frame.
        assertTrue(s.lostMs in 290L..340L, "lost for ${s.lostMs} ms")
    }

    @Test
    fun `the last seconds of a run are the user getting up, and are left out`() {
        val d = TrackingDrops()
        d.hold(OK, 0, 30_000)
        // A blink well inside the run, and then — within the tail — a blink and the walk to the phone.
        d.hold(NO_SUBJECT, 30_000, 30_200)
        d.hold(OK, 30_200, 60_000)
        d.hold(NO_SUBJECT, 60_000, 60_100)
        d.hold(OK, 60_100, 62_000)
        d.hold(NO_SUBJECT, 62_000, 60_000 + TAIL_MS - 500)
        val s = d.summary()
        assertEquals(1, s.drops, "a drop inside the last ${TAIL_MS / 1000} s was counted")
        assertEquals(1, s.recovered)
        assertTrue(s.lostMs in 190L..240L, "lost for ${s.lostMs} ms")
    }

    @Test
    fun `a drop that never comes back is a drop, but its length is not the tracker's`() {
        val d = TrackingDrops()
        d.hold(OK, 0, 20_000)
        // Stepped away for a minute and closed the app from recents.
        d.hold(NO_SUBJECT, 20_000, 80_000)
        val s = d.summary()
        assertEquals(1, s.drops)
        assertEquals(0, s.recovered)
        assertEquals(0L, s.lostMs)
    }

    @Test
    fun `a drop that outlasts the tail is counted once, and its recovery with it`() {
        val d = TrackingDrops()
        d.hold(OK, 0, 20_000)
        // Lost for longer than the tail — settled as a drop while still lost — then found.
        d.hold(OUT_OF_FRAME, 20_000, 20_000 + TAIL_MS + 2_000)
        d.hold(OK, 20_000 + TAIL_MS + 2_000, 20_000 + 3 * TAIL_MS)
        val s = d.summary()
        assertEquals(1, s.drops)
        assertEquals(1, s.recovered)
        assertTrue(s.lostMs in TAIL_MS + 1_950..TAIL_MS + 2_050, "lost for ${s.lostMs} ms")
    }

    @Test
    fun `changing movement is not losing the user, and the new one has to arm first`() {
        val d = TrackingDrops()
        d.hold(OK, 0, 30_000)
        // Stands up from the pushups: lost, then the switch is applied.
        d.hold(OUT_OF_FRAME, 30_000, 32_000)
        d.onSwitch()
        assertFalse(d.armed)
        // Squats: in view but not armed yet, a stumble, then armed.
        d.hold(OK, 32_000, 34_000, RepPhase.IDLE)
        d.hold(LOW_CONFIDENCE, 34_000, 34_300, RepPhase.IDLE)
        d.hold(OK, 34_300, 36_000, RepPhase.IDLE)
        d.hold(OK, 36_000, 50_000)
        assertTrue(d.armed)
        // One real blink in the squats.
        d.hold(NO_SUBJECT, 50_000, 50_100)
        d.hold(OK, 50_100, 50_000 + TAIL_MS + 1_000)
        val s = d.summary()
        assertEquals(TrackingDrops.Summary(armed = true, drops = 1, recovered = 1, lostMs = s.lostMs), s)
        assertTrue(s.lostMs in 90L..140L, "lost for ${s.lostMs} ms")
    }
}
