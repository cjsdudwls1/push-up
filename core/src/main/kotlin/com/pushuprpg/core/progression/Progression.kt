package com.pushuprpg.core.progression

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Lifetime-rep tiers.
 *
 * Separate from level on purpose, and driven by reps alone. Rank never falls, accrues whether the
 * run was won or lost, and accrues in every mode — it is the part of the game that keeps the
 * promise the clear screen makes: 이기든 지든 사라지지 않아요.
 */
enum class Rank(val korean: String, val threshold: Int) {
    SEEDLING("새싹", 0),
    TRAINEE("수련생", 100),
    WARRIOR("전사", 500),
    VETERAN("베테랑", 1_500),
    ELITE("정예", 3_500),
    CHAMPION("챔피언", 7_000),
    MASTER("마스터", 12_000),
    GRANDMASTER("그랜드마스터", 20_000),
    LEGEND("전설", 35_000),
    IMMORTAL("불멸", 60_000);

    companion object {
        fun forLifetimeReps(reps: Int): Rank =
            entries.last { reps >= it.threshold }

        fun next(rank: Rank): Rank? =
            entries.getOrNull(entries.indexOf(rank) + 1)
    }
}

/** Where the player sits between two ranks, for the card on the clear screen. */
data class RankProgress(
    val rank: Rank,
    val next: Rank?,
    val lifetimeReps: Int,
    val repsToNext: Int,
    /** 0..1, or 1 at the final rank. */
    val fraction: Float,
) {
    companion object {
        fun of(lifetimeReps: Int): RankProgress {
            val rank = Rank.forLifetimeReps(lifetimeReps)
            val next = Rank.next(rank)
            if (next == null) {
                return RankProgress(rank, null, lifetimeReps, 0, 1f)
            }
            val span = (next.threshold - rank.threshold).coerceAtLeast(1)
            val done = (lifetimeReps - rank.threshold).coerceAtLeast(0)
            return RankProgress(
                rank = rank,
                next = next,
                lifetimeReps = lifetimeReps,
                repsToNext = (next.threshold - lifetimeReps).coerceAtLeast(0),
                fraction = (done.toFloat() / span).coerceIn(0f, 1f),
            )
        }
    }
}

object Levels {
    const val MAX_LEVEL = 50

    fun xpToNext(level: Int): Int =
        if (level >= MAX_LEVEL) 0 else (30.0 * level.toDouble().pow(1.5)).roundToInt() + 20

    /** Applies [gained] XP and returns the new level and leftover XP. */
    fun apply(level: Int, xpIntoLevel: Int, gained: Int): LevelUpResult {
        var l = level
        var xp = xpIntoLevel + gained
        var levelsGained = 0
        while (l < MAX_LEVEL) {
            val need = xpToNext(l)
            if (xp < need) break
            xp -= need
            l++
            levelsGained++
        }
        if (l >= MAX_LEVEL) xp = 0
        return LevelUpResult(l, xp, levelsGained)
    }
}

data class LevelUpResult(val level: Int, val xpIntoLevel: Int, val levelsGained: Int) {
    val leveledUp: Boolean get() = levelsGained > 0
}

/**
 * The player's measured working capacity — the largest set of consecutive counted reps seen
 * recently. It never appears in the UI as a number: showing it invites both gaming and shame, and
 * its only job is to size encounters.
 */
object Capacity {
    const val FLOOR = 5f
    const val DEFAULT_PLANK_SECONDS = 20f

    /** Improvement moves it quickly; a bad day does not move it at all. */
    fun update(current: Float, observedSet: Int): Float =
        maxOf(current, 0.75f * current + 0.25f * observedSet).coerceAtLeast(FLOOR)

    /** Decays slowly while the app goes unused, so returning after a break is not brutal. */
    fun decay(current: Float, daysIdle: Int): Float {
        if (daysIdle < 7) return current
        val weeks = daysIdle / 7
        return (current * 0.97f.pow(weeks)).coerceAtLeast(FLOOR)
    }
}

/**
 * Consecutive-day streak.
 *
 * The bar to keep it is deliberately trivial — ten reps will do — because the streak's job is to
 * get the user to open the app on a bad day, not to extract a workout from them. A genuine break
 * halves the streak rather than zeroing it, so one missed week does not erase a year.
 */
object Streak {
    const val MIN_REPS_TO_MAINTAIN = 10
    const val MIN_PLANK_SECONDS = 60
    const val MIN_SQUATS = 15
    const val REST_PASSES_PER_MONTH = 2

    fun maintained(reps: Int, plankSeconds: Int = 0, squats: Int = 0): Boolean =
        reps >= MIN_REPS_TO_MAINTAIN ||
            plankSeconds >= MIN_PLANK_SECONDS ||
            squats >= MIN_SQUATS

    /** Bonus max HP from a streak, capped so it never becomes the reason to play. */
    fun hpBonus(days: Int): Float = (days * 0.01f).coerceAtMost(0.25f)

    fun afterBreak(peak: Int): Int = (peak / 2).coerceAtLeast(0)
}
