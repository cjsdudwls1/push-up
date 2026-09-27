package com.pushuprpg.core

import com.pushuprpg.core.detect.CalibrationSnapshot
import com.pushuprpg.core.detect.CalibrationState
import com.pushuprpg.core.detect.DepthSource
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.PoseQuality
import com.pushuprpg.core.detect.PoseTick
import com.pushuprpg.core.detect.RenderSkeleton
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.detect.RepGrade
import com.pushuprpg.core.detect.RepPhase
import com.pushuprpg.core.survival.CatCompanion
import com.pushuprpg.core.survival.CatLine
import com.pushuprpg.core.survival.CatPhase
import com.pushuprpg.core.survival.CatSession
import com.pushuprpg.core.survival.CatSessionEvent
import com.pushuprpg.core.survival.CeilingSurvival
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatSessionTest {

    private val lines = CalibrationSnapshot(
        exercise = ExerciseType.PUSHUP,
        top = 0f, bottom = 0f, bottomBest = 0f,
        state = CalibrationState.CONVERGED,
        completedReps = 0,
        countEnter = 70f,
        deepEnter = 88f,
    )

    /** A detector frame: in position and seen, unless said otherwise. */
    private fun tick(
        t: Long,
        quality: PoseQuality = PoseQuality.OK,
        phase: RepPhase = if (quality == PoseQuality.OK) RepPhase.READY_TOP else RepPhase.LOST,
        events: List<RepEvent> = emptyList(),
    ) = PoseTick(
        tMs = t, depth = 0f, depthVelocity = 0f, depthSource = DepthSource.PRIMARY,
        phase = phase, quality = quality, repCount = 0, combo = 0, maxCombo = 0,
        calibration = lines, render = RenderSkeleton.EMPTY, events = events,
    )

    private fun strike(t: Long, index: Int) = RepEvent.Strike(t, index, RepGrade.COUNTED, depth = 75f, combo = index)

    /** Three lives unless said otherwise: enough to see every transition, quicker than the real ten. */
    private fun session(lives: Int = 3, refunds: Int = CatSession.REFUNDS) =
        CatSession(newLife = { CeilingSurvival() }, lives = lives, refunds = refunds)

    /** Frames every 33 ms from [from] until [until] holds or [limitMs] passes; returns the last time. */
    private fun CatSession.runUntil(
        from: Long,
        limitMs: Long = 600_000,
        frame: (Long) -> PoseTick = { tick(it) },
        until: () -> Boolean,
    ): Long {
        var t = from
        while (!until() && t - from < limitMs) {
            t += 33
            onTick(frame(t))
        }
        return t
    }

    @Test
    fun `a session is ten lives, by the owner's number`() {
        val s = CatSession(newLife = { CeilingSurvival() })
        assertEquals(10, s.lives)
        assertEquals(10, s.state().livesLeft)
        var t = 0L
        var lost = 0
        while (s.state().phase != CatPhase.OVER && t < 3_000_000) {
            t += 33
            lost += s.onTick(tick(t)).session.count { it is CatSessionEvent.LifeLost }
        }
        assertEquals(10, lost)
        assertEquals(10, s.state().ended.size)
    }

    @Test
    fun `three lives, each a fresh ceiling, with a rest between them`() {
        val s = session()
        var t = s.runUntil(0) { s.state().phase == CatPhase.RESTING }
        assertEquals(2, s.state().livesLeft)
        assertEquals(1, s.state().ended.size)

        t = s.runUntil(t) { s.state().phase == CatPhase.PLAYING }
        val fresh = s.state().life
        assertEquals(1f, fresh.height, "the next life started under a lowered ceiling")
        assertEquals(0L, fresh.elapsedMs, "the next life inherited the last one's ramp")

        t = s.runUntil(t) { s.state().phase == CatPhase.RESTING }
        assertEquals(1, s.state().livesLeft)
        t = s.runUntil(t) { s.state().phase == CatPhase.PLAYING }
        s.runUntil(t) { s.state().phase == CatPhase.OVER }
        assertEquals(0, s.state().livesLeft)
        assertEquals(3, s.state().ended.size)
    }

    @Test
    fun `the rest lasts what was set, counted on the frames`() {
        val s = session()
        s.restMs = 90_000
        val died = s.runUntil(0) { s.state().phase == CatPhase.RESTING }
        s.onTick(tick(died + 45_000))
        assertEquals(CatPhase.RESTING, s.state().phase)
        assertEquals(45_000L, s.state().restLeftMs)
        val back = s.runUntil(died + 45_000) { s.state().phase == CatPhase.PLAYING }
        assertTrue(back - died in 90_000..90_100, "the rest took ${back - died}ms")
    }

    @Test
    fun `reps done during the rest are not played into anything`() {
        val s = session()
        var t = s.runUntil(0) { s.state().phase == CatPhase.RESTING }
        val before = s.state().totalReps
        repeat(10) { i ->
            t += 1_000
            s.onTick(tick(t, events = listOf(strike(t, 100 + i))))
        }
        assertEquals(before, s.state().totalReps)
        assertEquals(CatPhase.RESTING, s.state().phase)
    }

    @Test
    fun `after the rest the ceiling waits for the user to be in position`() {
        val s = session()
        var t = s.runUntil(0) { s.state().phase == CatPhase.RESTING }
        // Gone for water: the rest runs out while nobody is there.
        t = s.runUntil(t, frame = { tick(it, quality = PoseQuality.NO_SUBJECT) }) { s.state().phase == CatPhase.PLAYING }
        repeat(600) { t += 33; s.onTick(tick(t, quality = PoseQuality.NO_SUBJECT)) }
        assertFalse(s.state().life.started, "the next life started without the user")
        assertEquals(1f, s.state().life.height)
        s.onTick(tick(t + 33))
        assertTrue(s.state().life.started)
    }

    @Test
    fun `every life's reps and points add up`() {
        val s = session()
        var t = 0L
        var index = 0
        // Five reps, then nothing until the ceiling wins, three times.
        repeat(3) {
            s.onTick(tick(t))
            repeat(5) {
                t += 1_000
                s.onTick(tick(t, events = listOf(strike(t, ++index))))
            }
            t = s.runUntil(t) { s.state().phase != CatPhase.PLAYING }
            if (s.state().phase == CatPhase.RESTING) t = s.runUntil(t) { s.state().phase == CatPhase.PLAYING }
        }
        val state = s.state()
        assertEquals(CatPhase.OVER, state.phase)
        assertEquals(listOf(5, 5, 5), state.ended.map { it.reps })
        assertEquals(15, state.totalReps)
        assertEquals(state.ended.sumOf { it.score }, state.totalScore)
        assertEquals(5, state.bestLifeReps)
    }

    @Test
    fun `a life the camera lost is given back, once a session`() {
        val s = session()
        var t = 0L
        s.onTick(tick(t))
        // In position, then the tracker loses the user and the ceiling comes down meanwhile.
        t = s.runUntil(t, frame = { tick(it, quality = PoseQuality.LOW_CONFIDENCE) }) {
            s.state().phase != CatPhase.PLAYING
        }
        val first = s.state()
        assertTrue(first.ended.single().refunded, "a life lost to the tracker cost a life")
        assertEquals(3, first.livesLeft)
        assertEquals(CatPhase.RESTING, first.phase)

        t = s.runUntil(t) { s.state().phase == CatPhase.PLAYING }
        s.onTick(tick(t))
        s.runUntil(t, frame = { tick(it, quality = PoseQuality.LOW_CONFIDENCE) }) {
            s.state().phase != CatPhase.PLAYING
        }
        assertFalse(s.state().ended.last().refunded, "the camera's excuse was given twice")
        assertEquals(2, s.state().livesLeft)
    }

    @Test
    fun `a blink before the ceiling lands is not the camera's doing`() {
        val s = session()
        var t = 0L
        s.onTick(tick(t))
        var over: CatSessionEvent.LifeLost? = null
        while (over == null && t < 120_000) {
            t += 33
            // Lost for the last half second or so of every two: never two seconds on end.
            val blink = t % 2_000 > 1_500
            val step = s.onTick(tick(t, quality = if (blink) PoseQuality.LOW_CONFIDENCE else PoseQuality.OK))
            over = step.session.filterIsInstance<CatSessionEvent.LifeLost>().firstOrNull()
        }
        assertNotNull(over)
        assertFalse(over.result.refunded)
        assertEquals(2, over.livesLeft)
    }

    @Test
    fun `the tutorial is one life and ends with it`() {
        val s = session(lives = 1, refunds = 0)
        s.onTick(tick(0))
        s.runUntil(0, frame = { tick(it, quality = PoseQuality.NO_SUBJECT) }) { s.state().phase != CatPhase.PLAYING }
        assertEquals(CatPhase.OVER, s.state().phase)
        assertFalse(s.state().ended.single().refunded)
    }

    @Test
    fun `ending the session keeps the life in progress`() {
        val s = session()
        var t = 0L
        s.onTick(tick(t))
        repeat(7) {
            t += 1_000
            s.onTick(tick(t, events = listOf(strike(t, it + 1))))
        }
        val events = s.finish(t)
        assertTrue(events.single() is CatSessionEvent.Over)
        assertEquals(CatPhase.OVER, s.state().phase)
        assertEquals(7, s.state().totalReps)
        assertEquals(7, s.state().ended.single().reps)
        assertTrue(s.finish(t + 1).isEmpty(), "a session ended twice")
    }

    @Test
    fun `what a session left part way banks is every life so far and the one in progress`() {
        val s = session()
        var t = 0L
        s.onTick(tick(t))
        repeat(3) { i ->
            t += 1_000
            s.onTick(tick(t, events = listOf(strike(t, i + 1), RepEvent.DeepUpgrade(t, i + 1, depth = 95f))))
        }
        t = s.runUntil(t) { s.state().phase == CatPhase.RESTING }
        t = s.runUntil(t) { s.state().phase == CatPhase.PLAYING }
        s.onTick(tick(t))
        repeat(2) { i ->
            t += 1_000
            s.onTick(tick(t, events = listOf(strike(t, 10 + i))))
        }
        val snapshot = s.state()
        assertEquals(listOf(3, 2), snapshot.played.map { it.reps })
        assertEquals(listOf(3, 0), snapshot.played.map { it.deepReps })
        assertEquals(snapshot.totalReps, snapshot.played.sumOf { it.reps })
        assertEquals(snapshot.totalScore, snapshot.played.sumOf { it.score })
    }

    @Test
    fun `a session never started banks nothing when it is ended`() {
        val s = session()
        s.onTick(tick(0, quality = PoseQuality.NO_SUBJECT))
        s.finish(33)
        assertTrue(s.state().ended.isEmpty())
        assertFalse(s.state().started)
    }

    @Test
    fun `deep reps are counted per life`() {
        val s = session()
        var t = 0L
        s.onTick(tick(t))
        repeat(4) { i ->
            t += 1_000
            // Struck at the count line, then on past 깊게 on the same frame, as the detector does it.
            s.onTick(tick(t, events = listOf(strike(t, i + 1), RepEvent.DeepUpgrade(t, i + 1, depth = 95f))))
        }
        t += 1_000
        s.onTick(tick(t, events = listOf(strike(t, 5))))
        s.finish(t)
        assertEquals(4, s.state().ended.single().deepReps)
        assertEquals(5, s.state().ended.single().reps)
    }

    @Test
    fun `the cat is fine when a life ends, says when ten seconds are left, and asks again`() {
        val s = session()
        val cat = CatCompanion()
        var t = 0L
        val said = mutableListOf<CatLine>()
        fun record() { cat.view().speech?.line?.let { if (said.lastOrNull() != it) said += it } }
        while (t < 400_000 && s.state().phase != CatPhase.OVER) {
            t += 33
            val step = s.onTick(tick(t))
            val state = s.state()
            if (state.phase == CatPhase.RESTING || step.session.isNotEmpty()) {
                cat.update(state.life, step.life, t)
                cat.rest(state.restLeftMs, step.session, t)
            } else {
                cat.update(state.life, step.life, t)
            }
            record()
            if (said.count { it == CatLine.AGAIN } == 1 && state.phase == CatPhase.PLAYING && state.life.started) break
        }
        val rest = said.indexOf(CatLine.REST)
        val ten = said.indexOf(CatLine.REST_TEN)
        val again = said.indexOf(CatLine.AGAIN)
        assertTrue(rest >= 0 && ten > rest && again > ten, "the rest was said as $said")
    }
}
