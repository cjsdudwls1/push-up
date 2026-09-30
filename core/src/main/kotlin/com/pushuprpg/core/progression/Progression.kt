package com.pushuprpg.core.progression

import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind

/** A streak's length, and the last day (an epoch day) that met its bar. */
data class StreakState(val days: Int, val lastActiveDay: Long)

/**
 * Consecutive-day streak.
 *
 * The bar to keep it is deliberately trivial — ten reps will do — because the streak's job is to
 * get the user to open the app on a bad day, not to extract a workout from them. A genuine break
 * halves the streak rather than zeroing it, so one missed week does not erase a year.
 *
 * It is a day's, not a run's: the bar is met by everything done that day. Judged one run at a time,
 * two sets of five never kept it, and neither did the tutorial.
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
     * How much more of [exercise], in its bar's unit, keeps the streak on [today] after [dayWork]:
     * the least that does, the day's other movements counted at their share of their own bars. Zero
     * once today has kept it.
     */
    fun leftOn(current: StreakState, today: Long, dayWork: Map<ExerciseType, Int>, exercise: ExerciseType): Int {
        if (current.lastActiveDay >= today || maintained(dayWork)) return 0
        val bar = Exercises.of(exercise).streakBar
        return (1..bar).firstOrNull { maintained(sum(dayWork, mapOf(exercise to it))) } ?: bar
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

    fun afterBreak(peak: Int): Int = (peak / 2).coerceAtLeast(0)
}
