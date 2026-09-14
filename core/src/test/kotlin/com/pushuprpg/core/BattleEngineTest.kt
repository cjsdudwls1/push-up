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

/** The plank as a combat action rather than as a detector output. */
class PlankCombatTest {

    @Test
    fun `a held plank damages the enemy and keeps the boss off the player`() {
        val player = com.pushuprpg.core.game.PlayerState.create(PlayerClass.MAGE, level = 1)
        val enemy = Dungeons.FREE_DUNGEON.floors.first()
            .spawn(player, Difficulty.STANDARD, 8f, Dungeons.FREE_DUNGEON.referenceLevel)
        val encounter = Encounter(player, enemy, rng = NoCritRng)

        var t = 0L
        var dealt = 0
        // Thirty seconds of a good plank at the detector's 2Hz tick.
        repeat(60) {
            t += 500
            encounter.setHolding(true, t)
            dealt += encounter.onHold(3.0f, t).filterIsInstance<CombatEvent.Hit>().sumOf { h -> h.result.damage }
            encounter.advanceTo(t)
        }

        assertTrue(dealt > 0, "a held plank should actually hurt the enemy")
        assertEquals(player.maxHp, encounter.player.hp, "holding should keep the boss off entirely")
    }

    @Test
    fun `a plank tears down a ward far faster than pushups chip at it`() {
        val player = com.pushuprpg.core.game.PlayerState.create(PlayerClass.MAGE, level = 12)
        val template = Dungeons.byIndex(6)!!.floors.last()

        fun tickPlank(): Int {
            val e = Encounter(player, template.spawn(player, Difficulty.STANDARD, 8f, 12), rng = NoCritRng)
            var t = 0L
            var ticks = 0
            while (e.enemy.warded && ticks < 5000) {
                t += 500
                e.onHold(6.0f, t)
                ticks++
            }
            return ticks
        }

        assertTrue(tickPlank() < 5000, "the ward should break under a sustained plank")
    }
}

/** The presentation state the engine resolves, so the renderer never has to decide anything. */
class BattleEnginePresentationTest {

    private fun engine() = BattleEngine(
        dungeon = Dungeons.FREE_DUNGEON,
        difficulty = Difficulty.STANDARD,
        capacity = 8f,
        initialPlayer = PlayerState.create(PlayerClass.KNIGHT, level = 1),
        detector = RepDetectorImpl(DetectorConfig.pushup()),
        resolver = CombatResolver(),
    )

    @Test
    fun `the avatar coils in step with the user's descent`() {
        // The answer to "it only has one attack motion" is this, not more clips: the character does
        // the rep with the user every frame it is idle.
        val e = engine()
        var maxWindup = 0f
        var sawDeepWindup = false
        for (f in PoseFixtures.trace(count = 2, peakDepth = 0.95f)) {
            val s = e.onPoseFrame(f)
            maxWindup = maxOf(maxWindup, s.playerAnim.windup)
            if (s.depth > 80f && s.playerAnim.windup > 0.6f) sawDeepWindup = true
        }
        assertTrue(maxWindup > 0.7f, "the avatar never wound up, peaked at $maxWindup")
        assertTrue(sawDeepWindup, "the wind-up did not track the depth")
    }

    @Test
    fun `consecutive reps play different attack motions`() {
        val e = engine()
        val attacks = mutableListOf<com.pushuprpg.core.anim.AnimClip>()
        var last: com.pushuprpg.core.anim.AnimClip? = null
        for (f in PoseFixtures.trace(count = 6, peakDepth = 0.80f)) {
            val s = e.onPoseFrame(f)
            val clip = s.playerAnim.clip
            if (clip.isAttack && clip != last) attacks += clip
            last = clip
            if (s.outcome != null) break
        }
        assertTrue(attacks.size >= 3, "only saw $attacks")
        assertTrue(attacks.zipWithNext().all { (a, b) -> a != b }, "repeated a motion: $attacks")
    }

    @Test
    fun `the enemy flashes when struck and shatters when killed`() {
        val e = engine()
        var sawHurt = false
        var sawDeath = false
        for (f in PoseFixtures.trace(count = 60, peakDepth = 0.95f, restMs = 250)) {
            val s = e.onPoseFrame(f)
            if (s.enemyHurt > 0.5f) sawHurt = true
            if (s.enemyDeath > 0f) sawDeath = true
            if (s.outcome != null) break
        }
        assertTrue(sawHurt, "the enemy never reacted to being hit")
        assertTrue(sawDeath, "no enemy ever died")
    }

    @Test
    fun `the boss visibly charges rather than snapping into a warning`() {
        // The charge is exposed across the whole approach to the threshold, not only once the
        // telegraph fires, so the enemy is seen winding up. A user mid-rep is not reading the
        // screen and needs the peripheral cue before the words arrive.
        val e = engine()
        // Rage builds from resting far faster than from reps, which is what the warning is for.
        val frames = PoseFixtures.trace(count = 1, peakDepth = 0.95f).toMutableList()
        var t = frames.last().timestampMs + 33
        repeat(1800) { frames += PoseFixtures.frame(t, 0f); t += 33 }

        var peak = 0f
        var sawMidCharge = false
        for (f in frames) {
            val s = e.onPoseFrame(f)
            peak = maxOf(peak, s.telegraphCharge)
            if (s.telegraphCharge in 0.2f..0.9f) sawMidCharge = true
            if (s.outcome != null) break
        }
        assertTrue(peak > 0f, "the charge never moved")
        assertTrue(sawMidCharge, "the charge jumped straight to full, peaked at $peak")
    }

    @Test
    fun `a killed enemy finishes coming apart before the next one arrives`() {
        val e = engine()
        var sawPartialDeath = false
        var floorAtDeathStart = -1
        var floorAdvancedTooEarly = false
        for (f in PoseFixtures.trace(count = 60, peakDepth = 0.95f, restMs = 250)) {
            val s = e.onPoseFrame(f)
            if (s.enemyDeath > 0f && s.enemyDeath < 1f) {
                sawPartialDeath = true
                if (floorAtDeathStart < 0) floorAtDeathStart = s.floorIndex
                if (s.floorIndex != floorAtDeathStart) floorAdvancedTooEarly = true
            } else {
                floorAtDeathStart = -1
            }
            if (s.outcome != null) break
        }
        assertTrue(sawPartialDeath, "the death animation was never visible")
        assertTrue(!floorAdvancedTooEarly, "the next floor arrived mid-shatter")
    }

    @Test
    fun `the run's ending wins over a late combat event`() {
        val e = engine()
        var frames = PoseFixtures.trace(count = 3, peakDepth = 0.95f)
        frames.forEach { e.onPoseFrame(it) }
        val outcome = e.quit()
        assertTrue(!outcome.cleared)
    }
}
