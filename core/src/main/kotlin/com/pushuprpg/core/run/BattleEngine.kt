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

/**
 * One number floating up the screen when a rep lands.
 *
 * [amount] is the enemy's REMAINING rep count after the hit, not damage dealt. Under the volume
 * model a rep is worth exactly one, so a damage number would be either a permanent "1" or — as it
 * briefly was — a decorative figure in the twenties while the monster lost a single rep. The count
 * remaining is the number the user is actually working toward, and it is true.
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
    BOOTSTRAP, CALIBRATED, SHALLOW_TWICE, SHALLOW_FOUR, COMBO_BROKEN, COMBO_MILESTONE,
    IDLE, BOSS_LOW_HP, ULTIMATE_INCOMING, DEEP_STRIKE, QUALITY_LOST, QUALITY_RECOVERED,
}

/** Everything the battle screen draws, as one immutable snapshot. */
data class BattleState(
    val depth: Float = 0f,
    val depthVelocity: Float = 0f,
    val phase: RepPhase = RepPhase.IDLE,
    val quality: PoseQuality = PoseQuality.NO_SUBJECT,
    val calibrating: Boolean = true,
    val render: RenderSkeleton = RenderSkeleton.EMPTY,
    val reps: Int = 0,
    val combo: Int = 0,
    val maxCombo: Int = 0,
    val deepReps: Int = 0,
    val playerHp: Int = 100,
    val playerMaxHp: Int = 100,
    val enemyName: String = "",
    val enemyHp: Int = 1,
    val enemyMaxHp: Int = 1,
    val floorIndex: Int = 0,
    val floorCount: Int = 1,
    /** Reps this whole run costs, every floor summed. The denominator of the run's progress. */
    val runTotalReps: Int = 0,
    /** The movement being counted, so the screen can say 개 or 초 without guessing. */
    val exercise: ExerciseType = ExerciseType.PUSHUP,
    val ultimateIncoming: Boolean = false,
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
    private val detector: RepDetector,
    private val resolver: CombatResolver,
    private val rngSeed: Long = 0L,
) {
    private var player: PlayerState = initialPlayer
    private var floorIndex = 0
    private var encounter: Encounter = spawnFloor(0, 0L)

    private var startedAtMs = Long.MIN_VALUE
    private var lastFrameMs = 0L
    private var lastStrikeMs = Long.MIN_VALUE
    private var lastBottomMs = 0
    private var readyTopSinceMs = Long.MIN_VALUE
    private var damageSeq = 0L
    private val animator = FighterAnimator(initialPlayer.playerClass)
    private var enemyHurtAtMs = Long.MIN_VALUE
    private var enemyDiedAtMs = Long.MIN_VALUE
    private var plankHolding = false
    private var lastHoldTickMs = 0L
    private var repsTotal = 0
    private var deepReps = 0
    private var depthSum = 0f
    private var xpTotal = 0
    private var shallowStreak = 0

    /**
     * What the run costs in total, computed once at spawn from the same rule the entry screen quoted.
     *
     * Every floor's HP is a rep count, so the sum is the run's whole rep cost — the number the entry
     * picker promised, which the HUD can now show the user working through.
     */
    private val runTotalReps: Int = dungeon.repCost(difficulty, detector.config.exercise)

    private var state = BattleState(
        playerHp = initialPlayer.hp,
        playerMaxHp = initialPlayer.maxHp,
        enemyName = encounter.enemy.korean,
        enemyHp = encounter.enemy.hp,
        enemyMaxHp = encounter.enemy.maxHp,
        floorCount = dungeon.floors.size,
        runTotalReps = runTotalReps,
        exercise = detector.config.exercise,
        countEnter = detector.config.countEnter,
        deepEnter = detector.config.deepEnter,
    )

    fun currentState(): BattleState = state

    fun onPoseFrame(frame: PoseFrame): BattleState {
        val tick = detector.onFrame(frame)
        if (startedAtMs == Long.MIN_VALUE) {
            startedAtMs = tick.tMs
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
                                xpTotal += ce.result.xp
                                if (ce.result.deep) deepReps++
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
                                damageSeq++
                                damages += FloatingDamage(
                                    damageSeq, ce.result.enemy.remaining, ce.result.crit,
                                    ce.result.deep, event.tMs,
                                )
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
                            else -> alert = alertFor(ce) ?: alert
                        }
                    }
                }

                is RepEvent.DeepUpgrade -> {
                    alert = Toast(AlertKey.DEEP_STRIKE, 0, event.tMs)
                    shake = (shake + 0.3f).coerceAtMost(1f)
                    sounds += SoundRequest(SoundCue.REP_DEEP)
                }

                is RepEvent.Completed -> lastBottomMs = event.record.bottomMs

                // A plank pays out continuously rather than per rep. It freezes the boss's rest
                // timer for as long as it is held, which is what lets a player whose arms have
                // given out stay in the fight instead of choosing between quitting and being hit.
                is RepEvent.HoldTick -> {
                    plankHolding = true
                    lastHoldTickMs = event.tMs
                    val hit = encounter.onHold(event.damage, event.tMs)
                    for (ce in hit) {
                        when (ce) {
                            is CombatEvent.Hit -> {
                                damageSeq++
                                damages += FloatingDamage(
                                    damageSeq, ce.result.enemy.remaining, false, false, event.tMs,
                                )
                            }
                            is CombatEvent.EnemyDefeated -> enemyDiedAtMs = event.tMs
                            else -> Unit
                        }
                    }
                }

                is RepEvent.HoldBroken -> plankHolding = false

                is RepEvent.Shallow -> {
                    shallowStreak = event.consecutive
                    // One miss is not worth a message. Two is a nudge, four is an offer to make it
                    // easier — never a scold.
                    alert = when {
                        event.consecutive >= 4 -> Toast(AlertKey.SHALLOW_FOUR, 0, event.tMs)
                        event.consecutive == 2 -> Toast(AlertKey.SHALLOW_TWICE, 0, event.tMs)
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
                    is CombatEvent.Telegraph -> {
                        alert = Toast(AlertKey.ULTIMATE_INCOMING, 0, ce.atMs)
                        sounds += SoundRequest(SoundCue.TELEGRAPH)
                    }
                    is CombatEvent.Ultimate -> if (ce.damage > 0) {
                        shake = 1f
                        animator.onHurt(ce.atMs)
                        sounds += SoundRequest(SoundCue.PLAYER_HURT, volume = 1f)
                    }
                    is CombatEvent.Exhausted -> outcome = finish(cleared = false, atMs = ce.atMs)
                    else -> Unit
                }
            }
        }

        player = encounter.player

        val telegraphed = encounter.rage >= encounter.enemy.rageThreshold - Encounter.TELEGRAPH_LEAD

        // Advance once the enemy has finished shattering.
        if (enemyDiedAtMs != Long.MIN_VALUE && tick.tMs - enemyDiedAtMs >= DEATH_MS && outcome == null) {
            outcome = advanceFloor(tick.tMs)
        }

        if (outcome != null && state.outcome == null) {
            sounds += SoundRequest(if (outcome.cleared) SoundCue.VICTORY else SoundCue.DEFEAT)
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

        // Charge rises across the whole approach to the threshold, not just at the telegraph, so
        // the enemy visibly winds up rather than snapping into a warning.
        val charge = (encounter.rage.toFloat() / encounter.enemy.rageThreshold.coerceAtLeast(1))
            .coerceIn(0f, 1f)

        state = state.copy(
            depth = tick.depth,
            depthVelocity = tick.depthVelocity,
            phase = tick.phase,
            quality = tick.quality,
            calibrating = tick.calibration.state == CalibrationState.BOOTSTRAP,
            render = tick.render,
            reps = repsTotal,
            combo = player.combo,
            maxCombo = maxOf(state.maxCombo, player.combo),
            deepReps = deepReps,
            playerHp = player.hp,
            playerMaxHp = player.maxHp,
            enemyName = encounter.enemy.korean,
            enemyHp = encounter.enemy.hp,
            enemyMaxHp = encounter.enemy.maxHp,
            floorIndex = floorIndex,
            ultimateIncoming = telegraphed && outcome == null,
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

    private fun advanceFloor(atMs: Long): Outcome? {
        // Checked before incrementing so the final floor stays the reported floor; incrementing
        // first would leave the HUD showing "4 / 3" on the clear screen.
        if (floorIndex >= dungeon.floors.size - 1) return finish(cleared = true, atMs = atMs)
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
        val enemy = template.spawn(difficulty, detector.config.exercise)
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
        val summary = detector.sessionSummary()
        val bonus = if (cleared) encounter.clearBonusXp() else 0
        return Outcome(
            cleared = cleared,
            reps = repsTotal,
            maxCombo = maxOf(state.maxCombo, player.combo),
            deepReps = deepReps,
            durationMs = if (startedAtMs == Long.MIN_VALUE) 0 else atMs - startedAtMs,
            xpEarned = xpTotal + bonus,
            crackFraction = if (cleared) 0f else encounter.crackFraction(),
            meanDepth = if (repsTotal == 0) 0f else depthSum / repsTotal,
            plausibility = summary.plausibility,
        )
    }

    private fun alertFor(event: CombatEvent): Toast? = when (event) {
        is CombatEvent.ComboBroken -> Toast(AlertKey.COMBO_BROKEN, event.finalCombo, event.atMs)
        else -> null
    }

    companion object {
        const val DAMAGE_LIFETIME_MS = 1_100L
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
