package com.pushuprpg.core.game

import com.pushuprpg.core.detect.ExerciseType
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/** What happened in the encounter, for the UI and the audio layer to react to. */
sealed interface CombatEvent {
    val atMs: Long

    data class Hit(override val atMs: Long, val result: AttackResult) : CombatEvent
    data class RepRejected(override val atMs: Long, val reason: RejectReason) : CombatEvent

    /** The boss punished a rest. [index] escalates while the player stays idle. */
    data class BossTick(override val atMs: Long, val damage: Int, val index: Int) : CombatEvent

    /**
     * "필살기 준비" — the wind-up. It lands after [windowReps] more reps unless [answersNeeded] of
     * them are answers; see [Encounter.isAnswer]. Counted in reps, never in seconds, so resting
     * while it winds up costs nothing.
     */
    data class Telegraph(override val atMs: Long, val windowReps: Int, val answersNeeded: Int) : CombatEvent

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
    private var resolver: CombatResolver = CombatResolver(),
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
    private var staggeredUntilMs = 0L
    private var holdingSinceMs: Long? = null
    private var plankHeldMs = 0
    private var repsSinceRage = 0
    private var ultimatesStarted = 0

    /** True while an ultimate is winding up and the answer window is open. */
    val ultimateWindingUp: Boolean get() = telegraphed

    /** Reps still to come in the open answer window; zero when none is open. */
    val answerRepsLeft: Int get() = if (telegraphed) (ANSWER_WINDOW_REPS - repsSinceTelegraph).coerceAtLeast(0) else 0

    /** Answers landed in the open window so far. */
    var answersLanded: Int = 0
        private set

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

        if (telegraphed) {
            repsSinceTelegraph++
            if (isAnswer(rep, result)) answersLanded++
        }

        // Finishing the monster is the best answer of all: whatever it was winding up never lands.
        if (enemy.isDead) {
            finished = true
            telegraphed = false
            events += CombatEvent.EnemyDefeated(atMs, enemy)
            return events
        }

        events += resolveUltimate(atMs)
        if (!finished) events += checkTelegraph(atMs)
        return events
    }

    /**
     * Whether a rep in the answer window counts as an answer.
     *
     * A deep rep, for everyone — the thing the gauge already asks for. Each class also answers in
     * its own style, because a class is a way of training: an archer with pace (a fast rep), a mage
     * with stillness (a rep held at the bottom). The knight's style is depth, which everybody has.
     */
    private fun isAnswer(rep: RepInput, result: AttackResult): Boolean = result.deep || when (player.playerClass) {
        PlayerClass.ARCHER -> rep.cycleMs <= FAST_ANSWER_CYCLE_MS
        PlayerClass.MAGE -> rep.bottomHoldMs >= MAGE_ANSWER_HOLD_MS
        PlayerClass.KNIGHT -> false
    }

    /**
     * Ends an open window once it is decided: blocked the moment enough answers land, or landed
     * when its reps run out — lighter for every answer that was made.
     */
    private fun resolveUltimate(atMs: Long): List<CombatEvent> {
        if (!telegraphed) return emptyList()
        if (answersLanded >= ANSWERS_TO_BLOCK) {
            telegraphed = false
            return listOf(CombatEvent.Ultimate(atMs, damage = 0, mitigation = Mitigation.FULL))
        }
        if (repsSinceTelegraph < ANSWER_WINDOW_REPS) return emptyList()

        telegraphed = false
        val damage = (ultimateDamage * (1f - answersLanded.toFloat() / ANSWERS_TO_BLOCK))
            .roundToInt().coerceAtLeast(1)
        player = player.copy(hp = (player.hp - damage).coerceAtLeast(0))
        val events = mutableListOf<CombatEvent>(
            CombatEvent.Ultimate(atMs, damage, if (answersLanded > 0) Mitigation.PARTIAL else Mitigation.NONE)
        )
        if (player.hp <= 0) {
            finished = true
            events += CombatEvent.Exhausted(atMs, crackFraction())
        }
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
        // A held plank is the defensive stance: every tick of it answers, so holding through a
        // wind-up blocks it. Ticks advance the window the way reps do.
        if (telegraphed) {
            repsSinceTelegraph++
            answersLanded++
        }

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
            telegraphed = false
            events += CombatEvent.EnemyDefeated(atMs, enemy)
        } else {
            events += resolveUltimate(atMs)
        }
        return events
    }

    /** Advances the idle clock. Call every frame with the current pose timestamp. */
    /**
     * Advances the encounter's own clock. Under the volume model this can no longer hurt anybody.
     *
     * What used to be here: an idle timer. After a grace period of `min(14s, 8s + 200ms * level)` —
     * 8.2 seconds at level 1 — the enemy struck every three seconds, escalating. Measured against a
     * knight's 120 max HP, a perfectly ordinary between-sets rest cost this:
     *
     *     30 s rest -> 120/120, free
     *     60 s rest ->  60/120, half gone
     *     90 s rest ->   0/120, dead, run over
     *
     * Ninety seconds is the *recommended* rest between pushup sets and the short end for pull-ups;
     * a bench press wants 120-300. So the game killed people for resting correctly, and a bench
     * session was unsurvivable by construction. It could not be retuned either — no multiple of 8.2
     * seconds reaches a 300-second deadlift rest.
     *
     * The ultimate went with it for the same reason: its answer window was ten seconds of wall
     * clock, so it punished a pause just as surely, only less often.
     *
     * Nothing replaces them. A run ends when the user stops, and how far they got is the result —
     * which is the honest shape for an app whose own rules say reps survive a loss, that a tracking
     * failure is never punished, and that 실패 appears nowhere. The pull comes from the next tier's
     * count being visible ahead of you, not from a threat behind you.
     */
    fun advanceTo(nowMs: Long): List<CombatEvent> {
        if (finished) return emptyList()
        lastSeenMs = nowMs
        return emptyList()
    }

    /**
     * Starts an ultimate when the monster is worn down far enough — counted in reps, never time.
     *
     * The old ultimate was answered inside ten seconds of wall clock, so it punished a pause exactly
     * as the idle timer did, and both were removed. This is the version that note asked for: the
     * wind-up opens a window of [ANSWER_WINDOW_REPS] reps, and it lands only when those reps are
     * done without enough answers. Resting while it winds up costs nothing, and so does a tracking
     * gap — no rep, no progress.
     *
     * Once when a monster is down to [FIRST_ULTIMATE_AT] of its health, and for a boss again at
     * [SECOND_ULTIMATE_AT]. A monster too small to leave room for a window never starts one.
     */
    private fun checkTelegraph(atMs: Long): List<CombatEvent> {
        if (telegraphed || enemy.maxHp < MIN_HP_FOR_ULTIMATE) return emptyList()
        val left = enemy.hp.toFloat() / enemy.maxHp
        val due = when (ultimatesStarted) {
            0 -> left <= FIRST_ULTIMATE_AT
            1 -> enemy.isBoss && left <= SECOND_ULTIMATE_AT
            else -> false
        }
        if (!due) return emptyList()
        telegraphed = true
        ultimatesStarted++
        repsSinceTelegraph = 0
        answersLanded = 0
        telegraphAtMs = atMs
        return listOf(CombatEvent.Telegraph(atMs, ANSWER_WINDOW_REPS, ANSWERS_TO_BLOCK))
    }

    /**
     * The same fight, carried on with a different movement.
     *
     * [repriced] is this floor's enemy spawned fresh for the new movement. Whatever fraction of the
     * enemy was left stays left, now counted in the new movement's reps — half a monster is half a
     * monster whether it is finished with pushups or pull-ups. Rounded up, so a rep still owed is
     * never rounded away, and an enemy already down stays down.
     */
    fun switchMovement(repriced: Enemy, resolver: CombatResolver) {
        fun share(left: Int, of: Int, newTotal: Int): Int =
            if (left <= 0 || of <= 0) 0 else ceil(newTotal * left.toDouble() / of).toInt().coerceIn(1, newTotal.coerceAtLeast(1))
        enemy = repriced.copy(
            hp = share(enemy.hp, enemy.maxHp, repriced.maxHp),
            wardHp = share(enemy.wardHp, enemy.wardMaxHp, repriced.wardMaxHp),
        )
        this.resolver = resolver
    }

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

        /** Reps the wind-up gives the player, and how many of them must be answers to block it. */
        const val ANSWER_WINDOW_REPS = 5
        const val ANSWERS_TO_BLOCK = 3
        /** A monster must be at least this many reps for an ultimate to have room to happen. */
        const val MIN_HP_FOR_ULTIMATE = 8
        const val FIRST_ULTIMATE_AT = 0.60f
        const val SECOND_ULTIMATE_AT = 0.25f
    }
}
