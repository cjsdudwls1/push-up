package com.pushuprpg.core.progression

import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind

/**
 * One banked run, as the growth screens read it: what was done, and on which day.
 *
 * The app maps its session rows onto this, so everything below — the calories, the records, the week
 * — is worked out here, where it can be tested, from the same rows the records screen lists.
 */
data class SessionFacts(
    val exercise: ExerciseType,
    val epochDay: Long,
    val startedAtMs: Long,
    val reps: Int,
    /** The longest unbroken set in the run: the most reps one go reached. */
    val bestSet: Int,
    val deepReps: Int,
    /** For a hold, how long it was held; for anything else, how long the run took. */
    val durationMs: Long,
    /** A 고양이 session played to its last life: every set done. */
    val fullSession: Boolean = false,
)

/** One movement's work in its own unit: reps, or whole seconds held for a hold. */
fun SessionFacts.amount(): Int =
    if (Exercises.of(exercise).kind == MovementKind.HOLD) (durationMs / 1000L).toInt() else reps

/** One go at its best: a set's reps, or a hold's whole seconds. */
fun SessionFacts.oneGo(): Int =
    if (Exercises.of(exercise).kind == MovementKind.HOLD) (durationMs / 1000L).toInt() else bestSet

/**
 * Foods, by the calories in one, from a blueberry to a bale of rice.
 *
 * The number that says how far someone has come has to mean something without a table beside it,
 * the way a word count does in a vocabulary app. By the owner's decision it is the calories every
 * rep so far has burned, and calories are measured in food: a height was a number nobody feels, and
 * a meal is one everybody does. The first is a few reps off, so the first session already reaches
 * something; after that they spread out, so there is always a next one in sight, and the last is a
 * lifetime away. The figures are the usual ones for a serving, rounded.
 */
enum class Food(val kcal: Int) {
    BLUEBERRY(1),
    CHERRY_TOMATO(3),
    CANDY(20),
    BANANA(90),
    CHOCO_PIE(170),
    RICE(300),
    RAMEN(500),
    JJAJANGMYEON(800),
    CHICKEN(2_000),
    FIVE_CHICKENS(10_000),
    RAMEN_BOX(20_000),
    RICE_SACK(72_000),
    RICE_BALE(288_000),
}

/** Where a lifetime of reps has got to: the last food it burned off and the next one. */
data class BurnProgress(
    val kcal: Float,
    val reached: Food?,
    val next: Food?,
    /** 0..1 from [reached] (or nothing) to [next]; 1 past the last. */
    val fraction: Float,
) {
    val kcalToNext: Float get() = next?.let { (it.kcal - kcal).coerceAtLeast(0f) } ?: 0f
}

/**
 * Calories burned: every rep's energy, added up.
 *
 * Worked out the standard way — the movement's MET × body weight × the time it took — from the 2024
 * Adult Compendium of Physical Activities. The app does not ask for a weight, so the estimate is for
 * [REFERENCE_KG], and it is said as an estimate wherever it is shown: a way of counting that means
 * something, not a measurement.
 */
object Calories {

    /** The body weight the estimate is for: about the average Korean adult's. */
    const val REFERENCE_KG = 65f

    /**
     * The movement's MET: 02020 vigorous calisthenics (push-ups, pull-ups) 7.5, and dips with them;
     * 02057 high-intensity body-weight exercises (squat, lunge) 6.5; 02024 light calisthenics (plank)
     * 2.8. A set here is taken to the edge, which is the vigorous end of each.
     */
    fun met(exercise: ExerciseType): Float = when (exercise) {
        ExerciseType.PUSHUP, ExerciseType.PULL_UP, ExerciseType.DIP -> 7.5f
        ExerciseType.SQUAT, ExerciseType.LUNGE -> 6.5f
        ExerciseType.PLANK -> 2.8f
    }

    /** How long one rep takes at the pace the game is played at; a hold is counted by the second. */
    fun secondsPer(exercise: ExerciseType): Float = when (exercise) {
        ExerciseType.PUSHUP -> 2.0f
        ExerciseType.SQUAT, ExerciseType.LUNGE, ExerciseType.DIP -> 2.5f
        ExerciseType.PULL_UP -> 3.0f
        ExerciseType.PLANK -> 1.0f
    }

    /** kcal for one rep of [exercise], or one second of a hold: MET × kg × hours. */
    fun perUnit(exercise: ExerciseType): Float = met(exercise) * REFERENCE_KG * secondsPer(exercise) / 3_600f

    /** kcal burned by [work], each movement's in its own unit (see [SessionFacts.amount]). */
    fun of(work: Map<ExerciseType, Int>): Float =
        work.entries.sumOf { (exercise, amount) -> (amount * perUnit(exercise)).toDouble() }.toFloat()

    fun of(facts: List<SessionFacts>): Float =
        of(facts.groupBy { it.exercise }.mapValues { (_, runs) -> runs.sumOf { it.amount() } })

    fun progress(kcal: Float): BurnProgress {
        val reached = Food.entries.lastOrNull { kcal >= it.kcal }
        val next = Food.entries.firstOrNull { kcal < it.kcal }
        val from = reached?.kcal ?: 0
        val fraction = if (next == null) 1f else ((kcal - from) / (next.kcal - from)).coerceIn(0f, 1f)
        return BurnProgress(kcal, reached, next, fraction)
    }

    /** How many more of [exercise] reach the next food — rounded up, since part of a rep is not one. */
    fun toNext(progress: BurnProgress, exercise: ExerciseType): Int {
        if (progress.next == null) return 0
        return kotlin.math.ceil(progress.kcalToNext / perUnit(exercise)).toInt().coerceAtLeast(1)
    }

    /** The foods burned off between [before] and [after] kcal, smallest first. */
    fun passed(before: Float, after: Float): List<Food> =
        Food.entries.filter { before < it.kcal && after >= it.kcal }
}

/** One movement's personal records. */
data class MovementRecord(
    val exercise: ExerciseType,
    /** The best one go: the most reps in a set, or the most whole seconds held. */
    val best: Int,
    /** The first one go ever banked, for how far it has come. */
    val first: Int,
    /** Everything done with it, in its own unit. */
    val total: Int,
    /** Days it was done on. */
    val days: Int,
) {
    /** How far the best has come from the first; never negative, since a record only goes up. */
    val gain: Int get() = (best - first).coerceAtLeast(0)
}

object Records {

    /** Each movement's records, from every run of it. Movements never done are absent. */
    fun of(facts: List<SessionFacts>): Map<ExerciseType, MovementRecord> =
        facts.groupBy { it.exercise }.mapValues { (exercise, runs) ->
            val ordered = runs.sortedBy { it.startedAtMs }
            MovementRecord(
                exercise = exercise,
                best = ordered.maxOf { it.oneGo() },
                first = ordered.firstOrNull { it.oneGo() > 0 }?.oneGo() ?: 0,
                total = ordered.sumOf { it.amount() },
                days = ordered.map { it.epochDay }.distinct().size,
            )
        }.filterValues { it.total > 0 || it.best > 0 }

    /**
     * Whether [now] is a new record over [previousBest]. A first go is not one: there was nothing
     * to beat, and celebrating it would make every first try a record and the word mean nothing.
     */
    fun isNew(previousBest: Int?, now: Int): Boolean =
        previousBest != null && previousBest > 0 && now > previousBest

    /** The runs that broke a record, in the order they did: each against the best before it. */
    fun broken(facts: List<SessionFacts>): List<SessionFacts> {
        val best = HashMap<ExerciseType, Int>()
        val out = mutableListOf<SessionFacts>()
        for (run in facts.sortedBy { it.startedAtMs }) {
            val previous = best[run.exercise]
            val now = run.oneGo()
            if (isNew(previous, now)) out += run
            if (previous == null || now > previous) best[run.exercise] = now
        }
        return out
    }
}

/**
 * What the hub says when the app is opened, by how long it has been since the last workout.
 *
 * Always glad, never reproachful: a user who has been away for a week and is greeted with what they
 * missed has just been given a reason to close the app again.
 */
sealed interface Welcome {
    /** Nothing banked yet. */
    data object First : Welcome

    /** Already done something today. */
    data object Today : Welcome

    /** Last time was yesterday: today continues it. */
    data object Yesterday : Welcome

    /** Back after [days] days. */
    data class Back(val days: Int) : Welcome

    companion object {
        fun of(lastWorkoutDay: Long?, today: Long): Welcome {
            if (lastWorkoutDay == null) return First
            val gap = today - lastWorkoutDay
            return when {
                gap <= 0L -> Today
                gap == 1L -> Yesterday
                else -> Back(gap.toInt())
            }
        }
    }
}

/** A day's total, in the unit the calendar lights a day by. */
data class DayTotal(val epochDay: Long, val reps: Int, val activeMs: Long) {
    val worked: Boolean get() = reps > 0 || activeMs > 0L
}

/** One week, Monday to Sunday. */
data class WeekSummary(
    /** The Monday it starts on, as an epoch day. */
    val start: Long,
    /** Monday first: whether each day had any work in it. */
    val days: List<Boolean>,
    val reps: Int,
    val activeMs: Long,
) {
    val activeDays: Int get() = days.count { it }
}

object Weeks {
    /** The Monday of [epochDay]'s week. Epoch day 0 was a Thursday. */
    fun mondayOf(epochDay: Long): Long = epochDay - Math.floorMod(epochDay + 3, 7L)

    /** The week starting on [monday], from whatever days of it [totals] has. */
    fun summary(totals: List<DayTotal>, monday: Long): WeekSummary {
        val inWeek = totals.filter { it.epochDay in monday until monday + 7 }
        val byDay = inWeek.associateBy { it.epochDay }
        return WeekSummary(
            start = monday,
            days = (0 until 7).map { byDay[monday + it]?.worked == true },
            reps = inWeek.sumOf { it.reps },
            activeMs = inWeek.sumOf { it.activeMs },
        )
    }

    /** This week so far and the whole of last week, as [today] sees them. */
    fun thisAndLast(totals: List<DayTotal>, today: Long): Pair<WeekSummary, WeekSummary> {
        val monday = mondayOf(today)
        return summary(totals, monday) to summary(totals, monday - 7)
    }
}

/** A movement whose best one go a run beat: [previous] was the record, [now] is. */
data class NewRecord(val exercise: ExerciseType, val previous: Int, val now: Int)

/**
 * What one run changed in the growth the hub shows — the records it broke, the calories it burned
 * and the foods they passed — for the screen that ends it to say, since that is the moment it was
 * earned.
 */
data class RunGrowth(
    val records: List<NewRecord>,
    val kcalBefore: Float,
    val kcalAfter: Float,
    val passed: List<Food>,
    /** Gifts this run brought: things the cat found, in the order they arrive. */
    val gifts: List<Gift> = emptyList(),
) {
    val burned: Float get() = (kcalAfter - kcalBefore).coerceAtLeast(0f)

    companion object {
        /**
         * [run] is what is being banked; [before], everything banked until now. [bestStreakBefore]
         * and [bestStreakAfter] are the longest streak ever, before the run and with it: the gifts a
         * streak brings are read from them.
         */
        fun of(
            before: List<SessionFacts>,
            run: List<SessionFacts>,
            bestStreakBefore: Int = 0,
            bestStreakAfter: Int = bestStreakBefore,
        ): RunGrowth {
            val bests = Records.of(before)
            val records = run.groupBy { it.exercise }.mapNotNull { (exercise, runs) ->
                val previous = bests[exercise]?.best
                val now = runs.maxOf { it.oneGo() }
                if (Records.isNew(previous, now)) NewRecord(exercise, previous ?: 0, now) else null
            }
            val from = Calories.of(before)
            val to = from + Calories.of(run)
            val had = Gifts.earned(before, bestStreakBefore)
            val have = Gifts.earned(before + run, bestStreakAfter)
            return RunGrowth(records, from, to, Calories.passed(from, to), Gift.entries.filter { it in have && it !in had })
        }
    }
}

/** A finished week, as the hub sums it up at the start of the next one. */
data class WeekRecap(
    val summary: WeekSummary,
    /** kcal burned that week. */
    val kcal: Float,
    /** Personal records broken that week: runs that beat the best before them, not first goes. */
    val records: Int,
) {
    val empty: Boolean get() = summary.activeDays == 0
}

/** The recap of the week starting on [monday], from every run banked. */
fun Weeks.recap(facts: List<SessionFacts>, monday: Long): WeekRecap {
    val week = monday until monday + 7
    val inWeek = facts.filter { it.epochDay in week }
    val days = inWeek.map { DayTotal(it.epochDay, it.reps, it.durationMs) }
    val records = Records.broken(facts).count { it.epochDay in week }
    return WeekRecap(summary(days, monday), Calories.of(inWeek), records)
}
