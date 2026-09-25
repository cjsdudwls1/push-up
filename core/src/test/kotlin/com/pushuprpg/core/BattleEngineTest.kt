package com.pushuprpg.core

import com.pushuprpg.core.audio.Announcer
import com.pushuprpg.core.detect.*
import com.pushuprpg.core.fixtures.PoseFixtures
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.game.*
import com.pushuprpg.core.run.AlertKey
import com.pushuprpg.core.run.BattleEngine
import com.pushuprpg.core.run.BattleState
import com.pushuprpg.core.run.Stars
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

    /**
     * A phone's frame timestamps are milliseconds since boot, not since the run started.
     *
     * Every test in this file used to fabricate timestamps from 0, which happened to coincide with
     * the fake epoch floor 0's encounter was constructed at — so the whole module was blind to the
     * fact that on a real device the boss opened the fight owing tens of thousands of idle ticks
     * and killed the player on frame one. Any clock offset must be invisible.
     */
    @Test
    fun `a run behaves the same whatever the device clock says`() {
        val offsets = listOf(0L, 600_000L, 86_400_000L, 5L * 86_400_000L)
        val outcomes = offsets.map { offset ->
            val e = engine()
            var t = offset
            val frames = mutableListOf<PoseFrame>()
            repeat(20) { frames += PoseFixtures.frame(t, 0f); t += 33 }
            repeat(6) {
                frames += PoseFixtures.rep(t, peakDepth = 0.95f)
                t = frames.last().timestampMs + 33
            }
            val state = play(e, frames)
            Triple(state.playerHp, state.outcome?.reps ?: -1, state.outcome?.cleared)
        }

        outcomes.zip(offsets).forEach { (result, offset) ->
            assertEquals(outcomes.first(), result,
                "a clock offset of ${offset}ms changed the run: $result vs ${outcomes.first()}")
        }
        assertTrue(outcomes.first().first > 0,
            "the player should still be alive after six reps, hp=${outcomes.first().first}")
    }

    /**
     * Gating damage on [PoseQuality] is not enough on its own: the idle clock kept running under
     * the gate, so the whole dropout was paid out in one burst on the frame tracking recovered.
     */
    @Test
    fun `a tracking dropout costs nothing when tracking comes back`() {
        val withGap = engine()
        var t = 100_000L
        val frames = mutableListOf<PoseFrame>()
        repeat(20) { frames += PoseFixtures.frame(t, 0f); t += 33 }
        frames += PoseFixtures.rep(t, peakDepth = 0.95f)
        t = frames.last().timestampMs + 33
        // Ninety seconds where the tracker cannot see anybody at all.
        repeat(2700) { frames += PoseFrame.empty(t); t += 33 }
        // Then they are back, and push again.
        repeat(20) { frames += PoseFixtures.frame(t, 0f); t += 33 }
        frames += PoseFixtures.rep(t, peakDepth = 0.95f)

        val state = play(withGap, frames)
        assertTrue(state.outcome == null,
            "a 90s dropout must not end the run, outcome=${state.outcome}")
        assertEquals(state.playerMaxHp, state.playerHp,
            "the dropout was repaid as damage the instant tracking returned")
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
    fun `resting in front of the camera costs nothing`() {
        // The test this replaces was named `resting in front of the camera does cost health`, and it
        // was green. That name was the design decision being reversed: the no-punish rule covered a
        // tracker dropout but not a user who is in frame and simply resting between sets.
        val e = engine()
        val frames = mutableListOf<PoseFrame>()
        var t = 3_600_000L
        repeat(20) { frames += PoseFixtures.frame(t, 0f); t += 33 }
        frames += PoseFixtures.rep(t, peakDepth = 0.95f)
        t = frames.last().timestampMs + 33
        // Five motionless minutes in shot — longer than any rest a barbell asks for.
        repeat(9000) { frames += PoseFixtures.frame(t, 0f); t += 33 }

        val state = play(e, frames)
        assertEquals(
            state.playerMaxHp, state.playerHp,
            "five minutes of resting cost ${state.playerMaxHp - state.playerHp} health",
        )
        assertTrue(state.outcome == null, "resting ended the run")
    }

    @Test
    fun `stopping part way through banks every rep`() {
        // There is no losing, so this can no longer be provoked by resting. What matters is the
        // promise underneath it: quit at any point and the reps are still yours.
        val e = engine()
        val frames = mutableListOf<PoseFrame>()
        var t = 3_600_000L
        repeat(20) { frames += PoseFixtures.frame(t, 0f); t += 33 }
        repeat(3) {
            val r = PoseFixtures.rep(t, peakDepth = 0.95f)
            frames += r
            t = r.last().timestampMs + 33
        }
        play(e, frames)

        val outcome = e.quit()
        assertTrue(!outcome.cleared)
        assertTrue(outcome.reps >= 3, "the reps performed must survive, got ${outcome.reps}")
        assertTrue(outcome.xpEarned > 0, "XP is earned per rep, so stopping cannot zero it")
        assertTrue(outcome.crackFraction > 0f, "reps dealt should shorten the retry")
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
    fun `a run costs the same reps whoever is doing it`() {
        // This reverses the old rule on purpose. The run used to be sized from measured capacity so
        // an athlete did more reps than a beginner for the same dungeon. Under the volume model the
        // tier IS the rep count, so both pay it — the athlete simply finishes sooner in wall-clock
        // time, and picks a harder difficulty if they want more work.
        val beginner = play(engine(capacity = 8f), PoseFixtures.trace(count = 200, peakDepth = 0.95f, restMs = 200))
        val athlete = play(engine(capacity = 100f), PoseFixtures.trace(count = 200, peakDepth = 0.95f, restMs = 200))

        assertTrue(beginner.outcome?.cleared == true)
        assertTrue(athlete.outcome?.cleared == true)
        assertEquals(
            beginner.outcome!!.reps, athlete.outcome!!.reps,
            "capacity still moved the rep cost: ${beginner.outcome!!.reps} vs ${athlete.outcome!!.reps}",
        )
    }

    @Test
    fun `shallow reps never advance the run`() {
        val e = engine()
        val state = play(e, PoseFixtures.trace(count = 60, peakDepth = 0.5f))
        assertEquals(0, state.reps)
        assertEquals(state.enemyMaxHp, state.enemyHp, "half reps should not scratch the enemy")
        assertTrue(state.outcome == null || !state.outcome!!.cleared)
    }

    /**
     * The coach knows what is being done: the offer to put the knees down is a pushup's, every
     * other movement is shown the nudge again, and a pull-up is nudged up rather than told to go down.
     */
    @Test
    fun `the shallow nudge fits the movement`() {
        val shallow = setOf(AlertKey.SHALLOW_TWICE, AlertKey.SHALLOW_FOUR, AlertKey.SHALLOW_PULL)
        /** Each nudge given, by how many times: the second short rep, and every one from the fourth. */
        fun nudges(exercise: ExerciseType, frames: List<PoseFrame>): Map<AlertKey, Int> {
            val e = BattleEngine(
                dungeon = Dungeons.FREE_DUNGEON,
                difficulty = Difficulty.STANDARD,
                capacity = 8f,
                initialPlayer = PlayerState.create(PlayerClass.KNIGHT, level = 1),
                detector = DetectorFactory.create(exercise),
                resolver = CombatResolver(DetectorConfig.forExercise(exercise)),
            )
            return frames.mapNotNull { e.onPoseFrame(it).alert }.filter { it.textKey in shallow }
                .distinct().groupingBy { it.textKey }.eachCount()
        }

        // Six short reps: a nudge on the second, and one on each of the fourth to the sixth.
        assertEquals(
            mapOf(AlertKey.SHALLOW_TWICE to 1, AlertKey.SHALLOW_FOUR to 3),
            nudges(ExerciseType.PUSHUP, PoseFixtures.trace(count = 6, peakDepth = 0.5f)),
        )
        assertEquals(
            mapOf(AlertKey.SHALLOW_TWICE to 4),
            nudges(ExerciseType.SQUAT, PoseFixtures.squatTrace(count = 6, peakDepth = 0.5f)),
        )
        assertEquals(
            mapOf(AlertKey.SHALLOW_PULL to 4),
            nudges(ExerciseType.PULL_UP, PoseFixtures.pullUpTrace(count = 6, peakDepth = 0.5f)),
        )
    }

    /**
     * The banner repeats the nudge on every short rep from the fourth; the voice says it once, on the
     * second. Said aloud on every short rep, it is a drill instructor.
     */
    @Test
    fun `the shallow nudge is said aloud once, however often it is shown`() {
        val shallow = setOf(AlertKey.SHALLOW_TWICE, AlertKey.SHALLOW_FOUR, AlertKey.SHALLOW_PULL)
        for ((exercise, frames) in listOf(
            ExerciseType.PUSHUP to PoseFixtures.trace(count = 10, peakDepth = 0.5f),
            ExerciseType.SQUAT to PoseFixtures.squatTrace(count = 10, peakDepth = 0.5f),
            ExerciseType.PULL_UP to PoseFixtures.pullUpTrace(count = 10, peakDepth = 0.5f),
        )) {
            val e = BattleEngine(
                dungeon = Dungeons.FREE_DUNGEON,
                difficulty = Difficulty.STANDARD,
                capacity = 8f,
                initialPlayer = PlayerState.create(PlayerClass.KNIGHT, level = 1),
                detector = DetectorFactory.create(exercise),
                resolver = CombatResolver(DetectorConfig.forExercise(exercise)),
            )
            val announcer = Announcer()
            val said = frames.flatMap { f -> announcer.battle(e.onPoseFrame(f), f.timestampMs) }
                .mapNotNull { it.alert }.filter { it in shallow }
            assertEquals(1, said.size, "$exercise: said $said")
        }
    }

    @Test
    fun `the gauge thresholds the HUD draws come from the detector`() {
        val config = DetectorConfig.pushup()
        val state = engine().currentState()
        assertEquals(config.countEnter, state.countEnter)
        assertEquals(config.deepEnter, state.deepEnter)
    }

    /**
     * The test above compares a config with itself. This is the rule it stands for: once the range
     * is learned, the rep counts on the frame the gauge crosses the 인정 line the HUD draws, and the
     * 깊은 타격 comes on the frame it crosses the 깊게 line.
     */
    @Test
    fun `a learned rep counts and goes deep where the gauge's lines are drawn`() {
        val e = engine(playerClass = PlayerClass.ARCHER)
        var prev = e.currentState()
        var counted = 0
        var deepened = 0
        for (f in PoseFixtures.trace(count = 14, peakDepth = 0.95f, restMs = 250)) {
            val s = e.onPoseFrame(f)
            if (!prev.calibrating && !s.calibrating) {
                if (s.reps > prev.reps) {
                    assertTrue(
                        prev.depth < s.countEnter && s.depth >= s.countEnter,
                        "counted going from ${prev.depth} to ${s.depth}; the gauge draws 인정 at ${s.countEnter}",
                    )
                    counted++
                }
                if (s.deepReps > prev.deepReps) {
                    assertTrue(
                        prev.depth < s.deepEnter && s.depth >= s.deepEnter,
                        "깊게 going from ${prev.depth} to ${s.depth}; the gauge draws it at ${s.deepEnter}",
                    )
                    deepened++
                }
            }
            prev = s
            if (s.outcome != null) break
        }
        assertTrue(counted >= 5 && deepened >= 5, "checked $counted counts and $deepened deep lines")
    }

    /**
     * The device-shaped version of the rule: the tracker loses the user at the bottom of the rep
     * that closes the answer window, and the detector abandons it. Nothing may land while they are
     * out of view, and coming back must not be met with the hit either.
     */
    @Test
    fun `losing the user on the window's last rep never lands the ultimate`() {
        // One floor big enough to wind up: twelve reps for a 기사.
        val dungeon = Dungeon(99, "시험", listOf(EnemyTemplate("test", "시험용", 20)), 1..1)
        val e = engine(playerClass = PlayerClass.KNIGHT, dungeon = dungeon)
        var t = 3_600_000L
        var state = e.currentState()
        fun feed(frames: List<PoseFrame>) {
            frames.forEach { state = e.onPoseFrame(it) }
            t = frames.last().timestampMs + 33
        }
        // Quick and short of 깊게: a 기사's rep that never answers.
        fun plainRep() = feed(PoseFixtures.rep(t, descentMs = 500, peakDepth = 0.72f, restMs = 400))

        feed(PoseFixtures.trace(count = 0, startMs = t))
        repeat(20) { if (!state.ultimateIncoming) plainRep() }
        assertTrue(state.ultimateIncoming, "no wind-up after ${state.reps} reps")
        while (state.ultimateRepsLeft > 1) plainRep()
        val full = state.playerHp
        val repsBefore = state.reps

        // Down to the bottom, and the camera loses them there.
        val down = PoseFixtures.rep(t, descentMs = 500, bottomMs = 150, peakDepth = 0.72f)
            .takeWhile { it.timestampMs < t + 650 }
        feed(down)
        repeat(90) { state = e.onPoseFrame(PoseFrame.empty(t)); t += 33 }
        assertEquals(repsBefore + 1, state.reps, "the fixture's last rep never struck")
        feed((0 until 30).map { PoseFixtures.frame(t + it * 33L, 0f) })

        assertEquals(full, state.playerHp, "the ultimate landed on a rep the tracker lost")
        assertTrue(state.ultimateIncoming, "the window was decided on a rep nobody saw end")
        assertEquals(1, state.ultimateRepsLeft, "the lost rep used up the last chance")
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
    fun `a held plank takes a second off per second held and keeps the boss off the player`() {
        val player = com.pushuprpg.core.game.PlayerState.create(PlayerClass.KNIGHT, level = 1)
        val enemy = Dungeons.FREE_DUNGEON.floors.first()
            .spawn(Difficulty.STANDARD, ExerciseType.PLANK)
        val encounter = Encounter(player, enemy, rng = NoCritRng, startedAtMs = 0L)

        var t = 0L
        var dealt = 0
        // A good plank at the detector's 2Hz tick: a whole second comes due every other tick.
        while (!encounter.finished && t < 600_000) {
            t += 500
            encounter.setHolding(true, t)
            val seconds = if (t % 1000 == 0L) 1 else 0
            dealt += encounter.onHold(seconds, t).filterIsInstance<CombatEvent.Hit>().sumOf { h -> h.result.damage }
            encounter.advanceTo(t)
        }

        assertEquals(enemy.maxHp, dealt, "a second held was not a second off")
        assertEquals(enemy.maxHp * 1000L, t, "a ${enemy.maxHp}-second monster fell after ${t}ms of holding")
        assertEquals(player.maxHp, encounter.player.hp, "holding should keep the boss off entirely")
    }

    @Test
    fun `a plank tears down a ward far faster than pushups chip at it`() {
        val player = com.pushuprpg.core.game.PlayerState.create(PlayerClass.KNIGHT, level = 12)
        val template = Dungeons.byIndex(6)!!.floors.last()

        fun holdPlank(): Int {
            val e = Encounter(player, template.spawn(Difficulty.STANDARD, ExerciseType.PUSHUP), rng = NoCritRng, startedAtMs = 0L)
            var t = 0L
            var seconds = 0
            while (e.enemy.warded && seconds < 5000) {
                t += 1000
                e.onHold(1, t)
                seconds++
            }
            return seconds
        }

        assertTrue(holdPlank() < 5000, "the ward should break under a sustained plank")
    }

    @Test
    fun `holding through a wind-up blocks it`() {
        val player = PlayerState.create(PlayerClass.KNIGHT, level = 1)
        val e = Encounter(player, Enemy(id = "test", korean = "시험용", maxHp = 20, hp = 20), rng = NoCritRng, startedAtMs = 0L)
        var t = 0L
        while (!e.ultimateWindingUp && t < 300_000) {
            t += 3000
            e.onRep(RepInput(72f, RepGrade.COUNTED, ExerciseType.PUSHUP, cycleMs = 3000), t)
            e.onRepEnd(t + 900)
        }
        assertTrue(e.ultimateWindingUp, "no wind-up")

        val events = mutableListOf<CombatEvent>()
        repeat(Encounter.ANSWERS_TO_BLOCK) { t += 500; events += e.onHold(0, t) }
        assertEquals(Mitigation.FULL, events.filterIsInstance<CombatEvent.Ultimate>().single().mitigation)
        assertEquals(player.maxHp, e.player.hp)
    }

    private fun plankEngine(dungeon: Dungeon = Dungeons.FREE_DUNGEON) = BattleEngine(
        dungeon = dungeon,
        difficulty = Difficulty.STANDARD,
        capacity = 8f,
        initialPlayer = PlayerState.create(PlayerClass.KNIGHT, level = 1),
        detector = DetectorFactory.create(ExerciseType.PLANK),
        resolver = CombatResolver(DetectorConfig.forExercise(ExerciseType.PLANK)),
    )

    /**
     * What a plank run used to be: the HUD read 0개 and 0초 / 36 the whole way, because it showed a
     * count of reps a plank never makes; the 36초 dungeon was over in about eight, because each tick
     * took off a form-weighted damage figure; and stopping part way banked no XP.
     */
    @Test
    fun `a plank dungeon takes the seconds it quoted, counted up on the HUD`() {
        val quoted = Dungeons.FREE_DUNGEON.repCost(Difficulty.STANDARD, ExerciseType.PLANK, PlayerClass.KNIGHT)
        val e = plankEngine()
        var state = e.currentState()
        var shown = 0
        var owed = state.enemyHp
        for (f in PoseFixtures.plankTrace(durationMs = (quoted + 15) * 1000)) {
            val floor = state.floorIndex
            state = e.onPoseFrame(f)
            assertEquals(quoted, state.runTotalReps, "the total moved off the quote")
            val held = (state.heldMs / 1000L).toInt()
            assertTrue(held >= shown, "the seconds on the HUD went back from $shown to $held")
            shown = held
            if (state.floorIndex == floor) {
                assertTrue(state.enemyHp in owed - 1..owed, "the monster lost ${owed - state.enemyHp} in a frame")
            }
            owed = state.enemyHp
            if (state.outcome != null) break
        }

        val outcome = state.outcome
        assertEquals(true, outcome?.cleared, "held ${state.heldMs}ms of a ${quoted}s dungeon and it did not clear")
        val held = outcome!!.segments.single().holdMs
        assertTrue(
            held in quoted * 1000L..(quoted + 2) * 1000L,
            "a ${quoted}s dungeon took ${held}ms of holding",
        )
        assertEquals(0, outcome.reps)
        assertTrue(outcome.xpEarned > 0)
    }

    @Test
    fun `stopping a hold part way keeps its time and its XP`() {
        // A first floor of fourteen seconds, so ten of them all come off the one monster.
        val e = plankEngine(Dungeons.byIndex(3)!!)
        var state = e.currentState()
        PoseFixtures.plankTrace(durationMs = 10_000).forEach { state = e.onPoseFrame(it) }
        assertEquals(0, state.floorIndex)
        val outcome = e.quit()

        assertTrue(!outcome.cleared)
        val held = outcome.segments.single().holdMs
        assertTrue(held > 8_000, "held ${held}ms of 10s")
        assertEquals(state.heldMs, held, "the HUD's seconds are not the ones banked")
        assertTrue(outcome.xpEarned >= 8, "${held}ms of plank banked ${outcome.xpEarned} XP")
        // And the monster owes what the HUD says was held off it, give or take the tick in flight.
        val off = state.enemyMaxHp - state.enemyHp
        assertTrue(off in (held / 1000L).toInt() - 1..(held / 1000L).toInt(), "held ${held}ms, took $off off")
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
    fun `nothing the boss does is measured in seconds`() {
        // What this replaces asserted that the boss's charge built up while the user rested, and it
        // was green. Under the volume model no threat may be keyed to wall clock, because rest is
        // free and a rest is indistinguishable from not playing.
        val e = engine()
        val frames = PoseFixtures.trace(count = 1, peakDepth = 0.95f, startMs = 3_600_000L).toMutableList()
        var t = frames.last().timestampMs + 33
        repeat(1800) { frames += PoseFixtures.frame(t, 0f); t += 33 }

        var peakCharge = 0f
        var sawIncoming = false
        for (f in frames) {
            val s = e.onPoseFrame(f)
            peakCharge = maxOf(peakCharge, s.telegraphCharge)
            if (s.ultimateIncoming) sawIncoming = true
        }
        assertEquals(0f, peakCharge, "the boss charged an ultimate while the user rested")
        assertTrue(!sawIncoming, "an ultimate was announced during a rest")
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

    // ------------------------------------------------------------ switching movement mid-run

    private fun engineIn(dungeon: Dungeon) = BattleEngine(
        dungeon = dungeon,
        difficulty = Difficulty.STANDARD,
        capacity = 8f,
        initialPlayer = PlayerState.create(PlayerClass.KNIGHT, level = 1),
        detector = RepDetectorImpl(DetectorConfig.pushup()),
        resolver = CombatResolver(),
    )

    /** Plays [frames] without stopping at an outcome, and returns the last state. */
    private fun feed(engine: BattleEngine, frames: List<PoseFrame>): BattleState {
        var last = engine.currentState()
        for (f in frames) last = engine.onPoseFrame(f)
        return last
    }

    /**
     * A real session is not one movement: pushups for a while, then pull-ups, then squats. The
     * monster being fought keeps the share of it that was left, counted in the new movement — half
     * a monster stays half a monster.
     */
    @Test
    fun `switching movement mid-fight keeps the share of the enemy that was left`() {
        val dungeon = Dungeons.byIndex(3)!!
        val e = engineIn(dungeon)
        val before = feed(e, PoseFixtures.trace(count = 4, peakDepth = 0.95f, restMs = 250))
        assertEquals(0, before.floorIndex, "the fixture should still be on the first floor")
        val leftBefore = before.enemyHp.toFloat() / before.enemyMaxHp
        assertTrue(before.reps > 0 && leftBefore in 0.01f..0.99f, "no damage landed before the switch")

        val retired = e.switchExercise(RepDetectorImpl(DetectorConfig.squat()))
        val after = e.currentState()

        assertEquals(DetectorConfig.pushup().exercise, retired.config.exercise)
        assertEquals(ExerciseType.SQUAT, after.exercise)
        val squatFloor = dungeon.floors[0].spawn(Difficulty.STANDARD, ExerciseType.SQUAT, PlayerClass.KNIGHT)
        assertEquals(squatFloor.maxHp, after.enemyMaxHp, "the enemy was not re-priced in squats")
        val leftAfter = after.enemyHp.toFloat() / after.enemyMaxHp
        assertTrue(
            leftAfter >= leftBefore && leftAfter - leftBefore <= 1f / after.enemyMaxHp + 1e-4f,
            "left $leftBefore of the enemy in pushups, $leftAfter in squats",
        )
        // The total is still the honest remaining cost: done so far, this enemy, and every floor
        // to come, all in squats now.
        val rest = dungeon.floors.drop(1).sumOf {
            CombatResolver.expectedReps(it.standardRepCost, Difficulty.STANDARD, ExerciseType.SQUAT, PlayerClass.KNIGHT)
        }
        assertEquals(after.reps + after.enemyHp + rest, after.runTotalReps)
    }

    @Test
    fun `a mixed run banks each movement as its own segment, and every rep counts`() {
        val e = engineIn(Dungeons.byIndex(3)!!)
        val pushups = PoseFixtures.trace(count = 5, peakDepth = 0.95f, restMs = 250)
        feed(e, pushups)
        val pushupReps = e.currentState().reps
        e.switchExercise(RepDetectorImpl(DetectorConfig.squat()))
        feed(e, PoseFixtures.squatTrace(count = 5, startMs = pushups.last().timestampMs + 33))
        val outcome = e.quit()

        assertEquals(listOf(ExerciseType.PUSHUP, ExerciseType.SQUAT), outcome.segments.map { it.exercise })
        assertEquals(pushupReps, outcome.segments[0].reps)
        assertTrue(outcome.segments[1].reps > 0, "the squats after the switch did not count")
        assertEquals(outcome.reps, outcome.segments.sumOf { it.reps }, "a rep went missing between segments")
    }

    /**
     * Leaving a run asks first once there is something in it, and says what will be kept. A plank
     * counts no reps, so the time held has to be on the state — and be the time the run banks,
     * across a switch as well.
     */
    @Test
    fun `the time held is on the state, and is the time the run banks`() {
        val e = BattleEngine(
            dungeon = Dungeons.byIndex(3)!!,
            difficulty = Difficulty.STANDARD,
            capacity = 8f,
            initialPlayer = PlayerState.create(PlayerClass.KNIGHT, level = 1),
            detector = DetectorFactory.create(ExerciseType.PLANK),
            resolver = CombatResolver(DetectorConfig.forExercise(ExerciseType.PLANK)),
        )
        assertTrue(!e.currentState().workDone, "a run with nothing in it yet has nothing to keep")

        val plank = PoseFixtures.plankTrace(durationMs = 10_000)
        val held = feed(e, plank)
        assertEquals(0, held.reps)
        assertTrue(held.heldMs > 8_000, "held ${held.heldMs}ms of 10s")
        assertTrue(held.workDone, "ten seconds of plank is something to keep")

        e.switchExercise(RepDetectorImpl(DetectorConfig.pushup()))
        assertEquals(held.heldMs, e.currentState().heldMs, "the plank's time went missing at the switch")
        val pushups = PoseFixtures.trace(count = 3, startMs = plank.last().timestampMs + 33, restMs = 250)
        val after = feed(e, pushups)
        assertEquals(held.heldMs, after.heldMs, "pushups added time held")
        assertEquals(e.quit().segments.sumOf { it.holdMs }, after.heldMs)
    }

    /** Pushups then a plank: the total is seconds from the switch on, never reps and seconds added. */
    @Test
    fun `switching to a hold counts the run in seconds`() {
        val dungeon = Dungeons.byIndex(3)!!
        val e = engineIn(dungeon)
        val pushups = PoseFixtures.trace(count = 3, peakDepth = 0.95f, restMs = 250)
        val before = feed(e, pushups)
        assertTrue(before.reps > 0 && before.floorIndex == 0, "the fixture should have hit the first floor")

        e.switchExercise(DetectorFactory.create(ExerciseType.PLANK))
        val switched = e.currentState()
        val rest = dungeon.floors.drop(1).sumOf { it.repCost(Difficulty.STANDARD, ExerciseType.PLANK, PlayerClass.KNIGHT) }
        assertEquals(switched.enemyHp + rest, switched.runTotalReps, "the pushups were added to a count of seconds")

        val held = feed(e, PoseFixtures.plankTrace(durationMs = 6_000, startMs = pushups.last().timestampMs + 33))
        assertEquals(switched.runTotalReps, held.runTotalReps, "holding moved the total")
        assertEquals(before.reps, held.reps)
        assertTrue(held.heldMs > 4_000 && held.enemyHp < switched.enemyHp, "held ${held.heldMs}ms for nothing")
    }

    @Test
    fun `a single rep is work done`() {
        val e = engineIn(Dungeons.byIndex(3)!!)
        assertTrue(!e.currentState().workDone)
        val state = feed(e, PoseFixtures.trace(count = 2, peakDepth = 0.95f, restMs = 250))
        assertTrue(state.reps > 0 && state.workDone, "counted ${state.reps}")
        assertEquals(0L, state.heldMs)
    }

    /**
     * The device report: every rep went chest to the floor, and the result screen said 다음엔 더 깊게.
     * A rep counts at the 인정 line on its way down, so the depth it strikes at is always about 70;
     * the stars averaged that, and could not rise above one whatever the user did.
     */
    @Test
    fun `reps that go all the way down earn three stars and count as 깊게`() {
        val e = engineIn(Dungeons.byIndex(3)!!)
        feed(e, PoseFixtures.trace(count = 8, peakDepth = 0.98f, restMs = 250))
        val outcome = e.quit()
        assertTrue(outcome.reps >= 6, "counted ${outcome.reps} of 8")
        assertEquals(Stars.THREE, outcome.stars, "mean depth ${outcome.meanDepth}")
        assertEquals(outcome.reps, outcome.deepReps, "every rep reached 깊게 and was told so")
        assertEquals(outcome.deepReps, outcome.segments.single().deepReps)
    }

    @Test
    fun `reps that stop at the 인정 line are still one star`() {
        val e = engineIn(Dungeons.byIndex(3)!!)
        // Just past the 인정 line and nowhere near 깊게. The range learns this user and the later reps
        // read a little deeper (70 to 83), which is the calibration working, not the stars.
        feed(e, PoseFixtures.trace(count = 8, peakDepth = 0.70f, restMs = 250))
        val outcome = e.quit()
        assertTrue(outcome.reps > 0, "the shallow-but-counted reps did not count")
        assertEquals(Stars.ONE, outcome.stars, "mean depth ${outcome.meanDepth}")
        assertEquals(0, outcome.deepReps)
    }

    @Test
    fun `a run that never switches is one segment, the whole run`() {
        val e = engine()
        feed(e, PoseFixtures.trace(count = 6, peakDepth = 0.95f, restMs = 250))
        val outcome = e.quit()
        assertEquals(1, outcome.segments.size)
        assertEquals(ExerciseType.PUSHUP, outcome.segments.single().exercise)
        assertEquals(outcome.reps, outcome.segments.single().reps)
    }

    /** So the result screen can say 고블린 킹에게 N개 남기고 멈췄어요, and mean it. */
    @Test
    fun `a stopped run says which monster it left, and how much of it`() {
        val e = engineIn(Dungeons.byIndex(3)!!)
        val state = feed(e, PoseFixtures.trace(count = 3, peakDepth = 0.95f, restMs = 250))
        assertTrue(state.enemyHp in 1 until state.enemyMaxHp, "the fixture should leave the first monster part done")
        val outcome = e.quit()
        assertEquals(state.enemyName, outcome.enemyName)
        assertEquals(state.enemyHp, outcome.enemyLeft)

        // A hold's is in seconds, as its fight is.
        val plank = BattleEngine(
            dungeon = Dungeons.byIndex(3)!!,
            difficulty = Difficulty.STANDARD,
            capacity = 8f,
            initialPlayer = PlayerState.create(PlayerClass.KNIGHT, level = 1),
            detector = DetectorFactory.create(ExerciseType.PLANK),
            resolver = CombatResolver(DetectorConfig.forExercise(ExerciseType.PLANK)),
        )
        val held = feed(plank, PoseFixtures.plankTrace(durationMs = 6_000))
        val stopped = plank.quit()
        assertEquals(held.enemyHp, stopped.enemyLeft)
        assertTrue(stopped.enemyLeft < held.enemyMaxHp, "six seconds held took nothing off")

        // And a cleared run left nothing.
        val cleared = feed(engine(), PoseFixtures.trace(count = 80, peakDepth = 0.95f, restMs = 250)).outcome
        assertEquals(true, cleared?.cleared)
        assertEquals(0, cleared!!.enemyLeft)
        assertEquals(Dungeons.FREE_DUNGEON.floors.last().korean, cleared.enemyName)
    }
}
