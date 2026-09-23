package com.pushuprpg.core

import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.RepGrade
import com.pushuprpg.core.game.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EncounterTest {

    private fun boss(player: PlayerState) =
        Dungeons.FREE_DUNGEON.floors.last().spawn(Difficulty.STANDARD, ExerciseType.PUSHUP)

    private fun newEncounter(
        playerClass: PlayerClass = PlayerClass.KNIGHT,
        level: Int = 1,
    ): Pair<Encounter, PlayerState> {
        val p = PlayerState.create(playerClass, level)
        return Encounter(p, boss(p), rng = NoCritRng, startedAtMs = 0L) to p
    }

    private fun deepRep(cycleMs: Int = 3000, holdMs: Int = 0) =
        RepInput(95f, RepGrade.DEEP, ExerciseType.PUSHUP, cycleMs, bottomHoldMs = holdMs)

    @Test
    fun `resting costs nothing, however long it goes on`() {
        // The single most important property of the volume model, and the reverse of what this file
        // used to assert. Measured before the idle timer was deleted: 60 seconds cost half a
        // knight's HP and 90 seconds killed them — where 90 seconds is the *recommended* rest
        // between pushup sets and the short end for pull-ups, and a bench press wants 120-300.
        val (e, p) = newEncounter()
        e.onRep(deepRep(), 3_600_000L)

        listOf(30_000L, 90_000L, 300_000L, 1_800_000L).forEach { restMs ->
            val events = e.advanceTo(3_600_000L + restMs)
            assertTrue(events.isEmpty(), "resting ${restMs / 1000}s produced $events")
            assertEquals(p.maxHp, e.player.hp, "resting ${restMs / 1000}s cost health")
            assertTrue(!e.finished, "resting ${restMs / 1000}s ended the run")
        }
    }


    @Test
    fun `doing nothing at all simply does nothing`() {
        // A motionless player is not playing. They are not losing either — there is nothing to lose.
        val (e, p) = newEncounter()
        var t = 3_600_000L
        while (t < 3_600_000L + 600_000L) {
            t += 500
            assertTrue(e.advanceTo(t).isEmpty(), "idling produced an event at ${t - 3_600_000L}ms")
        }
        assertEquals(p.maxHp, e.player.hp, "ten motionless minutes cost health")
        assertEquals(0, e.repsCounted, "ten motionless minutes counted reps")
        assertTrue(!e.finished)
    }





    @Test
    fun `the ultimate never kills from full health`() {
        for (cls in PlayerClass.entries) {
            for (level in listOf(1, 10, 20)) {
                val p = PlayerState.create(cls, level)
                for (dungeon in Dungeons.ALL) {
                    val bossTemplate = dungeon.floors.last()
                    val enc = Encounter(p, bossTemplate.spawn(Difficulty.HELL, ExerciseType.PUSHUP), Difficulty.HELL, startedAtMs = 0L)
                    assertTrue(enc.ultimateDamage < p.maxHp,
                        "${cls} L$level vs ${dungeon.korean}: ult ${enc.ultimateDamage} >= hp ${p.maxHp}")
                }
            }
        }
    }

    @Test
    fun `stopping part way through banks every rep`() {
        // There is no losing any more — a run ends when the user stops, and how far they got is the
        // result. What still has to hold is the promise the clear screen makes: the reps are theirs.
        val (e, _) = newEncounter()
        repeat(3) { i -> e.onRep(deepRep(), 3_600_000L + i * 1_000L) }

        assertEquals(3, e.repsCounted)
        assertTrue(e.runXp > 0, "XP is earned per rep, so stopping cannot zero it")
        assertTrue(e.crackFraction() > 0f, "reps dealt should shorten the retry")
        assertTrue(e.crackFraction() <= Encounter.MAX_CRACK)
    }

    @Test
    fun `a fought encounter ends in victory and pays a clear bonus`() {
        val (e, _) = newEncounter()
        var t = 0L
        var defeated = false
        while (!e.finished && t < 400_000) {
            // Keep up a steady pace inside the grace window so the boss never gets a turn.
            t += 2_500
            defeated = e.onRep(deepRep(cycleMs = 2_500), t).any { it is CombatEvent.EnemyDefeated }
        }
        assertTrue(defeated, "the boss should die to sustained reps")
        assertTrue(e.repsCounted in 5..25, "took ${e.repsCounted} reps; authored for about 7")
        assertTrue(e.clearBonusXp() > 0)
        // Most XP must already be banked per rep, so defeat can never cost much.
        assertTrue(e.clearBonusXp() < e.runXp, "clear bonus ${e.clearBonusXp()} vs run XP ${e.runXp}")
    }

    // ------------------------------------------------------------ the rep-counted ultimate

    /** A 20-rep monster: big enough for a wind-up and a full answer window. */
    private fun bigFight(playerClass: PlayerClass = PlayerClass.KNIGHT, hp: Int? = null): Encounter {
        val p = PlayerState.create(playerClass, level = 1).let { if (hp != null) it.copy(hp = hp) else it }
        val enemy = Enemy(id = "test", korean = "시험용", maxHp = 20, hp = 20, ultimateFraction = 0.40f)
        return Encounter(p, enemy, rng = NoCritRng, startedAtMs = 0L)
    }

    /** Counted but not deep, not fast, not held: never an answer, for any class. */
    private fun plainRep() = RepInput(72f, RepGrade.COUNTED, ExerciseType.PUSHUP, cycleMs = 3000)

    /** Reps until the wind-up starts; returns the time of the last one. */
    private fun untilTelegraph(e: Encounter): Long {
        var t = 0L
        while (!e.ultimateWindingUp) {
            t += 3000
            e.onRep(plainRep(), t)
            assertTrue(t < 60_000, "no wind-up in 20 reps")
        }
        return t
    }

    @Test
    fun `the wind-up starts with the monster worn down to sixty percent`() {
        val e = bigFight()
        untilTelegraph(e)
        assertTrue(e.enemy.hp <= 12 && e.enemy.hp >= 11, "wound up at ${e.enemy.hp}/20")
        assertEquals(Encounter.ANSWER_WINDOW_REPS, e.answerRepsLeft)
    }

    @Test
    fun `five reps with no answers and it lands`() {
        val e = bigFight()
        var t = untilTelegraph(e)
        val full = e.player.hp
        val hits = mutableListOf<CombatEvent.Ultimate>()
        repeat(Encounter.ANSWER_WINDOW_REPS) { t += 3000; hits += e.onRep(plainRep(), t).filterIsInstance<CombatEvent.Ultimate>() }
        assertEquals(1, hits.size)
        assertEquals(e.ultimateDamage, hits.single().damage)
        assertEquals(full - e.ultimateDamage, e.player.hp)
        assertTrue(!e.ultimateWindingUp)
    }

    @Test
    fun `three deep reps in the window block it completely`() {
        val e = bigFight()
        var t = untilTelegraph(e)
        val full = e.player.hp
        val events = mutableListOf<CombatEvent>()
        repeat(Encounter.ANSWERS_TO_BLOCK) { t += 3000; events += e.onRep(deepRep(), t) }
        val ultimate = events.filterIsInstance<CombatEvent.Ultimate>().single()
        assertEquals(0, ultimate.damage)
        assertEquals(Mitigation.FULL, ultimate.mitigation)
        assertEquals(full, e.player.hp)
    }

    @Test
    fun `every answer made takes a share off the hit`() {
        val e = bigFight()
        var t = untilTelegraph(e)
        t += 3000; e.onRep(deepRep(), t)
        var hit: CombatEvent.Ultimate? = null
        repeat(Encounter.ANSWER_WINDOW_REPS - 1) {
            t += 3000
            hit = hit ?: e.onRep(plainRep(), t).filterIsInstance<CombatEvent.Ultimate>().firstOrNull()
        }
        assertEquals(Mitigation.PARTIAL, hit!!.mitigation)
        assertTrue(hit!!.damage < e.ultimateDamage, "one answer took nothing off")
    }

    @Test
    fun `resting while it winds up costs nothing — it is counted in reps, not seconds`() {
        val e = bigFight()
        val t = untilTelegraph(e)
        val hp = e.player.hp
        // Ten minutes, out of view half of it.
        e.setTracking(false, t + 300_000)
        assertTrue(e.advanceTo(t + 600_000).isEmpty())
        assertEquals(hp, e.player.hp)
        assertTrue(e.ultimateWindingUp, "the wind-up expired by itself")
        assertEquals(Encounter.ANSWER_WINDOW_REPS, e.answerRepsLeft)
    }

    @Test
    fun `finishing the monster inside the window means it never lands`() {
        val p = PlayerState.create(PlayerClass.KNIGHT, level = 1)
        val e = Encounter(p, Enemy(id = "t", korean = "t", maxHp = 8, hp = 8), rng = NoCritRng, startedAtMs = 0L)
        var t = 0L
        val events = mutableListOf<CombatEvent>()
        while (!e.finished) { t += 3000; events += e.onRep(plainRep(), t) }
        assertTrue(events.any { it is CombatEvent.Telegraph }, "an 8-rep monster should still wind up")
        assertTrue(events.none { it is CombatEvent.Ultimate }, "the ultimate landed after the monster died")
        assertEquals(p.hp, e.player.hp)
    }

    @Test
    fun `each class answers in its own style as well as with depth`() {
        fun blockedWith(playerClass: PlayerClass, rep: RepInput): Boolean {
            val e = bigFight(playerClass)
            var t = untilTelegraph(e)
            val events = mutableListOf<CombatEvent>()
            repeat(Encounter.ANSWERS_TO_BLOCK) { t += 1500; events += e.onRep(rep, t) }
            return events.filterIsInstance<CombatEvent.Ultimate>().any { it.mitigation == Mitigation.FULL }
        }
        val fast = RepInput(72f, RepGrade.COUNTED, ExerciseType.PUSHUP, cycleMs = 1500)
        val held = RepInput(72f, RepGrade.COUNTED, ExerciseType.PUSHUP, cycleMs = 3000, bottomHoldMs = 1200)
        assertTrue(blockedWith(PlayerClass.ARCHER, fast), "an archer's pace did not answer")
        assertTrue(blockedWith(PlayerClass.MAGE, held), "a mage's hold did not answer")
        assertTrue(!blockedWith(PlayerClass.KNIGHT, fast), "pace answered for a knight")
    }

    @Test
    fun `a hit that empties the player's health ends the run, not the reps`() {
        val e = bigFight(hp = 5)
        var t = untilTelegraph(e)
        val reps = e.repsCounted
        val events = mutableListOf<CombatEvent>()
        repeat(Encounter.ANSWER_WINDOW_REPS) { t += 3000; events += e.onRep(plainRep(), t) }
        assertTrue(events.any { it is CombatEvent.Exhausted }, "health reached ${e.player.hp} without ending the run")
        assertTrue(e.finished)
        assertEquals(reps + Encounter.ANSWER_WINDOW_REPS, e.repsCounted, "reps done in the window were lost")
    }

    @Test
    fun `a monster too small for a window never winds up`() {
        val p = PlayerState.create(PlayerClass.KNIGHT, level = 1)
        val e = Encounter(p, Enemy(id = "t", korean = "t", maxHp = 7, hp = 7), rng = NoCritRng, startedAtMs = 0L)
        var t = 0L
        val events = mutableListOf<CombatEvent>()
        while (!e.finished) { t += 3000; events += e.onRep(plainRep(), t) }
        assertTrue(events.none { it is CombatEvent.Telegraph })
    }
}
