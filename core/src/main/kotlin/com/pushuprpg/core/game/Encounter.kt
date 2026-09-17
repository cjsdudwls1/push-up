package com.pushuprpg.core.game

import com.pushuprpg.core.detect.ExerciseType
import kotlin.math.min
import kotlin.math.roundToInt

/** What happened in the encounter, for the UI and the audio layer to react to. */
sealed interface CombatEvent {
    val atMs: Long

    data class Hit(override val atMs: Long, val result: AttackResult) : CombatEvent
    data class RepRejected(override val atMs: Long, val reason: RejectReason) : CombatEvent

    /** The boss punished a rest. [index] escalates while the player stays idle. */
    data class BossTick(override val atMs: Long, val damage: Int, val index: Int) : CombatEvent

    /** "필살기 온다" — the wind-up, fired early enough to physically respond to. */
    data class Telegraph(override val atMs: Long, val deadlineMs: Long) : CombatEvent

    data class Ultimate(
        override val atMs: Long,
        val damage: Int,
        val mitigation: Mitigation,
    ) : CombatEvent

    data class EnemyDefeated(override val atMs: Long, val enemy: Enemy) : CombatEvent

    /** Not "defeat": the run ends, but nothing the player earned is taken away. */
    data class Exhausted(override val atMs: Long, val crackFraction: Float) : CombatEvent

    data class ComboBroken(override val atMs: Long, val finalCombo: Int) : CombatEvent
}

enum class Mitigation {
    /** Took it in full. */
    NONE,

    /** Class answer performed: no damage. */
    FULL,

    /** The universal plank fallback: 60% off. */
    PARTIAL,
}

/**
 * One fight.
 *
 * Drive it by calling [advanceTo] every frame with the current pose timestamp and [onRep] when the
 * detector counts one. It reads no clock of its own, so a whole encounter replays deterministically
 * from a recorded session.
 */
class Encounter(
    initialPlayer: PlayerState,
    initialEnemy: Enemy,
    private val difficulty: Difficulty = Difficulty.STANDARD,
    private val resolver: CombatResolver = CombatResolver(),
    private val rng: Rng = SeededRng(0),
    /**
     * The timestamp this fight's deadlines are measured from. No default: the only sensible value
     * is a real frame timestamp, and a `0L` default is exactly what let floor 0 be constructed
     * against an epoch the device's clock had passed millions of milliseconds earlier.
     */
    startedAtMs: Long,
) {
    var player: PlayerState = initialPlayer
        private set
    var enemy: Enemy = initialEnemy
        private set

    var rage: Int = 0
        private set
    var damageDealt: Int = 0
        private set
    var repsCounted: Int = 0
        private set
    var runXp: Int = 0
        private set
    var finished: Boolean = false
        private set

    /**
     * Milliseconds of "resting by exercising" left.
     *
     * A player whose arms have given out can drop into a plank and keep the boss off them. Without
     * this the only options are quit or get hit, which is how a fitness game teaches people to
     * stop opening it.
     */
    var defenseGaugeMs: Int = DEFENSE_GAUGE_MS
        private set

    private var lastRepAtMs: Long = startedAtMs
    private var lastTickAtMs: Long = startedAtMs
    private var lastSeenMs: Long = startedAtMs
    private var tickIndex: Int = 0
    private var telegraphed = false
    private var telegraphAtMs = 0L
    private var repsSinceTelegraph = 0
    private var deepRepsSinceTelegraph = 0
    private var fastRepsSinceTelegraph = 0
    private var mageAnswerReady = false
    private var staggeredUntilMs = 0L
    private var holdingSinceMs: Long? = null
    private var plankHeldMs = 0
    private var repsSinceRage = 0

    /** Roughly how many reps this fight should take, used to pace the boss's rage. */
    private val expectedReps: Int =
        (initialEnemy.maxHp / (initialPlayer.attack * initialPlayer.playerClass.expectedDprCoefficient)
            .coerceAtLeast(1f)).toInt().coerceIn(1, 2000)

    val graceMs: Int get() = min(14_000, 8_000 + 200 * player.level)

    /**
     * How many reps buy the boss one point of rage.
     *
     * Derived from how long this fight is expected to last rather than being a flat count. At a
     * fixed rate a long encounter — an athlete on a late dungeon, several hundred reps — would
     * generate a continuous stream of ultimates, each needing a physical answer within ten seconds,
     * which is not a fight anyone can win. Scaling it means every encounter gets roughly the same
     * handful of them regardless of length.
     */
    private val repsPerRage: Int =
        (expectedReps / RAGE_EVENTS_PER_ENCOUNTER).coerceAtLeast(MIN_REPS_PER_RAGE)

    /** The boss's ultimate lands this hard; never enough to one-shot from full. */
    val ultimateDamage: Int
        get() = (player.maxHp * enemy.ultimateFraction * difficulty.enemyAtkMultiplier).roundToInt()

    /**
     * Anchors every deadline this fight owns to the first real frame of the run.
     *
     * Floor 0's encounter has to exist before any frame does — [BattleEngine] builds it in a
     * property initialiser — so its epoch cannot be known at construction. Without this call the
     * idle clock starts at whatever was guessed, and since a device's frame timestamps are
     * milliseconds since boot, the boss opens the fight owing tens of thousands of punish ticks
     * and kills the player on frame one.
     */
    fun startAt(atMs: Long) {
        lastRepAtMs = atMs
        lastTickAtMs = atMs
        lastSeenMs = atMs
    }

    /**
     * Tell the encounter whether the tracker can currently see the player.
     *
     * Gating the damage call on [PoseQuality] stops a dropout hurting *during* it, but the idle
     * clock kept running underneath, so the whole gap was paid out in one burst the instant
     * tracking recovered. That is still punishing a tracking failure, just one frame late. Moving
     * every deadline forward by the gap means the dropout costs nothing then and nothing after.
     *
     * Call once per frame, tracked or not.
     */
    fun setTracking(tracking: Boolean, atMs: Long) {
        val gap = atMs - lastSeenMs
        lastSeenMs = atMs
        if (tracking || finished || gap <= 0) return
        lastRepAtMs += gap
        lastTickAtMs += gap
        telegraphAtMs += gap
        staggeredUntilMs += gap
        holdingSinceMs = holdingSinceMs?.plus(gap)
    }

    /**
     * Tell the encounter the player is holding a static position (a plank, or the top of a
     * pushup). While held, the rest timer is frozen and the gauge drains instead.
     */
    fun setHolding(holding: Boolean, atMs: Long) {
        if (holding && holdingSinceMs == null) {
            holdingSinceMs = atMs
        } else if (!holding && holdingSinceMs != null) {
            plankHeldMs = 0
            holdingSinceMs = null
        }
    }

    fun onRep(rep: RepInput, atMs: Long): List<CombatEvent> {
        if (finished) return emptyList()
        val events = mutableListOf<CombatEvent>()

        // A rest long enough to break the combo does so before the rep lands, so the rep starts
        // the new chain rather than extending a chain the player already lost.
        if (player.combo > 0 && atMs - lastRepAtMs > player.playerClass.comboWindowMs) {
            events += CombatEvent.ComboBroken(atMs, player.combo)
            player = player.copy(combo = 0, tempoStreak = 0)
        }

        val result = resolver.resolve(player, enemy, rep, rng)
        player = result.player
        lastRepAtMs = atMs
        tickIndex = 0

        if (result.rejected) {
            events += CombatEvent.RepRejected(atMs, result.rejectReason ?: RejectReason.TOO_SHALLOW)
            return events
        }

        enemy = result.enemy
        damageDealt += result.damage
        repsCounted++
        runXp += result.xp
        events += CombatEvent.Hit(atMs, result)

        // Pushing through without resting earns a little health back.
        if (repsCounted % REGEN_EVERY_REPS == 0) {
            val regen = (player.maxHp * 0.02f).roundToInt().coerceAtLeast(1)
            player = player.copy(hp = min(player.maxHp, player.hp + regen))
        }
        if (repsCounted % GAUGE_REFILL_EVERY_REPS == 0) {
            defenseGaugeMs = min(DEFENSE_GAUGE_MS, defenseGaugeMs + GAUGE_REFILL_MS)
        }

        repsSinceRage++
        if (repsSinceRage >= repsPerRage) {
            repsSinceRage = 0
            rage++
        }

        if (telegraphed) {
            repsSinceTelegraph++
            if (rep.depth >= DEEP_ANSWER_DEPTH) deepRepsSinceTelegraph++
            if (rep.cycleMs <= FAST_ANSWER_CYCLE_MS) fastRepsSinceTelegraph++
            if (rep.depth >= MAGE_ANSWER_DEPTH && rep.bottomHoldMs >= MAGE_ANSWER_HOLD_MS) {
                mageAnswerReady = true
            }
        }

        if (enemy.isDead) {
            finished = true
            events += CombatEvent.EnemyDefeated(atMs, enemy)
            return events
        }

        events += checkTelegraph(atMs)
        return events
    }

    /**
     * Applies one tick of damage from a held position rather than from a rep.
     *
     * A plank tears through a ward at full rate, which is the whole reason a warded enemy is worth
     * answering with one. It does not build combo — a hold is not a sequence of attacks — but it
     * does count as activity, so the rest timer resets.
     */
    fun onHold(damage: Float, atMs: Long): List<CombatEvent> {
        if (finished || damage <= 0f) return emptyList()
        val events = mutableListOf<CombatEvent>()

        val dealt = damage.roundToInt().coerceAtLeast(1)
        enemy = if (enemy.warded) {
            val remainingWard = (enemy.wardHp - dealt).coerceAtLeast(0)
            val spill = (dealt - enemy.wardHp).coerceAtLeast(0)
            enemy.copy(wardHp = remainingWard, hp = (enemy.hp - spill).coerceAtLeast(0))
        } else {
            enemy.copy(hp = (enemy.hp - dealt).coerceAtLeast(0))
        }

        damageDealt += dealt
        lastRepAtMs = atMs
        tickIndex = 0

        events += CombatEvent.Hit(
            atMs,
            AttackResult(
                damage = dealt,
                crit = false,
                deep = false,
                rejected = false,
                player = player,
                enemy = enemy,
                xp = 1,
            ),
        )
        runXp += 1

        if (enemy.isDead) {
            finished = true
            events += CombatEvent.EnemyDefeated(atMs, enemy)
        }
        return events
    }

    /** Advances the idle clock. Call every frame with the current pose timestamp. */
    fun advanceTo(nowMs: Long): List<CombatEvent> {
        if (finished) return emptyList()
        val events = mutableListOf<CombatEvent>()

        // Resolve the ultimate first: its window can expire independently of the idle ticks.
        if (telegraphed && nowMs >= telegraphAtMs + TELEGRAPH_WINDOW_MS) {
            events += resolveUltimate(nowMs)
            if (finished) return events
        }

        val holding = holdingSinceMs
        if (holding != null) {
            // Holding freezes the rest timer, paid for out of the gauge.
            val spent = (nowMs - maxOf(holding, lastTickAtMs)).toInt().coerceAtLeast(0)
            if (defenseGaugeMs > 0) {
                defenseGaugeMs = (defenseGaugeMs - spent).coerceAtLeast(0)
                lastRepAtMs = maxOf(lastRepAtMs, nowMs - graceMs / 2)
                lastTickAtMs = nowMs
                return events
            }
        }

        if (nowMs < staggeredUntilMs) return events

        val idleMs = nowMs - lastRepAtMs
        if (idleMs <= graceMs) return events

        val ticksDue = ((idleMs - graceMs) / TICK_MS).toInt()
        while (tickIndex < ticksDue) {
            tickIndex++
            val escalation = min(3.0f, 1.0f + 0.25f * (tickIndex - 1))
            val raw = (enemy.attack * difficulty.enemyAtkMultiplier * escalation).roundToInt()
            val absorbed = min(player.shield, raw)
            val net = (raw - absorbed).coerceAtLeast(0)

            if (player.combo > 0) events += CombatEvent.ComboBroken(nowMs, player.combo)
            player = player.copy(
                hp = (player.hp - net).coerceAtLeast(0),
                shield = player.shield - absorbed,
                combo = 0,
                tempoStreak = 0,
            )
            rage++
            events += CombatEvent.BossTick(nowMs, net, tickIndex)
            lastTickAtMs = nowMs

            if (player.hp <= 0) {
                finished = true
                events += CombatEvent.Exhausted(nowMs, crackFraction())
                return events
            }
        }

        events += checkTelegraph(nowMs)
        return events
    }

    private fun checkTelegraph(atMs: Long): List<CombatEvent> {
        if (telegraphed || rage < enemy.rageThreshold - TELEGRAPH_LEAD) return emptyList()
        telegraphed = true
        telegraphAtMs = atMs
        repsSinceTelegraph = 0
        deepRepsSinceTelegraph = 0
        fastRepsSinceTelegraph = 0
        mageAnswerReady = false
        return listOf(CombatEvent.Telegraph(atMs, atMs + TELEGRAPH_WINDOW_MS))
    }

    private fun resolveUltimate(atMs: Long): List<CombatEvent> {
        telegraphed = false
        rage = 0

        val mitigation = when {
            player.playerClass == PlayerClass.KNIGHT && deepRepsSinceTelegraph >= 2 -> Mitigation.FULL
            player.playerClass == PlayerClass.MAGE && mageAnswerReady -> Mitigation.FULL
            player.playerClass == PlayerClass.ARCHER && fastRepsSinceTelegraph >= 4 -> Mitigation.FULL
            // The universal answer: hold a plank. A beginner whose arms are finished still has one.
            holdingSinceMs != null && atMs - holdingSinceMs!! >= PLANK_FALLBACK_MS -> Mitigation.PARTIAL
            else -> Mitigation.NONE
        }

        val damage = when (mitigation) {
            Mitigation.FULL -> 0
            Mitigation.PARTIAL -> (ultimateDamage * 0.40f).roundToInt()
            Mitigation.NONE -> ultimateDamage
        }

        val events = mutableListOf<CombatEvent>()
        if (mitigation == Mitigation.FULL) {
            // Answering the telegraph staggers the boss and keeps the chain alive; that reward is
            // what makes the warning worth reacting to rather than just enduring.
            staggeredUntilMs = atMs + STAGGER_MS
        } else {
            if (player.combo > 0) events += CombatEvent.ComboBroken(atMs, player.combo)
            player = player.copy(
                hp = (player.hp - damage).coerceAtLeast(0),
                combo = 0,
                tempoStreak = 0,
            )
        }
        events += CombatEvent.Ultimate(atMs, damage, mitigation)

        if (player.hp <= 0) {
            finished = true
            events += CombatEvent.Exhausted(atMs, crackFraction())
        }
        return events
    }

    /**
     * How much of this enemy's health stays broken for the retry.
     *
     * Losing already keeps every rep, every point of XP and the streak. This goes further and makes
     * the attempt itself count for something: the next try is measurably shorter.
     */
    fun crackFraction(): Float =
        min(MAX_CRACK, 0.5f * damageDealt.toFloat() / enemy.maxHp.coerceAtLeast(1))

    /** Clear bonus, kept deliberately small: most XP is already banked per rep. */
    fun clearBonusXp(): Int = (0.43f * runXp).roundToInt()

    companion object {
        const val TICK_MS = 3_000L
        const val RAGE_EVENTS_PER_ENCOUNTER = 4
        const val MIN_REPS_PER_RAGE = 6
        const val TELEGRAPH_LEAD = 2
        const val TELEGRAPH_WINDOW_MS = 10_000L
        const val STAGGER_MS = 6_000L
        const val PLANK_FALLBACK_MS = 5_000L
        const val DEFENSE_GAUGE_MS = 30_000
        const val GAUGE_REFILL_MS = 5_000
        const val GAUGE_REFILL_EVERY_REPS = 10
        const val REGEN_EVERY_REPS = 10
        const val MAX_CRACK = 0.30f

        const val DEEP_ANSWER_DEPTH = 88f
        const val MAGE_ANSWER_DEPTH = 92f
        const val MAGE_ANSWER_HOLD_MS = 1_000
        const val FAST_ANSWER_CYCLE_MS = 1_800
    }
}
