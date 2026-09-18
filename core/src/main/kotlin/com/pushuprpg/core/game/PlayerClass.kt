package com.pushuprpg.core.game

import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises

/**
 * The three classes, distinguished by the *kind of training* they reward rather than by a stat
 * spread. A knight wants slow, deep, heavy reps; a mage wants isometric holds; an archer wants a
 * fast, even cadence held for a long set. Picking a class therefore picks a workout style, which
 * is the only way a class choice can mean anything in a game whose input is your body.
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
     * Bundles the class's expected depth, tempo and crit multipliers into one number.
     *
     * Enemy HP is derived through this, which is what keeps time-to-kill equal across classes.
     * It is the *only* knob that should ever be touched to rebalance class parity — never enemy HP,
     * because that is authored in reps and must stay comparable.
     */
    val expectedDprCoefficient: Float,
) {
    KNIGHT(
        korean = "기사",
        baseAtk = 10.0f, atkPerLevel = 2.2f, hpMultiplier = 1.20f,
        comboCap = 50, comboWindowMs = 7000,
        baseCrit = 0.05f, maxCrit = 0.25f, critMultiplier = 2.00f,
        tempoBandMinMs = 2500, tempoBandMaxMs = 4000, tempoK = 0.020f, tempoStreakCap = 5,
        expectedDprCoefficient = 1.57f,
    ),
    MAGE(
        korean = "법사",
        baseAtk = 8.0f, atkPerLevel = 2.6f, hpMultiplier = 0.90f,
        comboCap = 45, comboWindowMs = 7000,
        baseCrit = 0.08f, maxCrit = 0.27f, critMultiplier = 1.90f,
        tempoBandMinMs = 1800, tempoBandMaxMs = 4500, tempoK = 0.015f, tempoStreakCap = 4,
        expectedDprCoefficient = 1.62f,
    ),
    ARCHER(
        korean = "궁수",
        baseAtk = 8.0f, atkPerLevel = 1.9f, hpMultiplier = 0.85f,
        comboCap = 65, comboWindowMs = 5000,
        baseCrit = 0.12f, maxCrit = 0.35f, critMultiplier = 1.70f,
        tempoBandMinMs = 1000, tempoBandMaxMs = 1800, tempoK = 0.035f, tempoStreakCap = 10,
        expectedDprCoefficient = 1.74f,
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
