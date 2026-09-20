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
}
