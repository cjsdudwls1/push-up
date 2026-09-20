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

    private fun engine(dungeon: Dungeon = Dungeons.FREE_DUNGEON) = BattleEngine(
        dungeon = dungeon,
        difficulty = Difficulty.STANDARD,
        capacity = 8f,
        initialPlayer = PlayerState.create(PlayerClass.KNIGHT, level = 1),
        detector = RepDetectorImpl(DetectorConfig.pushup()),
        resolver = CombatResolver(),
    )

    @Test
    fun `the reps a dungeon advertises are the reps it takes`() {
        val dungeon = Dungeons.FREE_DUNGEON
        val advertised = dungeon.floors.sumOf { floor ->
            val enemy = floor.spawn(Difficulty.STANDARD, ExerciseType.PUSHUP)
            enemy.maxHp + enemy.wardMaxHp
        }

        val e = engine(dungeon)
        var state = e.currentState()
        PoseFixtures.trace(count = advertised + 20, startMs = 3_600_000L).forEach {
            if (state.outcome == null) state = e.onPoseFrame(it)
        }

        assertEquals(true, state.outcome?.cleared, "the dungeon did not clear")
        assertEquals(
            advertised, state.outcome!!.reps,
            "advertised $advertised reps, took ${state.outcome?.reps}",
        )
    }

    @Test
    fun `the run total the HUD shows is the number the entry screen quoted`() {
        // The entry picker prints CombatResolver.expectedReps per movement and the HUD counts up to
        // BattleState.runTotalReps. If those two ever disagree the app promises one number and asks
        // for another, which is the whole thing the volume model exists to prevent.
        ExerciseType.entries.forEach { exercise ->
            val dungeon = Dungeons.FREE_DUNGEON
            val quoted = dungeon.repCost(Difficulty.STANDARD, exercise)
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
                dungeon.standardRepCost, Difficulty.STANDARD, exercise,
            )
            if (aggregate != quoted) {
                assertEquals(
                    quoted, dungeon.repCost(Difficulty.STANDARD, exercise),
                    "$exercise: repCost must be the per-floor sum, not the aggregate $aggregate",
                )
            }
            assertEquals(exercise, engine.currentState().exercise)
        }
    }

    @Test
    fun `a long rest in the middle changes nothing about the cost`() {
        val dungeon = Dungeons.FREE_DUNGEON
        val advertised = dungeon.floors.sumOf {
            val en = it.spawn(Difficulty.STANDARD, ExerciseType.PUSHUP)
            en.maxHp + en.wardMaxHp
        }

        val e = engine(dungeon)
        var state = e.currentState()
        var t = 3_600_000L

        // Five reps, three minutes of rest, then the remainder — a completely ordinary session.
        PoseFixtures.trace(count = 5, startMs = t).forEach { state = e.onPoseFrame(it) }
        t = state.elapsedMs + 3_600_000L
        repeat(5400) { state = e.onPoseFrame(PoseFixtures.frame(t, 0f)); t += 33 }
        assertEquals(state.playerMaxHp, state.playerHp, "the rest cost health")

        PoseFixtures.trace(count = advertised + 20, startMs = t).forEach {
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
    fun `a loaded movement's ladder is shorter than a bodyweight one's`() {
        // The reason sessionVolumeScale exists rather than reusing damageCoefficient. A trained
        // pushup session is around 150 reps; a trained bench session is 20-40 working reps. So the
        // same ladder cannot ask both for the same number, and the loaded movements must ask for
        // markedly less. Measured totals across all eight dungeons: pushup 446, squat 298,
        // lunge 388, pull-up 137, curl 137, hinge 115, bench 90, overhead press 90.
        fun ladder(exercise: ExerciseType) = Dungeons.ALL.sumOf { d ->
            d.floors.sumOf { CombatResolver.expectedReps(it.standardRepCost, Difficulty.STANDARD, exercise) }
        }

        val pushup = ladder(ExerciseType.PUSHUP)
        listOf(
            ExerciseType.BENCH_PRESS,
            ExerciseType.OVERHEAD_PRESS,
            ExerciseType.PULL_UP,
            ExerciseType.HINGE,
        ).forEach { loaded ->
            val total = ladder(loaded)
            assertTrue(
                total < pushup / 2,
                "$loaded asks for $total against a pushup's $pushup — a session that does not exist",
            )
        }

        // And a hold is counted in seconds, so its number is allowed to be the largest of all.
        assertTrue(ladder(ExerciseType.PLANK) > pushup, "a plank ladder should be seconds, not reps")
    }
}
