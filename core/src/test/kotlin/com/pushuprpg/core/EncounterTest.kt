package com.pushuprpg.core

import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.RepGrade
import com.pushuprpg.core.game.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EncounterTest {

    private fun boss(player: PlayerState) =
        Dungeons.FREE_DUNGEON.floors.last().spawn(player, Difficulty.STANDARD, capacity = 8f)

    private fun newEncounter(
        playerClass: PlayerClass = PlayerClass.KNIGHT,
        level: Int = 1,
    ): Pair<Encounter, PlayerState> {
        val p = PlayerState.create(playerClass, level)
        return Encounter(p, boss(p), rng = NoCritRng) to p
    }

    private fun deepRep(cycleMs: Int = 3000, holdMs: Int = 0) =
        RepInput(95f, RepGrade.DEEP, ExerciseType.PUSHUP, cycleMs, bottomHoldMs = holdMs)

    @Test
    fun `breathing between reps is never punished`() {
        val (e, _) = newEncounter()
        e.onRep(deepRep(), 0L)
        // The grace window is the whole point: a pause to breathe must cost nothing at all.
        val events = e.advanceTo(e.graceMs - 100L)
        assertTrue(events.none { it is CombatEvent.BossTick }, "took a hit inside the grace window")
        assertEquals(e.player.maxHp, e.player.hp)
    }

    @Test
    fun `resting past the grace window starts costing health and escalates`() {
        val (e, _) = newEncounter()
        e.onRep(deepRep(), 0L)
        val ticks = mutableListOf<CombatEvent.BossTick>()
        var t = 0L
        while (t < 40_000 && !e.finished) {
            t += 500
            ticks += e.advanceTo(t).filterIsInstance<CombatEvent.BossTick>()
        }
        assertTrue(ticks.isNotEmpty(), "resting forever should be punished")
        assertTrue(ticks.last().damage > ticks.first().damage, "pressure should escalate: $ticks")
    }

    @Test
    fun `doing nothing at all eventually exhausts the player, but not quickly`() {
        val (e, _) = newEncounter()
        e.onRep(deepRep(), 0L)
        var t = 0L
        var exhaustedAt: Long? = null
        while (t < 120_000 && exhaustedAt == null) {
            t += 500
            if (e.advanceTo(t).any { it is CombatEvent.Exhausted }) exhaustedAt = t
        }
        assertTrue(exhaustedAt != null, "a player who never moves should eventually lose")
        assertTrue(exhaustedAt!! > 30_000, "died after only ${exhaustedAt}ms of rest — too brutal")
    }

    @Test
    fun `holding a plank keeps the boss off a player whose arms are done`() {
        val (e, _) = newEncounter()
        e.onRep(deepRep(), 0L)
        e.setHolding(true, 100L)
        var t = 100L
        val ticks = mutableListOf<CombatEvent>()
        while (t < 25_000) {
            t += 500
            ticks += e.advanceTo(t).filter { it is CombatEvent.BossTick }
        }
        assertTrue(ticks.isEmpty(), "a held plank should freeze the rest timer, got $ticks")
        assertTrue(e.defenseGaugeMs < Encounter.DEFENSE_GAUGE_MS, "the hold should cost gauge")
    }

    @Test
    fun `the ultimate is telegraphed well before it lands`() {
        val (e, _) = newEncounter()
        var t = 0L
        var telegraph: CombatEvent.Telegraph? = null
        var ultimate: CombatEvent.Ultimate? = null
        // Rest so rage climbs from idle ticks.
        while (t < 60_000 && ultimate == null) {
            t += 500
            e.advanceTo(t).forEach {
                if (it is CombatEvent.Telegraph && telegraph == null) telegraph = it
                if (it is CombatEvent.Ultimate) ultimate = it
            }
            if (e.finished) break
        }
        assertTrue(telegraph != null, "the ultimate must be announced")
        if (ultimate != null) {
            assertTrue(ultimate!!.atMs - telegraph!!.atMs >= 9_000,
                "only ${ultimate!!.atMs - telegraph!!.atMs}ms to react")
        }
    }

    @Test
    fun `a knight who answers the telegraph with deep reps takes nothing`() {
        val (e, _) = newEncounter(PlayerClass.KNIGHT)
        var t = 0L
        var telegraphAt: Long? = null
        while (t < 60_000 && telegraphAt == null && !e.finished) {
            t += 500
            e.advanceTo(t).forEach { if (it is CombatEvent.Telegraph) telegraphAt = it.atMs }
        }
        assertTrue(telegraphAt != null)

        val hpBefore = e.player.hp
        e.onRep(deepRep(), telegraphAt!! + 1_000)
        e.onRep(deepRep(), telegraphAt!! + 3_000)
        val result = e.advanceTo(telegraphAt!! + Encounter.TELEGRAPH_WINDOW_MS + 100)
        val ult = result.filterIsInstance<CombatEvent.Ultimate>().firstOrNull()

        assertTrue(ult != null, "the ultimate should resolve when the window closes")
        assertEquals(Mitigation.FULL, ult!!.mitigation)
        assertEquals(0, ult.damage)
        assertTrue(e.player.hp >= hpBefore, "answering it correctly must not cost health")
    }

    @Test
    fun `a plank is the answer available to anyone, at a cost`() {
        val (e, _) = newEncounter(PlayerClass.ARCHER)
        var t = 0L
        var telegraphAt: Long? = null
        while (t < 60_000 && telegraphAt == null && !e.finished) {
            t += 500
            e.advanceTo(t).forEach { if (it is CombatEvent.Telegraph) telegraphAt = it.atMs }
        }
        assertTrue(telegraphAt != null)

        e.setHolding(true, telegraphAt!!)
        val result = e.advanceTo(telegraphAt!! + Encounter.TELEGRAPH_WINDOW_MS + 100)
        val ult = result.filterIsInstance<CombatEvent.Ultimate>().firstOrNull()
        assertTrue(ult != null)
        assertEquals(Mitigation.PARTIAL, ult!!.mitigation)
        assertTrue(ult.damage in 1 until e.ultimateDamage, "partial should soften, not negate")
    }

    @Test
    fun `the ultimate never kills from full health`() {
        for (cls in PlayerClass.entries) {
            for (level in listOf(1, 10, 20)) {
                val p = PlayerState.create(cls, level)
                for (dungeon in Dungeons.ALL) {
                    val bossTemplate = dungeon.floors.last()
                    val enc = Encounter(p, bossTemplate.spawn(p, Difficulty.HELL, 8f), Difficulty.HELL)
                    assertTrue(enc.ultimateDamage < p.maxHp,
                        "${cls} L$level vs ${dungeon.korean}: ult ${enc.ultimateDamage} >= hp ${p.maxHp}")
                }
            }
        }
    }

    @Test
    fun `losing still banks every rep and shortens the retry`() {
        val (e, _) = newEncounter()
        repeat(3) { i -> e.onRep(deepRep(), i * 1_000L) }
        val repsBanked = e.repsCounted
        val xpBanked = e.runXp

        var t = 3_000L
        while (!e.finished && t < 200_000) {
            t += 500
            e.advanceTo(t)
        }
        assertTrue(e.finished)
        // The clear screen promises reps survive a loss. That has to be true in the model, not
        // just in the copy.
        assertEquals(repsBanked, e.repsCounted)
        assertEquals(xpBanked, e.runXp)
        assertTrue(e.runXp > 0, "XP is earned per rep, so a loss cannot zero it")
        assertTrue(e.crackFraction() > 0f, "damage dealt should shorten the retry")
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
