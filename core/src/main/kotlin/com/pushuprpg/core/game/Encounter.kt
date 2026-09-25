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
     * them are answers; see [Encounter.onDeep]. Counted in reps, never in seconds, so resting
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

    /**
     * How a rep measured up to the class's way of doing it, once that is known: at the strike for a
     * 궁수, at the deep line or the rep's end for a 기사. [miss] is null for a whole rep. [left] is
     * what the monster still owes once the rep is decided — the number a 기사's rep shows.
     */
    data class Style(override val atMs: Long, val miss: StyleMiss?, val left: Int) : CombatEvent
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
    /** XP earned in this fight, booked rep by rep as each one's worth and depth become known. */
    var xp: Float = 0f
        private set
    val runXp: Int get() = xp.roundToInt()
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

    /** Reps done the class's way, whole reps off the count. */
    var styleReps: Int = 0
        private set

    /**
     * Reps this monster still owes if every rep from here is done the class's way, the one in
     * progress included — what the run's total is counted from.
     *
     * A 기사's rep takes its first half at the strike and is not decided until the deep line. Counted
     * as half until then, the total rose by one at every strike and fell back at the deep line, and
     * a run done perfectly ended on 10개 / 11. So the half still to come of an undecided rep is
     * counted as coming; a rep that ends half raises the total then, once, as it should.
     */
    val repsOwed: Int
        get() {
            val pending = repOpen && !repReachedDeep && player.playerClass == PlayerClass.KNIGHT
            val halves = 2 * enemy.remaining - (if (enemy.halfTaken) 1 else 0) - (if (pending) 1 else 0)
            return ((halves + 1) / 2).coerceAtLeast(0)
        }

    // The rep in progress, from its strike to its end: a 기사's is not decided until it has either
    // reached the deep line or come back up without it.
    private var repOpen = false
    private var repReachedDeep = false
    private var repAnswered = false
    /** The rep in progress is one of the open answer window's. */
    private var repInWindow = false

    // The last rep struck, as its XP is booked: what it is worth off the count so far, how deep it
    // has gone and the XP already booked for it. See [CombatResolver.repXp].
    private var repHalves = 0
    private var repDepth = 0f
    private var repCrit = false
    private var repExercise = ExerciseType.PUSHUP
    private var repXp = 0f

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

        // The last rep is over whether or not its end was reported (an abandoned rep never is).
        events += closeRep(atMs)
        if (finished) return events

        // A rest long enough to break the combo does so before the rep lands, so the rep starts
        // the new chain rather than extending a chain the player already lost.
        val startsSet = player.combo == 0 || atMs - lastRepAtMs > player.playerClass.comboWindowMs
        if (player.combo > 0 && atMs - lastRepAtMs > player.playerClass.comboWindowMs) {
            events += CombatEvent.ComboBroken(atMs, player.combo)
            player = player.copy(combo = 0, tempoStreak = 0)
        }

        // What the rep is worth now. A 궁수's is known at the strike; a 기사's first half lands here
        // and the second at the deep line, if the way down was slow.
        val halves = when (player.playerClass) {
            PlayerClass.ARCHER -> ClassStyle.archerHalves(rep.exercise, rep.cycleMs, startsSet)
            PlayerClass.KNIGHT -> 1
        }

        val result = resolver.resolve(player, enemy, rep, rng, halves)
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
        repHalves = halves
        repDepth = rep.depth
        repCrit = result.crit
        repExercise = rep.exercise
        repXp = 0f
        bookRepXp()
        events += CombatEvent.Hit(atMs, result)
        repOpen = true
        repReachedDeep = false
        repAnswered = false

        if (player.playerClass == PlayerClass.ARCHER) {
            if (halves == 2) styleReps++
            events += CombatEvent.Style(atMs, if (halves == 2) null else StyleMiss.LAGGING, enemy.remaining)
        }

        // Pushing through without resting earns a little health back.
        if (repsCounted % REGEN_EVERY_REPS == 0) {
            val regen = (player.maxHp * 0.02f).roundToInt().coerceAtLeast(1)
            player = player.copy(hp = min(player.maxHp, player.hp + regen))
        }
        if (repsCounted % GAUGE_REFILL_EVERY_REPS == 0) {
            defenseGaugeMs = min(DEFENSE_GAUGE_MS, defenseGaugeMs + GAUGE_REFILL_MS)
        }

        repInWindow = telegraphed
        if (telegraphed) {
            repsSinceTelegraph++
            if (answersAtStrike(rep)) answer()
        }

        // Finishing the monster is the best answer of all: whatever it was winding up never lands.
        if (enemy.isDead) return events + defeated(atMs)

        events += blockIfAnswered(atMs)
        if (!finished) events += checkTelegraph(atMs)
        return events
    }

    /**
     * The rep in progress reached the deep line. [loweringMs] is how long the way down took, top
     * band to deep line; for a 기사 it decides whether the rep is whole.
     *
     * Also where depth answers an ultimate, for everyone. It used to be read at the strike, but the
     * strike fires on crossing the count line, before anybody has gone deeper — so "깊게 막아요"
     * almost never counted.
     */
    fun onDeep(loweringMs: Int, atMs: Long): List<CombatEvent> {
        if (finished || !repOpen || repReachedDeep) return emptyList()
        repReachedDeep = true
        val events = mutableListOf<CombatEvent>()
        if (player.playerClass == PlayerClass.KNIGHT) {
            if (ClassStyle.knightSlowEnough(loweringMs)) {
                enemy = enemy.spend(1)
                repHalves = 2
                bookRepXp()
                styleReps++
                events += CombatEvent.Style(atMs, null, enemy.remaining)
                if (telegraphed) answer()
                if (enemy.isDead) return events + defeated(atMs)
            } else {
                events += CombatEvent.Style(atMs, StyleMiss.TOO_QUICK, enemy.remaining)
            }
        } else if (telegraphed) {
            answer()
        }
        events += blockIfAnswered(atMs)
        return events
    }

    /**
     * The rep in progress is over: back at the top, or abandoned. A 기사's rep that never reached
     * the deep line stays half, and an answer window whose reps are all done is decided now that
     * every one of them is known.
     *
     * [seen] is false when the rep ended because the tracker lost the user: whether it went deep
     * is unknown, and telling them to go all the way down would blame them for the tracker.
     */
    fun onRepEnd(atMs: Long, seen: Boolean = true): List<CombatEvent> =
        if (finished) emptyList() else closeRep(atMs, seen)

    /**
     * The last rep struck has gone [depth] deep: at the deep line, or as it finished. Its XP rises
     * to match, even if its monster has already fallen — the rep was done.
     */
    fun onDepth(depth: Float) {
        if (depth <= repDepth) return
        repDepth = depth
        bookRepXp()
    }

    private fun bookRepXp() {
        val now = resolver.repXp(repDepth, repExercise, player.playerClass, repHalves, repCrit)
        xp += now - repXp
        repXp = now
    }

    private fun closeRep(atMs: Long, seen: Boolean = true): List<CombatEvent> {
        val events = mutableListOf<CombatEvent>()
        if (seen && repOpen && !repReachedDeep && player.playerClass == PlayerClass.KNIGHT) {
            events += CombatEvent.Style(atMs, StyleMiss.NOT_FULL, enemy.remaining)
        }
        // A rep the tracker lost is not held against the user. Unless it had already answered, it
        // gives its chance in the window back — a 기사's answer comes at the deep line, after the
        // strike, and the tracker took that away — and the window is never decided on it: the hit
        // would land on somebody the camera cannot see, for a gap that was the tracker's.
        if (!seen && repOpen && repInWindow && !repAnswered && telegraphed) repsSinceTelegraph--
        repOpen = false
        if (seen) events += landIfSpent(atMs)
        return events
    }

    private fun answer() {
        if (repAnswered) return
        repAnswered = true
        answersLanded++
    }

    private fun defeated(atMs: Long): List<CombatEvent> {
        finished = true
        telegraphed = false
        repOpen = false
        return listOf(CombatEvent.EnemyDefeated(atMs, enemy))
    }

    /**
     * Whether a rep answers the ultimate at its strike. A 궁수 answers with pace. Depth answers for
     * everyone, and a 기사 with a slow, full rep — both at the deep line, in [onDeep].
     */
    private fun answersAtStrike(rep: RepInput): Boolean =
        player.playerClass == PlayerClass.ARCHER && rep.cycleMs <= ClassStyle.briskCycleMs(rep.exercise)

    /** Blocks the ultimate the moment enough answers have landed. */
    private fun blockIfAnswered(atMs: Long): List<CombatEvent> {
        if (!telegraphed || answersLanded < ANSWERS_TO_BLOCK) return emptyList()
        telegraphed = false
        return listOf(CombatEvent.Ultimate(atMs, damage = 0, mitigation = Mitigation.FULL))
    }

    /**
     * Lands the ultimate once its window's reps are all done and decided — lighter for every answer
     * that was made. Decided at the end of the last rep rather than its strike, because a 기사's
     * answer, and anyone's deep one, arrives after the strike.
     */
    private fun landIfSpent(atMs: Long): List<CombatEvent> {
        if (!telegraphed || repsSinceTelegraph < ANSWER_WINDOW_REPS) return emptyList()
        if (answersLanded >= ANSWERS_TO_BLOCK) return blockIfAnswered(atMs)

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
            ),
        )
        xp += 1f

        if (enemy.isDead) {
            events += defeated(atMs)
        } else {
            events += blockIfAnswered(atMs)
            events += landIfSpent(atMs)
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
        // The rep in progress was the last movement's; its end will never be reported.
        repOpen = false
    }

    fun crackFraction(): Float =
        min(MAX_CRACK, 0.5f * damageDealt.toFloat() / enemy.maxHp.coerceAtLeast(1))

    /** Clear bonus, kept deliberately small: most XP is already banked per rep. */
    fun clearBonusXp(): Int = clearBonusFor(xp).roundToInt()

    companion object {
        /**
         * The bonus for clearing a run that earned [runXp] — the whole run's, not its last fight's.
         * Read off the boss alone it came to a sixth of what the first dungeon's reps earned.
         */
        fun clearBonusFor(runXp: Float): Float = CLEAR_BONUS * runXp

        const val CLEAR_BONUS = 0.43f
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

        /** Reps the wind-up gives the player, and how many of them must be answers to block it. */
        const val ANSWER_WINDOW_REPS = 5
        const val ANSWERS_TO_BLOCK = 3
        /** A monster must be at least this many reps for an ultimate to have room to happen. */
        const val MIN_HP_FOR_ULTIMATE = 8
        const val FIRST_ULTIMATE_AT = 0.60f
        const val SECOND_ULTIMATE_AT = 0.25f
    }
}
