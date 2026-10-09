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

    private fun session(oneSet: Boolean = false) = CatSession(newLife = { CeilingSurvival() }, oneSet = oneSet)

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

    /** Ten lives, by the owner's decision (2026-10-09): each set the ceiling wins costs one. */
    @Test
    fun `a session is ten lives, one lost per set, and over when they are`() {
        val s = session()
        assertEquals(CatSession.LIVES, s.state().livesLeft)
        var t = 0L
        var sets = 0
        while (s.state().phase != CatPhase.OVER && t < 6_000_000) {
            t += 33
            val step = s.onTick(tick(t))
            if (step.session.any { it is CatSessionEvent.SetOver }) {
                sets++
                assertEquals(CatSession.LIVES - sets, s.state().livesLeft, "a set did not cost a life")
            }
            if (sets < CatSession.LIVES) {
                assertTrue(step.session.none { it is CatSessionEvent.Over }, "the session ended after $sets sets")
            }
        }
        assertEquals(CatSession.LIVES, sets)
        assertEquals(0, s.state().livesLeft)
        assertEquals(CatPhase.OVER, s.state().phase)
        assertTrue(s.finish(t).isEmpty())
    }

    @Test
    fun `the user can end a session before its lives are lost`() {
        val s = session()
        val t = s.runUntil(0) { s.state().phase == CatPhase.RESTING }
        assertEquals(CatSession.LIVES - 1, s.state().livesLeft)
        assertTrue(s.finish(t).single() is CatSessionEvent.Over)
        assertEquals(CatPhase.OVER, s.state().phase)
    }

    @Test
    fun `each set is a fresh ceiling, with a rest between them`() {
        val s = session()
        var t = s.runUntil(0) { s.state().phase == CatPhase.RESTING }
        assertEquals(1, s.state().ended.size)

        t = s.runUntil(t) { s.state().phase == CatPhase.PLAYING }
        val fresh = s.state().life
        assertEquals(1f, fresh.height, "the next set started under a lowered ceiling")
        assertEquals(0L, fresh.elapsedMs, "the next set inherited the last one's ramp")

        t = s.runUntil(t) { s.state().phase == CatPhase.RESTING }
        assertEquals(2, s.state().ended.size)
        s.runUntil(t) { s.state().phase == CatPhase.PLAYING }
        assertEquals(2, s.state().ended.size)
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
    fun `a skipped rest ends on the next frame, and the next set still waits for the user`() {
        val s = session()
        var t = s.runUntil(0) { s.state().phase == CatPhase.RESTING }
        s.skipRest()
        t += 33
        // Nobody in position yet: walking back from the phone after tapping it.
        val step = s.onTick(tick(t, quality = PoseQuality.NO_SUBJECT))
        assertTrue(step.session.any { it is CatSessionEvent.RestOver }, "the skipped rest did not end")
        assertEquals(CatPhase.PLAYING, s.state().phase)
        assertEquals(1, s.state().ended.size, "skipping the rest counted as a set")
        assertFalse(s.state().life.started, "the skipped rest started the ceiling without the user")
        s.onTick(tick(t + 33))
        assertTrue(s.state().life.started)
    }

    @Test
    fun `a skip asked while a set is on does nothing, and the next rest is a full one`() {
        val s = session()
        s.onTick(tick(33))
        s.skipRest()
        s.onTick(tick(66))
        assertEquals(CatPhase.PLAYING, s.state().phase)
        val ended = s.runUntil(66) { s.state().phase == CatPhase.RESTING }
        s.onTick(tick(ended + 30_000))
        assertEquals(CatPhase.RESTING, s.state().phase, "an old skip cut the next rest short")
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
        assertFalse(s.state().life.started, "the next set started without the user")
        assertEquals(1f, s.state().life.height)
        s.onTick(tick(t + 33))
        assertTrue(s.state().life.started)
    }

    @Test
    fun `every set's reps and points add up`() {
        val s = session()
        var t = 0L
        var index = 0
        // Five reps, then nothing until the ceiling wins, three times; then the user ends it.
        repeat(3) {
            s.onTick(tick(t))
            repeat(5) {
                t += 1_000
                s.onTick(tick(t, events = listOf(strike(t, ++index))))
            }
            t = s.runUntil(t) { s.state().phase != CatPhase.PLAYING }
            t = s.runUntil(t) { s.state().phase == CatPhase.PLAYING }
        }
        s.finish(t)
        val state = s.state()
        assertEquals(CatPhase.OVER, state.phase)
        assertEquals(listOf(5, 5, 5), state.ended.map { it.reps })
        assertEquals(15, state.totalReps)
        assertEquals(state.ended.sumOf { it.score }, state.totalScore)
        assertEquals(5, state.bestSetReps)
    }

    /**
     * With no lives there is nothing for the camera's failure to cost: a set whose ceiling comes down
     * while the tracker has lost the user ends like any other, keeps its reps, and the session rests
     * and goes on.
     */
    @Test
    fun `a set the camera lost ends like any other, and the session goes on`() {
        val s = session()
        var t = 0L
        s.onTick(tick(t))
        repeat(3) {
            t += 1_000
            s.onTick(tick(t, events = listOf(strike(t, it + 1))))
        }
        t = s.runUntil(t, frame = { tick(it, quality = PoseQuality.LOW_CONFIDENCE) }) {
            s.state().phase != CatPhase.PLAYING
        }
        assertEquals(CatPhase.RESTING, s.state().phase)
        assertEquals(3, s.state().ended.single().reps)
        s.runUntil(t) { s.state().phase == CatPhase.PLAYING }
        assertEquals(CatPhase.PLAYING, s.state().phase)
    }

    @Test
    fun `the tutorial is one set and ends with it`() {
        val s = session(oneSet = true)
        val cat = CatCompanion()
        s.onTick(tick(0))
        var t = 0L
        var last = emptyList<CatSessionEvent>()
        while (s.state().phase == CatPhase.PLAYING && t < 600_000) {
            t += 33
            last = s.onTick(tick(t, quality = PoseQuality.NO_SUBJECT)).session
        }
        assertEquals(CatPhase.OVER, s.state().phase)
        assertEquals(1, s.state().ended.size)
        assertTrue(last.first() is CatSessionEvent.SetOver && last.last() is CatSessionEvent.Over, "ended as $last")
        cat.rest(0L, last, t)
        assertEquals(null, cat.view().speech?.line, "the cat spoke of a rest that does not come")
    }

    @Test
    fun `ending the session keeps the set in progress`() {
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
    fun `what a session banks is every set so far and the one in progress`() {
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
    fun `deep reps are counted per set`() {
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
    fun `the cat is fine when a set ends, says when ten seconds are left, and asks again`() {
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
