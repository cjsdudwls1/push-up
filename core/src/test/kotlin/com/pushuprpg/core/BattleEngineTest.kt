package com.pushuprpg.core

import com.pushuprpg.core.detect.*
import com.pushuprpg.core.fixtures.PoseFixtures
import com.pushuprpg.core.game.*
import com.pushuprpg.core.run.BattleEngine
import com.pushuprpg.core.run.BattleState
import com.pushuprpg.core.pose.PoseFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end runs: synthetic landmarks in, a finished dungeon out.
 *
 * These are the tests that would have caught every integration bug in this app, because they are
 * the only place the detector, the calibrator, the combat maths and the floor sequencing all run
 * against each other the way they will on a phone.
 */
class BattleEngineTest {

    private fun engine(
        playerClass: PlayerClass = PlayerClass.KNIGHT,
        difficulty: Difficulty = Difficulty.STANDARD,
        capacity: Float = 8f,
        dungeon: Dungeon = Dungeons.FREE_DUNGEON,
    ): BattleEngine {
        val player = PlayerState.create(playerClass, level = 1)
        return BattleEngine(
            dungeon = dungeon,
            difficulty = difficulty,
            capacity = capacity,
            initialPlayer = player,
            detector = RepDetectorImpl(DetectorConfig.pushup()),
            resolver = CombatResolver(),
        )
    }

    /** Feeds frames until the run ends or the trace runs out. */
    private fun play(engine: BattleEngine, frames: List<PoseFrame>): BattleState {
        var last = engine.currentState()
        for (f in frames) {
            last = engine.onPoseFrame(f)
            if (last.outcome != null) break
        }
        return last
    }

    @Test
    fun `a steady set clears the first dungeon`() {
        val e = engine()
        val state = play(e, PoseFixtures.trace(count = 80, peakDepth = 0.95f, restMs = 250))

        assertTrue(state.outcome != null, "the run should have ended")
        assertTrue(state.outcome!!.cleared, "a sustained set should clear 부서진 문")
        assertTrue(state.outcome!!.reps in 8..40,
            "clearing took ${state.outcome!!.reps} reps; authored for about 14")
        assertEquals(Dungeons.FREE_DUNGEON.floors.size - 1, state.floorIndex)
    }

    @Test
    fun `the boss is never idle-killed while the tracker cannot see the user`() {
        val e = engine()
        // Two minutes of an empty frame: nobody in shot at all.
        val frames = (0 until 3600).map { PoseFrame.empty(it * 33L) }
        val state = play(e, frames)

        assertEquals(100, state.playerHp.coerceAtMost(100))
        assertTrue(state.outcome == null, "a user who is not in frame must not lose the run")
        assertTrue(state.paused, "the HUD should be telling them the game is waiting")
    }

    @Test
    fun `resting in front of the camera does cost health`() {
        val e = engine()
        // Arm, do one rep, then hold still in shot for a minute.
        val frames = mutableListOf<PoseFrame>()
        var t = 0L
        repeat(20) { frames += PoseFixtures.frame(t, 0f); t += 33 }
        frames += PoseFixtures.rep(t, peakDepth = 0.95f)
        t = frames.last().timestampMs + 33
        repeat(1800) { frames += PoseFixtures.frame(t, 0f); t += 33 }

        val state = play(e, frames)
        assertTrue(state.playerHp < state.playerMaxHp,
            "standing still in frame should draw fire, hp=${state.playerHp}")
    }

    @Test
    fun `a lost run still banks every rep and its xp`() {
        val e = engine()
        val frames = mutableListOf<PoseFrame>()
        var t = 0L
        repeat(20) { frames += PoseFixtures.frame(t, 0f); t += 33 }
        repeat(3) {
            val r = PoseFixtures.rep(t, peakDepth = 0.95f)
            frames += r
            t = r.last().timestampMs + 33
        }
        repeat(5000) { frames += PoseFixtures.frame(t, 0f); t += 33 }

        val state = play(e, frames)
        val outcome = state.outcome
        assertTrue(outcome != null, "resting that long should end the run")
        assertTrue(!outcome!!.cleared)
        assertTrue(outcome.reps >= 3, "the reps performed must survive the loss, got ${outcome.reps}")
        assertTrue(outcome.xpEarned > 0, "XP is earned per rep, so a loss cannot zero it")
        assertTrue(outcome.crackFraction > 0f, "damage dealt should shorten the retry")
    }

    @Test
    fun `quitting mid-run keeps what was earned`() {
        val e = engine()
        play(e, PoseFixtures.trace(count = 4, peakDepth = 0.95f))
        val outcome = e.quit()
        assertTrue(outcome.reps >= 3, "got ${outcome.reps}")
        assertTrue(outcome.xpEarned > 0)
        assertTrue(!outcome.cleared)
    }

    @Test
    fun `the run walks through every floor of the dungeon`() {
        val e = engine()
        var maxFloor = 0
        for (f in PoseFixtures.trace(count = 80, peakDepth = 0.95f, restMs = 250)) {
            val s = e.onPoseFrame(f)
            maxFloor = maxOf(maxFloor, s.floorIndex)
            if (s.outcome != null) break
        }
        assertEquals(Dungeons.FREE_DUNGEON.floors.size - 1, maxFloor,
            "should have fought all ${Dungeons.FREE_DUNGEON.floors.size} floors")
    }

    @Test
    fun `an athlete and a beginner both get a sane length run`() {
        val beginner = engine(capacity = 8f)
        val beginnerState = play(beginner, PoseFixtures.trace(count = 200, peakDepth = 0.95f, restMs = 200))

        val athlete = engine(capacity = 100f)
        val athleteState = play(athlete, PoseFixtures.trace(count = 300, peakDepth = 0.95f, restMs = 200))

        assertTrue(beginnerState.outcome?.cleared == true)
        assertTrue(athleteState.outcome?.cleared == true)

        val beginnerReps = beginnerState.outcome!!.reps
        val athleteReps = athleteState.outcome!!.reps
        assertTrue(athleteReps > beginnerReps, "the athlete should work harder: $athleteReps vs $beginnerReps")
        assertTrue(athleteReps < beginnerReps * 8,
            "the athlete would be here all day: $athleteReps vs $beginnerReps")
    }

    @Test
    fun `shallow reps never advance the run`() {
        val e = engine()
        val state = play(e, PoseFixtures.trace(count = 60, peakDepth = 0.5f))
        assertEquals(0, state.reps)
        assertEquals(state.enemyMaxHp, state.enemyHp, "half reps should not scratch the enemy")
        assertTrue(state.outcome == null || !state.outcome!!.cleared)
    }

    @Test
    fun `the gauge thresholds the HUD draws come from the detector`() {
        val config = DetectorConfig.pushup()
        val state = engine().currentState()
        assertEquals(config.countEnter, state.countEnter)
        assertEquals(config.deepEnter, state.deepEnter)
    }

    @Test
    fun `every class can clear the first dungeon`() {
        for (cls in PlayerClass.entries) {
            val e = engine(playerClass = cls)
            val state = play(e, PoseFixtures.trace(count = 150, peakDepth = 0.95f, restMs = 200))
            assertTrue(state.outcome?.cleared == true, "$cls could not clear 부서진 문")
        }
    }
}
