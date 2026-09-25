package com.pushuprpg.core.run

import com.pushuprpg.core.anim.AnimClip
import com.pushuprpg.core.anim.AnimState
import com.pushuprpg.core.anim.FighterAnimator
import com.pushuprpg.core.audio.BattleAudio
import com.pushuprpg.core.audio.SoundCue
import com.pushuprpg.core.audio.SoundRequest
import com.pushuprpg.core.detect.*
import com.pushuprpg.core.game.*
import com.pushuprpg.core.pose.PoseFrame
import kotlin.math.roundToInt

/**
 * One number floating up the screen when a rep lands.
 *
 * [amount] is the enemy's REMAINING rep count after the hit, not damage dealt. Under the volume
 * model a rep is worth exactly one, so a damage number would be either a permanent "1" or — as it
 * briefly was — a decorative figure in the twenties while the monster lost a single rep. The count
 * remaining is the number the user is actually working toward, and it is true.
 *
 * It rises when the rep's worth is known: at the strike for a 궁수, and for a 기사 at the deep line
 * or the rep's end, since a 기사's strike takes only the first half of a rep. So the numbers only
 * ever fall.
 *
 * [crit] and [deep] still style it, so a deep rep looks better without being worth more.
 */
data class FloatingDamage(
    val id: Long,
    val amount: Int,
    val crit: Boolean,
    val deep: Boolean,
    val atMs: Long,
)

/** A short-lived line of text in the alert slot. */
data class Toast(val textKey: AlertKey, val arg: Int = 0, val atMs: Long)

enum class AlertKey {
    BOOTSTRAP, CALIBRATED, SHALLOW_TWICE,
    /** Four short of the line in a row: the offer of a knee pushup, so a pushup's alone. */
    SHALLOW_FOUR,
    /** A pull-up short of the line: the same nudge, the way a pull-up goes. */
    SHALLOW_PULL,
    COMBO_BROKEN, COMBO_MILESTONE,
    IDLE, BOSS_LOW_HP, ULTIMATE_INCOMING, DEEP_STRIKE, QUALITY_LOST, QUALITY_RECOVERED,
    /** The ultimate was answered in full. */
    ULTIMATE_BLOCKED,
    /** The ultimate landed; the toast's arg is the damage taken. */
    ULTIMATE_HIT,
    /** A lunge refused because the feet were side by side: a squat. */
    NOT_SPLIT,
    /** 기사: a rep that came down too fast was worth half. */
    STYLE_TOO_QUICK,
    /** 기사: a rep that never reached the 깊게 line was worth half. */
    STYLE_NOT_FULL,
    /** 궁수: a rep that lagged inside a set was worth half. */
    STYLE_LAGGING,
}

/** Everything the battle screen draws, as one immutable snapshot. */
data class BattleState(
    val depth: Float = 0f,
    val depthVelocity: Float = 0f,
    val phase: RepPhase = RepPhase.IDLE,
    val quality: PoseQuality = PoseQuality.NO_SUBJECT,
    /** Landmark indices the movement needs and the tracker cannot see; what the quality line names. */
    val missingParts: List<Int> = emptyList(),
    /** What to tell the user about where they and the phone are, or nothing. See [PlacementCoach]. */
    val placement: Placement = Placement(PlacementAdvice.STEP_INTO_VIEW),
    val calibrating: Boolean = true,
    val render: RenderSkeleton = RenderSkeleton.EMPTY,
    val reps: Int = 0,
    /** Time held this run, every hold summed across switches. A plank counts no [reps]. */
    val heldMs: Long = 0,
    val combo: Int = 0,
    val maxCombo: Int = 0,
    val deepReps: Int = 0,
    val playerHp: Int = 100,
    val playerMaxHp: Int = 100,
    val enemyName: String = "",
    /** What this monster still owes, ward included, so the bar moves while a ward comes down. */
    val enemyHp: Int = 1,
    /** All it owed as it spawned, ward included. */
    val enemyMaxHp: Int = 1,
    val floorIndex: Int = 0,
    val floorCount: Int = 1,
    /** Reps this whole run costs, every floor summed. The denominator of the run's progress. */
    val runTotalReps: Int = 0,
    /** The movement being counted, so the screen can say 개 or 초 without guessing. */
    val exercise: ExerciseType = ExerciseType.PUSHUP,
    val ultimateIncoming: Boolean = false,
    /** Reps left in the open answer window; zero when nothing is winding up. */
    val ultimateRepsLeft: Int = 0,
    /** Answers landed in the open window, out of [com.pushuprpg.core.game.Encounter.ANSWERS_TO_BLOCK]. */
    val ultimateAnswers: Int = 0,
    val ultimateDeadlineMs: Long = 0,
    val damages: List<FloatingDamage> = emptyList(),
    val alert: Toast? = null,
    val shake: Float = 0f,
    val outcome: Outcome? = null,
    val elapsedMs: Long = 0,
    val countEnter: Float = 70f,
    val deepEnter: Float = 88f,

    // --- presentation state, resolved here so the renderer stays a renderer ---
    val playerAnim: AnimState = AnimState(AnimClip.IDLE, 0f, 0f, 0f),
    val enemyId: String = "",
    val enemyIsBoss: Boolean = false,
    /** 0..1, flashes when the enemy is struck. */
    val enemyHurt: Float = 0f,
    /** 0..1 through the enemy's death animation. */
    val enemyDeath: Float = 0f,
    /** 0..1 as the boss charges toward its ultimate. */
    val telegraphCharge: Float = 0f,
    /** Sounds to play for this frame. Empty on most frames, never null. */
    val sounds: List<SoundRequest> = emptyList(),
) {
    val enemyHpFraction: Float get() = enemyHp.toFloat() / enemyMaxHp.coerceAtLeast(1)
    val playerHpFraction: Float get() = playerHp.toFloat() / playerMaxHp.coerceAtLeast(1)

    /**
     * The game clock only runs while tracking is good. A user must never lose health because the
     * tracker blinked — that reads as the app cheating, and it is the fastest way to lose trust.
     */
    val paused: Boolean get() = quality != PoseQuality.OK

    /**
     * Whether the run has anything in it to bank: a rep, or a second held — the same second a
     * finished run needs before a held stretch is worth a record row.
     */
    val workDone: Boolean get() = reps > 0 || heldMs >= 1_000L
}

/**
 * How well a run was performed, from one to three.
 *
 * The volume model deliberately made every accepted rep worth exactly one, so that the count on the
 * entry screen is the count performed. That left depth with nothing to do, and a rep at the 인정
 * line paying the same as one well past 깊게 is not what this app wants to teach. Stars are the
 * other axis: volume decides WHICH monster falls, depth decides how well you felled it.
 *
 * Read off the detector's own two lines rather than numbers of their own, for the same reason the
 * gauge is: the user aims at 인정 and 깊게, so those are what they must be graded against.
 */
enum class Stars {
    ONE, TWO, THREE;

    val count: Int get() = ordinal + 1

    companion object {
        fun of(meanDepth: Float, countEnter: Float, deepEnter: Float): Stars = when {
            meanDepth >= deepEnter -> THREE
            // Halfway between the two lines. Not a third star's worth of depth, but visibly more
            // than scraping the 인정 line on every rep.
            meanDepth >= (countEnter + deepEnter) / 2f -> TWO
            else -> ONE
        }
    }
}

data class Outcome(
    val cleared: Boolean,
    val reps: Int,
    val maxCombo: Int,
    val deepReps: Int,
    val durationMs: Long,
    val xpEarned: Int,
    val crackFraction: Float,
    val meanDepth: Float,
    val plausibility: Float,
    /** How deep the run was, graded against the detector's own 인정 and 깊게 lines. */
    val stars: Stars = Stars.ONE,
    /** Reps done the class's way — whole reps off the count. The rest were worth half. */
    val styleReps: Int = 0,
    /** The run movement by movement, in the order they were done. One entry if it never switched. */
    val segments: List<ExerciseSegment> = emptyList(),
    /** The monster being fought when the run ended. */
    val enemyName: String = "",
    /**
     * What that monster still owed, ward included, in the fight's own unit — reps, or seconds for a
     * hold, as the last segment's movement says. Zero once cleared. What a run stopped part way can
     * honestly say it left.
     */
    val enemyLeft: Int = 0,
)

/**
 * One stretch of a run done with one movement.
 *
 * A dungeon run can change movement as often as the user likes — a real session goes pushups,
 * pull-ups, squats — so what it banks is kept per movement: each one's reps, best set and hold
 * time feed that movement's own records and capacity, not a pushup's.
 */
data class ExerciseSegment(
    val exercise: ExerciseType,
    val reps: Int,
    val maxCombo: Int,
    val deepReps: Int,
    val meanDepth: Float,
    /** Time held, for a hold. Zero for a counted movement. */
    val holdMs: Long,
    val durationMs: Long,
    val plausibility: Float,
)

/**
 * Drives one dungeon run: pose frames in, battle state out.
 *
 * This lives in `:core` rather than in a ViewModel on purpose. Sequencing floors, converting reps
 * into attacks, deciding when a run ends and what it banked are all game rules, and putting them
 * behind an Android class would make the most consequential logic in the app the only part that
 * cannot be replayed from a recorded landmark trace in a plain JVM test. The ViewModel owns the
 * camera and the coroutine scope; this owns the rules.
 */
class BattleEngine(
    private val dungeon: Dungeon,
    private val difficulty: Difficulty,
    private val capacity: Float,
    initialPlayer: PlayerState,
    detector: RepDetector,
    resolver: CombatResolver,
    private val rngSeed: Long = 0L,
) {
    /** The movement being done now; replaced by [switchExercise]. */
    private var detector: RepDetector = detector
    private var coach = PlacementCoach(detector.config.exercise, detector.config)
    private var resolver: CombatResolver = resolver
    private var player: PlayerState = initialPlayer
    // Before the first floor spawns: its price depends on the class.
    private val playerClass = initialPlayer.playerClass
    private var floorIndex = 0
    private var encounter: Encounter = spawnFloor(0, 0L)

    private var startedAtMs = Long.MIN_VALUE
    private var lastFrameMs = 0L
    private var lastStrikeMs = Long.MIN_VALUE
    private var lastBottomMs = 0
    private var readyTopSinceMs = Long.MIN_VALUE
    private var damageSeq = 0L
    /** The last strike's crit, which styles a 기사's number when the rep is decided. */
    private var strikeCrit = false
    private val animator = FighterAnimator(initialPlayer.playerClass)
    private var enemyHurtAtMs = Long.MIN_VALUE
    private var enemyDiedAtMs = Long.MIN_VALUE
    private var plankHolding = false
    private var lastHoldTickMs = 0L
    private var repsTotal = 0
    private var deepReps = 0
    private var depthSum = 0f
    /**
     * The rep whose depth is on the books, and how much it has put there. A rep counts at the 인정
     * line, on its way down, so the depth it strikes at is always about 70; how deep it went is only
     * known as it keeps going. Averaging the strike depth graded every set one star and told people
     * whose chest touched the floor to go deeper next time.
     */
    private var bookedRep = -1
    private var bookedDepth = 0f
    private var bookedDeep = false
    /** XP from floors already cleared; the current floor's is on its encounter. */
    private var xpBanked = 0f
    private var shallowStreak = 0
    /** Half-worth reps in a row, so a reminder is given on the first and then only now and then. */
    private var styleMisses = 0
    /** Whole reps banked from floors already cleared; the current floor's are on its encounter. */
    private var styleRepsTotal = 0

    // The movement in progress, since the run started or last switched.
    private val segments = mutableListOf<ExerciseSegment>()
    private var segStartMs = Long.MIN_VALUE
    private var segReps = 0
    private var segDeep = 0
    private var segDepthSum = 0f
    private var segMaxCombo = 0
    /** Time held by the movements already switched away from. */
    private var heldBeforeMs = 0L
    /** How much of the current detector's time held has been taken off monsters, in whole seconds. */
    private var holdCreditedMs = 0L

    /**
     * What the run costs in total, computed once at spawn from the same rule the entry screen quoted.
     *
     * Every floor's HP is a rep count, so the sum is the run's whole rep cost — the number the entry
     * picker promised, which the HUD can now show the user working through.
     */
    private var runTotalReps: Int = dungeon.repCost(difficulty, detector.config.exercise, initialPlayer.playerClass)

    private var state = BattleState(
        playerHp = initialPlayer.hp,
        playerMaxHp = initialPlayer.maxHp,
        enemyName = encounter.enemy.korean,
        enemyHp = encounter.enemy.remaining,
        enemyMaxHp = encounter.enemy.fullCount,
        floorCount = dungeon.floors.size,
        runTotalReps = runTotalReps,
        exercise = detector.config.exercise,
        countEnter = detector.config.countEnter,
        deepEnter = detector.config.deepEnter,
    )

    fun currentState(): BattleState = state

    fun onPoseFrame(frame: PoseFrame): BattleState {
        val tick = detector.onFrame(frame)
        val placement = coach.update(frame, tick)
        if (startedAtMs == Long.MIN_VALUE) {
            startedAtMs = tick.tMs
            segStartMs = tick.tMs
            // Floor 0's encounter was built before a frame existed, so this is the first moment its
            // deadlines can be anchored to the clock the rest of the run will use.
            encounter.startAt(tick.tMs)
        }
        lastFrameMs = tick.tMs
        // Before anything reads a deadline this frame, so a dropout is invisible to the rep loop
        // and the holding check as well as to the damage tick.
        encounter.setTracking(
            tracking = tick.quality == PoseQuality.OK && enemyDiedAtMs == Long.MIN_VALUE,
            atMs = tick.tMs,
        )

        val sounds = mutableListOf<SoundRequest>()
        val damages = state.damages.filter { tick.tMs - it.atMs < DAMAGE_LIFETIME_MS }.toMutableList()
        var alert = state.alert?.takeIf { tick.tMs - it.atMs < ALERT_LIFETIME_MS }
        var shake = (state.shake - SHAKE_DECAY).coerceAtLeast(0f)
        var outcome = state.outcome

        // The ultimate's three outcomes, whether a rep or a held plank decided them.
        fun onUltimate(ce: CombatEvent) {
            when (ce) {
                is CombatEvent.Telegraph -> {
                    alert = Toast(AlertKey.ULTIMATE_INCOMING, ce.answersNeeded, ce.atMs)
                    sounds += SoundRequest(SoundCue.TELEGRAPH)
                }
                is CombatEvent.Ultimate -> if (ce.damage > 0) {
                    shake = 1f
                    animator.onHurt(ce.atMs)
                    sounds += SoundRequest(SoundCue.PLAYER_HURT, volume = 1f)
                    alert = Toast(AlertKey.ULTIMATE_HIT, ce.damage, ce.atMs)
                } else {
                    alert = Toast(AlertKey.ULTIMATE_BLOCKED, 0, ce.atMs)
                    sounds += SoundRequest(SoundCue.COMBO_UP)
                }
                is CombatEvent.Exhausted -> outcome = finish(cleared = false, atMs = ce.atMs)
                else -> Unit
            }
        }

        // How a rep measured up to the class's way. A half-worth rep is said on the first of a run of
        // them and then every third, so the reminder is heard without becoming the soundtrack.
        fun onStyle(ce: CombatEvent.Style) {
            // A 기사's rep is worth what it is only from here, so this is where its number rises:
            // at the strike it showed a count the deep line was about to take one off.
            if (playerClass == PlayerClass.KNIGHT) {
                damageSeq++
                damages += FloatingDamage(damageSeq, ce.left, strikeCrit, ce.miss != StyleMiss.NOT_FULL, ce.atMs)
            }
            val miss = ce.miss
            if (miss == null) {
                styleMisses = 0
                return
            }
            styleMisses++
            if (styleMisses == 1 || styleMisses % STYLE_REMIND_EVERY == 0) {
                alert = Toast(
                    when (miss) {
                        StyleMiss.TOO_QUICK -> AlertKey.STYLE_TOO_QUICK
                        StyleMiss.NOT_FULL -> AlertKey.STYLE_NOT_FULL
                        StyleMiss.LAGGING -> AlertKey.STYLE_LAGGING
                    },
                    0, ce.atMs,
                )
            }
        }

        // What the deep line and the end of a rep can decide: the rest of a 기사's rep, an answer, the
        // monster falling, the ultimate landing.
        fun onAfterStrike(events: List<CombatEvent>) {
            for (ce in events) when (ce) {
                is CombatEvent.Style -> onStyle(ce)
                is CombatEvent.EnemyDefeated -> {
                    enemyDiedAtMs = ce.atMs
                    sounds += SoundRequest(SoundCue.ENEMY_DOWN)
                }
                is CombatEvent.Telegraph, is CombatEvent.Ultimate, is CombatEvent.Exhausted -> onUltimate(ce)
                else -> Unit
            }
        }

        // Holding the top with the elbows locked counts as a defensive stance: it is genuine
        // isometric work, and it gives someone whose arms are finished a way to stay in the fight
        // instead of choosing between quitting and being hit.
        readyTopSinceMs = when {
            tick.phase == RepPhase.READY_TOP && readyTopSinceMs == Long.MIN_VALUE -> tick.tMs
            tick.phase != RepPhase.READY_TOP -> Long.MIN_VALUE
            else -> readyTopSinceMs
        }
        // A plank that has stopped ticking is a plank that has ended, even if its detector has not
        // said so yet.
        if (plankHolding && tick.tMs - lastHoldTickMs > HOLD_STALE_MS) plankHolding = false
        val topHold = readyTopSinceMs != Long.MIN_VALUE && tick.tMs - readyTopSinceMs >= TOP_HOLD_MS
        encounter.setHolding(topHold || plankHolding, tick.tMs)

        for (event in tick.events) {
            when (event) {
                is RepEvent.Strike -> {
                    val cycleMs = if (lastStrikeMs == Long.MIN_VALUE) DEFAULT_CYCLE_MS
                    else (event.tMs - lastStrikeMs).toInt()
                    lastStrikeMs = event.tMs

                    val rep = RepInput(
                        depth = event.depth,
                        grade = event.grade,
                        exercise = detector.config.exercise,
                        cycleMs = cycleMs,
                        // The current rep's own bottom hold is not knowable yet — the strike fires
                        // at the bottom, before the user has finished holding it. The previous
                        // rep's hold is the best available estimate, and for a mage holding every
                        // rep it is the right one from the second rep onward.
                        bottomHoldMs = lastBottomMs,
                    )
                    val combatEvents = encounter.onRep(rep, event.tMs)
                    for (ce in combatEvents) {
                        when (ce) {
                            is CombatEvent.Hit -> {
                                repsTotal++
                                depthSum += event.depth
                                if (ce.result.deep) deepReps++
                                segReps++
                                segDepthSum += event.depth
                                if (ce.result.deep) segDeep++
                                bookedRep = event.repIndex
                                bookedDepth = event.depth
                                bookedDeep = ce.result.deep
                                // The detector's own combo: it restarts with each movement and at
                                // each rest, so its peak is this movement's longest set.
                                segMaxCombo = maxOf(segMaxCombo, event.combo)
                                animator.onStrike(event.tMs, ce.result.deep, ce.result.crit)
                                enemyHurtAtMs = event.tMs

                                // Both bands fire: the dry cue confirms the rep counted, the low
                                // one confirms it landed. They answer different questions.
                                sounds += SoundRequest(SoundCue.REP_ACCEPT)
                                sounds += SoundRequest(
                                    cue = when {
                                        ce.result.crit -> SoundCue.CRIT
                                        ce.result.deep -> SoundCue.HIT_HEAVY
                                        else -> SoundCue.HIT
                                    },
                                    rate = BattleAudio.pitchForCombo(event.combo),
                                )
                                // A 궁수's rep is decided here; a 기사's number waits for its Style,
                                // unless this half was the last the monster had.
                                strikeCrit = ce.result.crit
                                if (playerClass != PlayerClass.KNIGHT || ce.result.enemyDefeated) {
                                    damageSeq++
                                    damages += FloatingDamage(
                                        damageSeq, ce.result.enemy.remaining, ce.result.crit,
                                        ce.result.deep, event.tMs,
                                    )
                                }
                                shake = (shake + if (ce.result.crit) 1.0f else 0.45f).coerceAtMost(1f)
                                shallowStreak = 0
                                if (event.combo > 0 && event.combo % COMBO_MILESTONE == 0) {
                                    alert = Toast(AlertKey.COMBO_MILESTONE, event.combo, event.tMs)
                                    sounds += SoundRequest(SoundCue.COMBO_UP)
                                }
                            }
                            is CombatEvent.EnemyDefeated -> {
                                // The next floor is held back until the enemy has finished coming
                                // apart. Spawning it on the same frame cancelled the death outright
                                // — the kill the player just earned simply never appeared.
                                enemyDiedAtMs = event.tMs
                                sounds += SoundRequest(SoundCue.ENEMY_DOWN)
                            }
                            is CombatEvent.Telegraph, is CombatEvent.Ultimate, is CombatEvent.Exhausted ->
                                onUltimate(ce)
                            is CombatEvent.Style -> onStyle(ce)
                            else -> alert = alertFor(ce) ?: alert
                        }
                    }
                }

                is RepEvent.DeepUpgrade -> {
                    deepen(event.repIndex, event.depth, deep = true)
                    alert = Toast(AlertKey.DEEP_STRIKE, 0, event.tMs)
                    shake = (shake + 0.3f).coerceAtMost(1f)
                    sounds += SoundRequest(SoundCue.REP_DEEP)
                    if (enemyDiedAtMs == Long.MIN_VALUE) onAfterStrike(encounter.onDeep(event.loweringMs, event.tMs))
                }

                is RepEvent.Completed -> {
                    deepen(event.record.repIndex, event.record.maxDepth, deep = false)
                    lastBottomMs = event.record.bottomMs
                    if (enemyDiedAtMs == Long.MIN_VALUE) onAfterStrike(encounter.onRepEnd(event.tMs))
                }

                // A plank pays out continuously rather than per rep: every second the detector
                // counts as held is a second off the monster. Seconds held while one is falling go
                // to the next, so the quote is exactly the time the run takes.
                is RepEvent.HoldTick -> {
                    plankHolding = true
                    lastHoldTickMs = event.tMs
                    val hit = if (enemyDiedAtMs != Long.MIN_VALUE) emptyList() else {
                        val seconds = ((detector.sessionSummary().holdMs - holdCreditedMs) / 1000L).toInt()
                        holdCreditedMs += seconds * 1000L
                        encounter.onHold(seconds, event.tMs, detector.config.exercise)
                    }
                    for (ce in hit) {
                        when (ce) {
                            is CombatEvent.Hit -> {
                                damageSeq++
                                damages += FloatingDamage(
                                    damageSeq, ce.result.enemy.remaining, false, false, event.tMs,
                                )
                            }
                            is CombatEvent.EnemyDefeated -> enemyDiedAtMs = event.tMs
                            is CombatEvent.Telegraph, is CombatEvent.Ultimate, is CombatEvent.Exhausted ->
                                onUltimate(ce)
                            else -> Unit
                        }
                    }
                }

                is RepEvent.HoldBroken -> plankHolding = false

                is RepEvent.Shallow -> {
                    shallowStreak = event.consecutive
                    // One miss is not worth a message. Two is a nudge, four is an offer to make it
                    // easier — never a scold. The offer is to put the knees down, which only a
                    // pushup can take; every other movement sees the nudge again, and a pull-up's
                    // goes up, not down. The toast carries the count, so the voice says it once.
                    val exercise = detector.config.exercise
                    val nudge = if (exercise == ExerciseType.PULL_UP) AlertKey.SHALLOW_PULL else AlertKey.SHALLOW_TWICE
                    alert = when {
                        event.consecutive >= 4 && exercise == ExerciseType.PUSHUP ->
                            Toast(AlertKey.SHALLOW_FOUR, 0, event.tMs)
                        event.consecutive == 2 || event.consecutive >= 4 -> Toast(nudge, event.consecutive, event.tMs)
                        else -> alert
                    }
                }

                is RepEvent.ComboBroken -> {
                    alert = Toast(AlertKey.COMBO_BROKEN, event.finalCombo, event.tMs)
                    sounds += SoundRequest(SoundCue.COMBO_BREAK)
                }

                is RepEvent.QualityChanged -> {
                    alert = when (event.quality) {
                        PoseQuality.OK -> if (state.quality != PoseQuality.OK) {
                            Toast(AlertKey.QUALITY_RECOVERED, 0, event.tMs)
                        } else alert
                        else -> Toast(AlertKey.QUALITY_LOST, 0, event.tMs)
                    }
                }

                // Said out loud rather than dropped: a rep refused in silence reads as the game not
                // counting, and this one has a fix the user can make on the next rep.
                is RepEvent.Abandoned -> {
                    if (event.reason == AbandonReason.NOT_SPLIT) alert = Toast(AlertKey.NOT_SPLIT, 0, event.tMs)
                    if (enemyDiedAtMs == Long.MIN_VALUE) {
                        val seen = event.reason != AbandonReason.QUALITY_LOST
                        onAfterStrike(encounter.onRepEnd(event.tMs, seen))
                    }
                }

                else -> Unit
            }
        }

        // Idle pressure only accrues while the tracker can actually see the user, and never while
        // an enemy is mid-death — being hit by a corpse reads as a bug.
        if (tick.quality == PoseQuality.OK && outcome == null && enemyDiedAtMs == Long.MIN_VALUE) {
            for (ce in encounter.advanceTo(tick.tMs)) {
                when (ce) {
                    is CombatEvent.BossTick -> {
                        shake = (shake + 0.6f).coerceAtMost(1f)
                        animator.onHurt(ce.atMs)
                        sounds += SoundRequest(SoundCue.PLAYER_HURT)
                        alert = Toast(AlertKey.IDLE, 0, ce.atMs)
                    }
                    is CombatEvent.Telegraph, is CombatEvent.Ultimate, is CombatEvent.Exhausted ->
                        onUltimate(ce)
                    else -> Unit
                }
            }
        }

        player = encounter.player

        val telegraphed = encounter.ultimateWindingUp

        // Advance once the enemy has finished shattering.
        if (enemyDiedAtMs != Long.MIN_VALUE && tick.tMs - enemyDiedAtMs >= DEATH_MS && outcome == null) {
            outcome = advanceFloor(tick.tMs)
        }

        val ended = outcome
        if (ended != null && state.outcome == null) {
            sounds += SoundRequest(if (ended.cleared) SoundCue.VICTORY else SoundCue.DEFEAT)
        }
        outcome?.let { animator.onRunEnded(tick.tMs, it.cleared) }
        val anim = animator.update(tick.tMs, tick.depth)

        val hurtElapsed = if (enemyHurtAtMs == Long.MIN_VALUE) Long.MAX_VALUE else tick.tMs - enemyHurtAtMs
        val enemyHurt = if (hurtElapsed >= HURT_FLASH_MS) 0f
        else (1f - hurtElapsed.toFloat() / HURT_FLASH_MS).coerceIn(0f, 1f)

        val deathElapsed = if (enemyDiedAtMs == Long.MIN_VALUE) -1L else tick.tMs - enemyDiedAtMs
        val enemyDeath = if (deathElapsed < 0) 0f
        else (deathElapsed.toFloat() / DEATH_MS).coerceIn(0f, 1f)
        val dying = enemyDiedAtMs != Long.MIN_VALUE

        // A half-worth rep makes the run a little longer, and the total says so: done so far plus
        // what is still owed if every rep from here is done the class's way. On the frame the
        // monster falls too, or the total stays on whatever the last rep's strike made it.
        if (Exercises.of(detector.config.exercise).kind == MovementKind.REP) {
            runTotalReps = repsTotal + owedFromHere()
        }

        // The monster visibly winds up across the answer window, so the reps left are readable off
        // its body as well as off the banner.
        val charge = if (!telegraphed) 0f
        else 1f - encounter.answerRepsLeft.toFloat() / Encounter.ANSWER_WINDOW_REPS

        state = state.copy(
            depth = tick.depth,
            depthVelocity = tick.depthVelocity,
            phase = tick.phase,
            quality = tick.quality,
            missingParts = tick.missing,
            placement = placement,
            calibrating = tick.calibration.state == CalibrationState.BOOTSTRAP,
            render = tick.render,
            reps = repsTotal,
            heldMs = heldMs(),
            combo = player.combo,
            maxCombo = maxOf(state.maxCombo, player.combo),
            deepReps = deepReps,
            playerHp = player.hp,
            playerMaxHp = player.maxHp,
            enemyName = encounter.enemy.korean,
            enemyHp = encounter.enemy.remaining,
            enemyMaxHp = encounter.enemy.fullCount,
            runTotalReps = runTotalReps,
            floorIndex = floorIndex,
            ultimateIncoming = telegraphed && outcome == null,
            ultimateRepsLeft = encounter.answerRepsLeft,
            ultimateAnswers = encounter.answersLanded,
            damages = damages,
            alert = alert,
            shake = shake,
            outcome = outcome,
            elapsedMs = if (startedAtMs == Long.MIN_VALUE) 0 else tick.tMs - startedAtMs,
            countEnter = detector.config.countEnter,
            deepEnter = detector.config.deepEnter,
            playerAnim = anim,
            enemyId = encounter.enemy.id,
            enemyIsBoss = encounter.enemy.isBoss,
            enemyHurt = enemyHurt,
            enemyDeath = enemyDeath,
            telegraphCharge = charge,
            sounds = sounds,
        )
        return state
    }

    /** Gives up the run. Everything earned so far is still banked — that is the whole promise. */
    fun quit(): Outcome = finish(cleared = false, atMs = lastFrameMs)

    /**
     * Carries on the run with a different movement, as often as the user likes.
     *
     * The enemy being fought keeps the fraction of it that was left, re-priced in the new movement's
     * reps, and every floor still to come is priced in it too — so the total on screen stays the
     * honest remaining cost of the run. What was done so far is kept as a segment and banked with
     * its own movement at the end.
     *
     * Returns the detector that was retired. The caller banks its calibration: it is no longer fed
     * frames, so reading it from another thread is now safe.
     */
    fun switchExercise(next: RepDetector, atMs: Long = lastFrameMs): RepDetector {
        val retired = detector
        segments += segmentSoFar(atMs)
        heldBeforeMs += segments.last().holdMs
        segStartMs = if (startedAtMs == Long.MIN_VALUE) Long.MIN_VALUE else atMs
        segReps = 0
        segDeep = 0
        segDepthSum = 0f
        segMaxCombo = 0
        // The new detector numbers its reps from one again, and counts its time held from zero.
        bookedRep = -1
        holdCreditedMs = 0L

        detector = next
        // A new movement wants the phone somewhere else, and gets talked into position afresh.
        coach = PlacementCoach(next.config.exercise, next.config)
        resolver = CombatResolver(next.config)
        val to = next.config.exercise
        encounter.switchMovement(dungeon.floors[floorIndex].spawn(difficulty, to, playerClass), resolver)
        runTotalReps = doneSoFar() + owedFromHere()

        // The retired movement's rhythm says nothing about the new one's.
        lastStrikeMs = Long.MIN_VALUE
        readyTopSinceMs = Long.MIN_VALUE
        plankHolding = false

        state = state.copy(
            exercise = to,
            countEnter = next.config.countEnter,
            deepEnter = next.config.deepEnter,
            runTotalReps = runTotalReps,
            heldMs = heldMs(),
            enemyHp = encounter.enemy.remaining,
            enemyMaxHp = encounter.enemy.fullCount,
        )
        return retired
    }

    /**
     * Reps still owed this run, done the class's way: this monster's, then every floor after it,
     * wards included — priced by the same rule the entry screen quoted.
     */
    private fun owedFromHere(): Int {
        val to = detector.config.exercise
        return encounter.repsOwed + dungeon.floors.drop(floorIndex + 1)
            .sumOf { it.repCost(difficulty, to, playerClass) }
    }

    /**
     * What the run has done in the unit of the movement being done now: reps, or for a hold every
     * second held this run. The two are never added together — a total of reps and seconds is
     * neither.
     */
    private fun doneSoFar(): Int =
        if (Exercises.of(detector.config.exercise).kind == MovementKind.HOLD) (heldMs() / 1000L).toInt() else repsTotal

    /** The movements left behind, and the one in progress if it is a hold: what the run banks as held. */
    private fun heldMs(): Long {
        val hold = Exercises.of(detector.config.exercise).kind == MovementKind.HOLD
        return heldBeforeMs + if (hold) detector.sessionSummary().holdMs else 0L
    }

    private fun segmentSoFar(atMs: Long): ExerciseSegment {
        val summary = detector.sessionSummary()
        return ExerciseSegment(
            exercise = detector.config.exercise,
            reps = segReps,
            maxCombo = segMaxCombo,
            deepReps = segDeep,
            meanDepth = if (segReps == 0) 0f else segDepthSum / segReps,
            holdMs = summary.holdMs,
            durationMs = if (segStartMs == Long.MIN_VALUE) 0 else (atMs - segStartMs).coerceAtLeast(0),
            plausibility = summary.plausibility,
        )
    }

    private fun advanceFloor(atMs: Long): Outcome? {
        // Checked before incrementing so the final floor stays the reported floor; incrementing
        // first would leave the HUD showing "4 / 3" on the clear screen.
        if (floorIndex >= dungeon.floors.size - 1) return finish(cleared = true, atMs = atMs)
        styleRepsTotal += encounter.styleReps
        xpBanked += encounter.xp
        floorIndex++
        enemyDiedAtMs = Long.MIN_VALUE
        // Between floors the player is topped up and the chain starts again; a dungeon should be a
        // sequence of fights, not one unbroken set that only the fittest can finish.
        player = player.copy(hp = player.maxHp, combo = 0, tempoStreak = 0)
        encounter = spawnFloor(floorIndex, atMs)
        return null
    }

    private fun spawnFloor(index: Int, atMs: Long): Encounter {
        enemyHurtAtMs = Long.MIN_VALUE
        val template = dungeon.floors[index]
        val enemy = template.spawn(difficulty, detector.config.exercise, playerClass)
        return Encounter(
            initialPlayer = player,
            initialEnemy = enemy,
            difficulty = difficulty,
            resolver = resolver,
            rng = SeededRng(rngSeed + index),
            startedAtMs = atMs,
        )
    }

    private fun finish(cleared: Boolean, atMs: Long): Outcome {
        val all = segments + segmentSoFar(atMs)
        // Every rep's XP is banked whether the run was cleared or not; the bonus is the only part
        // that waits for the clear.
        val xp = xpBanked + encounter.xp
        val bonus = if (cleared) Encounter.clearBonusFor(xp) else 0f
        return Outcome(
            cleared = cleared,
            reps = repsTotal,
            maxCombo = maxOf(state.maxCombo, player.combo),
            deepReps = deepReps,
            durationMs = if (startedAtMs == Long.MIN_VALUE) 0 else atMs - startedAtMs,
            xpEarned = (xp + bonus).roundToInt(),
            crackFraction = if (cleared) 0f else encounter.crackFraction(),
            meanDepth = if (repsTotal == 0) 0f else depthSum / repsTotal,
            styleReps = styleRepsTotal + encounter.styleReps,
            stars = Stars.of(
                meanDepth = if (repsTotal == 0) 0f else depthSum / repsTotal,
                countEnter = detector.config.countEnter,
                deepEnter = detector.config.deepEnter,
            ),
            // Weighted by reps, so a long clean set is not outvoted by a two-rep stretch.
            plausibility = if (repsTotal == 0) all.last().plausibility
            else all.sumOf { (it.plausibility * it.reps).toDouble() }.toFloat() / repsTotal,
            segments = all,
            enemyName = encounter.enemy.korean,
            enemyLeft = if (cleared) 0 else encounter.enemy.remaining,
        )
    }

    /**
     * Raises the booked rep to how deep it actually went: [depth] from the deep line or from the
     * finished rep. [deep] marks it a 깊게 rep in the tallies, as the 깊은 타격 toast already told the
     * user. Only the rep that was booked, and only upward.
     */
    private fun deepen(repIndex: Int, depth: Float, deep: Boolean) {
        if (repIndex != bookedRep) return
        encounter.onDepth(depth)
        val more = (depth - bookedDepth).coerceAtLeast(0f)
        depthSum += more
        segDepthSum += more
        bookedDepth += more
        if (deep && !bookedDeep) {
            bookedDeep = true
            deepReps++
            segDeep++
        }
    }

    private fun alertFor(event: CombatEvent): Toast? = when (event) {
        is CombatEvent.ComboBroken -> Toast(AlertKey.COMBO_BROKEN, event.finalCombo, event.atMs)
        else -> null
    }

    companion object {
        const val DAMAGE_LIFETIME_MS = 1_100L
        const val STYLE_REMIND_EVERY = 3
        const val ALERT_LIFETIME_MS = 2_600L
        const val SHAKE_DECAY = 0.08f
        const val COMBO_MILESTONE = 10
        const val TOP_HOLD_MS = 2_000L

        /** A hold with no tick for this long has ended, whatever the detector last said. */
        const val HOLD_STALE_MS = 1_500L
        const val DEFAULT_CYCLE_MS = 3_000
        const val HURT_FLASH_MS = 220L
        const val DEATH_MS = 700L
    }
}
