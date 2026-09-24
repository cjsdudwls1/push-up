package com.pushuprpg.core.game

import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises

/**
 * The two classes, each a way of training rather than a stat spread, by the owner's decision.
 *
 * - [KNIGHT], 근비대: slow and all the way down. A rep that lowers under control for about two
 *   seconds and reaches the 깊게 line is a whole rep off the monster; anything less is half. Fewer
 *   reps a fight, each one harder. (The old 법사 folded in here: a hold at the hard point is the
 *   same training, time under tension.)
 * - [ARCHER], 수행능력: fast and many. A rep that follows the last one inside the movement's brisk
 *   cadence is a whole rep; one that lags inside a set is half. The first rep of a set is always
 *   whole, so resting between sets costs nothing. More reps a fight.
 *
 * Both are still the volume model — a rep is counted by the detector and the count is what fells
 * the monster — but what a rep is worth now depends on doing it the class's way, which is the only
 * thing that can make a class mean something in a game whose input is your body. The numbers that
 * fly up the screen (attack, crit, tempo) remain flavour. See [ClassStyle].
 */
enum class PlayerClass(
    val korean: String,
    val baseAtk: Float,
    val atkPerLevel: Float,
    val hpMultiplier: Float,
    val comboCap: Int,
    val comboWindowMs: Int,
    val baseCrit: Float,
    val maxCrit: Float,
    val critMultiplier: Float,
    val tempoBandMinMs: Int,
    val tempoBandMaxMs: Int,
    val tempoK: Float,
    val tempoStreakCap: Int,
    /**
     * Bundles the class's expected depth, tempo and crit multipliers into one number. Paces the
     * boss's rage only; it no longer touches what a monster costs.
     */
    val expectedDprCoefficient: Float,
    /**
     * Reps a monster costs this class, relative to the content's standard rep cost, when every rep
     * is done the class's way. Hypertrophy is fewer, slower reps; performance is more, faster ones.
     * With [expectedDprCoefficient] retired from pricing, this and [EnemyTemplate.standardRepCost]
     * are the only balance levers on a fight's length.
     */
    val repCostScale: Float,
) {
    KNIGHT(
        korean = "기사",
        baseAtk = 10.0f, atkPerLevel = 2.2f, hpMultiplier = 1.20f,
        comboCap = 50, comboWindowMs = 7000,
        baseCrit = 0.05f, maxCrit = 0.25f, critMultiplier = 2.00f,
        tempoBandMinMs = 2500, tempoBandMaxMs = 5000, tempoK = 0.020f, tempoStreakCap = 5,
        expectedDprCoefficient = 1.57f,
        repCostScale = 0.6f,
    ),
    ARCHER(
        korean = "궁수",
        baseAtk = 8.0f, atkPerLevel = 1.9f, hpMultiplier = 0.85f,
        comboCap = 65, comboWindowMs = 5000,
        baseCrit = 0.12f, maxCrit = 0.35f, critMultiplier = 1.70f,
        tempoBandMinMs = 800, tempoBandMaxMs = 1800, tempoK = 0.035f, tempoStreakCap = 10,
        expectedDprCoefficient = 1.74f,
        repCostScale = 1.4f,
    );

    fun attackAt(level: Int): Float = baseAtk + atkPerLevel * (level - 1)
}

/**
 * Player-chosen difficulty.
 *
 * Capacity scaling alone cannot close the gap between a beginner and an athlete — it is
 * deliberately compressive, so the athlete ends up working at a lower multiple of their own
 * capacity. This is the lever that lets them close it themselves, out in the open rather than
 * through hidden tuning.
 */
enum class Difficulty(
    val korean: String,
    val repMultiplier: Float,
    val enemyAtkMultiplier: Float,
    val rewardMultiplier: Float,
) {
    NOVICE("입문", 0.75f, 0.75f, 0.80f),
    STANDARD("표준", 1.00f, 1.00f, 1.00f),
    ELITE("정예", 1.45f, 1.20f, 1.45f),
    HELL("지옥", 2.10f, 1.45f, 2.20f);

    companion object {
        /**
         * What to put in front of the player by default. Surfacing it as a recommendation rather
         * than leaving them to guess is the whole point: the athlete has to know a harder tier
         * exists or the compression just reads as the game being easy.
         */
        fun recommendedFor(capacity: Float): Difficulty = when {
            capacity >= 80f -> HELL
            capacity >= 40f -> ELITE
            else -> STANDARD
        }
    }
}

/**
 * How a movement converts into damage relative to a pushup.
 *
 * Declared with the movement rather than here, so adding an exercise cannot leave it dealing a
 * silently wrong amount of damage. It is the only lever for making a floor cost the right number of
 * a given movement — enemy HP is derived from a rep cost and is never authored.
 */
fun ExerciseType.coefficient(): Float = Exercises.of(this).damageCoefficient
