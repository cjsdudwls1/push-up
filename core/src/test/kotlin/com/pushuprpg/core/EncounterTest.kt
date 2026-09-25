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

    /**
     * One rep as the engine reports it: the strike, the deep line if it got there ([loweringMs],
     * top band to deep line), and the end. A 기사's rep is not decided until then.
     */
    private fun Encounter.fullRep(input: RepInput, t: Long, loweringMs: Int? = null): List<CombatEvent> {
        val events = onRep(input, t).toMutableList()
        if (loweringMs != null) events += onDeep(loweringMs, t + 400)
        events += onRepEnd(t + 900)
        return events
    }

    /** A deep rep lowered slowly: whole, for either class, and an answer for either. */
    private fun Encounter.slowDeep(t: Long, cycleMs: Int = 3000) = fullRep(deepRep(cycleMs), t, loweringMs = 800)

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
            defeated = e.slowDeep(t, cycleMs = 2_500).any { it is CombatEvent.EnemyDefeated }
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
            e.fullRep(plainRep(), t)
            // A plain rep is half for a 기사: the 20-rep monster takes forty of them.
            assertTrue(t < 120_000, "no wind-up in 40 reps")
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
        repeat(Encounter.ANSWER_WINDOW_REPS) { t += 3000; hits += e.fullRep(plainRep(), t).filterIsInstance<CombatEvent.Ultimate>() }
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
        repeat(Encounter.ANSWERS_TO_BLOCK) { t += 3000; events += e.slowDeep(t) }
        val ultimate = events.filterIsInstance<CombatEvent.Ultimate>().single()
        assertEquals(0, ultimate.damage)
        assertEquals(Mitigation.FULL, ultimate.mitigation)
        assertEquals(full, e.player.hp)
    }

    @Test
    fun `every answer made takes a share off the hit`() {
        val e = bigFight()
        var t = untilTelegraph(e)
        t += 3000; e.slowDeep(t)
        var hit: CombatEvent.Ultimate? = null
        repeat(Encounter.ANSWER_WINDOW_REPS - 1) {
            t += 3000
            hit = hit ?: e.fullRep(plainRep(), t).filterIsInstance<CombatEvent.Ultimate>().firstOrNull()
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
        // Whole reps that answer nothing: a 궁수 starting a fresh set every time.
        val p = PlayerState.create(PlayerClass.ARCHER, level = 1)
        val e = Encounter(p, Enemy(id = "t", korean = "t", maxHp = 8, hp = 8), rng = NoCritRng, startedAtMs = 0L)
        var t = 0L
        val events = mutableListOf<CombatEvent>()
        val rested = RepInput(72f, RepGrade.COUNTED, ExerciseType.PUSHUP, cycleMs = 6000)
        while (!e.finished) { t += 6000; events += e.fullRep(rested, t) }
        assertTrue(events.any { it is CombatEvent.Telegraph }, "an 8-rep monster should still wind up")
        assertTrue(events.none { it is CombatEvent.Ultimate }, "the ultimate landed after the monster died")
        assertEquals(p.hp, e.player.hp)
    }

    @Test
    fun `each class answers in its own style as well as with depth`() {
        fun blockedWith(playerClass: PlayerClass, rep: RepInput, loweringMs: Int? = null): Boolean {
            val e = bigFight(playerClass)
            var t = untilTelegraph(e)
            val events = mutableListOf<CombatEvent>()
            repeat(Encounter.ANSWERS_TO_BLOCK) { t += 1500; events += e.fullRep(rep, t, loweringMs) }
            return events.filterIsInstance<CombatEvent.Ultimate>().any { it.mitigation == Mitigation.FULL }
        }
        val fast = RepInput(72f, RepGrade.COUNTED, ExerciseType.PUSHUP, cycleMs = 1200)
        val slow = RepInput(72f, RepGrade.COUNTED, ExerciseType.PUSHUP, cycleMs = 4000)
        assertTrue(blockedWith(PlayerClass.ARCHER, fast), "a 궁수's pace did not answer")
        assertTrue(blockedWith(PlayerClass.KNIGHT, slow, loweringMs = 800), "a 기사's slow, full rep did not answer")
        assertTrue(!blockedWith(PlayerClass.KNIGHT, fast), "pace answered for a 기사")
        assertTrue(!blockedWith(PlayerClass.KNIGHT, fast, loweringMs = 300), "a 기사's quick dive answered")
        // Depth answers for everyone — read at the deep line, which the strike comes before.
        assertTrue(blockedWith(PlayerClass.ARCHER, slow, loweringMs = 300), "depth did not answer for a 궁수")
    }

    @Test
    fun `a hit that empties the player's health ends the run, not the reps`() {
        val e = bigFight(hp = 5)
        var t = untilTelegraph(e)
        val reps = e.repsCounted
        val events = mutableListOf<CombatEvent>()
        repeat(Encounter.ANSWER_WINDOW_REPS) { t += 3000; events += e.fullRep(plainRep(), t) }
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
        while (!e.finished) { t += 3000; events += e.fullRep(plainRep(), t) }
        assertTrue(events.none { it is CombatEvent.Telegraph })
    }

    // ------------------------------------------------------------ each class's own way

    /** A monster with a known count, and the half-reps it has taken so far. */
    private fun owed(e: Encounter): Float = e.enemy.hp - if (e.enemy.halfTaken) 0.5f else 0f

    @Test
    fun `a 기사's rep is whole only when it is slow and reaches 깊게`() {
        val e = bigFight(PlayerClass.KNIGHT)
        val rep = RepInput(72f, RepGrade.COUNTED, ExerciseType.PUSHUP, cycleMs = 4000)
        var t = 0L

        t += 4000; var ev = e.fullRep(rep, t, loweringMs = 800)
        assertEquals(19f, owed(e), "slow and full was not a whole rep")
        assertEquals(null, ev.filterIsInstance<CombatEvent.Style>().single().miss)

        t += 4000; ev = e.fullRep(rep, t, loweringMs = 300)
        assertEquals(18.5f, owed(e), "a quick dive was not half")
        assertEquals(StyleMiss.TOO_QUICK, ev.filterIsInstance<CombatEvent.Style>().single().miss)

        t += 4000; ev = e.fullRep(rep, t, loweringMs = null)
        assertEquals(18f, owed(e), "a rep short of 깊게 was not half")
        assertEquals(StyleMiss.NOT_FULL, ev.filterIsInstance<CombatEvent.Style>().single().miss)
        assertEquals(1, e.styleReps)
        assertEquals(3, e.repsCounted, "a half-worth rep did not count as a rep")
    }

    @Test
    fun `a 궁수's rep is whole when it keeps pace, and the first of a set always is`() {
        val e = bigFight(PlayerClass.ARCHER)
        fun at(cycleMs: Int) = RepInput(72f, RepGrade.COUNTED, ExerciseType.PUSHUP, cycleMs = cycleMs)
        var t = 0L

        t += 1000; e.fullRep(at(9000), t)
        assertEquals(19f, owed(e), "the first rep of a set was not whole")
        t += 1200; e.fullRep(at(1200), t)
        assertEquals(18f, owed(e), "a brisk rep was not whole")
        t += 3000; val ev = e.fullRep(at(3000), t)
        assertEquals(17.5f, owed(e), "a rep lagging inside the set was not half")
        assertEquals(StyleMiss.LAGGING, ev.filterIsInstance<CombatEvent.Style>().single().miss)
        // Resting long enough to end the set: the next rep starts a new one, and is whole.
        t += 20_000; e.fullRep(at(20_000), t)
        assertEquals(16.5f, owed(e), "resting between sets cost the next rep")
        assertEquals(3, e.styleReps)
    }

    @Test
    fun `a rep the tracker lost is not blamed on the user`() {
        val e = bigFight(PlayerClass.KNIGHT)
        e.onRep(RepInput(72f, RepGrade.COUNTED, ExerciseType.PUSHUP, cycleMs = 4000), 4000)
        val ended = e.onRepEnd(4900, seen = false)
        assertTrue(ended.none { it is CombatEvent.Style }, "told to go all the way down for a tracking gap: $ended")
    }

    /**
     * The last rep of the window struck, and the camera lost the user before it ended — before a
     * 기사 could reach the deep line and answer with it. Landing then hurt somebody the tracker could
     * not see, for a gap that was the tracker's.
     */
    @Test
    fun `a rep the tracker lost never lands the ultimate`() {
        val e = bigFight(PlayerClass.KNIGHT)
        var t = untilTelegraph(e)
        val full = e.player.hp
        repeat(Encounter.ANSWER_WINDOW_REPS - 1) { t += 3000; e.fullRep(plainRep(), t) }
        assertEquals(1, e.answerRepsLeft)

        t += 3000
        val lost = e.onRep(plainRep(), t) + e.onRepEnd(t + 900, seen = false)
        assertTrue(lost.none { it is CombatEvent.Ultimate }, "landed on a rep the tracker lost: $lost")
        assertEquals(full, e.player.hp)
        assertTrue(e.ultimateWindingUp, "the window closed on a rep nobody saw end")
        assertEquals(1, e.answerRepsLeft, "the lost rep used up a chance")

        // Back in view, the chance it gave back is theirs: a slow, full rep there answers.
        t += 3000
        e.fullRep(plainRep(), t, loweringMs = 800)
        assertEquals(1, e.answersLanded)
    }

    @Test
    fun `a lost rep that had already answered keeps its answer`() {
        val e = bigFight(PlayerClass.ARCHER)
        var t = untilTelegraph(e)
        // A 궁수 answers with pace at the strike, before anything can be lost.
        val brisk = RepInput(72f, RepGrade.COUNTED, ExerciseType.PUSHUP, cycleMs = 1200)
        t += 1200
        e.onRep(brisk, t)
        e.onRepEnd(t + 500, seen = false)
        assertEquals(1, e.answersLanded)
        assertEquals(Encounter.ANSWER_WINDOW_REPS - 1, e.answerRepsLeft, "an answer was handed back as a chance")
    }
}
