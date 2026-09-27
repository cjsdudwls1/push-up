package com.pushuprpg.core.progression

import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind

/**
 * One banked run, as the growth screens read it: what was done, and on which day.
 *
 * The app maps its session rows onto this, so everything below — the climb, the records, the week
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
)

/** One movement's work in its own unit: reps, or whole seconds held for a hold. */
fun SessionFacts.amount(): Int =
    if (Exercises.of(exercise).kind == MovementKind.HOLD) (durationMs / 1000L).toInt() else reps

/** One go at its best: a set's reps, or a hold's whole seconds. */
fun SessionFacts.oneGo(): Int =
    if (Exercises.of(exercise).kind == MovementKind.HOLD) (durationMs / 1000L).toInt() else bestSet

/**
 * Places on the way up, by height in metres.
 *
 * The number that says how far someone has come has to mean something without a table beside it,
 * the way a word count does in a vocabulary app. A lifetime of reps is turned into the height the
 * body has been lifted, and heights are places people know. The first is a few reps off, so the
 * first session already reaches something; after that they spread out, so there is always a next
 * one in sight and never a last one.
 */
enum class Landmark(val meters: Int) {
    CAT_TOWER(2),
    APARTMENT_5F(15),
    APARTMENT_15F(45),
    LIBERTY(93),
    SIXTY_THREE(249),
    LOTTE_TOWER(555),
    BUKHANSAN(836),
    HALLASAN(1_947),
    BAEKDUSAN(2_744),
    FUJI(3_776),
    KILIMANJARO(5_895),
    EVEREST(8_849),
    SPACE(100_000),
}

/** Where a lifetime of reps has got to: the last place passed and the next one. */
data class ClimbProgress(
    val meters: Float,
    val reached: Landmark?,
    val next: Landmark?,
    /** 0..1 from [reached] (or the ground) to [next]; 1 past the last. */
    val fraction: Float,
) {
    val metersToNext: Float get() = next?.let { (it.meters - meters).coerceAtLeast(0f) } ?: 0f
}

/**
 * The climb: every rep's lift added up.
 *
 * Roughly how far each movement raises the body, by an honest-looking round number rather than a
 * measurement — it is a way of counting, not a claim about work. A hold moves nothing, so a second
 * of it is worth what the rest of the game already says it is: half a pushup, since a plank's
 * session volume is twice a pushup's.
 */
object Climb {

    fun metersPer(exercise: ExerciseType): Float = when (exercise) {
        ExerciseType.PUSHUP -> 0.30f
        ExerciseType.SQUAT -> 0.40f
        ExerciseType.LUNGE -> 0.35f
        ExerciseType.PULL_UP -> 0.50f
        ExerciseType.DIP -> 0.30f
        // Per second held: a pushup's lift over a plank's session volume.
        ExerciseType.PLANK -> 0.30f / Exercises.of(ExerciseType.PLANK).sessionVolumeScale
    }

    /** Metres climbed by [work], each movement's in its own unit (see [SessionFacts.amount]). */
    fun meters(work: Map<ExerciseType, Int>): Float =
        work.entries.sumOf { (exercise, amount) -> (amount * metersPer(exercise)).toDouble() }.toFloat()

    fun meters(facts: List<SessionFacts>): Float =
        meters(facts.groupBy { it.exercise }.mapValues { (_, runs) -> runs.sumOf { it.amount() } })

    fun progress(meters: Float): ClimbProgress {
        val reached = Landmark.entries.lastOrNull { meters >= it.meters }
        val next = Landmark.entries.firstOrNull { meters < it.meters }
        val from = reached?.meters ?: 0
        val fraction = if (next == null) 1f else ((meters - from) / (next.meters - from)).coerceIn(0f, 1f)
        return ClimbProgress(meters, reached, next, fraction)
    }

    /** How many more of [exercise] reach the next place — rounded up, since part of a rep is not one. */
    fun toNext(progress: ClimbProgress, exercise: ExerciseType): Int {
        if (progress.next == null) return 0
        return kotlin.math.ceil(progress.metersToNext / metersPer(exercise)).toInt().coerceAtLeast(1)
    }

    /** The places passed between [before] and [after] metres, lowest first. */
    fun passed(before: Float, after: Float): List<Landmark> =
        Landmark.entries.filter { before < it.meters && after >= it.meters }
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
 * What one run changed in the growth the hub shows — the records it broke and the places it passed
 * — for the screen that ends it to say, since that is the moment it was earned.
 */
data class RunGrowth(
    val records: List<NewRecord>,
    val metersBefore: Float,
    val metersAfter: Float,
    val passed: List<Landmark>,
) {
    val climbed: Float get() = (metersAfter - metersBefore).coerceAtLeast(0f)

    companion object {
        /** [run] is what is being banked; [before], everything banked until now. */
        fun of(before: List<SessionFacts>, run: List<SessionFacts>): RunGrowth {
            val bests = Records.of(before)
            val records = run.groupBy { it.exercise }.mapNotNull { (exercise, runs) ->
                val previous = bests[exercise]?.best
                val now = runs.maxOf { it.oneGo() }
                if (Records.isNew(previous, now)) NewRecord(exercise, previous ?: 0, now) else null
            }
            val from = Climb.meters(before)
            val to = from + Climb.meters(run)
            return RunGrowth(records, from, to, Climb.passed(from, to))
        }
    }
}
