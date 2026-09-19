package com.pushuprpg.core.game

import com.pushuprpg.core.detect.DetectorConfig
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.RepGrade
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Everything about the player that combat reads. */
data class PlayerState(
    val playerClass: PlayerClass,
    val level: Int = 1,
    val hp: Int = 100,
    val maxHp: Int = 100,
    val combo: Int = 0,
    val tempoStreak: Int = 0,
    val shield: Int = 0,
    val gearMultiplier: Float = 1.0f,
    val streakBonus: Float = 0f,
) {
    /** Reps beyond the combo cap are not wasted; they feed a small, hard-capped attack bonus. */
    val momentumStacks: Int
        get() = min(5, ((combo - playerClass.comboCap) / 5).coerceAtLeast(0))

    val momentumBonus: Float get() = 0.02f * momentumStacks

    val attack: Float
        get() = playerClass.attackAt(level) * (1f + momentumBonus) * gearMultiplier

    companion object {
        fun maxHpFor(playerClass: PlayerClass, level: Int, streakBonus: Float = 0f): Int =
            ((100f + 10f * (level - 1)) * playerClass.hpMultiplier * (1f + streakBonus)).roundToInt()

        fun create(playerClass: PlayerClass, level: Int = 1, streakBonus: Float = 0f): PlayerState {
            val hp = maxHpFor(playerClass, level, streakBonus)
            return PlayerState(playerClass, level, hp, hp, streakBonus = streakBonus)
        }
    }
}

/** One rep, as combat sees it. */
data class RepInput(
    val depth: Float,
    val grade: RepGrade,
    val exercise: ExerciseType = ExerciseType.PUSHUP,
    val cycleMs: Int = 3000,
    val bottomHoldMs: Int = 0,
    /** 0..1 from hip-line straightness. Below 0.5 the rep does not count at all. */
    val formScore: Float = 1.0f,
    val skillMultiplier: Float = 1.0f,
)

data class Enemy(
    val id: String,
    val korean: String,
    val maxHp: Int,
    val hp: Int,
    val defense: Int = 0,
    val attack: Int = 4,
    val rageThreshold: Int = 8,
    val ultimateFraction: Float = 0.35f,
    val weakness: ExerciseType? = null,
    val resist: ExerciseType? = null,
    val isBoss: Boolean = false,
    /**
     * A shield that must be worn down before the enemy itself takes damage.
     *
     * It is a *weakness*, not a wall: a plank tears through it at full rate while other movements
     * chip at it, so the intended answer is obvious without the fight becoming unwinnable for
     * someone who cannot hold a plank. Making it a pool that depletes rather than a permanent
     * damage reduction is what keeps that true — an undepletable ward is just a tenfold rep cost
     * wearing a costume.
     */
    val wardHp: Int = 0,
    val wardMaxHp: Int = 0,
) {
    val isDead: Boolean get() = hp <= 0
    val warded: Boolean get() = wardHp > 0
}

/** The outcome of resolving one rep. */
data class AttackResult(
    val damage: Int,
    val crit: Boolean,
    val deep: Boolean,
    val rejected: Boolean,
    val rejectReason: RejectReason? = null,
    val depthMultiplier: Float = 1f,
    val comboMultiplier: Float = 1f,
    val tempoMultiplier: Float = 1f,
    val formMultiplier: Float = 1f,
    val weaknessMultiplier: Float = 1f,
    val xp: Int = 0,
    val player: PlayerState,
    val enemy: Enemy,
) {
    val enemyDefeated: Boolean get() = enemy.isDead
}

enum class RejectReason { TOO_SHALLOW, FORM_BROKEN }

/**
 * Turns a rep into damage.
 *
 * Deterministic given (player, enemy, rep, rng): no clock reads, no hidden state. The one random
 * draw happens last, after every multiplier is settled, so the damage table can be pinned by tests
 * and future additions cannot perturb existing golden values.
 */
class CombatResolver(
    private val detectorConfig: DetectorConfig = DetectorConfig.pushup(),
) {
    /**
     * Depth-to-damage curve, read off the *detector's* thresholds rather than its own copy.
     *
     * Two sets of numbers for the same two lines would drift apart the first time either is tuned,
     * and the gauge the user watches is drawn from the detector's values — so the payoff they see
     * has to come from the same place.
     */
    fun depthMultiplier(depth: Float, playerClass: PlayerClass, bottomHoldMs: Int): Float {
        val accept = detectorConfig.countEnter
        val deep = detectorConfig.deepEnter

        // Floors at 1.0 rather than dropping to zero below the line. Whether a rep counts at all is
        // the detector's call, not this function's — and the detector deliberately relaxes its
        // accept line while it is still learning a new user's range. Re-testing the raw depth here
        // would mean a beginner's first reps tick the counter up and then deal no damage, which
        // reads as the game ignoring them.
        var m = if (depth < deep) {
            (1.00f + 0.60f * (depth - accept) / (deep - accept)).coerceAtLeast(1.0f)
        } else {
            val slope = if (playerClass == PlayerClass.KNIGHT) 0.25f else 0.15f
            1.60f + slope * (depth - deep) / (100f - deep)
        }
        // The mage is paid for time under tension rather than for depth alone.
        if (playerClass == PlayerClass.MAGE && bottomHoldMs >= 1200) m += 0.15f
        return m
    }

    fun comboMultiplier(combo: Int, playerClass: PlayerClass): Float =
        1.0f + 0.02f * min(combo, playerClass.comboCap)

    fun tempoMultiplier(tempoStreak: Int, playerClass: PlayerClass): Float =
        1.0f + playerClass.tempoK * min(tempoStreak, playerClass.tempoStreakCap)

    fun inTempoBand(cycleMs: Int, playerClass: PlayerClass): Boolean =
        cycleMs in playerClass.tempoBandMinMs..playerClass.tempoBandMaxMs

    fun resolve(player: PlayerState, enemy: Enemy, rep: RepInput, rng: Rng): AttackResult {
        // A collapsing hip line is not a rep, however deep it looks: the depth signal cannot tell
        // a controlled descent from a body folding in the middle, so form has a veto.
        if (rep.formScore < 0.50f) {
            return rejected(player, enemy, RejectReason.FORM_BROKEN)
        }
        // The detector owns the question of whether this was a rep; it has the calibrated range,
        // the hysteresis and the anti-cheat checks. Combat only turns an accepted rep into damage.
        if (rep.grade == RepGrade.SHALLOW) {
            return rejected(player, enemy, RejectReason.TOO_SHALLOW)
        }

        val combo = player.combo + 1
        val tempoStreak = if (inTempoBand(rep.cycleMs, player.playerClass)) player.tempoStreak + 1 else 0

        val depthMult = depthMultiplier(rep.depth, player.playerClass, rep.bottomHoldMs)
        val comboMult = comboMultiplier(combo, player.playerClass)
        val tempoMult = tempoMultiplier(tempoStreak, player.playerClass)
        val formMult = 0.85f + 0.15f * rep.formScore
        val weaknessMult = when (rep.exercise) {
            enemy.weakness -> 1.25f
            enemy.resist -> 0.70f
            else -> 1.00f
        }

        val advanced = player.copy(combo = combo, tempoStreak = tempoStreak)
        val deep = rep.depth >= detectorConfig.deepEnter

        var raw = advanced.attack *
            depthMult * comboMult * tempoMult * formMult * weaknessMult *
            rep.exercise.coefficient() * rep.skillMultiplier

        val critChance = min(
            player.playerClass.maxCrit,
            player.playerClass.baseCrit +
                (if (deep) 0.10f else 0f) +
                0.002f * min(combo, player.playerClass.comboCap),
        )
        val crit = rng.nextFloat() < critChance
        if (crit) raw *= player.playerClass.critMultiplier

        val damage = (floor(raw).toInt() - enemy.defense).coerceAtLeast(1)

        // A plank tears the ward down at full rate; anything else chips at it. Either way the ward
        // is being spent, so the fight always progresses.
        val nextEnemy = if (enemy.warded) {
            val wardDamage = if (rep.exercise == ExerciseType.PLANK) damage else (damage * WARD_CHIP).toInt().coerceAtLeast(1)
            val remainingWard = (enemy.wardHp - wardDamage).coerceAtLeast(0)
            // Overkill carries through, so the rep that breaks the ward also lands on the enemy.
            val spill = (wardDamage - enemy.wardHp).coerceAtLeast(0)
            enemy.copy(wardHp = remainingWard, hp = (enemy.hp - spill).coerceAtLeast(0))
        } else {
            enemy.copy(hp = (enemy.hp - damage).coerceAtLeast(0))
        }

        val xp = (2.0f * depthMult).roundToInt() + if (crit) 1 else 0

        return AttackResult(
            damage = damage,
            crit = crit,
            deep = deep,
            rejected = false,
            depthMultiplier = depthMult,
            comboMultiplier = comboMult,
            tempoMultiplier = tempoMult,
            formMultiplier = formMult,
            weaknessMultiplier = weaknessMult,
            xp = xp,
            player = advanced,
            enemy = nextEnemy,
        )
    }

    private fun rejected(player: PlayerState, enemy: Enemy, reason: RejectReason) = AttackResult(
        damage = 0,
        crit = false,
        deep = false,
        rejected = true,
        rejectReason = reason,
        // Costly, but never a wipe: a cheated rep should sting, not end the run.
        player = player.copy(combo = (player.combo - 3).coerceAtLeast(0)),
        enemy = enemy,
        xp = 0,
    )

    companion object {
        /** What fraction of a non-plank hit goes into wearing down a ward. */
        const val WARD_CHIP = 0.45f

        /**
         * How the player's measured capacity scales an encounter's authored rep cost.
         *
         * The exponent is below 1 on purpose. Scaling linearly would hand someone who can do a
         * hundred pushups a twenty-minute dungeon; compressing a twelvefold capacity spread into a
         * sixfold volume spread keeps every session a sane length. What compression gives away —
         * the athlete working at a lower multiple of their own capacity — the difficulty tier
         * gives back, as a choice they make rather than a number they never see.
         */
        fun capacityScale(capacity: Float): Float =
            ((capacity / 8.0).pow(0.72) * 0.755).toFloat().coerceIn(0.60f, 6.00f)

        /** Average combo multiplier over a fight of [reps] reps, used to derive enemy HP. */
        fun expectedComboMultiplier(reps: Int, comboCap: Int = 50): Float {
            if (reps <= 1) return 1.0f
            val avg = if (reps <= comboCap + 1) {
                (reps - 1) / 2.0
            } else {
                (comboCap.toDouble() * reps - comboCap.toDouble() * (comboCap + 1) / 2.0) / reps
            }
            return (1.0 + 0.02 * avg).toFloat()
        }

        /**
         * Enemy HP is never authored — it is derived from how many reps the encounter should cost.
         *
         * Authoring HP directly would mean every level-up and every balance change silently
         * retunes how long a fight takes, and a number that is right for a beginner is nonsense
         * for an athlete. Authoring the *rep cost* keeps the design intent ("this boss is about
         * fourteen pushups") true for everyone, forever.
         */
        fun enemyMaxHp(
            standardRepCost: Int,
            player: PlayerState,
            difficulty: Difficulty,
            capacity: Float,
            defense: Int = 0,
            referenceLevel: Int = player.level,
        ): Int {
            val reps = (standardRepCost * capacityScale(capacity) * difficulty.repMultiplier)
                .roundToInt().coerceAtLeast(1)
            val k = player.playerClass.expectedDprCoefficient

            // Scaled against the attack of a player at the dungeon's *recommended* level, not the
            // player's own. Deriving it from live attack would mean twenty levels of grinding
            // changed the rep cost of every encounter by exactly zero — an RPG with no power
            // fantasy, where progress is a number that buys nothing. Anchoring to the recommended
            // level keeps content at your level costing what it was authored to cost, while older
            // dungeons genuinely get easier as you outgrow them.
            val referenceAttack = player.playerClass.attackAt(referenceLevel.coerceAtLeast(1)) *
                (1f + player.momentumBonus) * player.gearMultiplier

            val perRep = (referenceAttack * k * expectedComboMultiplier(reps, player.playerClass.comboCap) - defense)
                .coerceAtLeast(1f)
            return (perRep * reps).roundToInt().coerceAtLeast(1)
        }

        /** Reps this encounter is meant to take for a player of this capacity. */
        fun expectedReps(standardRepCost: Int, difficulty: Difficulty, capacity: Float): Int =
            (standardRepCost * capacityScale(capacity) * difficulty.repMultiplier)
                .roundToInt().coerceAtLeast(1)
    }
}
