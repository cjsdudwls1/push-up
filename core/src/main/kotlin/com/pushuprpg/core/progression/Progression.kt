package com.pushuprpg.core.progression

import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind
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

/** A streak's length, and the last day (an epoch day) that met its bar. */
data class StreakState(val days: Int, val lastActiveDay: Long)

/**
 * Consecutive-day streak.
 *
 * The bar to keep it is deliberately trivial — ten reps will do — because the streak's job is to
 * get the user to open the app on a bad day, not to extract a workout from them. A genuine break
 * halves the streak rather than zeroing it, so one missed week does not erase a year.
 *
 * It is a day's, not a run's: the bar is met by everything done that day, in any mode. Judged one
 * run at a time, two sets of five never kept it, and neither did the tutorial or 고냥이 지켜줘.
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

    /**
     * Whether a day's work on one movement keeps the streak.
     *
     * The bar belongs to the movement, so adding an exercise cannot leave it counted as pushups —
     * which is the bug that once let twelve squats keep a streak needing fifteen, and let a
     * five-minute plank (zero reps by construction) lose one.
     *
     * [amount] is reps for a counted movement and seconds for a hold.
     */
    fun maintained(exercise: ExerciseType, amount: Int): Boolean =
        amount >= Exercises.of(exercise).streakBar

    /**
     * Whether a day's work across several movements keeps the streak.
     *
     * Each movement contributes its share of its own bar, and the shares add: five pushups (half
     * of ten) and eight squats (over half of fifteen) keep it, where judging either alone would
     * not. For one movement this is exactly [maintained].
     */
    fun maintained(work: Map<ExerciseType, Int>): Boolean =
        work.entries.sumOf { (exercise, amount) ->
            amount.toDouble() / Exercises.of(exercise).streakBar
        } >= 1.0 - 1e-9

    /**
     * One movement's work in the unit its bar is in: reps for a counted movement, whole seconds
     * held for a hold.
     */
    fun amount(exercise: ExerciseType, reps: Int, heldMs: Long): Int =
        if (Exercises.of(exercise).kind == MovementKind.HOLD) (heldMs / 1000L).toInt() else reps

    /** Two tallies of work, added movement by movement. */
    fun sum(a: Map<ExerciseType, Int>, b: Map<ExerciseType, Int>): Map<ExerciseType, Int> =
        (a.keys + b.keys).associateWith { (a[it] ?: 0) + (b[it] ?: 0) }

    /**
     * The streak once [dayWork] has been done on [day].
     *
     * [dayWork] is the whole day's so far, per movement in [amount]'s units, the run being banked
     * included. A run belongs to the day it started on — the day its record is filed under — so a
     * set that runs past midnight counts for the evening it began in. A day that has already met the
     * bar changes nothing, and neither does a day before the last one that did (a clock moved back):
     * it never rewinds the streak and never counts a day twice.
     */
    fun advance(current: StreakState, day: Long, dayWork: Map<ExerciseType, Int>): StreakState {
        if (!maintained(dayWork)) return current
        val gap = day - current.lastActiveDay
        return when {
            gap <= 0L -> current.copy(days = current.days.coerceAtLeast(1))
            gap == 1L -> StreakState(current.days + 1, day)
            else -> StreakState(afterBreak(current.days) + 1, day)
        }
    }

    /**
     * A whole day has gone by without the bar being met. Yesterday's streak is still alive today —
     * there is all of today to keep it.
     */
    fun broken(current: StreakState, today: Long): Boolean =
        current.days > 0 && today - current.lastActiveDay >= 2

    /**
     * What the streak reads on [today]. A broken one reads as what it goes on from, [afterBreak] of
     * it, so the number on screen is the one the next day's work adds to, not the one it had.
     */
    fun shown(current: StreakState, today: Long): Int =
        if (broken(current, today)) afterBreak(current.days) else current.days

    /** Bonus max HP from a streak, capped so it never becomes the reason to play. */
    fun hpBonus(days: Int): Float = (days * 0.01f).coerceAtMost(0.25f)

    fun afterBreak(peak: Int): Int = (peak / 2).coerceAtLeast(0)
}
