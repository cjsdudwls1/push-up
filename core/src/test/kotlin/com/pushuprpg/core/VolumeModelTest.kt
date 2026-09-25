package com.pushuprpg.core

import com.pushuprpg.core.detect.DetectorConfig
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.DetectorFactory
import com.pushuprpg.core.detect.RepDetectorImpl
import com.pushuprpg.core.detect.UserProfile
import com.pushuprpg.core.fixtures.PoseFixtures
import com.pushuprpg.core.game.*
import com.pushuprpg.core.run.BattleEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The promise the entry screen makes, asserted end to end.
 *
 * A run is one session of one movement, and the count decides which grade of monster falls. So the
 * number shown before the run has to be the number actually performed — not approximately, and not
 * conditional on holding a combo, resting little, or having a particular measured capacity.
 */
class VolumeModelTest {

    private fun engine(dungeon: Dungeon = Dungeons.FREE_DUNGEON, cls: PlayerClass = PlayerClass.KNIGHT) = BattleEngine(
        dungeon = dungeon,
        difficulty = Difficulty.STANDARD,
        capacity = 8f,
        initialPlayer = PlayerState.create(cls, level = 1),
        detector = RepDetectorImpl(DetectorConfig.pushup()),
        resolver = CombatResolver(),
    )

    /**
     * Reps done each class's way: a 기사 lowering for two seconds all the way down, a 궁수 at a
     * little over a second a rep. The promise is about reps done the way the class asks.
     */
    private fun inStyle(cls: PlayerClass, count: Int, startMs: Long) = when (cls) {
        PlayerClass.KNIGHT -> PoseFixtures.trace(count = count, startMs = startMs, descentMs = 2000)
        PlayerClass.ARCHER -> PoseFixtures.trace(
            count = count, startMs = startMs, descentMs = 450, bottomMs = 60, ascentMs = 450, restMs = 100,
        )
    }

    @Test
    fun `the reps a dungeon advertises are the reps it takes`() {
        val dungeon = Dungeons.FREE_DUNGEON
        for (cls in PlayerClass.entries) {
            val advertised = dungeon.floors.sumOf { floor ->
                val enemy = floor.spawn(Difficulty.STANDARD, ExerciseType.PUSHUP, cls)
                enemy.maxHp + enemy.wardMaxHp
            }
            assertEquals(dungeon.repCost(Difficulty.STANDARD, ExerciseType.PUSHUP, cls), advertised)

            val e = engine(dungeon, cls)
            var state = e.currentState()
            inStyle(cls, advertised + 20, 3_600_000L).forEach {
                if (state.outcome == null) state = e.onPoseFrame(it)
            }

            assertEquals(true, state.outcome?.cleared, "$cls: the dungeon did not clear")
            assertEquals(
                advertised, state.outcome!!.reps,
                "$cls: advertised $advertised reps, took ${state.outcome?.reps}",
            )
            assertEquals(advertised, state.outcome!!.styleReps, "$cls: reps done its way were not all whole")
        }
    }

    @Test
    fun `a class's own way costs fewer reps for a 기사 and more for a 궁수`() {
        val dungeon = Dungeons.FREE_DUNGEON
        val standard = dungeon.repCost(Difficulty.STANDARD, ExerciseType.PUSHUP)
        val knight = dungeon.repCost(Difficulty.STANDARD, ExerciseType.PUSHUP, PlayerClass.KNIGHT)
        val archer = dungeon.repCost(Difficulty.STANDARD, ExerciseType.PUSHUP, PlayerClass.ARCHER)
        assertTrue(knight < standard && standard < archer, "기사 $knight, standard $standard, 궁수 $archer")
        // A hold has no tempo to do one way or the other, so it costs the same for both.
        assertEquals(
            dungeon.repCost(Difficulty.STANDARD, ExerciseType.PLANK, PlayerClass.KNIGHT),
            dungeon.repCost(Difficulty.STANDARD, ExerciseType.PLANK, PlayerClass.ARCHER),
        )
    }

    @Test
    fun `a rep not done the class's way is worth half, and the total on screen says so`() {
        val dungeon = Dungeons.FREE_DUNGEON
        val advertised = dungeon.repCost(Difficulty.STANDARD, ExerciseType.PUSHUP, PlayerClass.KNIGHT)

        // A 기사 diving down in under half a second: every rep counts, each is half.
        val e = engine(dungeon, PlayerClass.KNIGHT)
        var state = e.currentState()
        PoseFixtures.trace(count = 2 * advertised + 20, startMs = 3_600_000L, descentMs = 600).forEach {
            if (state.outcome == null) state = e.onPoseFrame(it)
            if (state.outcome == null) {
                assertTrue(state.runTotalReps >= advertised, "the total shrank to ${state.runTotalReps}")
            }
        }
        assertEquals(true, state.outcome?.cleared, "half-worth reps never cleared the dungeon")
        assertEquals(2 * advertised, state.outcome!!.reps, "quick reps were not worth half")
        assertEquals(0, state.outcome!!.styleReps)
        assertEquals(state.outcome!!.reps, state.runTotalReps, "the total did not grow to what was done")
    }

    @Test
    fun `the run total the HUD shows is the number the entry screen quoted`() {
        // The entry picker prints CombatResolver.expectedReps per movement and the HUD counts up to
        // BattleState.runTotalReps. If those two ever disagree the app promises one number and asks
        // for another, which is the whole thing the volume model exists to prevent.
        ExerciseType.entries.forEach { exercise ->
            val dungeon = Dungeons.FREE_DUNGEON
            val quoted = dungeon.repCost(Difficulty.STANDARD, exercise, PlayerClass.KNIGHT)
            val engine = BattleEngine(
                dungeon = dungeon,
                difficulty = Difficulty.STANDARD,
                capacity = 8f,
                initialPlayer = PlayerState.create(PlayerClass.KNIGHT, level = 1),
                detector = DetectorFactory.create(exercise, profile = UserProfile.empty()),
                resolver = CombatResolver(),
            )
            val shown = engine.currentState().runTotalReps

            assertEquals(
                quoted, shown,
                "$exercise: the entry screen said $quoted and the HUD counts to $shown",
            )
            // And the aggregate shortcut must not be used anywhere, because it rounds once where
            // the run rounds per floor: for a pull-up that gap advertised 5 for a run costing 6.
            val aggregate = CombatResolver.expectedReps(
                dungeon.standardRepCost, Difficulty.STANDARD, exercise, PlayerClass.KNIGHT,
            )
            if (aggregate != quoted) {
                assertEquals(
                    quoted, dungeon.repCost(Difficulty.STANDARD, exercise, PlayerClass.KNIGHT),
                    "$exercise: repCost must be the per-floor sum, not the aggregate $aggregate",
                )
            }
            assertEquals(exercise, engine.currentState().exercise)
        }
    }

    /**
     * Every movement pays a ward — the one it is weak to pays its face value — so leaving wards out
     * of the quote advertised 42 reps for a run of 50, and the total jumped when the warded floor
     * spawned.
     */
    @Test
    fun `a warded dungeon's quote is what its floors ask, wards included`() {
        for (index in listOf(6, 8)) {
            val dungeon = Dungeons.byIndex(index)!!
            for (exercise in listOf(ExerciseType.PUSHUP, ExerciseType.PLANK)) for (cls in PlayerClass.entries) {
                val floors = dungeon.floors.map { it.spawn(Difficulty.STANDARD, exercise, cls) }
                assertTrue(floors.any { it.wardMaxHp > 0 }, "dungeon $index has no ward to test")
                val asked = floors.sumOf { it.maxHp + it.wardMaxHp }
                assertEquals(asked, dungeon.repCost(Difficulty.STANDARD, exercise, cls), "dungeon $index, $exercise, $cls")

                val engine = BattleEngine(
                    dungeon = dungeon,
                    difficulty = Difficulty.STANDARD,
                    capacity = 8f,
                    initialPlayer = PlayerState.create(cls, level = 1),
                    detector = DetectorFactory.create(exercise, profile = UserProfile.empty()),
                    resolver = CombatResolver(),
                )
                assertEquals(asked, engine.currentState().runTotalReps, "dungeon $index, $exercise, $cls: the HUD's total")
            }
        }
    }

    @Test
    fun `a warded dungeon takes the reps it quoted, and the bar moves through the ward`() {
        val dungeon = Dungeons.byIndex(6)!!
        val warded = dungeon.floors.indexOfFirst { it.wardFraction > 0f }
        for (cls in PlayerClass.entries) {
            val quoted = dungeon.repCost(Difficulty.STANDARD, ExerciseType.PUSHUP, cls)
            val wardedCount = dungeon.floors[warded].repCost(Difficulty.STANDARD, ExerciseType.PUSHUP, cls)

            val e = engine(dungeon, cls)
            var state = e.currentState()
            val barOnWardedFloor = mutableSetOf<Int>()
            inStyle(cls, quoted + 20, 3_600_000L).forEach {
                if (state.outcome != null) return@forEach
                state = e.onPoseFrame(it)
                assertEquals(quoted, state.runTotalReps, "$cls: the total moved off the quote at ${state.reps}")
                if (state.floorIndex == warded) {
                    assertEquals(wardedCount, state.enemyMaxHp)
                    barOnWardedFloor += state.enemyHp
                }
            }

            assertEquals(true, state.outcome?.cleared, "$cls: the dungeon did not clear")
            assertEquals(quoted, state.outcome!!.reps, "$cls: quoted $quoted reps, took ${state.outcome?.reps}")
            assertEquals((0..wardedCount).toSet(), barOnWardedFloor, "$cls: the bar skipped or stuck on the warded floor")
        }
    }

    /**
     * A 기사's strike takes the first half of a rep and the deep line the second. Counted as half
     * until then, the total rose by one at every strike and fell back at the deep line, the number
     * that rose showed a count the deep line was about to change, and a perfect run ended on
     * 10개 / 11.
     */
    @Test
    fun `a 기사's own way keeps the total still and the numbers only falling`() {
        val dungeon = Dungeons.FREE_DUNGEON
        val quoted = dungeon.repCost(Difficulty.STANDARD, ExerciseType.PUSHUP, PlayerClass.KNIGHT)
        val e = engine(dungeon, PlayerClass.KNIGHT)
        var state = e.currentState()
        var lastId = 0L
        val numbers = mutableMapOf<Int, MutableList<Int>>()
        inStyle(PlayerClass.KNIGHT, quoted + 20, 3_600_000L).forEach {
            if (state.outcome != null) return@forEach
            state = e.onPoseFrame(it)
            assertEquals(quoted, state.runTotalReps, "the total moved at ${state.reps} reps")
            for (d in state.damages) if (d.id > lastId) {
                numbers.getOrPut(state.floorIndex) { mutableListOf() } += d.amount
                lastId = d.id
            }
        }

        assertEquals(true, state.outcome?.cleared)
        assertEquals(state.runTotalReps, state.reps, "the run ended short of its own total")
        dungeon.floors.forEachIndexed { i, floor ->
            val count = floor.repCost(Difficulty.STANDARD, ExerciseType.PUSHUP, PlayerClass.KNIGHT)
            assertEquals<List<Int>?>((count - 1 downTo 0).toList(), numbers[i], "floor $i's numbers")
        }
    }

    @Test
    fun `a long rest in the middle changes nothing about the cost`() {
        val dungeon = Dungeons.FREE_DUNGEON
        val advertised = dungeon.floors.sumOf {
            val en = it.spawn(Difficulty.STANDARD, ExerciseType.PUSHUP, PlayerClass.KNIGHT)
            en.maxHp + en.wardMaxHp
        }

        val e = engine(dungeon)
        var state = e.currentState()
        var t = 3_600_000L

        // Five reps, three minutes of rest, then the remainder — a completely ordinary session.
        inStyle(PlayerClass.KNIGHT, 5, t).forEach { state = e.onPoseFrame(it) }
        t = state.elapsedMs + 3_600_000L
        repeat(5400) { state = e.onPoseFrame(PoseFixtures.frame(t, 0f)); t += 33 }
        assertEquals(state.playerMaxHp, state.playerHp, "the rest cost health")

        inStyle(PlayerClass.KNIGHT, advertised + 20, t).forEach {
            if (state.outcome == null) state = e.onPoseFrame(it)
        }

        assertEquals(true, state.outcome?.cleared, "the run did not survive a three-minute rest")
        assertEquals(
            advertised, state.outcome!!.reps,
            "the rest changed the cost: advertised $advertised, took ${state.outcome?.reps}",
        )
    }

    @Test
    fun `a higher tier needs more reps, monotonically`() {
        val costs = Dungeons.ALL.map { d ->
            d.floors.sumOf {
                val e = it.spawn(Difficulty.STANDARD, ExerciseType.PUSHUP)
                e.maxHp + e.wardMaxHp
            }
        }
        assertEquals(costs.sorted(), costs, "the ladder is not ascending: $costs")
        assertTrue(costs.first() < 40, "the first tier is not reachable for a beginner: ${costs.first()}")
    }

    @Test
    fun `a hard movement's ladder is shorter than an easy one's`() {
        // The reason sessionVolumeScale exists rather than reusing damageCoefficient. A trained
        // pushup session is around 150 reps; a trained pull-up or dip session is about 45. So the
        // same ladder cannot ask both for the same number, and the hard movements must ask for
        // markedly less. Measured totals across all eight dungeons: pushup 446, squat 298,
        // lunge 388, pull-up 137, dip 137.
        fun ladder(exercise: ExerciseType) = Dungeons.ALL.sumOf { d ->
            d.floors.sumOf { CombatResolver.expectedReps(it.standardRepCost, Difficulty.STANDARD, exercise) }
        }

        val pushup = ladder(ExerciseType.PUSHUP)
        listOf(ExerciseType.PULL_UP, ExerciseType.DIP).forEach { hard ->
            val total = ladder(hard)
            assertTrue(
                total < pushup / 2,
                "$hard asks for $total against a pushup's $pushup — a session that does not exist",
            )
        }

        // And a hold is counted in seconds, so its number is allowed to be the largest of all.
        assertTrue(ladder(ExerciseType.PLANK) > pushup, "a plank ladder should be seconds, not reps")
    }
}
