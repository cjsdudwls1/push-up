package com.pushuprpg.core.game

import com.pushuprpg.core.detect.DetectorConfig
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind
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
    /**
     * Half a rep has been taken off and its pair has not landed yet. A rep done a class's way is a
     * whole rep; one that is not is half — see [ClassStyle]. Two halves make one off the count.
     */
    val halfTaken: Boolean = false,
) {
    val isDead: Boolean get() = hp <= 0
    val warded: Boolean get() = wardHp > 0

    /**
     * Takes [halves] half-reps off, the ward first. A lone half is banked on the enemy until its
     * pair lands, so the count only ever moves in whole reps and the bar never shows a fraction the
     * user cannot do.
     */
    fun spend(halves: Int): Enemy {
        var e = this
        repeat(halves) {
            e = if (!e.halfTaken) e.copy(halfTaken = true)
            else if (e.warded) e.copy(wardHp = e.wardHp - 1, halfTaken = false)
            else e.copy(hp = (e.hp - 1).coerceAtLeast(0), halfTaken = false)
        }
        return e
    }

    /**
     * Reps still owed, ward included — the number the health bar is really showing.
     *
     * HP is a rep count under the volume model, and a ward is more of the same reps rather than a
     * separate resource, so a user answering a warded enemy with the wrong movement sees an honest
     * total rather than a bar that appears stuck.
     */
    val remaining: Int get() = hp + wardHp

    /** [remaining] as spawned: the whole count, ward included, that the bar is drawn against. */
    val fullCount: Int get() = maxHp + wardMaxHp
}

/** The outcome of resolving one rep. */
data class AttackResult(
    /** The number that flies up the screen. Flavour — it is not what the monster loses. */
    val damage: Int,
    /** What the monster actually lost, in half-reps: two for a rep done the class's way, else one. */
    val halvesSpent: Int = 2,
    val crit: Boolean,
    val deep: Boolean,
    val rejected: Boolean,
    val rejectReason: RejectReason? = null,
    val depthMultiplier: Float = 1f,
    val comboMultiplier: Float = 1f,
    val tempoMultiplier: Float = 1f,
    val formMultiplier: Float = 1f,
    val weaknessMultiplier: Float = 1f,
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
        return m
    }

    fun comboMultiplier(combo: Int, playerClass: PlayerClass): Float =
        1.0f + 0.02f * min(combo, playerClass.comboCap)

    fun tempoMultiplier(tempoStreak: Int, playerClass: PlayerClass): Float =
        1.0f + playerClass.tempoK * min(tempoStreak, playerClass.tempoStreakCap)

    fun inTempoBand(cycleMs: Int, playerClass: PlayerClass): Boolean =
        cycleMs in playerClass.tempoBandMinMs..playerClass.tempoBandMaxMs

    /**
     * [halves] is what this rep is worth off the count, in half-reps — two for a rep done the
     * class's way, one otherwise. Decided by [ClassStyle] from how the rep was done, never from its
     * depth: whether it counted at all is the detector's call.
     */
    fun resolve(player: PlayerState, enemy: Enemy, rep: RepInput, rng: Rng, halves: Int = 2): AttackResult {
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

        // One accepted rep, one rep off the count. Every multiplier above still exists and still
        // shows — `raw` is the number that flies up the screen, and depth, combo, tempo and a crit
        // all move it — but none of them decides how many reps the monster costs. That is the whole
        // reason the volume model is legible: the tier says 100 and the user does 100, rather than
        // 100 becoming 71 because they held a combo or 140 because they broke one.
        //
        // It also removes an incentive the app should never have had. Combo and tempo rewarded an
        // unbroken grind, which is bad training and not what a body will do; now they are flavour on
        // a number, not a reason to skip a rest.
        val shown = (floor(raw).toInt() - enemy.defense).coerceAtLeast(1)

        // The ward spends reps like anything else. It used to take a fraction of the damage from
        // the wrong movement, which is not expressible once a rep is worth exactly one — so the
        // ward's SIZE carries that instead, set at spawn from whether the movement is the one it is
        // weak to. Same intent, in whole reps.
        val nextEnemy = enemy.spend(halves)

        return AttackResult(
            damage = shown,
            halvesSpent = halves,
            crit = crit,
            deep = deep,
            rejected = false,
            depthMultiplier = depthMult,
            comboMultiplier = comboMult,
            tempoMultiplier = tempoMult,
            formMultiplier = formMult,
            weaknessMultiplier = weaknessMult,
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
    )

    /**
     * XP for one rep: [XP_PER_REP] for a standard rep at the 인정 line, more the deeper it went and
     * for a crit, times what the rep is worth off the count — [halves] of two, and [repWorth] of the
     * content's standard reps.
     *
     * [depth] is how deep the rep went, not where it struck: the strike fires at the 인정 line on the
     * way down, so reading it there paid every rep as the shallowest that counts. And a rep's worth
     * is what the monster lost for it, so a dungeon pays the same XP however it is done: a 기사's ten
     * slow reps, a 궁수's twenty-six brisk ones and a handful of pull-ups are all the same eighteen
     * standard reps of work. Paid by the rep instead, a 기사 doing it the class's way earned half of
     * what diving through twice as many half reps did, and never levelled on the first dungeon.
     */
    fun repXp(depth: Float, exercise: ExerciseType, playerClass: PlayerClass, halves: Int, crit: Boolean): Float {
        val base = XP_PER_REP * depthMultiplier(depth, playerClass, 0) + if (crit) CRIT_XP else 0f
        return base * halves / 2f * repWorth(exercise, playerClass)
    }

    companion object {
        /** XP for a standard rep at the 인정 line, and what a crit adds to it. */
        const val XP_PER_REP = 2f
        const val CRIT_XP = 1f

        /** What fraction of a non-plank hit goes into wearing down a ward. */
        const val WARD_CHIP = 0.45f

        /**
         * Enemy HP **is** the number of reps the encounter costs. One accepted rep takes one off.
         *
         * A run is one session of one movement, and the total reps in it decide which grade of
         * monster falls — 100 pushups fells this one, 400 fells something further up the ladder.
         * Making HP the rep count directly is what makes that promise literally true rather than
         * approximately true: the health bar is a count of reps remaining, and the number the user
         * sees before they start is the number they will actually do.
         *
         * What this deletes is the reason the old derivation existed. HP used to be reps multiplied
         * by an expected damage per rep, which folded in the player's level, their class, their
         * measured capacity and the combo they were predicted to hold. Every one of those made the
         * cost of a fight something the user could not read off the screen, and the capacity term in
         * particular rested on "largest consecutive set", a measurement that cannot tell a 100 kg
         * bench from an empty bar. Volume needs no such estimate. It is counted.
         *
         * Difficulty stays, because it is a choice the user makes and can see.
         */
        fun enemyMaxHp(
            standardRepCost: Int,
            difficulty: Difficulty,
            exercise: ExerciseType = ExerciseType.PUSHUP,
            playerClass: PlayerClass? = null,
        ): Int = expectedReps(standardRepCost, difficulty, exercise, playerClass)

        /**
         * Reps of [exercise] this encounter costs — for everybody, which is the point.
         *
         * Content is authored in pushups and converted by the movement's own session volume, because
         * a session of a near-max movement is genuinely shorter: a trained pushup session is ~150
         * reps and a trained bench session is 20-40. Scaled by [ExerciseDescriptor.sessionVolumeScale]
         * and NOT by damageCoefficient, which answers a per-rep question and gave 154 pull-ups and
         * 471 bench reps for a 400-rep tier.
         *
         * A hold's number is seconds rather than reps, and is the same for both classes: a hold has
         * no tempo to do one way or the other.
         *
         * Scaled by the class's [PlayerClass.repCostScale]: the reps a monster costs when every one
         * is done the class's way. Fewer for a 기사, more for a 궁수. Null is the content's own
         * standard, before any class — what the rep costs in [Dungeons] are written in.
         */
        fun expectedReps(
            standardRepCost: Int,
            difficulty: Difficulty,
            exercise: ExerciseType = ExerciseType.PUSHUP,
            playerClass: PlayerClass? = null,
        ): Int {
            val descriptor = Exercises.of(exercise)
            return (standardRepCost * difficulty.repMultiplier * descriptor.sessionVolumeScale * classScale(exercise, playerClass))
                .roundToInt().coerceAtLeast(1)
        }

        /**
         * How many of the content's standard reps one rep of [exercise] is worth: the inverse of what
         * [expectedReps] prices a standard rep at, so a rep's share of a fight is its share of the
         * fight's cost. For a hold, one second's.
         */
        fun repWorth(exercise: ExerciseType, playerClass: PlayerClass? = null): Float =
            1f / (Exercises.of(exercise).sessionVolumeScale * classScale(exercise, playerClass))

        private fun classScale(exercise: ExerciseType, playerClass: PlayerClass?): Float =
            if (playerClass == null || Exercises.of(exercise).kind == MovementKind.HOLD) 1f
            else playerClass.repCostScale
    }
}
