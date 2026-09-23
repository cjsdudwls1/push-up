package com.pushuprpg.core

import com.pushuprpg.core.detect.DetectorConfig
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.RepGrade
import com.pushuprpg.core.game.*
import com.pushuprpg.core.detect.RepGrade as Grade
import com.pushuprpg.core.progression.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CombatTest {

    private val resolver = CombatResolver()
    private val config = DetectorConfig.pushup()

    private fun knight(level: Int = 1, combo: Int = 0) =
        PlayerState.create(PlayerClass.KNIGHT, level).copy(combo = combo)

    private fun dummy(hp: Int = 10_000, defense: Int = 0) =
        Enemy("dummy", "허수아비", hp, hp, defense = defense)

    private fun rep(depth: Float, cycleMs: Int = 3000, exercise: ExerciseType = ExerciseType.PUSHUP) =
        RepInput(depth, if (depth >= config.deepEnter) RepGrade.DEEP else RepGrade.COUNTED, exercise, cycleMs)

    @Test
    fun `a deep rep at combo eleven reproduces the demo's twenty damage`() {
        // The demo shows "20 / 깊은 타격" for a level-1 knight mid-combo. Tempo is deliberately out
        // of band so this pins the depth and combo terms alone:
        //   ATK 10 x depth 1.6417 (90 on the gauge, knight slope) x combo 1.24 = 20.4 -> 20.
        val r = resolver.resolve(knight(combo = 11), dummy(), rep(90f, cycleMs = 5000), NoCritRng)
        assertEquals(20, r.damage, "damage was ${r.damage} (depth x${r.depthMultiplier}, combo x${r.comboMultiplier})")
        assertTrue(r.deep)
        assertEquals(12, r.player.combo)
    }

    @Test
    fun `damage rises monotonically with depth and with combo`() {
        val byDepth = listOf(70f, 80f, 88f, 95f, 100f).map {
            resolver.resolve(knight(combo = 5), dummy(), rep(it, cycleMs = 5000), NoCritRng).damage
        }
        assertEquals(byDepth.sorted(), byDepth, "deeper must never hit softer: $byDepth")

        val byCombo = listOf(0, 10, 25, 50).map {
            resolver.resolve(knight(combo = it), dummy(), rep(95f, cycleMs = 5000), NoCritRng).damage
        }
        assertEquals(byCombo.sorted(), byCombo, "longer combo must never hit softer: $byCombo")
    }

    @Test
    fun `the combo multiplier is capped so a hundred rep set cannot snowball`() {
        val atCap = resolver.resolve(knight(combo = 50), dummy(), rep(95f, 5000), NoCritRng).damage
        val wayPast = resolver.resolve(knight(combo = 200), dummy(), rep(95f, 5000), NoCritRng)
        // Past the cap the multiplier stops; the small momentum bonus is the only further gain.
        assertTrue(wayPast.damage <= atCap * 1.15f,
            "combo 200 dealt ${wayPast.damage} against $atCap at the cap")
        assertEquals(2.0f, resolver.comboMultiplier(999, PlayerClass.KNIGHT))
    }

    @Test
    fun `a shallow rep deals nothing and costs combo without wiping it`() {
        // Shallowness is the detector's verdict, carried on the grade — combat does not re-measure.
        val shallow = RepInput(config.countEnter - 1f, RepGrade.SHALLOW)
        val r = resolver.resolve(knight(combo = 10), dummy(), shallow, NoCritRng)
        assertEquals(0, r.damage)
        assertTrue(r.rejected)
        assertEquals(RejectReason.TOO_SHALLOW, r.rejectReason)
        assertEquals(7, r.player.combo, "a cheated rep should sting, not end the run")
    }

    @Test
    fun `a collapsing hip line is not a rep however deep it looks`() {
        val r = resolver.resolve(
            knight(combo = 10), dummy(),
            RepInput(100f, RepGrade.DEEP, formScore = 0.4f), NoCritRng,
        )
        assertTrue(r.rejected)
        assertEquals(RejectReason.FORM_BROKEN, r.rejectReason)
    }

    @Test
    fun `weakness and resistance move damage the expected way`() {
        val weak = dummy().copy(weakness = ExerciseType.SQUAT)
        val resist = dummy().copy(resist = ExerciseType.SQUAT)
        val neutral = resolver.resolve(knight(), dummy(), rep(95f, 5000, ExerciseType.SQUAT), NoCritRng).damage
        val vsWeak = resolver.resolve(knight(), weak, rep(95f, 5000, ExerciseType.SQUAT), NoCritRng).damage
        val vsResist = resolver.resolve(knight(), resist, rep(95f, 5000, ExerciseType.SQUAT), NoCritRng).damage
        assertTrue(vsWeak > neutral && neutral > vsResist, "$vsWeak > $neutral > $vsResist")
    }

    @Test
    fun `damage is never zero even against heavy armour`() {
        val armoured = dummy(defense = 9999)
        val r = resolver.resolve(knight(), armoured, rep(95f, 5000), NoCritRng)
        assertEquals(1, r.damage, "a real rep must always do something")
    }

    @Test
    fun `every class clears the same encounter in a comparable number of reps`() {
        // Class identity should change how you train, not how long the dungeon takes.
        val repsByClass = PlayerClass.entries.map { cls ->
            val player = PlayerState.create(cls, level = 1)
            val enemy = Dungeons.FREE_DUNGEON.floors.last().spawn(Difficulty.STANDARD, ExerciseType.PUSHUP)
            var p = player
            var e = enemy
            var reps = 0
            while (!e.isDead && reps < 500) {
                val cycle = (cls.tempoBandMinMs + cls.tempoBandMaxMs) / 2
                val r = resolver.resolve(p, e, rep(92f, cycle), NoCritRng)
                p = r.player; e = r.enemy; reps++
            }
            cls to reps
        }
        val counts = repsByClass.map { it.second }
        assertTrue(counts.max() - counts.min() <= 3,
            "class time-to-kill diverged: $repsByClass")
    }

    @Test
    fun `an encounter costs roughly the reps it was authored to cost`() {
        // The whole point of deriving HP from a rep cost: the design intent has to survive.
        for (capacity in listOf(8f, 20f, 35f, 100f)) {
            val player = PlayerState.create(PlayerClass.KNIGHT, level = 1)
            val boss = Dungeons.FREE_DUNGEON.floors.last()
            val enemy = boss.spawn(Difficulty.STANDARD, ExerciseType.PUSHUP)
            val expected = CombatResolver.expectedReps(boss.standardRepCost, Difficulty.STANDARD)

            var p = player
            var e = enemy
            var reps = 0
            while (!e.isDead && reps < 1000) {
                val r = resolver.resolve(p, e, rep(88f, 3000), NoCritRng)
                p = r.player; e = r.enemy; reps++
            }
            assertTrue(abs(reps - expected) <= maxOf(3, expected / 4),
                "capacity $capacity: took $reps reps, authored for $expected")
        }
    }

    @Test
    fun `a tier costs the same reps for everybody`() {
        // The property that replaced capacity scaling, and the reason the volume model is readable:
        // a tier that says 100 says 100 to a beginner and to an athlete. What used to personalise it
        // was capacityScale, keyed on "largest consecutive set" — a measurement that cannot tell a
        // 100 kg bench from an empty bar, and that a warm-up set could ratchet upward.
        val boss = Dungeons.FREE_DUNGEON.floors.last()
        val reps = CombatResolver.expectedReps(boss.standardRepCost, Difficulty.STANDARD)

        PlayerClass.entries.forEach { cls ->
            listOf(1, 5, 20, 60).forEach { level ->
                val p = PlayerState.create(cls, level)
                val e = boss.spawn(Difficulty.STANDARD, ExerciseType.PUSHUP)
                var player = p
                var enemy = e
                var done = 0
                while (!enemy.isDead && done < 5000) {
                    val r = resolver.resolve(player, enemy, rep(92f), NoCritRng)
                    player = r.player; enemy = r.enemy; done++
                }
                assertEquals(reps, done, "$cls at level $level paid $done reps, not $reps")
            }
        }
    }

    @Test
    fun `difficulty is the one thing that moves a tier's cost`() {
        // It stays because the user picks it and can see it, unlike a level or a measured capacity.
        val costs = Difficulty.entries.map { CombatResolver.expectedReps(18, it) }
        assertEquals(costs.sortedBy { it }, costs, "difficulty tiers are out of order: $costs")
        assertTrue(costs.distinct().size == costs.size, "two difficulties cost the same: $costs")
    }

    @Test
    fun `a movement's tier cost is its own session, not a pushup's`() {
        // Authored in pushups, converted by session volume. A pull-up session is about 45 reps
        // where a pushup session is 150, so the same tier cannot ask both for the same number.
        val standard = CombatResolver.expectedReps(100, Difficulty.STANDARD, ExerciseType.PUSHUP)
        val squat = CombatResolver.expectedReps(100, Difficulty.STANDARD, ExerciseType.SQUAT)
        val pullUp = CombatResolver.expectedReps(100, Difficulty.STANDARD, ExerciseType.PULL_UP)

        assertEquals(100, standard)
        assertTrue(squat < standard, "a squat tier asked for $squat reps against a pushup's $standard")
        assertTrue(pullUp < standard / 3, "a pull-up tier asked for $pullUp reps against a pushup's $standard")
    }
}

class ProgressionTest {

    @Test
    fun `rank thresholds match the demo's champion card`() {
        // The demo shows 챔피언 with "마스터까지 431개", i.e. 11,569 lifetime reps.
        val p = RankProgress.of(11_569)
        assertEquals(Rank.CHAMPION, p.rank)
        assertEquals(Rank.MASTER, p.next)
        assertEquals(431, p.repsToNext)
        assertTrue(p.fraction > 0.9f)
    }

    @Test
    fun `rank never falls and tops out gracefully`() {
        assertEquals(Rank.SEEDLING, Rank.forLifetimeReps(0))
        assertEquals(Rank.IMMORTAL, Rank.forLifetimeReps(999_999))
        val top = RankProgress.of(999_999)
        assertEquals(null, top.next)
        assertEquals(1f, top.fraction)
    }

    @Test
    fun `the first dungeon clear levels the player up`() {
        // Onboarding has to produce a level-up; the first session is where retention is won.
        val xpFromReps = 14 * 3
        val clearBonus = (0.43f * xpFromReps).toInt()
        val result = Levels.apply(level = 1, xpIntoLevel = 0, gained = xpFromReps + clearBonus)
        assertTrue(result.leveledUp, "gained ${xpFromReps + clearBonus} XP, needed ${Levels.xpToNext(1)}")
    }

    @Test
    fun `levelling stops at the cap without losing xp accounting`() {
        val r = Levels.apply(level = Levels.MAX_LEVEL, xpIntoLevel = 0, gained = 1_000_000)
        assertEquals(Levels.MAX_LEVEL, r.level)
        assertEquals(0, r.xpIntoLevel)
    }

    @Test
    fun `capacity rises on a good set and never on a bad one`() {
        assertEquals(20f, Capacity.update(20f, 8), 0.01f, "one bad set must not lower capacity")
        assertTrue(Capacity.update(20f, 40) > 20f)
        assertEquals(Capacity.FLOOR, Capacity.update(1f, 1))
    }

    @Test
    fun `capacity decays slowly and never below the floor`() {
        assertEquals(30f, Capacity.decay(30f, 3), 0.01f, "a few days off changes nothing")
        assertTrue(Capacity.decay(30f, 28) < 30f)
        assertEquals(Capacity.FLOOR, Capacity.decay(5f, 3650))
    }

    @Test
    fun `a streak survives on a token effort and a break only halves it`() {
        assertTrue(Streak.maintained(reps = 10))
        assertTrue(Streak.maintained(reps = 0, plankSeconds = 60))
        assertTrue(Streak.maintained(reps = 0, squats = 15))
        assertTrue(!Streak.maintained(reps = 3))
        assertEquals(50, Streak.afterBreak(100), "a missed week must not erase a year")
        assertTrue(Streak.hpBonus(1000) <= 0.25f)
    }
}

/** Properties that emerged from reviewing the balance design rather than from the original spec. */
class BalanceRegressionTest {

    private val resolver = CombatResolver()

    private fun repsToKill(player: PlayerState, enemy: Enemy, depth: Float = 92f): Int {
        var p = player
        var e = enemy
        var reps = 0
        while (!e.isDead && reps < 5000) {
            val r = resolver.resolve(
                p, e,
                RepInput(depth, com.pushuprpg.core.detect.RepGrade.DEEP, cycleMs = 3000),
                NoCritRng,
            )
            p = r.player; e = r.enemy; reps++
        }
        return reps
    }

    @Test
    fun `a level does not change what a tier costs, and that is the point`() {
        // This deliberately reverses an earlier rule. Enemy HP used to be derived from the attack of
        // a player at the dungeon's recommended level, so twenty levels genuinely made old content
        // cheaper. Under the volume model HP is a rep count, so it cannot: 100 reps is 100 reps at
        // level 1 and at level 20. What levelling buys is everything else — the number on screen, the
        // crit rate, the class fantasy — and not a shorter session, because a shorter session for the
        // same tier would mean the count on the entry screen was a lie.
        val boss = Dungeons.FREE_DUNGEON.floors.last()
        val enemy = boss.spawn(Difficulty.STANDARD, ExerciseType.PUSHUP)

        val fresh = repsToKill(PlayerState.create(PlayerClass.KNIGHT, level = 1), enemy)
        val veteran = repsToKill(PlayerState.create(PlayerClass.KNIGHT, level = 20), enemy)

        assertEquals(fresh, veteran, "a level-20 player paid $veteran reps against a beginner's $fresh")
    }

    @Test
    fun `every tier costs exactly what it was authored to cost`() {
        // Not "roughly" any more. The old derivation could only be checked to within a third because
        // it multiplied a rep count by a predicted damage per rep; now the rep count IS the HP, so
        // the assertion is exact — apart from a ward, which is the weakness mechanic doing its job.
        for (dungeon in Dungeons.ALL) {
            val player = PlayerState.create(PlayerClass.KNIGHT, level = 1)
            for (floor in dungeon.floors) {
                val enemy = floor.spawn(Difficulty.STANDARD, ExerciseType.PUSHUP)
                // HP and ward are both counts of reps now, so the total is simply their sum.
                val expected = enemy.maxHp + enemy.wardMaxHp
                assertEquals(
                    CombatResolver.expectedReps(floor.standardRepCost, Difficulty.STANDARD),
                    enemy.maxHp,
                    "${dungeon.korean} ${floor.korean}: HP is not the authored rep cost",
                )
                val actual = repsToKill(player, enemy, depth = 88f)
                assertEquals(
                    expected, actual,
                    "${dungeon.korean} ${floor.korean}: took $actual reps, costs $expected",
                )
            }
        }
    }

    @Test
    fun `a long encounter does not become a stream of unanswerable ultimates`() {
        // Rage used to accrue at a flat rate per rep, so a several-hundred-rep fight produced an
        // ultimate every few seconds, each needing its own physical answer.
        val player = PlayerState.create(PlayerClass.KNIGHT, level = 18)
        val dungeon = Dungeons.ALL.last()
        val boss = dungeon.floors.last()
        val enemy = boss.spawn(Difficulty.HELL, ExerciseType.PUSHUP)
        val encounter = Encounter(player, enemy, Difficulty.HELL, rng = NoCritRng, startedAtMs = 0L)

        var t = 0L
        var ultimates = 0
        var reps = 0
        while (!encounter.finished && reps < 2000) {
            t += 2_500
            reps++
            ultimates += encounter.onRep(
                RepInput(95f, com.pushuprpg.core.detect.RepGrade.DEEP, cycleMs = 2500), t
            ).count { it is CombatEvent.Ultimate }
            ultimates += encounter.advanceTo(t).count { it is CombatEvent.Ultimate }
        }

        assertTrue(ultimates <= 12, "a single fight produced $ultimates ultimates over $reps reps")
    }

    @Test
    fun `a ward is worn down rather than being a permanent damage tax`() {
        val player = PlayerState.create(PlayerClass.KNIGHT, level = 12)
        val template = Dungeons.byIndex(6)!!.floors.last()
        val weakness = template.weakness
        assertTrue(weakness != null, "this test needs an enemy with a weakness")

        fun repsThrough(exercise: ExerciseType): Int {
            val enemy = template.spawn(Difficulty.STANDARD, exercise)
            assertTrue(enemy.warded, "this enemy is supposed to have a ward")
            var p = player
            var e = enemy
            var reps = 0
            while (e.warded && reps < 2000) {
                val r = resolver.resolve(
                    p, e,
                    RepInput(92f, com.pushuprpg.core.detect.RepGrade.DEEP, exercise = exercise),
                    NoCritRng,
                )
                p = r.player; e = r.enemy; reps++
            }
            assertTrue(!e.warded, "the ward never broke after $reps $exercise reps")
            return reps
        }

        // The intended answer is markedly cheaper, and the wrong movement is slower but never
        // blocked — which is the difference between a weakness and a wall.
        val answer = repsThrough(weakness!!)
        val bruteForce = repsThrough(ExerciseType.PUSHUP.takeIf { it != weakness } ?: ExerciseType.SQUAT)
        assertTrue(answer < bruteForce, "the answer ($answer) should beat brute force ($bruteForce)")
    }

    @Test
    fun `the calibration clamps are wide enough for an unusual camera angle`() {
        // The ratio shifts with how steeply the phone is tilted. A clamp tight enough to exclude a
        // real living-room setup would make the accept line permanently unreachable while every
        // quality indicator reported OK — silent, and unrecoverable by the user.
        val config = com.pushuprpg.core.detect.DetectorConfig.pushup()
        assertTrue(config.topClampMax / config.topClampMin >= 3f,
            "top clamp spans only ${config.topClampMin}..${config.topClampMax}")
        assertTrue(config.botClampMin <= 0.15f)
    }
}
